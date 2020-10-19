/**
 * Created on 2019/6/7.
 */
package com.alicp.jetcache.anno.support;

import com.alicp.jetcache.anno.SerialPolicy;
import com.alicp.jetcache.support.JavaValueDecoder;
import com.alicp.jetcache.support.SpringJavaValueDecoder;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.function.Function;

/**
 * 支持获取用户自定义编码或者解码函数
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class DefaultSpringEncoderParser extends DefaultEncoderParser implements ApplicationContextAware {
	private ApplicationContext applicationContext;

	/**
	 * 截取名称的前缀("bean:")，如果满足条件
	 * 
	 * @param str 截取前
	 * @return 截取后
	 */
	static String parseBeanName(String str) {
		final String beanPrefix = "bean:";
		int len = beanPrefix.length();
		if (str != null && str.startsWith(beanPrefix) && str.length() > len) {
			return str.substring(len);
		} else {
			return null;
		}
	}

	/**
	 * 根据 valueEncoder 生成缓存数据的编码函数
	 *
	 * @param valueEncoder value的编码类型
	 * @return 编码函数
	 */
	@Override
	public Function<Object, byte[]> parseEncoder(String valueEncoder) {
		// 根据 valueEncoder 解析对应的 bean 名称，用于用户自定义 value 编码函数
		String beanName = parseBeanName(valueEncoder);
		if (beanName == null) {
			return super.parseEncoder(valueEncoder);
		} else { // 从 Spring 容器中获取用户自定义的 value 编码函数
			Object bean = applicationContext.getBean(beanName);
			if (bean instanceof Function) {
				return (Function<Object, byte[]>) bean;
			} else {
				return ((SerialPolicy) bean).encoder();
			}
		}
	}

	/**
	 * 根据 valueDecoder 生成缓存数据的解码函数
	 *
	 * @param valueDecoder value的解码类型
	 * @return 解码类型
	 */
	@Override
	public Function<byte[], Object> parseDecoder(String valueDecoder) {
		// 根据 valueEncoder 解析对应的 bean 名称，用于用户自定义 value 解码函数
		String beanName = parseBeanName(valueDecoder);
		if (beanName == null) {
			return super.parseDecoder(valueDecoder);
		} else { // 从 Spring 容器中获取用户自定义的 value 解码函数
			Object bean = applicationContext.getBean(beanName);
			if (bean instanceof Function) {
				return (Function<byte[], Object>) bean;
			} else {
				return ((SerialPolicy) bean).decoder();
			}
		}
	}

	@Override
	JavaValueDecoder javaValueDecoder(boolean useIdentityNumber) {
		return new SpringJavaValueDecoder(useIdentityNumber);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
