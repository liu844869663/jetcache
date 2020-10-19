package com.alicp.jetcache;

import com.alicp.jetcache.embedded.AbstractEmbeddedCache;
import com.alicp.jetcache.event.*;
import com.alicp.jetcache.external.AbstractExternalCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created on 2016/10/7.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public abstract class AbstractCache<K, V> implements Cache<K, V> {

    private static Logger logger = LoggerFactory.getLogger(AbstractCache.class);

    /**
     * 当缓存未命中时，并发情况同一个Key是否只允许一个线程去加载，其他线程等待结果（可以设置timeout，超时则自己加载并直接返回）
     * 如果是的话则由获取到Key对应的 LoaderLock.signal（采用了 CountDownLatch）的线程进行加载
     * loaderMap临时保存 Key 对应的 LoaderLock 对象
     */
    private volatile ConcurrentHashMap<Object, LoaderLock> loaderMap;

    ConcurrentHashMap<Object, LoaderLock> initOrGetLoaderMap() {
        if (loaderMap == null) {
            synchronized (this) {
                if (loaderMap == null) {
                    loaderMap = new ConcurrentHashMap<>();
                }
            }
        }
        return loaderMap;
    }

    protected void logError(String oper, Object key, Throwable e) {
        StringBuilder sb = new StringBuilder(64);
        sb.append("jetcache(")
                .append(this.getClass().getSimpleName()).append(") ")
                .append(oper)
                .append(" error.");
        if (!(key instanceof byte[])) {
            try {
                sb.append(" key=[")
                        .append(config().getKeyConvertor().apply((K) key))
                        .append(']');
            } catch (Exception ex) {
                // ignore
            }
        }
        if (needLogStackTrace(e)) {
            logger.error(sb.toString(), e);
        } else {
            sb.append(' ');
            while (e != null) {
                sb.append(e.getClass().getName());
                sb.append(':');
                sb.append(e.getMessage());
                e = e.getCause();
                if (e != null) {
                    sb.append("\ncause by ");
                }
            }
            logger.error(sb.toString());
        }

    }

    protected boolean needLogStackTrace(Throwable e) {
//        if (e instanceof CacheEncodeException) {
//            return true;
//        }
//        return false;
        return true;
    }

    public void notify(CacheEvent e) {
        List<CacheMonitor> monitors = config().getMonitors();
        for (CacheMonitor m : monitors) {
            m.afterOperation(e);
        }
    }

    @Override
    public final CacheGetResult<V> GET(K key) {
        long t = System.currentTimeMillis();
        CacheGetResult<V> result;
        if (key == null) {
            result = new CacheGetResult<V>(CacheResultCode.FAIL, CacheResult.MSG_ILLEGAL_ARGUMENT, null);
        } else {
            result = do_GET(key);
        }
        result.future().thenRun(() -> {
            CacheGetEvent event = new CacheGetEvent(this, System.currentTimeMillis() - t, key, result);
            notify(event);
        });
        return result;
    }

    protected abstract CacheGetResult<V> do_GET(K key);

    @Override
    public final MultiGetResult<K, V> GET_ALL(Set<? extends K> keys) {
        long t = System.currentTimeMillis();
        MultiGetResult<K, V> result;
        if (keys == null) {
            result = new MultiGetResult<>(CacheResultCode.FAIL, CacheResult.MSG_ILLEGAL_ARGUMENT, null);
        } else {
            result = do_GET_ALL(keys);
        }
        result.future().thenRun(() -> {
            CacheGetAllEvent event = new CacheGetAllEvent(this, System.currentTimeMillis() - t, keys, result);
            notify(event);
        });
        return result;
    }

    protected abstract MultiGetResult<K, V> do_GET_ALL(Set<? extends K> keys);

    @Override
    public final V computeIfAbsent(K key, Function<K, V> loader, boolean cacheNullWhenLoaderReturnNull) {
        return computeIfAbsentImpl(key, loader, cacheNullWhenLoaderReturnNull,
                0, null, this);
    }

    @Override
    public final V computeIfAbsent(K key, Function<K, V> loader, boolean cacheNullWhenLoaderReturnNull,
                                   long expireAfterWrite, TimeUnit timeUnit) {
        return computeIfAbsentImpl(key, loader, cacheNullWhenLoaderReturnNull,
                expireAfterWrite, timeUnit, this);
    }

    private static <K, V> boolean needUpdate(V loadedValue, boolean cacheNullWhenLoaderReturnNull, Function<K, V> loader) {
        if (loadedValue == null && !cacheNullWhenLoaderReturnNull) {
            return false;
        }
        if (loader instanceof CacheLoader && ((CacheLoader<K, V>) loader).vetoCacheUpdate()) {
            return false;
        }
        return true;
    }

    static <K, V> V computeIfAbsentImpl(K key, Function<K, V> loader, boolean cacheNullWhenLoaderReturnNull,
                                               long expireAfterWrite, TimeUnit timeUnit, Cache<K, V> cache) {
    	// 获取内部的 Cache 对象
        AbstractCache<K, V> abstractCache = CacheUtil.getAbstractCache(cache);
        // 封装 loader 函数成一个 ProxyLoader 对象，主要在重新加载缓存后发出一个 CacheLoadEvent 到 CacheMonitor
        CacheLoader<K, V> newLoader = CacheUtil.createProxyLoader(cache, loader, abstractCache::notify);
        CacheGetResult<V> r;
        if (cache instanceof RefreshCache) { // 该缓存实例需要刷新
            RefreshCache<K, V> refreshCache = ((RefreshCache<K, V>) cache);
            /*
             * 从缓存中获取数据
             * 如果是多级缓存（先从本地缓存获取，获取不到则从远程缓存获取）
             * 如果缓存数据是从远程缓存获取到的数据则会更新至本地缓存，并且如果本地缓存没有设置 localExpire 则使用远程缓存的到期时间作为自己的到期时间
             * 我一般不设置 localExpire ，因为可能导致本地缓存的有效时间比远程缓存的有效时间更长
             * 如果设置 localExpire 了记得设置 expireAfterAccessInMillis
             */
            r = refreshCache.GET(key);
            // 添加/更新当前 RefreshCache 的刷新缓存任务，存放于 RefreshCache 的 taskMap 中
            refreshCache.addOrUpdateRefreshTask(key, newLoader);
        } else {
            // 从缓存中获取数据
            r = cache.GET(key);
        }
        if (r.isSuccess()) { // 缓存命中
            return r.getValue();
        } else { // 缓存未命中
            // 创建当缓存未命中去更新缓存的函数
            Consumer<V> cacheUpdater = (loadedValue) -> {
                if(needUpdate(loadedValue, cacheNullWhenLoaderReturnNull, newLoader)) {
                    /*
                     * 未在缓存注解中配置 key 的生成方式则默认取入参作为缓存 key
                     * 在进入当前方法时是否可以考虑为 key 创建一个副本？？？？
                     * 因为缓存未命中然后通过 loader 重新加载方法时，如果方法内部对入参进行了修改，那么生成的缓存 key 也会被修改
                     * 从而导致相同的 key 进入该方法时一直与缓存中的 key 不相同，一直出现缓存未命中
                     */
                    if (timeUnit != null) {
                        cache.PUT(key, loadedValue, expireAfterWrite, timeUnit).waitForResult();
                    } else {
                        cache.PUT(key, loadedValue).waitForResult();
                    }
                }
            };

            V loadedValue;
            if (cache.config().isCachePenetrationProtect()) { // 添加了 @CachePenetrationProtect 注解
            	// 一个JVM只允许一个线程执行
                loadedValue = synchronizedLoad(cache.config(), abstractCache, key, newLoader, cacheUpdater);
            } else {
            	// 执行方法
                loadedValue = newLoader.apply(key);
                // 将新的结果异步缓存
                cacheUpdater.accept(loadedValue);
            }

            return loadedValue;
        }
    }

    static <K, V> V synchronizedLoad(CacheConfig config, AbstractCache<K,V> abstractCache,
                                     K key, Function<K, V> newLoader, Consumer<V> cacheUpdater) {
        ConcurrentHashMap<Object, LoaderLock> loaderMap = abstractCache.initOrGetLoaderMap();
        Object lockKey = buildLoaderLockKey(abstractCache, key);
        while (true) {
            // 为什么加一个 create[] 数组 疑问？？
            boolean create[] = new boolean[1];
            LoaderLock ll = loaderMap.computeIfAbsent(lockKey, (unusedKey) -> {
                create[0] = true;
                LoaderLock loaderLock = new LoaderLock();
                loaderLock.signal = new CountDownLatch(1);
                loaderLock.loaderThread = Thread.currentThread();
                return loaderLock;
            });
            if (create[0] || ll.loaderThread == Thread.currentThread()) {
                try {
                    // 加载该 Key 实例的方法
                    V loadedValue = newLoader.apply(key);
                    ll.success = true;
                    ll.value = loadedValue;
                    // 将重新加载的数据更新至缓存
                    cacheUpdater.accept(loadedValue);
                    return loadedValue;
                } finally {
                    // 标记已完成
                    ll.signal.countDown();
                    if (create[0]) {
                        loaderMap.remove(lockKey);
                    }
                }
            } else { // 等待其他线程加载，如果出现异常或者超时则自己加载返回数据，但是不更新缓存
                try {
                    Duration timeout = config.getPenetrationProtectTimeout();
                    if (timeout == null) {
                        ll.signal.await();
                    } else {
                        boolean ok = ll.signal.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
                        if(!ok) {
                            logger.info("loader wait timeout:" + timeout);
                            return newLoader.apply(key);
                        }
                    }
                } catch (InterruptedException e) {
                    logger.warn("loader wait interrupted");
                    return newLoader.apply(key);
                }
                if (ll.success) {
                    return (V) ll.value;
                } else {
                    continue;
                }

            }
        }
    }

    private static Object buildLoaderLockKey(Cache c, Object key) {
        if (c instanceof AbstractEmbeddedCache) {
            return ((AbstractEmbeddedCache) c).buildKey(key);
        } else if (c instanceof AbstractExternalCache) {
            byte bytes[] = ((AbstractExternalCache) c).buildKey(key);
            return ByteBuffer.wrap(bytes);
        } else if (c instanceof MultiLevelCache) {
            c = ((MultiLevelCache) c).caches()[0];
            return buildLoaderLockKey(c, key);
        } else if(c instanceof ProxyCache) {
            c = ((ProxyCache) c).getTargetCache();
            return buildLoaderLockKey(c, key);
        } else {
            throw new CacheException("impossible");
        }
    }

    @Override
    public final CacheResult PUT(K key, V value, long expireAfterWrite, TimeUnit timeUnit) {
        long t = System.currentTimeMillis();
        CacheResult result;
        if (key == null) {
            result = CacheResult.FAIL_ILLEGAL_ARGUMENT;
        } else {
            result = do_PUT(key, value, expireAfterWrite, timeUnit);
        }
        result.future().thenRun(() -> {
            CachePutEvent event = new CachePutEvent(this, System.currentTimeMillis() - t, key, value, result);
            // 发送 PUT 事件
            notify(event);
        });
        return result;
    }

    protected abstract CacheResult do_PUT(K key, V value, long expireAfterWrite, TimeUnit timeUnit);

    @Override
    public final CacheResult PUT_ALL(Map<? extends K, ? extends V> map, long expireAfterWrite, TimeUnit timeUnit) {
        long t = System.currentTimeMillis();
        CacheResult result;
        if (map == null) {
            result = CacheResult.FAIL_ILLEGAL_ARGUMENT;
        } else {
            result = do_PUT_ALL(map, expireAfterWrite, timeUnit);
        }
        result.future().thenRun(() -> {
            CachePutAllEvent event = new CachePutAllEvent(this, System.currentTimeMillis() - t, map, result);
            // 发送 PUTAll 事件
            notify(event);
        });
        return result;
    }

    protected abstract CacheResult do_PUT_ALL(Map<? extends K, ? extends V> map, long expireAfterWrite, TimeUnit timeUnit);

    @Override
    public final CacheResult REMOVE(K key) {
        long t = System.currentTimeMillis();
        CacheResult result;
        if (key == null) {
            result = CacheResult.FAIL_ILLEGAL_ARGUMENT;
        } else {
            result = do_REMOVE(key);
        }
        result.future().thenRun(() -> {
            CacheRemoveEvent event = new CacheRemoveEvent(this, System.currentTimeMillis() - t, key, result);
            // 发送 Remove 事件
            notify(event);
        });
        return result;
    }

    protected abstract CacheResult do_REMOVE(K key);

    @Override
    public final CacheResult REMOVE_ALL(Set<? extends K> keys) {
        long t = System.currentTimeMillis();
        CacheResult result;
        if (keys == null) {
            result = CacheResult.FAIL_ILLEGAL_ARGUMENT;
        } else {
            result = do_REMOVE_ALL(keys);
        }
        result.future().thenRun(() -> {
            CacheRemoveAllEvent event = new CacheRemoveAllEvent(this, System.currentTimeMillis() - t, keys, result);
            // 发送 RemoveAll 事件
            notify(event);
        });
        return result;
    }

    protected abstract CacheResult do_REMOVE_ALL(Set<? extends K> keys);

    @Override
    public final CacheResult PUT_IF_ABSENT(K key, V value, long expireAfterWrite, TimeUnit timeUnit) {
        long t = System.currentTimeMillis();
        CacheResult result;
        if (key == null) {
            result = CacheResult.FAIL_ILLEGAL_ARGUMENT;
        } else {
            result = do_PUT_IF_ABSENT(key, value, expireAfterWrite, timeUnit);
        }
        result.future().thenRun(() -> {
            CachePutEvent event = new CachePutEvent(this, System.currentTimeMillis() - t, key, value, result);
            // 发送 PUT 事件
            notify(event);
        });
        return result;
    }

    protected abstract CacheResult do_PUT_IF_ABSENT(K key, V value, long expireAfterWrite, TimeUnit timeUnit);

    /**
     * 重新加载数据锁
     */
    static class LoaderLock {
        /**
         * 栅栏
         */
        CountDownLatch signal;
        /**
         * 持有的线程
         */
        Thread loaderThread;
        /**
         * 是否加载成功
         */
        boolean success;
        /**
         * 加载出来的数据
         */
        Object value;
    }
}
