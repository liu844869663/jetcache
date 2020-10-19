package com.alicp.jetcache.autoconfigure;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.Arrays;

/**
 * 自定义一个 BeanFactoryPostProcessor ，用于 BeanFactory 容器初始后需要执行的操作
 *
 * Created on 2017/5/5.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class BeanDependencyManager implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 获取 AbstractCacheAutoInit 类型的 BeanDefinition 的名称
        // 也就是在配置文件中配置了哪些缓存类型 type ，则根据条件哪些 CacheAutoInit 会被 Spring 容器管理
        String[] autoInitBeanNames = beanFactory.getBeanNamesForType(AbstractCacheAutoInit.class, false, false);
        if (autoInitBeanNames != null) {
            // 获取 JetCacheAutoConfiguration 的 BeanDefinition
            BeanDefinition bd = beanFactory.getBeanDefinition(JetCacheAutoConfiguration.GLOBAL_CACHE_CONFIG_NAME);
            String[] dependsOn = bd.getDependsOn();
            if (dependsOn == null) {
                dependsOn = new String[0];
            }
            int oldLen = dependsOn.length;
            dependsOn = Arrays.copyOf(dependsOn, dependsOn.length + autoInitBeanNames.length);
            // 将需要注入的 AbstractCacheAutoInit 添加到 JetCacheAutoConfiguration 的依赖中
            System.arraycopy(autoInitBeanNames,0, dependsOn, oldLen, autoInitBeanNames.length);
            bd.setDependsOn(dependsOn);
        }
    }

}
