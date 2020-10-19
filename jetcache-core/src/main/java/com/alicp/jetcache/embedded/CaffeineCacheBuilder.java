package com.alicp.jetcache.embedded;

/**
 * Created on 2016/11/29.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class CaffeineCacheBuilder<T extends EmbeddedCacheBuilder<T>> extends EmbeddedCacheBuilder<T> {
    public static class CaffeineCacheBuilderImpl extends CaffeineCacheBuilder<CaffeineCacheBuilderImpl> {
    }

    public static CaffeineCacheBuilderImpl createCaffeineCacheBuilder() {
        return new CaffeineCacheBuilderImpl();
    }

    protected CaffeineCacheBuilder() {
        // 设置构建 CaffeineCache 缓存实例的函数
        buildFunc((c) -> new CaffeineCache((EmbeddedCacheConfig) c));
    }
}
