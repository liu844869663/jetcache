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
    /**
     * JetCache 缓存的管理器（包含很多信息）
     */
    private ConfigProvider configProvider;
    /**
     * 缓存的全局配置
     */
    private GlobalCacheConfig globalCacheConfig;
    /**
     * 缓存实例管理器
     */
    protected SimpleCacheManager cacheManager;

    public CacheContext(ConfigProvider configProvider, GlobalCacheConfig globalCacheConfig) {
        this.globalCacheConfig = globalCacheConfig;
        this.configProvider = configProvider;
        cacheManager = configProvider.getCacheManager();
    }

    public CacheInvokeContext createCacheInvokeContext(ConfigMap configMap) {
    	// 创建一个本次调用的上下文
        CacheInvokeContext c = newCacheInvokeContext();
        // 添加一个函数，后续用于获取缓存实例
        // 根据注解配置信息获取缓存实例对象，不存在则创建并设置到缓存注解配置类中
        c.setCacheFunction((invokeContext, cacheAnnoConfig) -> {
            Cache cache = cacheAnnoConfig.getCache();
            if (cache == null) {
                if (cacheAnnoConfig instanceof CachedAnnoConfig) { // 缓存注解
                    // 根据配置创建一个缓存实例对象，通过 CacheBuilder
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
    	// 缓存区域
        String area = ac.getArea();
        // 缓存实例名称
        String cacheName = ac.getName();
        if (CacheConsts.isUndefined(cacheName)) { // 没有定义缓存实例名称

        	// 生成缓存实例名称：类名+方法名+(参数类型)
            cacheName = configProvider.createCacheNameGenerator(invokeContext.getHiddenPackages())
                    .generateCacheName(invokeContext.getMethod(), invokeContext.getTargetObject());
        }
        // 创建缓存实例对象
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
        // 从缓存实例管理器中根据缓存区域和缓存实例名称获取缓存实例
        Cache cache = cacheManager.getCacheWithoutCreate(area, cacheName);
        if (cache == null) {
            synchronized (this) { // 加锁
                // 再次确认
                cache = cacheManager.getCacheWithoutCreate(area, cacheName);
                if (cache == null) {
                    /*
                     * 缓存区域的名称是否作为缓存 key 名称前缀，默认为 true ，我一般设置为 false
                     */
                    if (globalCacheConfig.isAreaInCacheName()) {
                        // for compatible reason, if we use default configuration, the prefix should same to that version <=2.4.3
                        cache = buildCache(cachedAnnoConfig, area, fullCacheName);
                    } else {
                        // 构建一个缓存实例
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
        	// 构建本地缓存实例
            Cache local = buildLocal(cachedAnnoConfig, area);
            // 构建远程缓存实例
            Cache remote = buildRemote(cachedAnnoConfig, area, cacheName);
            // 两级缓存时是否单独设置了本地缓存失效时间 localExpire
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
        // 将 cache 封装成 CacheHandlerRefreshCache，也就是 RefreshCache 类型
        // 后续添加刷新任务时会判断是否为 RefreshCache 类型，然后决定是否执行 addOrUpdateRefreshTask 方法，添加刷新任务，没有刷新策略不会添加
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
        // 获取缓存区域对应的 CacheBuilder 构造器
        ExternalCacheBuilder cacheBuilder = (ExternalCacheBuilder) globalCacheConfig.getRemoteCacheBuilders().get(area);
        if (cacheBuilder == null) {
            throw new CacheConfigException("no remote cache builder: " + area);
        }
        // 克隆一个 CacheBuilder 构造器，因为不同缓存实例有不同的配置
        cacheBuilder = (ExternalCacheBuilder) cacheBuilder.clone();

        if (cachedAnnoConfig.getExpire() > 0 ) {
        	// 设置失效时间
            cacheBuilder.expireAfterWrite(cachedAnnoConfig.getExpire(), cachedAnnoConfig.getTimeUnit());
        }

        // 设置缓存 key 的前缀
        if (cacheBuilder.getConfig().getKeyPrefix() != null) {
            // 配置文件中配置了 prefix，则设置为 prefix+cacheName
            cacheBuilder.setKeyPrefix(cacheBuilder.getConfig().getKeyPrefix() + cacheName);
        } else { // 设置为 cacheName
            cacheBuilder.setKeyPrefix(cacheName);
        }

        if (!CacheConsts.isUndefined(cachedAnnoConfig.getKeyConvertor())) { // 如果注解中设置了Key的转换方式则替换，否则还是使用全局的
        	// 设置 key 的转换器，只支持 FASTJSON
            cacheBuilder.setKeyConvertor(configProvider.parseKeyConvertor(cachedAnnoConfig.getKeyConvertor()));
        }
        if (!CacheConsts.isUndefined(cachedAnnoConfig.getSerialPolicy())) {
        	// 缓存数据保存至远程需要进行编码和解码，所以这里设置其编码和解码方式，KRYO 和 JAVA 可选择
            cacheBuilder.setValueEncoder(configProvider.parseValueEncoder(cachedAnnoConfig.getSerialPolicy()));
            cacheBuilder.setValueDecoder(configProvider.parseValueDecoder(cachedAnnoConfig.getSerialPolicy()));
        }
        // 设置是否缓存 null 值
        cacheBuilder.setCacheNullValue(cachedAnnoConfig.isCacheNullValue());
        return cacheBuilder.buildCache();
    }

    protected Cache buildLocal(CachedAnnoConfig cachedAnnoConfig, String area) {
    	// 获取缓存区域对应的 CacheBuilder 构造器
        EmbeddedCacheBuilder cacheBuilder = (EmbeddedCacheBuilder) globalCacheConfig.getLocalCacheBuilders().get(area);
        if (cacheBuilder == null) {
            throw new CacheConfigException("no local cache builder: " + area);
        }
        // 克隆一个 CacheBuilder 构造器，因为不同缓存实例有不同的配置
        cacheBuilder = (EmbeddedCacheBuilder) cacheBuilder.clone();

        if (cachedAnnoConfig.getLocalLimit() != CacheConsts.UNDEFINED_INT) {
            // 本地缓存数量限制
            cacheBuilder.setLimit(cachedAnnoConfig.getLocalLimit());
        }
        if (cachedAnnoConfig.getCacheType() == CacheType.BOTH && cachedAnnoConfig.getLocalExpire() > 0) {
        	// 设置本地缓存失效时间，前提是多级缓存，一般和远程缓存保持一致不设置
            cacheBuilder.expireAfterWrite(cachedAnnoConfig.getLocalExpire(), cachedAnnoConfig.getTimeUnit());
        } else if (cachedAnnoConfig.getExpire() > 0) {
        	// 设置失效时间
            cacheBuilder.expireAfterWrite(cachedAnnoConfig.getExpire(), cachedAnnoConfig.getTimeUnit());
        }
        if (!CacheConsts.isUndefined(cachedAnnoConfig.getKeyConvertor())) {
            cacheBuilder.setKeyConvertor(configProvider.parseKeyConvertor(cachedAnnoConfig.getKeyConvertor()));
        }
        // 设置是否缓存 null 值
        cacheBuilder.setCacheNullValue(cachedAnnoConfig.isCacheNullValue());
        // 构建一个缓存实例
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
