/**
 * Created on 2019/2/1.
 */
package com.alicp.jetcache.anno.support;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.CacheConsts;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public interface CacheManager {
    /**
     * 根据缓存区域和缓存实例名称获取缓存实例
     *
     * @param area      缓存区域
     * @param cacheName 缓存实例名称
     * @return 缓存实例
     */
    Cache getCache(String area, String cacheName);

    /**
     * 根据缓存实例名称从默认区域获取缓存实例
     *
     * @param cacheName 缓存实例名称
     * @return 缓存实例
     */
    default Cache getCache(String cacheName) {
        return getCache(CacheConsts.DEFAULT_AREA, cacheName);
    }

    /**
     * 返回默认的缓存实例管理器
     *
     * @return 缓存实例管理器
     */
    static CacheManager defaultManager() {
        return SimpleCacheManager.defaultManager;
    }
}
