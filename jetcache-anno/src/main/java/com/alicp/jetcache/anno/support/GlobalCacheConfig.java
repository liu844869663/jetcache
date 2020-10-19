/**
 * Created on  13-09-09 17:29
 */
package com.alicp.jetcache.anno.support;

import com.alicp.jetcache.CacheBuilder;

import java.util.Map;

/**
 * 缓存的全局配置
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class GlobalCacheConfig {

	/**
	 * 生成缓存实例名称需要截取的包名
	 */
	private String[] hiddenPackages;
	/**
	 * 统计时间间隔
	 */
	protected int statIntervalMinutes;
	/**
	 * 缓存区域 area 是否作为缓存实例名称的前缀（默认true，为了兼容老版本）
	 */
	private boolean areaInCacheName = true;
	
	/**
	 * 是否开启保护模式（缓存未命中，同一个JVM里面是否只允许一个线程去执行原方法，其它线程等待结果）
	 */
	private boolean penetrationProtect = false;
	/**
	 * 是否开启缓存
	 */
	private boolean enableMethodCache = true;
	/**
	 * 保存本地缓存 CacheBuilder 构造器
	 */
	private Map<String, CacheBuilder> localCacheBuilders;
	/**
	 * 保存远程缓存 CacheBuilder 构造器
	 */
	private Map<String, CacheBuilder> remoteCacheBuilders;

	public GlobalCacheConfig() {
	}

	public String[] getHiddenPackages() {
		return hiddenPackages;
	}

	public void setHiddenPackages(String[] hiddenPackages) {
		this.hiddenPackages = hiddenPackages;
	}

	public Map<String, CacheBuilder> getLocalCacheBuilders() {
		return localCacheBuilders;
	}

	public void setLocalCacheBuilders(Map<String, CacheBuilder> localCacheBuilders) {
		this.localCacheBuilders = localCacheBuilders;
	}

	public Map<String, CacheBuilder> getRemoteCacheBuilders() {
		return remoteCacheBuilders;
	}

	public void setRemoteCacheBuilders(Map<String, CacheBuilder> remoteCacheBuilders) {
		this.remoteCacheBuilders = remoteCacheBuilders;
	}

	public int getStatIntervalMinutes() {
		return statIntervalMinutes;
	}

	public void setStatIntervalMinutes(int statIntervalMinutes) {
		this.statIntervalMinutes = statIntervalMinutes;
	}

	public boolean isAreaInCacheName() {
		return areaInCacheName;
	}

	public void setAreaInCacheName(boolean areaInCacheName) {
		this.areaInCacheName = areaInCacheName;
	}

	public boolean isPenetrationProtect() {
		return penetrationProtect;
	}

	public void setPenetrationProtect(boolean penetrationProtect) {
		this.penetrationProtect = penetrationProtect;
	}

	public boolean isEnableMethodCache() {
		return enableMethodCache;
	}

	public void setEnableMethodCache(boolean enableMethodCache) {
		this.enableMethodCache = enableMethodCache;
	}
}
