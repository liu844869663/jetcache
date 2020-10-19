/**
 * Created on  13-09-10 10:33
 */
package com.alicp.jetcache.anno.support;

import com.alicp.jetcache.RefreshPolicy;
import com.alicp.jetcache.anno.CacheType;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class CachedAnnoConfig extends CacheAnnoConfig {

    /**
     * 是否开启缓存
     */
    private boolean enabled;
    /**
     * 时间单位
     */
    private TimeUnit timeUnit;
    /**
     * 缓存时长
     */
    private long expire;
    /**
     * 本地缓存时长（仅当 cacheType 为 BOTH 时适用，通常小于 expire）
     */
    private long localExpire;
    /**
     * 缓存类型（REMOTE, LOCAL, BOTH）
     */
    private CacheType cacheType;
    /**
     * 本地缓存的最大元素数量
     */
    private int localLimit;
    /**
     * 是否缓存 null 值，默认 false
     */
    private boolean cacheNullValue;
    /**
     * 远程缓存的序列化方式（可选值为 SerialPolicy.JAVA 和 SerialPolicy.KRYO，默认 JAVA）
     */
    private String serialPolicy;
    /**
     * KEY的转换方式（可选值为  KeyConvertor.FASTJSON 和 KeyConvertor.NONE）
     */
    private String keyConvertor;
    /**
     * 使用SpEL指定条件，如果表达式返回true的时候才更新缓存，该评估在方法执行后进行，因此可以访问到#result
     */
    private String postCondition;

    private Function<Object, Boolean> postConditionEvaluator;
    /**
     * 刷新策略
     */
    private RefreshPolicy refreshPolicy;
    /**
     * 缓存访问未命中，保护策略
     */
    private PenetrationProtectConfig penetrationProtectConfig;

    public boolean isEnabled() {
        return enabled;
    }

    public long getExpire() {
        return expire;
    }

    public CacheType getCacheType() {
        return cacheType;
    }

    public int getLocalLimit() {
        return localLimit;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setExpire(long expire) {
        this.expire = expire;
    }

    public void setCacheType(CacheType cacheType) {
        this.cacheType = cacheType;
    }

    public void setLocalLimit(int localLimit) {
        this.localLimit = localLimit;
    }

    public boolean isCacheNullValue() {
        return cacheNullValue;
    }

    public void setCacheNullValue(boolean cacheNullValue) {
        this.cacheNullValue = cacheNullValue;
    }

    public String getSerialPolicy() {
        return serialPolicy;
    }

    public void setSerialPolicy(String serialPolicy) {
        this.serialPolicy = serialPolicy;
    }

    public String getKeyConvertor() {
        return keyConvertor;
    }

    public void setKeyConvertor(String keyConvertor) {
        this.keyConvertor = keyConvertor;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }


    public String getPostCondition() {
        return postCondition;
    }

    public void setPostCondition(String postCondition) {
        this.postCondition = postCondition;
    }

    public Function<Object, Boolean> getPostConditionEvaluator() {
        return postConditionEvaluator;
    }

    public void setPostConditionEvaluator(Function<Object, Boolean> postConditionEvaluator) {
        this.postConditionEvaluator = postConditionEvaluator;
    }

    public RefreshPolicy getRefreshPolicy() {
        return refreshPolicy;
    }

    public void setRefreshPolicy(RefreshPolicy refreshPolicy) {
        this.refreshPolicy = refreshPolicy;
    }

    public PenetrationProtectConfig getPenetrationProtectConfig() {
        return penetrationProtectConfig;
    }

    public void setPenetrationProtectConfig(PenetrationProtectConfig penetrationProtectConfig) {
        this.penetrationProtectConfig = penetrationProtectConfig;
    }

    public long getLocalExpire() {
        return localExpire;
    }

    public void setLocalExpire(long localExpire) {
        this.localExpire = localExpire;
    }
}
