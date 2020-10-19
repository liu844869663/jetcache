package com.alicp.jetcache.autoconfigure;

import com.alicp.jetcache.CacheBuilder;
import com.alicp.jetcache.CacheConfigException;
import com.alicp.jetcache.anno.CacheConsts;
import com.alicp.jetcache.external.ExternalCacheBuilder;
import com.alicp.jetcache.redis.lettuce.JetCacheCodec;
import com.alicp.jetcache.redis.lettuce.LettuceConnectionManager;
import com.alicp.jetcache.redis.lettuce.RedisLettuceCacheBuilder;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.cluster.ClusterClientOptions;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.masterslave.MasterSlave;
import io.lettuce.core.masterslave.StatefulRedisMasterSlaveConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created on 2017/5/10.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
@Configuration
@Conditional(RedisLettuceAutoConfiguration.RedisLettuceCondition.class)
public class RedisLettuceAutoConfiguration {
    public static final String AUTO_INIT_BEAN_NAME = "redisLettuceAutoInit";

    /**
     * 注入 spring 容器的条件
     */
    public static class RedisLettuceCondition extends JetCacheCondition {
        // 配置了缓存类型为 redis.lettuce 当前类才会被注入 Spring 容器
        public RedisLettuceCondition() {
            super("redis.lettuce");
        }
    }

    @Bean(name = {AUTO_INIT_BEAN_NAME})
    public RedisLettuceAutoInit redisLettuceAutoInit() {
        return new RedisLettuceAutoInit();
    }

    public static class RedisLettuceAutoInit extends ExternalCacheAutoInit {

        public RedisLettuceAutoInit() {
            // 设置缓存类型
            super("redis.lettuce");
        }

        /**
         * 初始化 RedisLettuceCacheBuilder 构造器
         *
         * @param ct                  配置信息
         * @param cacheAreaWithPrefix 配置前缀
         * @return 构造器
         */
        @Override
        protected CacheBuilder initCache(ConfigTree ct, String cacheAreaWithPrefix) {
            Map<String, Object> map = ct.subTree("uri"/*there is no dot*/).getProperties();
            // 数据节点偏好设置
            String readFromStr = ct.getProperty("readFrom");
            // 集群模式
            String mode = ct.getProperty("mode");
            // 异步获取结果的超时时间，默认1s
            long asyncResultTimeoutInMillis = Long.parseLong(
                    ct.getProperty("asyncResultTimeoutInMillis", Long.toString(CacheConsts.ASYNC_RESULT_TIMEOUT.toMillis())));
            ReadFrom readFrom = null;
            if (readFromStr != null) {
                /*
                 * MASTER：只从Master节点中读取。
                 * MASTER_PREFERRED：优先从Master节点中读取。
                 * SLAVE_PREFERRED：优先从Slave节点中读取。
                 * SLAVE：只从Slave节点中读取。
                 * NEAREST：使用最近一次连接的Redis实例读取。
                 */
                readFrom = ReadFrom.valueOf(readFromStr.trim());
            }

            AbstractRedisClient client;
            StatefulConnection connection = null;
            if (map == null || map.size() == 0) {
                throw new CacheConfigException("lettuce uri is required");
            } else {
                // 创建对应的 RedisURI
                List<RedisURI> uriList = map.values().stream().map((k) -> RedisURI.create(URI.create(k.toString())))
                        .collect(Collectors.toList());
                if (uriList.size() == 1) { // 只有一个 URI，集群模式只给一个域名怎么办 TODO 疑问？？
                    RedisURI uri = uriList.get(0);
                    if (readFrom == null) {
                        // 创建一个 Redis 客户端
                        client = RedisClient.create(uri);
                        // 设置失去连接时的行为，拒绝命令，默认为 DEFAULT
                        ((RedisClient) client).setOptions(ClientOptions.builder().
                                disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS).build());
                    } else {
                        // 创建一个 Redis 客户端
                        client = RedisClient.create();
                        ((RedisClient) client).setOptions(ClientOptions.builder().
                                disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS).build());
                        // 创建一个安全连接并设置数据节点偏好
                        StatefulRedisMasterSlaveConnection c = MasterSlave.connect(
                                (RedisClient) client, new JetCacheCodec(), uri);
                        c.setReadFrom(readFrom);
                        connection = c;
                    }
                } else { // 多个 URI，集群模式
                    if (mode != null && mode.equalsIgnoreCase("MasterSlave")) {
                        client = RedisClient.create();
                        ((RedisClient) client).setOptions(ClientOptions.builder().
                                disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS).build());
                        StatefulRedisMasterSlaveConnection c = MasterSlave.connect(
                                (RedisClient) client, new JetCacheCodec(), uriList);
                        if (readFrom != null) {
                            c.setReadFrom(readFrom);
                        }
                        connection = c;
                    } else {
                        // 创建一个 Redis 客户端
                        client = RedisClusterClient.create(uriList);
                        ((RedisClusterClient) client).setOptions(ClusterClientOptions.builder().
                                disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS).build());
                        if (readFrom != null) {
                            StatefulRedisClusterConnection c = ((RedisClusterClient) client).connect(new JetCacheCodec());
                            c.setReadFrom(readFrom);
                            connection = c;
                        }
                    }
                }
            }

            // 创建一个 RedisLettuceCacheBuilder 构造器
            ExternalCacheBuilder externalCacheBuilder = RedisLettuceCacheBuilder.createRedisLettuceCacheBuilder()
                    .connection(connection)
                    .redisClient(client)
                    .asyncResultTimeoutInMillis(asyncResultTimeoutInMillis);
            // 解析相关配置至 RedisLettuceCacheBuilder 的 CacheConfig 中
            parseGeneralConfig(externalCacheBuilder, ct);

            // eg: "remote.default.client"
            autoConfigureBeans.getCustomContainer().put(cacheAreaWithPrefix + ".client", client);
            // 开始将 Redis 客户端和安全连接保存至 LettuceConnectionManager 管理器中
            LettuceConnectionManager m = LettuceConnectionManager.defaultManager();
            // 初始化 Lettuce 连接 Redis
            m.init(client, connection);
            // 初始化 Redis 连接的相关信息保存至 LettuceObjects 中，并将相关信息保存至 AutoConfigureBeans.customContainer
            autoConfigureBeans.getCustomContainer().put(cacheAreaWithPrefix + ".connection", m.connection(client));
            autoConfigureBeans.getCustomContainer().put(cacheAreaWithPrefix + ".commands", m.commands(client));
            autoConfigureBeans.getCustomContainer().put(cacheAreaWithPrefix + ".asyncCommands", m.asyncCommands(client));
            autoConfigureBeans.getCustomContainer().put(cacheAreaWithPrefix + ".reactiveCommands", m.reactiveCommands(client));
            return externalCacheBuilder;
        }
    }
}
