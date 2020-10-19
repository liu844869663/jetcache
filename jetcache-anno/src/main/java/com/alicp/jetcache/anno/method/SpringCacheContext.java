package com.alicp.jetcache.anno.method;

import com.alicp.jetcache.anno.support.CacheContext;
import com.alicp.jetcache.anno.support.ConfigMap;
import com.alicp.jetcache.anno.support.GlobalCacheConfig;
import com.alicp.jetcache.anno.support.SpringConfigProvider;
import org.springframework.context.ApplicationContext;

/**
 * Created on 2016/10/19.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class SpringCacheContext extends CacheContext {

    private ApplicationContext applicationContext;

    public SpringCacheContext(SpringConfigProvider configProvider, GlobalCacheConfig globalCacheConfig, ApplicationContext applicationContext) {
        super(configProvider, globalCacheConfig);
        this.applicationContext = applicationContext;
        init();
    }

    @Override
    protected CacheInvokeContext newCacheInvokeContext() {
        return new SpringCacheInvokeContext(applicationContext);
    }

    public void init() {
        if (applicationContext != null) {
            ConfigMap configMap = applicationContext.getBean(ConfigMap.class);
            /*
             * 往缓存实例管理器中添加一个获取（不存在则创建）缓存实例的函数
             */
            cacheManager.setCacheCreator((area, cacheName) -> {
                CacheInvokeConfig cic = configMap.getByCacheName(area, cacheName);
                if (cic == null) {
                    throw new IllegalArgumentException("cache definition not found: area=" + area + ",cacheName=" + cacheName);
                }
                return __createOrGetCache(cic.getCachedAnnoConfig(), area, cacheName);
            });
        }
    }
}
