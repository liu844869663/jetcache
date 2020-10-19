package com.alicp.jetcache;

import java.io.Serializable;

/**
 * Created on 16/9/13.
 * This class is not thread safe.
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public final class CacheValueHolder<V> implements Serializable {
    private static final long serialVersionUID = -7973743507831565203L;
    /**
     * 缓存数据
     */
    private V value;
    /**
     * 失效时间戳
     */
    private long expireTime;
    /**
     * 第一次访问的时间
     */
    private long accessTime;

    public CacheValueHolder() {
    }

    public CacheValueHolder(V value, long expireAfterWrite) {
        this.value = value;
        this.accessTime = System.currentTimeMillis();
        this.expireTime = accessTime + expireAfterWrite;
    }

    public V getValue() {
        return value;
    }

    public void setValue(V value) {
        this.value = value;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    public long getAccessTime() {
        return accessTime;
    }

    public void setAccessTime(long accessTime) {
        this.accessTime = accessTime;
    }
}
