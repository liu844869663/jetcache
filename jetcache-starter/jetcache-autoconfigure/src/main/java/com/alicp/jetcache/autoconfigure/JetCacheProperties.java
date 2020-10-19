package com.alicp.jetcache.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Created on 2016/11/23.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
@ConfigurationProperties(prefix = "jetcache")
public class JetCacheProperties {

    /**
     * 自动生成缓存实例名称时需要被截断的包名
     */
    private String[] hiddenPackages;
    /**
     * 统计间隔，0表示不统计
     */
    private int statIntervalMinutes;
    /**
     * 是否将缓存区域 area 作为缓存实例名称的前缀
     */
    private boolean areaInCacheName = true;
    /**
     * 是否开启保护模式（缓存未命中，是否只允许一个线程加载原方法，其他线程等待）
     */
    private boolean penetrationProtect = false;
    /**
     * 是否开启缓存
     */
    private boolean enableMethodCache = true;

    public JetCacheProperties(){
    }

    public String[] getHiddenPackages() {
        // keep same with GlobalCacheConfig
        return hiddenPackages;
    }

    public void setHiddenPackages(String[] hiddenPackages) {
        // keep same with GlobalCacheConfig
        this.hiddenPackages = hiddenPackages;
    }

    public void setHidePackages(String[] hidePackages) {
        // keep same with GlobalCacheConfig
        this.hiddenPackages = hidePackages;
    }

    public int getStatIntervalMinutes() {
        return statIntervalMinutes;
    }

    public void setStatIntervalMinutes(int statIntervalMinutes) {
        this.statIntervalMinutes = statIntervalMinutes;
    }

    public boolean isAreaInCacheName() {
        return areaInCacheName;
    }

    public void setAreaInCacheName(boolean areaInCacheName) {
        this.areaInCacheName = areaInCacheName;
    }

    public boolean isPenetrationProtect() {
        return penetrationProtect;
    }

    public void setPenetrationProtect(boolean penetrationProtect) {
        this.penetrationProtect = penetrationProtect;
    }

    public boolean isEnableMethodCache() {
        return enableMethodCache;
    }

    public void setEnableMethodCache(boolean enableMethodCache) {
        this.enableMethodCache = enableMethodCache;
    }
}
