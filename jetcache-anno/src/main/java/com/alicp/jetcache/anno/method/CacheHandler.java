/**
 * Created on  13-09-09 15:59
 */
package com.alicp.jetcache.anno.method;

import com.alicp.jetcache.*;
import com.alicp.jetcache.anno.support.*;
import com.alicp.jetcache.event.CacheLoadEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class CacheHandler implements InvocationHandler {
	private static Logger logger = LoggerFactory.getLogger(CacheHandler.class);

	private Object src;
	private Supplier<CacheInvokeContext> contextSupplier;
	private String[] hiddenPackages;
	private ConfigMap configMap;

	private static class CacheContextSupport extends CacheContext {

		public CacheContextSupport() {
			super(null, null);
		}

		static void _enable() {
			enable();
		}

		static void _disable() {
			disable();
		}

		static boolean _isEnabled() {
			return isEnabled();
		}
	}

	public CacheHandler(Object src, ConfigMap configMap, Supplier<CacheInvokeContext> contextSupplier,
			String[] hiddenPackages) {
		this.src = src;
		this.configMap = configMap;
		this.contextSupplier = contextSupplier;
		this.hiddenPackages = hiddenPackages;
	}

	@Override
	public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
		CacheInvokeContext context = null;

		String sig = ClassUtil.getMethodSig(method);
		CacheInvokeConfig cac = configMap.getByMethodInfo(sig);
		if (cac != null) {
			context = contextSupplier.get();
			context.setCacheInvokeConfig(cac);
		}
		if (context == null) {
			return method.invoke(src, args);
		} else {
			context.setInvoker(() -> method.invoke(src, args));
			context.setHiddenPackages(hiddenPackages);
			context.setArgs(args);
			context.setMethod(method);
			return invoke(context);
		}
	}

	public static Object invoke(CacheInvokeContext context) throws Throwable {
		if (context.getCacheInvokeConfig().isEnableCacheContext()) {
			try {
				CacheContextSupport._enable();
				return doInvoke(context);
			} finally {
				CacheContextSupport._disable();
			}
		} else {
			return doInvoke(context);
		}
	}

	private static Object doInvoke(CacheInvokeContext context) throws Throwable {
		// 获取本地调用的上下文
		CacheInvokeConfig cic = context.getCacheInvokeConfig();
		// 获取注解配置信息
		CachedAnnoConfig cachedConfig = cic.getCachedAnnoConfig();
		if (cachedConfig != null && (cachedConfig.isEnabled() || CacheContextSupport._isEnabled())) {
			// 经过缓存中获取结果
			return invokeWithCached(context);
		} else if (cic.getInvalidateAnnoConfigs() != null || cic.getUpdateAnnoConfig() != null) {
			// 根据结果删除或者更新缓存
			return invokeWithInvalidateOrUpdate(context);
		} else {
			// 执行该方法
			return invokeOrigin(context);
		}
	}

	private static Object invokeWithInvalidateOrUpdate(CacheInvokeContext context) throws Throwable {
		// 执行当前方法
		Object originResult = invokeOrigin(context);
		context.setResult(originResult);
		CacheInvokeConfig cic = context.getCacheInvokeConfig();

		if (cic.getInvalidateAnnoConfigs() != null) {
			// 是否删除缓存
			doInvalidate(context, cic.getInvalidateAnnoConfigs());
		}
		CacheUpdateAnnoConfig updateAnnoConfig = cic.getUpdateAnnoConfig();
		if (updateAnnoConfig != null) {
			// 是否更新缓存
			doUpdate(context, updateAnnoConfig);
		}

		return originResult;
	}

	private static Iterable toIterable(Object obj) {
		if (obj.getClass().isArray()) {
			if (obj instanceof Object[]) {
				return Arrays.asList((Object[]) obj);
			} else {
				List list = new ArrayList();
				int len = Array.getLength(obj);
				for (int i = 0; i < len; i++) {
					list.add(Array.get(obj, i));
				}
				return list;
			}
		} else if (obj instanceof Iterable) {
			return (Iterable) obj;
		} else {
			return null;
		}
	}

	private static void doInvalidate(CacheInvokeContext context, List<CacheInvalidateAnnoConfig> annoConfig) {
		for (CacheInvalidateAnnoConfig config : annoConfig) {
			doInvalidate(context, config);
		}
	}

	private static void doInvalidate(CacheInvokeContext context, CacheInvalidateAnnoConfig annoConfig) {
		// 获取对应的缓存实例
		Cache cache = context.getCacheFunction().apply(context, annoConfig);
		if (cache == null) {
			return;
		}
		// 表达式结果
		boolean condition = ExpressionUtil.evalCondition(context, annoConfig);
		if (!condition) {
			return;
		}
		// 生成缓存 key
		Object key = ExpressionUtil.evalKey(context, annoConfig);
		if (key == null) {
			return;
		}
		if (annoConfig.isMulti()) {
			Iterable it = toIterable(key);
			if (it == null) {
				logger.error("jetcache @CacheInvalidate key is not instance of Iterable or array: "
						+ annoConfig.getDefineMethod());
				return;
			}
			Set keys = new HashSet();
			it.forEach(k -> keys.add(k));
			cache.removeAll(keys);
		} else {
			cache.remove(key);
		}
	}

	private static void doUpdate(CacheInvokeContext context, CacheUpdateAnnoConfig updateAnnoConfig) {
		Cache cache = context.getCacheFunction().apply(context, updateAnnoConfig);
		if (cache == null) {
			return;
		}
		// 表达式结果
		boolean condition = ExpressionUtil.evalCondition(context, updateAnnoConfig);
		if (!condition) {
			return;
		}
		// 生成缓存 key
		Object key = ExpressionUtil.evalKey(context, updateAnnoConfig);
		Object value = ExpressionUtil.evalValue(context, updateAnnoConfig);
		if (key == null || value == ExpressionUtil.EVAL_FAILED) {
			return;
		}
		if (updateAnnoConfig.isMulti()) {
			if (value == null) {
				return;
			}
			Iterable keyIt = toIterable(key);
			Iterable valueIt = toIterable(value);
			if (keyIt == null) {
				logger.error("jetcache @CacheUpdate key is not instance of Iterable or array: "
						+ updateAnnoConfig.getDefineMethod());
				return;
			}
			if (valueIt == null) {
				logger.error("jetcache @CacheUpdate value is not instance of Iterable or array: "
						+ updateAnnoConfig.getDefineMethod());
				return;
			}

			List keyList = new ArrayList();
			List valueList = new ArrayList();
			keyIt.forEach(o -> keyList.add(o));
			valueIt.forEach(o -> valueList.add(o));
			if (keyList.size() != valueList.size()) {
				logger.error("jetcache @CacheUpdate key size not equals with value size: "
						+ updateAnnoConfig.getDefineMethod());
				return;
			} else {
				Map m = new HashMap();
				for (int i = 0; i < valueList.size(); i++) {
					m.put(keyList.get(i), valueList.get(i));
				}
				cache.putAll(m);
			}
		} else {
			cache.put(key, value);
		}
	}

	private static Object invokeWithCached(CacheInvokeContext context) throws Throwable {
		// 获取缓存实例配置
		CacheInvokeConfig cic = context.getCacheInvokeConfig();
		// 获取注解配置信息
		CachedAnnoConfig cac = cic.getCachedAnnoConfig();
		// 获取缓存实例对象（不存在则会创建并设置到 cac 中）
		// 可在 JetCacheInterceptor 创建本次调用的上下文时，调用 createCacheInvokeContext(cacheConfigMap) 方法中查看详情
		Cache cache = context.getCacheFunction().apply(context, cac);
		if (cache == null) {
			logger.error("no cache with name: " + context.getMethod());
			// 无缓存实例对象，执行原有方法
			return invokeOrigin(context);
		}

		// 生成缓存 Key 对象（注解中没有配置的话就是入参，没有入参则为 "_$JETCACHE_NULL_KEY$_" ）
		Object key = ExpressionUtil.evalKey(context, cic.getCachedAnnoConfig());
		if (key == null) {
			 // 生成缓存 Key 失败则执行原方法，并记录 CacheLoadEvent 事件
			return loadAndCount(context, cache, key);
		}

		/*
		 * 根据配置的 condition 来决定是否走缓存
		 * 缓存注解中没有配置 condition 表示所有请求都走缓存
		 * 配置了 condition 表示满足条件的才走缓存
		 */
		if (!ExpressionUtil.evalCondition(context, cic.getCachedAnnoConfig())) {
			// 不满足 condition 则直接执行原方法，并记录 CacheLoadEvent 事件
			return loadAndCount(context, cache, key);
		}

		try {
			// 创建一个执行原有方法的函数
			CacheLoader loader = new CacheLoader() {
				@Override
				public Object load(Object k) throws Throwable {
					Object result = invokeOrigin(context);
					context.setResult(result);
					return result;
				}

				@Override
				public boolean vetoCacheUpdate() {
					// 本次执行原方法后是否需要更新缓存
					return !ExpressionUtil.evalPostCondition(context, cic.getCachedAnnoConfig());
				}
			};
			// 获取结果
			Object result = cache.computeIfAbsent(key, loader);
			return result;
		} catch (CacheInvokeException e) {
			throw e.getCause();
		}
	}

	private static Object loadAndCount(CacheInvokeContext context, Cache cache, Object key) throws Throwable {
		long t = System.currentTimeMillis();
		Object v = null;
		boolean success = false;
		try {
			// 调用原有方法
			v = invokeOrigin(context);
			success = true;
		} finally {
			t = System.currentTimeMillis() - t;
			// 发送 CacheLoadEvent 事件
			CacheLoadEvent event = new CacheLoadEvent(cache, t, key, v, success);
			while (cache instanceof ProxyCache) {
				cache = ((ProxyCache) cache).getTargetCache();
			}
			if (cache instanceof AbstractCache) {
				((AbstractCache) cache).notify(event);
			}
		}
		return v;
	}

	private static Object invokeOrigin(CacheInvokeContext context) throws Throwable {
		// 执行被拦截的方法
		return context.getInvoker().invoke();
	}

	public static class CacheHandlerRefreshCache<K, V> extends RefreshCache<K, V> {

		public CacheHandlerRefreshCache(Cache cache) {
			super(cache);
		}

		@Override
		public void addOrUpdateRefreshTask(K key, CacheLoader<K, V> loader) {
			super.addOrUpdateRefreshTask(key, loader);
		}
	}

}
