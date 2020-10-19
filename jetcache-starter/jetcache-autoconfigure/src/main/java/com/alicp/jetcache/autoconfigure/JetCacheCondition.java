package com.alicp.jetcache.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.Assert;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 根据配置信息判断哪些类型的 CacheAutoInit 符合条件
 * <p>
 * Created on 2016/11/28.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public abstract class JetCacheCondition extends SpringBootCondition {

    private String[] cacheTypes;

    protected JetCacheCondition(String... cacheTypes) {
        Objects.requireNonNull(cacheTypes, "cacheTypes can't be null");
        Assert.isTrue(cacheTypes.length > 0, "cacheTypes length is 0");
        this.cacheTypes = cacheTypes;
    }

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext conditionContext, AnnotatedTypeMetadata annotatedTypeMetadata) {
        // 获取 jetcache 所有配置信息
        ConfigTree ct = new ConfigTree((ConfigurableEnvironment) conditionContext.getEnvironment(), "jetcache.");
        if (match(ct, "local.") || match(ct, "remote.")) {
            return ConditionOutcome.match();
        } else {
            return ConditionOutcome.noMatch("no match for " + cacheTypes[0]);
        }
    }

    /**
     * 是否匹配当前的 cacheTypes
     *
     * @param ct     jetcache 的所有配置信息
     * @param prefix 本地或者远程前缀
     * @return 缓存类型 type 是否匹配
     */
    private boolean match(ConfigTree ct, String prefix) {
        // 获取本地或者远程的所有配置信息
        Map<String, Object> m = ct.subTree(prefix).getProperties();
        // 获取本地或者远程的所有子 key，也就是缓存区域 area
        Set<String> cacheAreaNames = m.keySet().stream().map((s) -> s.substring(0, s.indexOf('.'))).collect(Collectors.toSet());
        final List<String> cacheTypesList = Arrays.asList(cacheTypes);
        // 判断本地或者远程的每个缓存区域 area 中设置的缓存类型 type 是否存在于 cacheTypes
        return cacheAreaNames.stream().anyMatch((s) -> cacheTypesList.contains(m.get(s + ".type")));
    }
}
