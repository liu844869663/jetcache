package com.alicp.jetcache.autoconfigure;

import com.alicp.jetcache.CacheBuilder;
import com.alicp.jetcache.embedded.CaffeineCacheBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Created on 2016/12/2.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
@Component
@Conditional(CaffeineAutoConfiguration.CaffeineCondition.class)
public class CaffeineAutoConfiguration extends EmbeddedCacheAutoInit {
    public CaffeineAutoConfiguration() {
        super("caffeine");
    }

    @Override
    protected CacheBuilder initCache(ConfigTree ct, String cacheAreaWithPrefix) {
        // 创建一个 CaffeineCacheBuilder 构造器
        CaffeineCacheBuilder builder = CaffeineCacheBuilder.createCaffeineCacheBuilder();
        // 解析相关配置至 CaffeineCacheBuilder 的 CacheConfig 中
        parseGeneralConfig(builder, ct);
        return builder;
    }

    public static class CaffeineCondition extends JetCacheCondition {
        // 配置了缓存类型为 caffeine 当前类才会被注入 Spring 容器
        public CaffeineCondition() {
            super("caffeine");
        }
    }
}
