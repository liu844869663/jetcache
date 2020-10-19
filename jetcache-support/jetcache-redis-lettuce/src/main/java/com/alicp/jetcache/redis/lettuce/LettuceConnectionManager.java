package com.alicp.jetcache.redis.lettuce;

import com.alicp.jetcache.CacheConfigException;
import com.alicp.jetcache.CacheException;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.sentinel.api.StatefulRedisSentinelConnection;


import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Created on 2017/4/28.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class LettuceConnectionManager {

    private static class LettuceObjects {
        private StatefulConnection connection;
        private Object commands;
        private Object asyncCommands;
        private Object reactiveCommands;
    }

    private static final LettuceConnectionManager defaultManager = new LettuceConnectionManager();

    /**
     * 用于存放 RedisClient 与 LettuceObjects的关系
     * 用于缓存 RedisClient 的连接信息
     */
    private Map<AbstractRedisClient, LettuceObjects> map = Collections.synchronizedMap(new WeakHashMap());

    private LettuceConnectionManager() {
    }

    public static LettuceConnectionManager defaultManager() {
        return defaultManager;
    }

    private LettuceObjects getLettuceObjectsFromMap(AbstractRedisClient redisClient) {
        LettuceObjects lo = map.get(redisClient);
        if (lo == null) {
            throw new CacheException("LettuceObjects is not initialized");
        }
        return lo;
    }

    public void init(AbstractRedisClient redisClient, StatefulConnection connection) {
        map.computeIfAbsent(redisClient, key -> {
            LettuceObjects lo = new LettuceObjects();
            lo.connection = connection;
            return lo;
        });
    }

    /**
     * 尝试获取 redisClient 对应的 LettuceObjects，其中封装了连接信息，然后返回与redis的连接
     *
     * @param redisClient Redis客户端
     * @return 连接
     */
    public StatefulConnection connection(AbstractRedisClient redisClient) {
        LettuceObjects lo = getLettuceObjectsFromMap(redisClient);
        if (lo.connection == null) {
            if (redisClient instanceof RedisClient) {
                lo.connection = ((RedisClient) redisClient).connect(new JetCacheCodec());
            } else if (redisClient instanceof RedisClusterClient) {
                lo.connection = ((RedisClusterClient) redisClient).connect(new JetCacheCodec());
            } else {
                throw new CacheConfigException("type " + redisClient.getClass() + " is not supported");
            }
        }
        return lo.connection;
    }

    /**
     * 尝试获取 redisClient 对应的同步命令
     *
     * @param redisClient Redis客户端
     * @return 同步命令
     */
    public Object commands(AbstractRedisClient redisClient) {
        // 如果 Redis 客户端没有创建连接，这里会初始化
        connection(redisClient);
        // 获取封装的 Redis 客户端 LettuceObjects对象
        LettuceObjects lo = getLettuceObjectsFromMap(redisClient);
        if (lo.commands == null) {
            if (lo.connection instanceof StatefulRedisConnection) {
                lo.commands = ((StatefulRedisConnection) lo.connection).sync();
            } else if (lo.connection instanceof StatefulRedisClusterConnection) {
                lo.commands = ((StatefulRedisClusterConnection) lo.connection).sync();
            } else if (lo.connection instanceof StatefulRedisSentinelConnection) {
                lo.commands = ((StatefulRedisSentinelConnection) lo.connection).sync();
            } else {
                throw new CacheConfigException("type " + lo.connection.getClass() + " is not supported");
            }
        }
        return lo.commands;
    }

    /**
     * 尝试获取 redisClient 对应的异步命令
     *
     * @param redisClient Redis客户端
     * @return 异步命令
     */
    public Object asyncCommands(AbstractRedisClient redisClient) {
        connection(redisClient);
        LettuceObjects lo = getLettuceObjectsFromMap(redisClient);
        if (lo.asyncCommands == null) {
            if (lo.connection instanceof StatefulRedisConnection) {
                lo.asyncCommands = ((StatefulRedisConnection) lo.connection).async();
            } else if (lo.connection instanceof StatefulRedisClusterConnection) {
                lo.asyncCommands = ((StatefulRedisClusterConnection) lo.connection).async();
            } else if (lo.connection instanceof StatefulRedisSentinelConnection) {
                lo.asyncCommands = ((StatefulRedisSentinelConnection) lo.connection).async();
            } else {
                throw new CacheConfigException("type " + lo.connection.getClass() + " is not supported");
            }
        }
        return lo.asyncCommands;
    }

    /**
     * 尝试获取 redisClient 对应的反应式编程命令
     *
     * @param redisClient Redis客户端
     * @return 反应式编程命令
     */
    public Object reactiveCommands(AbstractRedisClient redisClient) {
        connection(redisClient);
        LettuceObjects lo = getLettuceObjectsFromMap(redisClient);
        if (lo.reactiveCommands == null) {
            if (lo.connection instanceof StatefulRedisConnection) {
                lo.reactiveCommands = ((StatefulRedisConnection) lo.connection).reactive();
            } else if (lo.connection instanceof StatefulRedisClusterConnection) {
                lo.reactiveCommands = ((StatefulRedisClusterConnection) lo.connection).reactive();
            } else if (lo.connection instanceof StatefulRedisSentinelConnection) {
                lo.reactiveCommands = ((StatefulRedisSentinelConnection) lo.connection).reactive();
            } else {
                throw new CacheConfigException("type " + lo.connection.getClass() + " is not supported");
            }
        }
        return lo.reactiveCommands;
    }

    /**
     * 释放 Redis 客户端
     *
     * @param redisClient Redis客户端
     */
    public void removeAndClose(AbstractRedisClient redisClient) {
        LettuceObjects lo = (LettuceObjects) map.remove(redisClient);
        if (lo == null) {
            return;
        }
        /*
        if (lo.commands != null && lo.commands instanceof RedisClusterCommands) {
            ((RedisClusterCommands) lo.commands).close();
        }
        if (lo.asyncCommands != null && lo.asyncCommands instanceof RedisClusterAsyncCommands) {
            ((RedisClusterAsyncCommands) lo.asyncCommands).close();
        }
        if (lo.reactiveCommands != null && lo.reactiveCommands instanceof RedisClusterReactiveCommands) {
            ((RedisClusterReactiveCommands) lo.reactiveCommands).close();
        }
        */
        if (lo.connection != null) {
            lo.connection.close();
        }
        redisClient.shutdown();
    }
}
