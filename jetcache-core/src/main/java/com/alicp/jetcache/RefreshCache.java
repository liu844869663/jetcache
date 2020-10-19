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

	/**
	 * 用于保存刷新任务
	 */
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

	/**
	 * 用于关闭资源
	 * 查看 com.alicp.jetcache.anno.support.ConfigProvider#doShutdown()
	 */
	@Override
	public void close() {
		// 关闭刷新任务
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
				// 如果是两级缓存则返回远程缓存
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
				/*
				 * 获取 ScheduledExecutorService 周期/延迟线程池，10个核心线程，创建的线程都是守护线程
				 * scheduleWithFixedDelay(Runnable command, long initialDelay, long period, TimeUnit unit)
				 * 运行的任务task、多久延迟后开始执行、后续执行的周期间隔多长，时间单位
				 * 通过其创建一个循环任务，用于刷新缓存数据
				 */
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
		 * 唯一标志符，也就是Key转换后的值
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
		 * 该 Task 的执行策略
		 */
		private ScheduledFuture future;

		RefreshTask(Object taskId, K key, CacheLoader<K, V> loader) {
			this.taskId = taskId;
			this.key = key;
			this.loader = loader;
		}

		private void cancel() {
			logger.debug("cancel refresh: {}", key);
			// 尝试中断当前任务
			future.cancel(false);
			// 从任务列表中删除
			taskMap.remove(taskId);
		}

		/**
		 * 重新加载数据
		 *
		 * @throws Throwable 异常
		 */
		private void load() throws Throwable {
			CacheLoader<K, V> l = loader == null ? config.getLoader() : loader;
			if (l != null) {
				// 封装 CacheLoader 成 ProxyLoader，加载后会发起 Load 事件
				l = CacheUtil.createProxyLoader(cache, l, eventConsumer);
				// 加载
				V v = l.load(key);
				if (needUpdate(v, l)) {
					// 将重新加载的数据放入缓存
					cache.PUT(key, v);
				}
			}
		}

		/**
		 * 远程加载数据
		 *
		 * @param concreteCache 缓存对象
		 * @param currentTime   当前时间
		 * @throws Throwable 异常
		 */
		private void externalLoad(final Cache concreteCache, final long currentTime) throws Throwable {
			// 获取 Key 转换后的值
			byte[] newKey = ((AbstractExternalCache) concreteCache).buildKey(key);
			// 创建分布式锁对应的Key
			byte[] lockKey = combine(newKey, "_#RL#".getBytes());
			// 分布式锁的存在时间
			long loadTimeOut = RefreshCache.this.config.getRefreshPolicy().getRefreshLockTimeoutMillis();
			// 刷新间隔
			long refreshMillis = config.getRefreshPolicy().getRefreshMillis();
			// Key对应的时间戳Key（用于存放上次刷新时间）
			byte[] timestampKey = combine(newKey, "_#TS#".getBytes());

			// AbstractExternalCache buildKey method will not convert byte[]
			// 获取Key上一次刷新时间
			CacheGetResult refreshTimeResult = concreteCache.GET(timestampKey);
			boolean shouldLoad = false; // 是否需要重新加载
			if (refreshTimeResult.isSuccess()) {
				// 当前时间与上一次刷新的时间间隔是否大于或等于刷新间隔
				shouldLoad = currentTime >= Long.parseLong(refreshTimeResult.getValue().toString()) + refreshMillis;
			} else if (refreshTimeResult.getResultCode() == CacheResultCode.NOT_EXISTS) { // 无缓存
				shouldLoad = true;
			}

			if (!shouldLoad) {
				if (multiLevelCache) {
					// 将顶层的缓存数据更新至低层的缓存中，例如将远程的缓存数据放入本地缓存
					// 因为如果是多级缓存，创建刷新任务后，我们只需更新远程的缓存，然后从远程缓存获取缓存数据更新低层的缓存，保证缓存一致
					refreshUpperCaches(key);
				}
				return;
			}

			// 重新加载
			Runnable r = () -> {
				try {
					load();
					// AbstractExternalCache buildKey method will not convert byte[]
					// 保存一个key-value至redis，其中的信息为该value的生成时间，刷新缓存
					concreteCache.put(timestampKey, String.valueOf(System.currentTimeMillis()));
				} catch (Throwable e) {
					throw new CacheException("refresh error", e);
				}
			};

			// AbstractExternalCache buildKey method will not convert byte[]
			// 分布式缓存没有一个全局分配的功能，这里尝试获取一把非严格的分布式锁，获取锁的超时时间默认60秒，也就是获取到这把锁最多可以拥有60秒
			// 只有获取Key对应的这把分布式锁，才执行重新加载的操作
			boolean lockSuccess = concreteCache.tryLockAndRun(lockKey, loadTimeOut, TimeUnit.MILLISECONDS, r);
			if (!lockSuccess && multiLevelCache) { // 没有获取到锁并且是多级缓存
				// 这个时候应该有其他实例在刷新缓存，所以这里设置过一会直接获取远程的缓存数据更新到本地
				// 创建一个延迟任务（1/5刷新间隔后），将最顶层的缓存数据更新至每一层
				JetCacheExecutor.heavyIOExecutor().schedule(() -> refreshUpperCaches(key), (long) (0.2 * refreshMillis),
						TimeUnit.MILLISECONDS);
			}
		}

		private void refreshUpperCaches(K key) {
			MultiLevelCache<K, V> targetCache = (MultiLevelCache<K, V>) getTargetCache();
			Cache[] caches = targetCache.caches();
			int len = caches.length;

			// 获取多级缓存中顶层的缓存数据
			CacheGetResult cacheGetResult = caches[len - 1].GET(key);
			if (!cacheGetResult.isSuccess()) {
				return;
			}
			// 将缓存数据重新放入低层缓存
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
				// 获取缓存实例对象，如果是多层则返回顶层，也就是远程缓存
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
