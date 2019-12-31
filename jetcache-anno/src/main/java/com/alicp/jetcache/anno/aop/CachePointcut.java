/**
 * Created on  13-09-19 20:56
 */
package com.alicp.jetcache.anno.aop;

import com.alicp.jetcache.anno.method.CacheConfigUtil;
import com.alicp.jetcache.anno.method.CacheInvokeConfig;
import com.alicp.jetcache.anno.method.ClassUtil;
import com.alicp.jetcache.anno.support.ConfigMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.asm.Type;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class CachePointcut extends StaticMethodMatcherPointcut implements ClassFilter {

	private static final Logger logger = LoggerFactory.getLogger(CachePointcut.class);

	/**
	 * 本地缓存的方法注解信息
	 */
	private ConfigMap cacheConfigMap;
	/**
	 * 需要扫描的包名
	 */
	private String[] basePackages;

	public CachePointcut(String[] basePackages) {
		setClassFilter(this);
		this.basePackages = basePackages;
	}

	@Override
	public boolean matches(Class clazz) {
		// 该类是否匹配
		boolean b = matchesImpl(clazz);
		logger.trace("check class match {}: {}", b, clazz);
		return b;
	}

	private boolean matchesImpl(Class clazz) {
		if (matchesThis(clazz)) { // 匹配类对象
			return true;
		}
		Class[] cs = clazz.getInterfaces();
		if (cs != null) { // 类对象的接口是否需要匹配
			for (Class c : cs) {
				if (matchesImpl(c)) {
					return true;
				}
			}
		}
		if (!clazz.isInterface()) { // 类对象的父类是否需要匹配
			Class sp = clazz.getSuperclass();
			if (sp != null && matchesImpl(sp)) {
				return true;
			}
		}
		return false;
	}

	public boolean matchesThis(Class clazz) {
		String name = clazz.getName();
		if (exclude(name)) { // 如果该类对象需要exclude则返回false
			return false;
		}
		// 判断该类对象是否在basePackages中
		return include(name);
	}

	private boolean include(String name) {
		if (basePackages != null) {
			for (String p : basePackages) {
				if (name.startsWith(p)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean exclude(String name) {
		if (name.startsWith("java")) {
			return true;
		}
		if (name.startsWith("org.springframework")) {
			return true;
		}
		if (name.indexOf("$$EnhancerBySpringCGLIB$$") >= 0) {
			return true;
		}
		if (name.indexOf("$$FastClassBySpringCGLIB$$") >= 0) {
			return true;
		}
		return false;
	}

	@Override
	public boolean matches(Method method, Class targetClass) {
		// 该方法是否匹配
		boolean b = matchesImpl(method, targetClass);
		if (b) {
			if (logger.isDebugEnabled()) {
				logger.debug("check method match true: method={}, declaringClass={}, targetClass={}", method.getName(),
						ClassUtil.getShortClassName(method.getDeclaringClass().getName()),
						targetClass == null ? null : ClassUtil.getShortClassName(targetClass.getName()));
			}
		} else {
			if (logger.isTraceEnabled()) {
				logger.trace("check method match false: method={}, declaringClass={}, targetClass={}", method.getName(),
						ClassUtil.getShortClassName(method.getDeclaringClass().getName()),
						targetClass == null ? null : ClassUtil.getShortClassName(targetClass.getName()));
			}
		}
		return b;
	}

	private boolean matchesImpl(Method method, Class targetClass) {
		if (!matchesThis(method.getDeclaringClass())) { // 如果该方法所在的类对象不匹配则直接返回false
			return false;
		}
		if (exclude(targetClass.getName())) { // 如果该类对象在排除范围类则直接返回false
			return false;
		}
		// 生成key
		String key = getKey(method, targetClass);
		// 获取本地缓存中该key对应的注解信息
		CacheInvokeConfig cac = cacheConfigMap.getByMethodInfo(key);
		if (cac == CacheInvokeConfig.getNoCacheInvokeConfigInstance()) { // 注解信息为NoCacheInvokeConfig，表示无须缓存，则返回false
			return false;
		} else if (cac != null) { // 注解信息不为null表示该方法匹配成功
			return true;
		} else { // cac为null
			cac = new CacheInvokeConfig();
			// 解析方法的相关注解信息
			CacheConfigUtil.parse(cac, method);

			String name = method.getName();
			Class<?>[] paramTypes = method.getParameterTypes();
			// 解析父类中方法的注解信息
			parseByTargetClass(cac, targetClass, name, paramTypes);

			if (!cac.isEnableCacheContext() && cac.getCachedAnnoConfig() == null
					&& cac.getInvalidateAnnoConfigs() == null && cac.getUpdateAnnoConfig() == null) { // 该方法不需要缓存
				cacheConfigMap.putByMethodInfo(key, CacheInvokeConfig.getNoCacheInvokeConfigInstance());
				return false;
			} else {
				// 该方法需要缓存
				cacheConfigMap.putByMethodInfo(key, cac);
				return true;
			}
		}
	}

	public static String getKey(Method method, Class targetClass) {
		StringBuilder sb = new StringBuilder();
		sb.append(method.getDeclaringClass().getName());
		sb.append('.');
		sb.append(method.getName());
		sb.append(Type.getMethodDescriptor(method));
		if (targetClass != null) {
			sb.append('_');
			sb.append(targetClass.getName());
		}
		return sb.toString();
	}

	private void parseByTargetClass(CacheInvokeConfig cac, Class<?> clazz, String name, Class<?>[] paramTypes) {
		if (!clazz.isInterface() && clazz.getSuperclass() != null) {
			parseByTargetClass(cac, clazz.getSuperclass(), name, paramTypes);
		}
		Class<?>[] intfs = clazz.getInterfaces();
		for (Class<?> it : intfs) {
			parseByTargetClass(cac, it, name, paramTypes);
		}

		boolean matchThis = matchesThis(clazz);
		if (matchThis) {
			Method[] methods = clazz.getDeclaredMethods();
			for (Method method : methods) {
				if (methodMatch(name, method, paramTypes)) {
					CacheConfigUtil.parse(cac, method);
					break;
				}
			}
		}
	}

	private boolean methodMatch(String name, Method method, Class<?>[] paramTypes) {
		if (!Modifier.isPublic(method.getModifiers())) {
			return false;
		}
		if (!name.equals(method.getName())) {
			return false;
		}
		Class<?>[] ps = method.getParameterTypes();
		if (ps.length != paramTypes.length) {
			return false;
		}
		for (int i = 0; i < ps.length; i++) {
			if (!ps[i].equals(paramTypes[i])) {
				return false;
			}
		}
		return true;
	}

	public void setCacheConfigMap(ConfigMap cacheConfigMap) {
		this.cacheConfigMap = cacheConfigMap;
	}
}
