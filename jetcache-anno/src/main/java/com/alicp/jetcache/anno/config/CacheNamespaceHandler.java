/**
 * Created on  13-09-18 16:37
 */
package com.alicp.jetcache.anno.config;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class CacheNamespaceHandler extends NamespaceHandlerSupport {

    @Override
    public void init() {
        // 注册一个解析器，解析 xml 配置文件中的 annotation-driven 标签
        registerBeanDefinitionParser("annotation-driven", new CacheAnnotationParser());
    }
}
