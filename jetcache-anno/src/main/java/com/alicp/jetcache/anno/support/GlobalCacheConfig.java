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
	 * 需要隐藏的包名
	 */
	private String[] hiddenPackages;
	/**
	 * 统计时间单位(min)
	 */
	protected int statIntervalMinutes;
	/**
	 * area名称是否作为缓存key的前缀(默认true,为了兼容老版本)
	 */
	private boolean areaInCacheName = true;
	
	/**
	 * 当缓存未命中的时候，是否一个JVM里面只有一个线程去执行方法，其它线程等待结果
	 */
	private boolean penetrationProtect = false;
	/**
	 * 开启方法缓存
	 */
	private boolean enableMethodCache = true;
	/**
	 * 保存本地缓存CacheBuilder
	 */
	private Map<String, CacheBuilder> localCacheBuilders;
	/**
	 * 保存远程缓存CacheBuilder
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
