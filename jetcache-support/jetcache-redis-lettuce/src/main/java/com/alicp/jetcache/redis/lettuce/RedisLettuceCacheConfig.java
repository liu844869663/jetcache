package com.alicp.jetcache.redis.lettuce;

import com.alicp.jetcache.anno.CacheConsts;
import com.alicp.jetcache.external.ExternalCacheConfig;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.api.StatefulConnection;

/**
 * Created on 2017/4/28.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class RedisLettuceCacheConfig<K, V> extends ExternalCacheConfig<K, V> {

    /**
     * Redis客户端
     */
    private AbstractRedisClient redisClient;
    /**
     * 线程安全连接
     */
    private StatefulConnection connection;
    /**
     * 异步超时时间
     */
    private long asyncResultTimeoutInMillis = CacheConsts.ASYNC_RESULT_TIMEOUT.toMillis();

    public AbstractRedisClient getRedisClient() {
        return redisClient;
    }

    public void setRedisClient(AbstractRedisClient redisClient) {
        this.redisClient = redisClient;
    }

    public StatefulConnection getConnection() {
        return connection;
    }

    public void setConnection(StatefulConnection connection) {
        this.connection = connection;
    }

    public long getAsyncResultTimeoutInMillis() {
        return asyncResultTimeoutInMillis;
    }

    public void setAsyncResultTimeoutInMillis(long asyncResultTimeoutInMillis) {
        this.asyncResultTimeoutInMillis = asyncResultTimeoutInMillis;
    }
}
