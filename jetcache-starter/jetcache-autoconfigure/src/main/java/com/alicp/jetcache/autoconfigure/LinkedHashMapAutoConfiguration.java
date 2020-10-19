package com.alicp.jetcache.autoconfigure;

import com.alicp.jetcache.CacheBuilder;
import com.alicp.jetcache.embedded.LinkedHashMapCacheBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Created on 2016/12/2.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
@Component
@Conditional(LinkedHashMapAutoConfiguration.LinkedHashMapCondition.class)
public class LinkedHashMapAutoConfiguration extends EmbeddedCacheAutoInit {
    public LinkedHashMapAutoConfiguration() {
        super("linkedhashmap");
    }

    @Override
    protected CacheBuilder initCache(ConfigTree ct, String cacheAreaWithPrefix) {
        // 创建一个 LinkedHashMapCacheBuilder 构造器
        LinkedHashMapCacheBuilder builder = LinkedHashMapCacheBuilder.createLinkedHashMapCacheBuilder();
        // 解析相关配置至 LinkedHashMapCacheBuilder 的 CacheConfig 中
        parseGeneralConfig(builder, ct);
        return builder;
    }

    public static class LinkedHashMapCondition extends JetCacheCondition {
        // 配置了缓存类型为 linkedhashmap 当前类才会被注入 Spring 容器
        public LinkedHashMapCondition() {
            super("linkedhashmap");
        }
    }
}
