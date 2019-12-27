/**
 * Created on  13-09-18 20:33
 */
package com.alicp.jetcache.anno.aop;

import com.alicp.jetcache.anno.method.CacheHandler;
import com.alicp.jetcache.anno.method.CacheInvokeConfig;
import com.alicp.jetcache.anno.method.CacheInvokeContext;
import com.alicp.jetcache.anno.support.ConfigMap;
import com.alicp.jetcache.anno.support.ConfigProvider;
import com.alicp.jetcache.anno.support.GlobalCacheConfig;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Method;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class JetCacheInterceptor implements MethodInterceptor, ApplicationContextAware {

    //private static final Logger logger = LoggerFactory.getLogger(JetCacheInterceptor.class);

    @Autowired
    private ConfigMap cacheConfigMap;
    private ApplicationContext applicationContext;
    private GlobalCacheConfig globalCacheConfig;
    ConfigProvider configProvider;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    // 有@Cached注解的方法会进入这里
    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        if (configProvider == null) {
            configProvider = applicationContext.getBean(ConfigProvider.class);
        }
        if (configProvider != null && globalCacheConfig == null) {
            globalCacheConfig = configProvider.getGlobalCacheConfig();
        }
        if (globalCacheConfig == null || !globalCacheConfig.isEnableMethodCache()) {
            return invocation.proceed();
        }

        // 获取被拦截的方法
        Method method = invocation.getMethod();
        // 获取被拦截的对象
        Object obj = invocation.getThis();
        CacheInvokeConfig cac = null;
        if (obj != null) {
        	// 获取改方法的Key(方法所在类名+方法名+(参数类型)+方法返回类型+_被拦截的类名)
            String key = CachePointcut.getKey(method, obj.getClass());
            // 获取该方法的注解详情(cachedAnnoConfig、invalidateAnnoConfig、updateAnnoConfig、enableCacheContext),
            // 其中cachedAnnoConfig包含刷新缓存的配置，这些信息被封装成了CacheInvokeConfig
            cac  = cacheConfigMap.getByMethodInfo(key);
        }

        /*
        if(logger.isTraceEnabled()){
            logger.trace("JetCacheInterceptor invoke. foundJetCacheConfig={}, method={}.{}(), targetClass={}",
                    cac != null,
                    method.getDeclaringClass().getName(),
                    method.getName(),
                    invocation.getThis() == null ? null : invocation.getThis().getClass().getName());
        }
        */

        if (cac == null || cac == CacheInvokeConfig.getNoCacheInvokeConfigInstance()) {
            return invocation.proceed();
        }

        // 创建一个上下文对象，并生成一个函数，入参为CacheInvokeContext和cachedAnnoConfig，目的是获取对应的Cache对象
        CacheInvokeContext context = configProvider.getCacheContext().createCacheInvokeContext(cacheConfigMap);
        context.setTargetObject(invocation.getThis()); // 被拦截的对象
        context.setInvoker(invocation::proceed); // 拦截器
        context.setMethod(method); // 被拦截的方法
        context.setArgs(invocation.getArguments()); // 入参
        context.setCacheInvokeConfig(cac); // 方法的注解信息
        context.setHiddenPackages(globalCacheConfig.getHiddenPackages()); // 需要隐藏的包名
        // 继续往下执行
        return CacheHandler.invoke(context);
    }

    public void setCacheConfigMap(ConfigMap cacheConfigMap) {
        this.cacheConfigMap = cacheConfigMap;
    }

}
