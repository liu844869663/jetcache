/**
 * Created on 2019/6/7.
 */
package com.alicp.jetcache.anno.support;

import com.alicp.jetcache.CacheConfigException;
import com.alicp.jetcache.anno.KeyConvertor;
import com.alicp.jetcache.support.FastjsonKeyConvertor;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.function.Function;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class DefaultSpringKeyConvertorParser extends DefaultKeyConvertorParser implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    
    @Override
    public Function<Object, Object> parseKeyConvertor(String convertor) {
    	// 如果是自定义的key转换器，则将convertor的"bean:"截取掉
        String beanName = DefaultSpringEncoderParser.parseBeanName(convertor);
        if (beanName == null) { // 为空表示需从本身定义的key转换器获取
            return super.parseKeyConvertor(convertor);
        } else {
        	// 从Spring IOC容器中加载对应的KeyConvertor(需实现BiFunction)
            return (Function<Object, Object>) applicationContext.getBean(beanName);
        }
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
