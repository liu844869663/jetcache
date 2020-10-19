/**
 * Created on  13-09-24 14:32
 */
package com.alicp.jetcache.anno.method;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public interface Invoker {
    /**
     * 被拦截的方法的调用器，也就是执行 MethodInvocation 的 proceed 方法
     *
     * @return 执行结果
     * @throws Throwable 异常
     */
    Object invoke() throws Throwable;
}
