/**
 * Created on  13-10-02 16:10
 */
package com.alicp.jetcache.anno.method;

import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.support.CacheAnnoConfig;

import java.lang.reflect.Method;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class CacheInvokeContext {
    /**
     * 被拦截的对象的调用器
     */
    private Invoker invoker;
    /**
     * 拦截的方法
     */
    private Method method;
    /**
     * 拦截的方法的入参
     */
    private Object[] args;
    /**
     * 拦截的方法的缓存配置信息，包含缓存实例
     */
    private CacheInvokeConfig cacheInvokeConfig;
    /**
     * 被拦截的对象
     */
    private Object targetObject;
    /**
     * 执行后的返回结果
     */
    private Object result;

    private BiFunction<CacheInvokeContext, CacheAnnoConfig, Cache> cacheFunction;
    /**
     * 需要隐藏的包名
     */
    private String[] hiddenPackages;

    public CacheInvokeContext(){
    }


    public void setInvoker(Invoker invoker) {
        this.invoker = invoker;
    }

    public Invoker getInvoker() {
        return invoker;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public void setArgs(Object[] args) {
        this.args = args;
    }

    public void setCacheInvokeConfig(CacheInvokeConfig cacheInvokeConfig) {
        this.cacheInvokeConfig = cacheInvokeConfig;
    }

    public CacheInvokeConfig getCacheInvokeConfig() {
        return cacheInvokeConfig;
    }

    public void setHiddenPackages(String[] hiddenPackages) {
        this.hiddenPackages = hiddenPackages;
    }

    public String[] getHiddenPackages() {
        return hiddenPackages;
    }

    public void setCacheFunction(BiFunction<CacheInvokeContext, CacheAnnoConfig, Cache> cacheFunction) {
        this.cacheFunction = cacheFunction;
    }

    public BiFunction<CacheInvokeContext, CacheAnnoConfig, Cache> getCacheFunction() {
        return cacheFunction;
    }

    public Object[] getArgs() {
        return args;
    }

    public Object getTargetObject() {
        return targetObject;
    }

    public void setTargetObject(Object targetObject) {
        this.targetObject = targetObject;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }
}
