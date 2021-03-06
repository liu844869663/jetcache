package com.alicp.jetcache.anno.config;

import com.alicp.jetcache.anno.aop.CacheAdvisor;
import com.alicp.jetcache.anno.aop.JetCacheInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Created on 2016/11/16.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
@Configuration
public class JetCacheProxyConfiguration implements ImportAware, ApplicationContextAware {

    protected AnnotationAttributes enableMethodCache;
    private ApplicationContext applicationContext;

    @Override
    public void setImportMetadata(AnnotationMetadata importMetadata) {
        // 获取 @EnableMethodCache 注解信息
        this.enableMethodCache = AnnotationAttributes.fromMap(
                importMetadata.getAnnotationAttributes(EnableMethodCache.class.getName(), false));
        if (this.enableMethodCache == null) {
            throw new IllegalArgumentException(
                    "@EnableMethodCache is not present on importing class " + importMetadata.getClassName());
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Bean(name = CacheAdvisor.CACHE_ADVISOR_BEAN_NAME)
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public CacheAdvisor jetcacheAdvisor(JetCacheInterceptor jetCacheInterceptor) {
        CacheAdvisor advisor = new CacheAdvisor();
        // bean的名称：jetcache2.internalCacheAdvisor
        advisor.setAdviceBeanName(CacheAdvisor.CACHE_ADVISOR_BEAN_NAME);
        // 设置缓存拦截器为 JetCacheInterceptor
        advisor.setAdvice(jetCacheInterceptor);
        // 设置需要扫描的包
        advisor.setBasePackages(this.enableMethodCache.getStringArray("basePackages"));
        // 设置优先级，默认 Integer 的最大值，最低优先级
        advisor.setOrder(this.enableMethodCache.<Integer>getNumber("order"));
        return advisor;
    }

    /**
     * 注入一个 JetCacheInterceptor 拦截器，设置为框架内部的角色
     *
     * @return JetCacheInterceptor
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public JetCacheInterceptor jetCacheInterceptor() {
        return new JetCacheInterceptor();
    }

}