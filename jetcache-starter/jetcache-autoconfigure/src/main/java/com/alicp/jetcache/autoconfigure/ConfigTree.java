package com.alicp.jetcache.autoconfigure;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.util.Assert;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created on 2017/11/20.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class ConfigTree {
    /**
     * 当前运行环境信息
     */
    private ConfigurableEnvironment environment;
    /**
     * 配置前缀
     */
    private String prefix;

    public ConfigTree(ConfigurableEnvironment environment, String prefix) {
        Assert.notNull(environment, "environment is required");
        Assert.notNull(prefix, "prefix is required");
        this.environment = environment;
        this.prefix = prefix;
    }

    public ConfigTree subTree(String prefix) {
        return new ConfigTree(environment, fullPrefixOrKey(prefix));
    }

    private String fullPrefixOrKey(String prefixOrKey) {
        return this.prefix + prefixOrKey;
    }

    /**
     * 从当前环境中获取配置文件指定前缀的配置项
     *
     * @return 配置项<key ( 不包含prefix ), value>
     */
    public Map<String, Object> getProperties() {
        Map<String, Object> m = new HashMap<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource) {
                // 遍历配置项
                for (String name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
                    if (name != null && name.startsWith(prefix)) { // 如果该配置以当前前缀开头
                        // 截取前缀后面的 Key 部分
                        String subKey = name.substring(prefix.length());
                        // 将配置放置返回结果
                        m.put(subKey, environment.getProperty(name));
                    }
                }
            }
        }
        return m;
    }

    /**
     * 判断当前环境的配置是否包含 prefix+key
     *
     * @param key key值
     * @return 是否包含
     */
    public boolean containsProperty(String key) {
        key = fullPrefixOrKey(key);
        return environment.containsProperty(key);
    }

    /**
     * 获取当前环境的配置中的 prefix+key 值
     *
     * @param key key值
     * @param <T> 类型
     * @return value值
     */
    public <T> T getProperty(String key) {
        key = fullPrefixOrKey(key);
        return (T) environment.getProperty(key);
    }

    /**
     * 获取当前环境的配置中的 prefix+key 值
     *
     * @param key          key值
     * @param defaultValue 默认值
     * @param <T>          类型
     * @return value值
     */
    public <T> T getProperty(String key, T defaultValue) {
        if (containsProperty(key)) {
            return getProperty(key);
        } else {
            return defaultValue;
        }
    }

    public String getPrefix() {
        return prefix;
    }

    /**
     * 从当前环境中获取配置文件中指定前缀的第一层子 Key
     *
     * @return 指定前缀的第一层子 Key
     */
    public Set<String> directChildrenKeys() {
        Map<String, Object> m = getProperties();
        return m.keySet().stream().map(
                s -> s.indexOf('.') >= 0 ? s.substring(0, s.indexOf('.')) : null)
                .filter(s -> s != null)
                .collect(Collectors.toSet());
    }
}
