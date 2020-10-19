package com.alicp.jetcache.autoconfigure;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Created on 2017/5/11.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class LettuceFactory implements FactoryBean {
    @Autowired
    private AutoConfigureBeans autoConfigureBeans;

    /**
     * 是否初始化
     */
    private boolean inited;
    /**
     * 被注入的对象
     */
    private Object obj;
    /**
     * 被注入的类型
     */
    private Class<?> clazz;
    /**
     * 从 AutoConfigureBeans 获取相关 Redis 连接的前缀
     */
    private String key;

    // for unit test
    LettuceFactory(AutoConfigureBeans autoConfigureBeans, String key, Class<?> clazz) {
        this(key, clazz);
        this.autoConfigureBeans = autoConfigureBeans;
    }


    public LettuceFactory(String key, Class<?> clazz) {
        this.clazz = clazz;
        if (AbstractRedisClient.class.isAssignableFrom(clazz)) {
            key += ".client";
        } else if (StatefulConnection.class.isAssignableFrom(clazz)) {
            key += ".connection";
        } else if (RedisClusterCommands.class.isAssignableFrom(clazz)) {
            // RedisCommands extends RedisClusterCommands
            key += ".commands";
        } else if (RedisClusterAsyncCommands.class.isAssignableFrom(clazz)) {
            // RedisAsyncCommands extends RedisClusterAsyncCommands
            key += ".asyncCommands";
        } else if (RedisClusterReactiveCommands.class.isAssignableFrom(clazz)) {
            // RedisReactiveCommands extends RedisClusterReactiveCommands
            key += ".reactiveCommands";
        } else {
            throw new IllegalArgumentException(clazz.getName());
        }
        this.key = key;
    }

    private void init() {
        if (!inited) {
            obj = autoConfigureBeans.getCustomContainer().get(key);
            inited = true;
        }
    }

    /**
     * 在 Spring 容器中被注入时会调用该方法返回对象
     *
     * @return 被注入的对象
     * @throws Exception 异常
     */
    @Override
    public Object getObject() throws Exception {
        init();
        return obj;
    }

    @Override
    public Class<?> getObjectType() {
        return clazz;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
