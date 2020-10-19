package com.alicp.jetcache;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Created on 16/9/7.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public abstract class AbstractCacheBuilder<T extends AbstractCacheBuilder<T>> implements CacheBuilder, Cloneable {

    /**
     * 该缓存实例的配置
     */
    protected CacheConfig config;
    /**
     * 创建缓存实例函数
     */
    private Function<CacheConfig, Cache> buildFunc;

    public abstract CacheConfig getConfig();

    protected T self() {
        return (T) this;
    }

    public T buildFunc(Function<CacheConfig, Cache> buildFunc) {
        this.buildFunc = buildFunc;
        return self();
    }

    protected void beforeBuild() {
    }

    @Deprecated
    public final <K, V> Cache<K, V> build() {
        return buildCache();
    }

    @Override
    public final <K, V> Cache<K, V> buildCache() {
        if (buildFunc == null) {
            throw new CacheConfigException("no buildFunc");
        }
        beforeBuild();
        // 克隆一份配置信息，因为这里获取到的是全局配置信息，以防后续被修改
        CacheConfig c = getConfig().clone();
        // 通过构建函数创建一个缓存实例
        Cache<K, V> cache = buildFunc.apply(c);
        /*
         * 目前发现 c.getLoader() 都是 null，后续都会把 cache 封装成 CacheHandlerRefreshCache
         * TODO 疑问？？？？
         */
        if (c.getLoader() != null) {
            if (c.getRefreshPolicy() == null) {
                cache = new LoadingCache<>(cache);
            } else {
                cache = new RefreshCache<>(cache);
            }
        }
        return cache;
    }

    @Override
    public Object clone() {
        AbstractCacheBuilder copy = null;
        try {
            copy = (AbstractCacheBuilder) super.clone();
            copy.config = getConfig().clone();
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new CacheException(e);
        }
    }

    public T keyConvertor(Function<Object, Object> keyConvertor) {
        getConfig().setKeyConvertor(keyConvertor);
        return self();
    }

    public void setKeyConvertor(Function<Object, Object> keyConvertor) {
        getConfig().setKeyConvertor(keyConvertor);
    }

    public T expireAfterAccess(long defaultExpire, TimeUnit timeUnit) {
        getConfig().setExpireAfterAccessInMillis(timeUnit.toMillis(defaultExpire));
        return self();
    }

    public void setExpireAfterAccessInMillis(long expireAfterAccessInMillis) {
        getConfig().setExpireAfterAccessInMillis(expireAfterAccessInMillis);
    }

    public T expireAfterWrite(long defaultExpire, TimeUnit timeUnit) {
        getConfig().setExpireAfterWriteInMillis(timeUnit.toMillis(defaultExpire));
        return self();
    }

    public void setExpireAfterWriteInMillis(long expireAfterWriteInMillis) {
        getConfig().setExpireAfterWriteInMillis(expireAfterWriteInMillis);
    }

    public T addMonitor(CacheMonitor monitor) {
        getConfig().getMonitors().add(monitor);
        return self();
    }

    public void setMonitors(List<CacheMonitor> monitors) {
        getConfig().setMonitors(monitors);
    }

    public T cacheNullValue(boolean cacheNullValue) {
        getConfig().setCacheNullValue(cacheNullValue);
        return self();
    }

    public void setCacheNullValue(boolean cacheNullValue) {
        getConfig().setCacheNullValue(cacheNullValue);
    }

    public <K, V> T loader(CacheLoader<K, V> loader) {
        getConfig().setLoader(loader);
        return self();
    }

    public <K, V> void setLoader(CacheLoader<K, V> loader) {
        getConfig().setLoader(loader);
    }

    public T refreshPolicy(RefreshPolicy refreshPolicy) {
        getConfig().setRefreshPolicy(refreshPolicy);
        return self();
    }

    public void setRefreshPolicy(RefreshPolicy refreshPolicy) {
        getConfig().setRefreshPolicy(refreshPolicy);
    }

    public T cachePenetrateProtect(boolean cachePenetrateProtect) {
        getConfig().setCachePenetrationProtect(cachePenetrateProtect);
        return self();
    }

    public void setCachePenetrateProtect(boolean cachePenetrateProtect) {
        getConfig().setCachePenetrationProtect(cachePenetrateProtect);
    }
}
