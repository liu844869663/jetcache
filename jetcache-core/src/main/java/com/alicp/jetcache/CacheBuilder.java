package com.alicp.jetcache;

/**
 * Created on 2016/11/17.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public interface CacheBuilder {
    /**
     * 构建一个缓存实例对象
     * @param <K>
     * @param <V>
     * @return 缓存实例对象
     */
    <K, V> Cache<K, V> buildCache();
}
