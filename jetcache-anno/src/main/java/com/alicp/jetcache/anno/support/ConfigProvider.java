package com.alicp.jetcache.anno.support;

import com.alicp.jetcache.support.CacheMessagePublisher;
import com.alicp.jetcache.support.StatInfo;
import com.alicp.jetcache.support.StatInfoLogger;

import javax.annotation.Resource;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Created on 2016/11/29.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class ConfigProvider extends AbstractLifecycle {

    /**
     * 缓存的全局配置
     */
    @Resource
    protected GlobalCacheConfig globalCacheConfig;
    /**
     * 缓存实例管理器
     */
    protected SimpleCacheManager cacheManager;
    /**
     * 根据不同类型生成缓存数据转换函数的转换器
     */
    protected EncoderParser encoderParser;
    /**
     * 根据不同类型生成缓存 Key 转换函数的转换器
     */
    protected KeyConvertorParser keyConvertorParser;
    /**
     * 缓存监控指标管理器
     */
    protected CacheMonitorManager cacheMonitorManager;
    /**
     * 打印缓存各项指标的函数
     */
    private Consumer<StatInfo> metricsCallback = new StatInfoLogger(false);
    /**
     * 缓存更新事件（REMOVE OR PUT）消息接收者，无实现类
     * 我们可以自己实现 CacheMessagePublisher 用于统计一些缓存的命中信息
     */
    private CacheMessagePublisher cacheMessagePublisher;

    /**
     * 默认的缓存监控指标管理器
     */
    private CacheMonitorManager defaultCacheMonitorManager = new DefaultCacheMonitorManager();

    /**
     * 缓存上下文
     */
    private CacheContext cacheContext;

    public ConfigProvider() {
        cacheManager = SimpleCacheManager.defaultManager;
        encoderParser = new DefaultEncoderParser();
        keyConvertorParser = new DefaultKeyConvertorParser();
        cacheMonitorManager = defaultCacheMonitorManager;
    }

    @Override
    public void doInit() {
        // 启动缓存指标监控器，周期性打印各项指标
        initDefaultCacheMonitorInstaller();
        // 初始化缓存上下文
        cacheContext = newContext();
    }

    protected void initDefaultCacheMonitorInstaller() {
        if (cacheMonitorManager == defaultCacheMonitorManager) {
            DefaultCacheMonitorManager installer = (DefaultCacheMonitorManager) cacheMonitorManager;
            installer.setGlobalCacheConfig(globalCacheConfig);
            installer.setMetricsCallback(metricsCallback);
            if (cacheMessagePublisher != null) {
                installer.setCacheMessagePublisher(cacheMessagePublisher);
            }
            // 启动缓存指标监控器
            installer.init();
        }
    }

    @Override
    public void doShutdown() {
        shutdownDefaultCacheMonitorInstaller();
        // 清除缓存实例
        cacheManager.rebuild();
    }

    protected void shutdownDefaultCacheMonitorInstaller() {
        if (cacheMonitorManager == defaultCacheMonitorManager) {
            ((DefaultCacheMonitorManager) cacheMonitorManager).shutdown();
        }
    }

    /**
     * 根据编码类型通过缓存value转换器生成编码函数
     *
     * @param valueEncoder 编码类型
     * @return 编码函数
     */
    public Function<Object, byte[]> parseValueEncoder(String valueEncoder) {
        return encoderParser.parseEncoder(valueEncoder);
    }

    /**
     * 根据解码类型通过缓存value转换器生成解码函数
     *
     * @param valueDecoder 解码类型
     * @return 解码函数
     */
    public Function<byte[], Object> parseValueDecoder(String valueDecoder) {
        return encoderParser.parseDecoder(valueDecoder);
    }

    /**
     * 根据转换类型通过缓存key转换器生成转换函数
     *
     * @param convertor 转换类型
     * @return 转换函数
     */
    public Function<Object, Object> parseKeyConvertor(String convertor) {
        return keyConvertorParser.parseKeyConvertor(convertor);
    }

    public CacheNameGenerator createCacheNameGenerator(String[] hiddenPackages) {
        return new DefaultCacheNameGenerator(hiddenPackages);
    }

    protected CacheContext newContext() {
        return new CacheContext(this, globalCacheConfig);
    }

    public void setCacheManager(SimpleCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public SimpleCacheManager getCacheManager() {
        return cacheManager;
    }

    public void setEncoderParser(EncoderParser encoderParser) {
        this.encoderParser = encoderParser;
    }

    public void setKeyConvertorParser(KeyConvertorParser keyConvertorParser) {
        this.keyConvertorParser = keyConvertorParser;
    }

    public CacheMonitorManager getCacheMonitorManager() {
        return cacheMonitorManager;
    }

    public void setCacheMonitorManager(CacheMonitorManager cacheMonitorManager) {
        this.cacheMonitorManager = cacheMonitorManager;
    }

    public GlobalCacheConfig getGlobalCacheConfig() {
        return globalCacheConfig;
    }

    public void setGlobalCacheConfig(GlobalCacheConfig globalCacheConfig) {
        this.globalCacheConfig = globalCacheConfig;
    }

    public CacheContext getCacheContext() {
        return cacheContext;
    }

    public void setMetricsCallback(Consumer<StatInfo> metricsCallback) {
        this.metricsCallback = metricsCallback;
    }

    public void setCacheMessagePublisher(CacheMessagePublisher cacheMessagePublisher) {
        this.cacheMessagePublisher = cacheMessagePublisher;
    }
}
