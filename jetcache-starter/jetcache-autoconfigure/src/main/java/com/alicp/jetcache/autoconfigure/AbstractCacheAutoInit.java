package com.alicp.jetcache.autoconfigure;

import com.alicp.jetcache.AbstractCacheBuilder;
import com.alicp.jetcache.CacheBuilder;
import com.alicp.jetcache.anno.support.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.Assert;

import java.util.*;

/**
 * Created on 2016/11/29.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public abstract class AbstractCacheAutoInit implements InitializingBean {

    private static Logger logger = LoggerFactory.getLogger(AbstractCacheAutoInit.class);

    @Autowired
    protected ConfigurableEnvironment environment;

    @Autowired
    protected AutoConfigureBeans autoConfigureBeans;

    @Autowired
    protected ConfigProvider configProvider;

    protected String[] typeNames;

    private boolean inited = false;

    public AbstractCacheAutoInit(String... cacheTypes) {
        Objects.requireNonNull(cacheTypes,"cacheTypes can't be null");
        Assert.isTrue(cacheTypes.length > 0, "cacheTypes length is 0");
        this.typeNames = cacheTypes;
    }

    /**
     * 初始化方法
     */
    @Override
    public void afterPropertiesSet() {
        if (!inited) {
            synchronized (this) {
                if (!inited) {
                    // 这里我们有两个指定前缀 'jetcache.local' 'jetcache.remote'
                    process("jetcache.local.", autoConfigureBeans.getLocalCacheBuilders(), true);
                    process("jetcache.remote.", autoConfigureBeans.getRemoteCacheBuilders(), false);
                    inited = true;
                }
            }
        }
    }

    private void process(String prefix, Map cacheBuilders, boolean local) {
        // 创建一个配置对象（本地或者远程）
        ConfigTree resolver = new ConfigTree(environment, prefix);
        // 获取本地或者远程的配置项
        Map<String, Object> m = resolver.getProperties();
        // 获取本地或者远程的 area ，这里我一般只有默认的 default
        Set<String> cacheAreaNames = resolver.directChildrenKeys();
        for (String cacheArea : cacheAreaNames) {
            // 获取本地或者远程存储类型，例如 caffeine，redis.lettuce
            final Object configType = m.get(cacheArea + ".type");
            // 缓存类型是否和当前 CacheAutoInit 的某一个 typeName 匹配（不同的 CacheAutoInit 会设置一个或者多个 typename）
            boolean match = Arrays.stream(typeNames).anyMatch((tn) -> tn.equals(configType));
            /*
             * 因为有很多 CacheAutoInit 继承者，都会执行这个方法，不同的继承者解析不同的配置
             * 例如 CaffeineAutoConfiguration 只解析 jetcache.local.default.type=caffeine 即可
             * RedisLettuceAutoInit 只解析 jetcache.remote.default.type=redis.lettuce 即可
             */
            if (!match) {
                continue;
            }
            // 获取本地或者远程的 area 的子配置项
            ConfigTree ct = resolver.subTree(cacheArea + ".");
            logger.info("init cache area {} , type= {}", cacheArea, typeNames[0]);
            // 根据配置信息构建本地或者远程缓存的 CacheBuilder 构造器
            CacheBuilder c = initCache(ct, local ? "local." + cacheArea : "remote." + cacheArea);
            // 将 CacheBuilder 构造器存放至 AutoConfigureBeans
            cacheBuilders.put(cacheArea, c);
        }
    }

    /**
     * 设置公共的配置到 CacheBuilder 构造器中
     *
     * @param builder 构造器
     * @param ct      配置信息
     */
    protected void parseGeneralConfig(CacheBuilder builder, ConfigTree ct) {
        AbstractCacheBuilder acb = (AbstractCacheBuilder) builder;
        // 设置 Key 的转换函数
        acb.keyConvertor(configProvider.parseKeyConvertor(ct.getProperty("keyConvertor")));
        // 设置超时时间
        String expireAfterWriteInMillis = ct.getProperty("expireAfterWriteInMillis");
        if (expireAfterWriteInMillis == null) {
            // compatible with 2.1 兼容老版本
            expireAfterWriteInMillis = ct.getProperty("defaultExpireInMillis");
        }
        if (expireAfterWriteInMillis != null) {
            acb.setExpireAfterWriteInMillis(Long.parseLong(expireAfterWriteInMillis));
        }
        // 多长时间没有访问就让缓存失效，0表示不使用该功能（注意：只支持本地缓存）
        String expireAfterAccessInMillis = ct.getProperty("expireAfterAccessInMillis");
        if (expireAfterAccessInMillis != null) {
            acb.setExpireAfterAccessInMillis(Long.parseLong(expireAfterAccessInMillis));
        }

    }

    /**
     * 初始化 CacheBuilder 构造器交由子类去实现
     *
     * @param ct                  配置信息
     * @param cacheAreaWithPrefix 配置前缀
     * @return CacheBuilder 构造器
     */
    protected abstract CacheBuilder initCache(ConfigTree ct, String cacheAreaWithPrefix);
}
