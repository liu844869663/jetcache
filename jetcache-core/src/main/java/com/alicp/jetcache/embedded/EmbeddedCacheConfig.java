package com.alicp.jetcache.embedded;

import com.alicp.jetcache.CacheConfig;
import com.alicp.jetcache.anno.CacheConsts;

/**
 * Created on 16/9/7.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class EmbeddedCacheConfig<K, V> extends CacheConfig<K, V> {
    /**
     * 本地缓存的缓存实例中的缓存数量
     */
    private int limit = CacheConsts.DEFAULT_LOCAL_LIMIT;

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

}
