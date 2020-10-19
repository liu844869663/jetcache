/**
 * Created on 2018/1/22.
 */
package com.alicp.jetcache.anno.support;

import com.alicp.jetcache.anno.CacheConsts;
import com.alicp.jetcache.anno.method.CacheInvokeConfig;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class ConfigMap {
    /**
     * 保存方法与缓存配置信息的映射关系（扫描的类不一定都是有缓存注解的，无需缓存的设置一个空的 CacheInvokeConfig ）
     */
    private ConcurrentHashMap<String, CacheInvokeConfig> methodInfoMap = new ConcurrentHashMap<>();
    /**
     * 保存缓存方法与缓存配置信息的映射关系
     */
    private ConcurrentHashMap<String, CacheInvokeConfig> cacheNameMap = new ConcurrentHashMap<>();

    public void putByMethodInfo(String key, CacheInvokeConfig config) {
        methodInfoMap.put(key, config);
        CachedAnnoConfig cac = config.getCachedAnnoConfig();
        if (cac != null && !CacheConsts.isUndefined(cac.getName())) { // 该方法被扫描到并且有缓存注解信息
            cacheNameMap.put(cac.getArea() + "_" + cac.getName(), config);
        }
    }

    public CacheInvokeConfig getByMethodInfo(String key) {
        return methodInfoMap.get(key);
    }

    public CacheInvokeConfig getByCacheName(String area, String cacheName) {
        return cacheNameMap.get(area + "_" + cacheName);
    }
}
