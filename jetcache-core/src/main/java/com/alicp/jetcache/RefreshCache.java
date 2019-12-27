package com.alicp.jetcache;

import com.alicp.jetcache.embedded.AbstractEmbeddedCache;
import com.alicp.jetcache.external.AbstractExternalCache;
import com.alicp.jetcache.support.JetCacheExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Created on 2017/5/25.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class RefreshCache<K, V> extends LoadingCache<K, V> {

	private static final Logger logger = LoggerFactory.getLogger(RefreshCache.class);

	private ConcurrentHashMap<Object, RefreshTask> taskMap = new ConcurrentHashMap<>();

	private boolean multiLevelCache;

	public RefreshCache(Cache cache) {
		super(cache);
		multiLevelCache = isMultiLevelCache();
	}

	protected void stopRefresh() {
		List<RefreshTask> tasks = new ArrayList<>();
		tasks.addAll(taskMap.values());
		tasks.forEach(task -> task.cancel());
	}

	@Override
	public void close() {
		stopRefresh();
		super.close();
	}

	private boolean hasLoader() {
		return config.getLoader() != null;
	}

	@Override
	public V computeIfAbsent(K key, Function<K, V> loader) {
		return computeIfAbsent(key, loader, config().isCacheNullValue());
	}

	@Override
	public V computeIfAbsent(K key, Function<K, V> loader, boolean cacheNullWhenLoaderReturnNull) {
		return AbstractCache.computeIfAbsentImpl(key, loader, cacheNullWhenLoaderReturnNull, 0, null, this);
	}

	@Override
	public V computeIfAbsent(K key, Function<K, V> loader, boolean cacheNullWhenLoaderReturnNull, long expireAfterWrite,
			TimeUnit timeUnit) {
		return AbstractCache.computeIfAbsentImpl(key, loader, cacheNullWhenLoaderReturnNull, expireAfterWrite, timeUnit,
				this);
	}

	protected Cache concreteCache() {
		Cache c = getTargetCache();
		while (true) {
			if (c instanceof ProxyCache) {
				c = ((ProxyCache) c).getTargetCache();
			} else if (c instanceof MultiLevelCache) {
				Cache[] caches = ((MultiLevelCache) c).caches();
				c = caches[caches.length - 1];
			} else {
				return c;
			}
		}
	}

	private boolean isMultiLevelCache() {
		Cache c = getTargetCache();
		while (c instanceof ProxyCache) {
			c = ((ProxyCache) c).getTargetCache();
		}
		return c instanceof MultiLevelCache;
	}

	private Object getTaskId(K key) {
		Cache c = concreteCache();
		if (c instanceof AbstractEmbeddedCache) { // 本地缓存
			return ((AbstractEmbeddedCache) c).buildKey(key);
		} else if (c instanceof AbstractExternalCache) { // 远程缓存
			byte[] bs = ((AbstractExternalCache) c).buildKey(key);
			return ByteBuffer.wrap(bs);
		} else {
			logger.error("can't getTaskId from " + c.getClass());
			return null;
		}
	}

	protected void addOrUpdateRefreshTask(K key, CacheLoader<K, V> loader) {
		// 获取缓存刷新策略
		RefreshPolicy refreshPolicy = config.getRefreshPolicy();
		if (refreshPolicy == null) { // 没有则不进行刷新
			return;
		}
		// 获取刷新时间间隔
		long refreshMillis = refreshPolicy.getRefreshMillis();
		if (refreshMillis > 0) {
			// 获取线程任务的ID
			Object taskId = getTaskId(key);
			// 获取对应的RefreshTask，不存在则创建一个
			RefreshTask refreshTask = taskMap.computeIfAbsent(taskId, tid -> {
				logger.debug("add refresh task. interval={},  key={}", refreshMillis, key);
				RefreshTask task = new RefreshTask(taskId, key, loader);
				task.lastAccessTime = System.currentTimeMillis();
				// JetCacheExecutor.heavyIOExecutor()获取一个ScheduledExecutorService周期/延迟线程池，使用10个线程
				// scheduleWithFixedDelay(Runnable command, long initialDelay, long period, TimeUnit unit)
				// 运行的任务task、多久延迟后开始执行、后续执行的周期间隔多长，时间单位
				ScheduledFuture<?> future = JetCacheExecutor.heavyIOExecutor().scheduleWithFixedDelay(task,
						refreshMillis, refreshMillis, TimeUnit.MILLISECONDS);
				task.future = future;
				return task;
			});
			// 设置最后一次访问时间
			refreshTask.lastAccessTime = System.currentTimeMillis();
		}
	}

	@Override
	public V get(K key) throws CacheInvokeException {
		if (config.getRefreshPolicy() != null && hasLoader()) {
			addOrUpdateRefreshTask(key, null);
		}
		return super.get(key);
	}

	@Override
	public Map<K, V> getAll(Set<? extends K> keys) throws CacheInvokeException {
		if (config.getRefreshPolicy() != null && hasLoader()) {
			for (K key : keys) {
				addOrUpdateRefreshTask(key, null);
			}
		}
		return super.getAll(keys);
	}

	class RefreshTask implements Runnable {
		/**
		 * 唯一标志符
		 */
		private Object taskId;
		/**
		 * 缓存的Key
		 */
		private K key;
		/**
		 * 执行方法的CacheLoader对象
		 */
		private CacheLoader<K, V> loader;

		/**
		 * 最后一次访问时间
		 */
		private long lastAccessTime;
		/**
		 * 执行任务
		 */
		private ScheduledFuture future;

		RefreshTask(Object taskId, K key, CacheLoader<K, V> loader) {
			this.taskId = taskId;
			this.key = key;
			this.loader = loader;
		}

		private void cancel() {
			logger.debug("cancel refresh: {}", key);
			// 先停止该任务的执行
			future.cancel(false);
			// 然后从本地缓存中删除该任务
			taskMap.remove(taskId);
		}

		private void load() throws Throwable {
			CacheLoader<K, V> l = loader == null ? config.getLoader() : loader;
			if (l != null) {
				l = CacheUtil.createProxyLoader(cache, l, eventConsumer);
				// 加载原有方法
				V v = l.load(key);
				if (needUpdate(v, l)) {
					// 将返回结果放入缓存
					cache.PUT(key, v);
				}
			}
		}

		private void externalLoad(final Cache concreteCache, final long currentTime) throws Throwable {
			// 获取对应的KEY
			byte[] newKey = ((AbstractExternalCache) concreteCache).buildKey(key);
			// 生成一个新的KEY，用于执行操作时使用
			byte[] lockKey = combine(newKey, "_#RL#".getBytes());
			// 刷新时Lock的超时时间
			long loadTimeOut = RefreshCache.this.config.getRefreshPolicy().getRefreshLockTimeoutMillis();
			// 刷新间隔
			long refreshMillis = config.getRefreshPolicy().getRefreshMillis();
			// 时间戳KEY
			byte[] timestampKey = combine(newKey, "_#TS#".getBytes());

			// AbstractExternalCache buildKey method will not convert byte[]
			// 获取上一次刷新该KEY时的信息
			CacheGetResult refreshTimeResult = concreteCache.GET(timestampKey);
			boolean shouldLoad = false; // 是否需要刷新
			if (refreshTimeResult.isSuccess()) {
				// 当前时间与上一次刷新的时间的间隔是否刷新间隔
				shouldLoad = currentTime >= Long.parseLong(refreshTimeResult.getValue().toString()) + refreshMillis;
			} else if (refreshTimeResult.getResultCode() == CacheResultCode.NOT_EXISTS) { // 无缓存
				shouldLoad = true;
			}

			if (!shouldLoad) { // 无须刷新缓存或者执行原有方法
				if (multiLevelCache) { // 两级缓存
					// 重新设置缓存(真实值从原有缓存中获取，设置新的过期时间和访问时间)
					refreshUpperCaches(key);
				}
				return;
			}

			Runnable r = () -> {
				try {
					// 执行原有方法
					load();
					// AbstractExternalCache buildKey method will not convert byte[]
					// 保存一个key-value至redis，其中的信息为该value的生成时间，刷新缓存
					concreteCache.put(timestampKey, String.valueOf(System.currentTimeMillis()));
				} catch (Throwable e) {
					throw new CacheException("refresh error", e);
				}
			};

			// AbstractExternalCache buildKey method will not convert byte[]
			// 分布式锁，分布式缓存中jetCache没有一个全局分配的功能，这里利用了一个非严格的分布式锁
			// 只有获取这个key的分布式锁，才执行r的操作，即执行方法并刷新缓存
			// 执行上述Runnable r
			boolean lockSuccess = concreteCache.tryLockAndRun(lockKey, loadTimeOut, TimeUnit.MILLISECONDS, r);
			if (!lockSuccess && multiLevelCache) { // 获取锁失败则
				JetCacheExecutor.heavyIOExecutor().schedule(() -> refreshUpperCaches(key), (long) (0.2 * refreshMillis),
						TimeUnit.MILLISECONDS);
			}
		}

		private void refreshUpperCaches(K key) {
			MultiLevelCache<K, V> targetCache = (MultiLevelCache<K, V>) getTargetCache();
			Cache[] caches = targetCache.caches();
			int len = caches.length;

			CacheGetResult cacheGetResult = caches[len - 1].GET(key);
			if (!cacheGetResult.isSuccess()) {
				return;
			}
			for (int i = 0; i < len - 1; i++) {
				caches[i].PUT(key, cacheGetResult.getValue());
			}
		}

		/**
		 * 刷新任务的具体执行
		 */
		@Override
		public void run() {
			try {
				if (config.getRefreshPolicy() == null || (loader == null && !hasLoader())) {
					// 取消执行
					cancel();
					return;
				}
				long now = System.currentTimeMillis();
				long stopRefreshAfterLastAccessMillis = config.getRefreshPolicy().getStopRefreshAfterLastAccessMillis();
				if (stopRefreshAfterLastAccessMillis > 0) {
					// 最后一次访问到现在时间的间隔超过了设置的 stopRefreshAfterLastAccessMillis，则取消当前任务执行
					if (lastAccessTime + stopRefreshAfterLastAccessMillis < now) {
						logger.debug("cancel refresh: {}", key);
						cancel();
						return;
					}
				}
				logger.debug("refresh key: {}", key);
				Cache concreteCache = concreteCache();
				if (concreteCache instanceof AbstractExternalCache) { // 远程缓存刷新
					externalLoad(concreteCache, now);
				} else { // 本地缓存刷新
					load();
				}
			} catch (Throwable e) {
				logger.error("refresh error: key=" + key, e);
			}
		}
	}

	private byte[] combine(byte[] bs1, byte[] bs2) {
		byte[] newArray = Arrays.copyOf(bs1, bs1.length + bs2.length);
		System.arraycopy(bs2, 0, newArray, bs1.length, bs2.length);
		return newArray;
	}
}
