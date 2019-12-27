/**
 * Created on  13-09-04 15:34
 */
package com.alicp.jetcache.anno.support;

import com.alibaba.fastjson.JSON;
import com.alicp.jetcache.Cache;
import com.alicp.jetcache.CacheConfigException;
import com.alicp.jetcache.MultiLevelCacheBuilder;
import com.alicp.jetcache.anno.CacheConsts;
import com.alicp.jetcache.anno.CacheType;
import com.alicp.jetcache.anno.EnableCache;
import com.alicp.jetcache.anno.method.CacheHandler;
import com.alicp.jetcache.anno.method.CacheInvokeConfig;
import com.alicp.jetcache.anno.method.CacheInvokeContext;
import com.alicp.jetcache.embedded.EmbeddedCacheBuilder;
import com.alicp.jetcache.external.ExternalCacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class CacheContext {

    private static Logger logger = LoggerFactory.getLogger(CacheContext.class);

    private static ThreadLocal<CacheThreadLocal> cacheThreadLocal = new ThreadLocal<CacheThreadLocal>() {
        @Override
        protected CacheThreadLocal initialValue() {
            return new CacheThreadLocal();
        }
    };

    private ConfigProvider configProvider;
    private GlobalCacheConfig globalCacheConfig;

    protected SimpleCacheManager cacheManager;

    public CacheContext(ConfigProvider configProvider, GlobalCacheConfig globalCacheConfig) {
        this.globalCacheConfig = globalCacheConfig;
        this.configProvider = configProvider;
        cacheManager = configProvider.getCacheManager();
    }

    public CacheInvokeContext createCacheInvokeContext(ConfigMap configMap) {
    	// 创建一个本次调用的上下文
        CacheInvokeContext c = newCacheInvokeContext();
        // 以下方法很关键,创建对应的缓存对象
        c.setCacheFunction((invokeContext, cacheAnnoConfig) -> {
            Cache cache = cacheAnnoConfig.getCache();
            if (cache == null) {
                if (cacheAnnoConfig instanceof CachedAnnoConfig) { // 缓存注解
                    cache = createCacheByCachedConfig((CachedAnnoConfig) cacheAnnoConfig, invokeContext);
                } else if ((cacheAnnoConfig instanceof CacheInvalidateAnnoConfig) || (cacheAnnoConfig instanceof CacheUpdateAnnoConfig)) { // 更新/使失效缓存注解
                    CacheInvokeConfig cacheDefineConfig = configMap.getByCacheName(cacheAnnoConfig.getArea(), cacheAnnoConfig.getName());
                    if (cacheDefineConfig == null) {
                        String message = "can't find @Cached definition with area=" + cacheAnnoConfig.getArea()
                                + " name=" + cacheAnnoConfig.getName() +
                                ", specified in " + cacheAnnoConfig.getDefineMethod();
                        CacheConfigException e = new CacheConfigException(message);
                        logger.error("Cache operation aborted because can't find @Cached definition", e);
                        return null;
                    }
                    cache = createCacheByCachedConfig(cacheDefineConfig.getCachedAnnoConfig(), invokeContext);
                }
                cacheAnnoConfig.setCache(cache);
            }
            return cache;
        });
        return c;
    }

    private Cache createCacheByCachedConfig(CachedAnnoConfig ac, CacheInvokeContext invokeContext) {
    	// 从CachedAnnoConfig注解详情中获取缓存区域(默认default)
        String area = ac.getArea();
        // 从CachedAnnoConfig注解详情中获取缓存名称(作为前缀)
        String cacheName = ac.getName();
        if (CacheConsts.isUndefined(cacheName)) { // 缓存名称没有定义

        	// 如果注解中没有定义cacheName,则自动生成(类名+方法名+(参数类型)+返回类型)
        	// 如果设置了hiddenPackages,则去除包含hiddenPackages的前缀
            cacheName = configProvider.createCacheNameGenerator(invokeContext.getHiddenPackages())
                    .generateCacheName(invokeContext.getMethod(), invokeContext.getTargetObject());
        }
        // 创建缓存对象
        Cache cache = __createOrGetCache(ac, area, cacheName);
        return cache;
    }

    @Deprecated
    public <K, V> Cache<K, V> getCache(String cacheName) {
        return getCache(CacheConsts.DEFAULT_AREA, cacheName);
    }

    @Deprecated
    public <K, V> Cache<K, V> getCache(String area, String cacheName) {
        Cache cache = cacheManager.getCacheWithoutCreate(area, cacheName);
        return cache;
    }

    public Cache __createOrGetCache(CachedAnnoConfig cachedAnnoConfig, String area, String cacheName) {
    	// 缓存名称拼接
        String fullCacheName = area + "_" + cacheName;
        // 从area获取cacheName的Cache缓存
        Cache cache = cacheManager.getCacheWithoutCreate(area, cacheName);
        if (cache == null) {
            synchronized (this) {
                cache = cacheManager.getCacheWithoutCreate(area, cacheName);
                if (cache == null) {
                    if (globalCacheConfig.isAreaInCacheName()) { // areaName是否作为缓存的key名称前缀,默认为true
                        // for compatible reason, if we use default configuration, the prefix should same to that version <=2.4.3
                        cache = buildCache(cachedAnnoConfig, area, fullCacheName);
                    } else { // 直接使用cacheName
                        cache = buildCache(cachedAnnoConfig, area, cacheName);
                    }
                    cacheManager.putCache(area, cacheName, cache);
                }
            }
        }
        return cache;
    }

    protected Cache buildCache(CachedAnnoConfig cachedAnnoConfig, String area, String cacheName) {
        Cache cache;
        if (cachedAnnoConfig.getCacheType() == CacheType.LOCAL) { // 本地缓存
            cache = buildLocal(cachedAnnoConfig, area);
        } else if (cachedAnnoConfig.getCacheType() == CacheType.REMOTE) { // 远程缓存
            cache = buildRemote(cachedAnnoConfig, area, cacheName);
        } else { // 两级缓存
        	// 本地缓存
            Cache local = buildLocal(cachedAnnoConfig, area);
            // 远程缓存
            Cache remote = buildRemote(cachedAnnoConfig, area, cacheName);

            // 是否本地缓存失效
            boolean useExpireOfSubCache = cachedAnnoConfig.getLocalExpire() > 0;
            // 创建一个两级缓存CacheBuilder
            cache = MultiLevelCacheBuilder.createMultiLevelCacheBuilder()
                    .expireAfterWrite(remote.config().getExpireAfterWriteInMillis(), TimeUnit.MILLISECONDS)
                    .addCache(local, remote)
                    .useExpireOfSubCache(useExpireOfSubCache)
                    .cacheNullValue(cachedAnnoConfig.isCacheNullValue())
                    .buildCache();
        }
        // 设置缓存刷新策略
        cache.config().setRefreshPolicy(cachedAnnoConfig.getRefreshPolicy());
        // 将cache封装成CacheHandlerRefreshCache
        cache = new CacheHandler.CacheHandlerRefreshCache(cache);

        // 设置缓存未命中时，JVM是否只允许一个线程执行方法，其他线程等待，全局配置默认为false
        cache.config().setCachePenetrationProtect(globalCacheConfig.isPenetrationProtect());
        PenetrationProtectConfig protectConfig = cachedAnnoConfig.getPenetrationProtectConfig();
        if (protectConfig != null) { // 方法的@CachePenetrationProtect注解
            cache.config().setCachePenetrationProtect(protectConfig.isPenetrationProtect());
            cache.config().setPenetrationProtectTimeout(protectConfig.getPenetrationProtectTimeout());
        }

        if (configProvider.getCacheMonitorManager() != null) {
        	// 添加监控统计配置
            configProvider.getCacheMonitorManager().addMonitors(area, cacheName, cache);
        }
        return cache;
    }

    protected Cache buildRemote(CachedAnnoConfig cachedAnnoConfig, String area, String cacheName) {
    	// 获取area远程缓存实例
        ExternalCacheBuilder cacheBuilder = (ExternalCacheBuilder) globalCacheConfig.getRemoteCacheBuilders().get(area);
        if (cacheBuilder == null) {
            throw new CacheConfigException("no remote cache builder: " + area);
        }
        // 克隆一个CacheBuilder
        cacheBuilder = (ExternalCacheBuilder) cacheBuilder.clone();

        if (cachedAnnoConfig.getExpire() > 0 ) {
        	// 设置失效时间
            cacheBuilder.expireAfterWrite(cachedAnnoConfig.getExpire(), cachedAnnoConfig.getTimeUnit());
        }

        // 设置前缀
        if (cacheBuilder.getConfig().getKeyPrefix() != null) {
            cacheBuilder.setKeyPrefix(cacheBuilder.getConfig().getKeyPrefix() + cacheName);
        } else {
            cacheBuilder.setKeyPrefix(cacheName);
        }

        if (!CacheConsts.isUndefined(cachedAnnoConfig.getKeyConvertor())) {
        	// 设置key的转换器，KeyConvertor为函数编程通过DefaultKeyConvertorParser生成一个FastjsonKeyConvertor
        	// FastjsonKeyConvertor为单例模式，实际上通过alibaba的JSON.toJSONString(originalKey)转换成字符串
            cacheBuilder.setKeyConvertor(configProvider.parseKeyConvertor(cachedAnnoConfig.getKeyConvertor()));
        }
        if (!CacheConsts.isUndefined(cachedAnnoConfig.getSerialPolicy())) {
        	// value保存至远程需要进行编码和解码，所以这里设置value的编码和解码方式，KRYO和JAVA可选择
        	// 也是生成一个函数，其中实现apply(Object value)对value的编码以及解码
            cacheBuilder.setValueEncoder(configProvider.parseValueEncoder(cachedAnnoConfig.getSerialPolicy()));
            cacheBuilder.setValueDecoder(configProvider.parseValueDecoder(cachedAnnoConfig.getSerialPolicy()));
        }
        // 设置是否缓存Null值，默认为false
        cacheBuilder.setCacheNullValue(cachedAnnoConfig.isCacheNullValue());
        return cacheBuilder.buildCache();
    }

    protected Cache buildLocal(CachedAnnoConfig cachedAnnoConfig, String area) {
    	// 获取area本地缓存实例
        EmbeddedCacheBuilder cacheBuilder = (EmbeddedCacheBuilder) globalCacheConfig.getLocalCacheBuilders().get(area);
        if (cacheBuilder == null) {
            throw new CacheConfigException("no local cache builder: " + area);
        }
        // 克隆一个CacheBuilder
        cacheBuilder = (EmbeddedCacheBuilder) cacheBuilder.clone();

        if (cachedAnnoConfig.getLocalLimit() != CacheConsts.UNDEFINED_INT) {
            cacheBuilder.setLimit(cachedAnnoConfig.getLocalLimit()); // 个数限制
        }
        if (cachedAnnoConfig.getCacheType() == CacheType.BOTH && cachedAnnoConfig.getLocalExpire() > 0) {
        	// 设置失效时间(本地失效时间)
            cacheBuilder.expireAfterWrite(cachedAnnoConfig.getLocalExpire(), cachedAnnoConfig.getTimeUnit());
        } else if (cachedAnnoConfig.getExpire() > 0) {
        	// 设置失效时间
            cacheBuilder.expireAfterWrite(cachedAnnoConfig.getExpire(), cachedAnnoConfig.getTimeUnit());
        }
        if (!CacheConsts.isUndefined(cachedAnnoConfig.getKeyConvertor())) {
            cacheBuilder.setKeyConvertor(configProvider.parseKeyConvertor(cachedAnnoConfig.getKeyConvertor()));
        }
        cacheBuilder.setCacheNullValue(cachedAnnoConfig.isCacheNullValue());
        return cacheBuilder.buildCache();
    }

    protected CacheInvokeContext newCacheInvokeContext() {
        return new CacheInvokeContext();
    }

    /**
     * Enable cache in current thread, for @Cached(enabled=false).
     *
     * @param callback
     * @see EnableCache
     */
    public static <T> T enableCache(Supplier<T> callback) {
        CacheThreadLocal var = cacheThreadLocal.get();
        try {
            var.setEnabledCount(var.getEnabledCount() + 1);
            return callback.get();
        } finally {
            var.setEnabledCount(var.getEnabledCount() - 1);
        }
    }

    protected static void enable() {
        CacheThreadLocal var = cacheThreadLocal.get();
        var.setEnabledCount(var.getEnabledCount() + 1);
    }

    protected static void disable() {
        CacheThreadLocal var = cacheThreadLocal.get();
        var.setEnabledCount(var.getEnabledCount() - 1);
    }

    protected static boolean isEnabled() {
        return cacheThreadLocal.get().getEnabledCount() > 0;
    }

}
