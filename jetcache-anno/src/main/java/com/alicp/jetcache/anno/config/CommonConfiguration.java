/**
 * Created on 2019/6/23.
 */
package com.alicp.jetcache.anno.config;

import com.alicp.jetcache.anno.support.ConfigMap;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
@Configuration
public class CommonConfiguration {
    /**
     * 注入一个 Bean 至容器，设置为框架内部的角色
     *
     * @return ConfigMap
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public ConfigMap jetcacheConfigMap() {
        return new ConfigMap();
    }
}
