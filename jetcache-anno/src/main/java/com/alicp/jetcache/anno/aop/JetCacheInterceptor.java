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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.lang.reflect.Method;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class JetCacheInterceptor implements MethodInterceptor, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(JetCacheInterceptor.class);

    /**
     * 缓存实例注解信息
     */
    @Autowired
    private ConfigMap cacheConfigMap;
    /**
     * Spring 上下文
     */
    private ApplicationContext applicationContext;
    /**
     * 缓存的全局配置
     */
    private GlobalCacheConfig globalCacheConfig;
    /**
     * JetCache 缓存的管理器（包含很多信息）
     */
    ConfigProvider configProvider;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        if (configProvider == null) {
            /**
             * 这里会获取到 SpringConfigProvider 可查看 {@link com.alicp.jetcache.autoconfigure.JetCacheAutoConfiguration}
             */
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
            // 获取该方法的缓存注解信息，在 Pointcut 中已经对注解进行解析并放入 ConfigMap 中
            cac  = cacheConfigMap.getByMethodInfo(key);
        }

        if(logger.isTraceEnabled()){
            logger.trace("JetCacheInterceptor invoke. foundJetCacheConfig={}, method={}.{}(), targetClass={}",
                    cac != null,
                    method.getDeclaringClass().getName(),
                    method.getName(),
                    invocation.getThis() == null ? null : invocation.getThis().getClass().getName());
        }

        // 无缓存相关注解配置信息表明无须缓存，直接执行该方法
        if (cac == null || cac == CacheInvokeConfig.getNoCacheInvokeConfigInstance()) {
            return invocation.proceed();
        }

        // 为本次调用创建一个上下文对象，包含对应的缓存实例
        CacheInvokeContext context = configProvider.getCacheContext().createCacheInvokeContext(cacheConfigMap);
        context.setTargetObject(invocation.getThis());
        context.setInvoker(invocation::proceed);
        context.setMethod(method);
        context.setArgs(invocation.getArguments());
        context.setCacheInvokeConfig(cac);
        context.setHiddenPackages(globalCacheConfig.getHiddenPackages());
        // 继续往下执行
        return CacheHandler.invoke(context);
    }

    public void setCacheConfigMap(ConfigMap cacheConfigMap) {
        this.cacheConfigMap = cacheConfigMap;
    }

}
