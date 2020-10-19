/**
 * Created on  13-10-02 18:38
 */
package com.alicp.jetcache.anno.method;

import com.alicp.jetcache.anno.CacheConsts;
import com.alicp.jetcache.anno.support.CacheAnnoConfig;
import com.alicp.jetcache.anno.support.CacheUpdateAnnoConfig;
import com.alicp.jetcache.anno.support.CachedAnnoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
class ExpressionUtil {

    private static final Logger logger = LoggerFactory.getLogger(ExpressionUtil.class);

    static Object EVAL_FAILED = new Object();

    public static boolean evalCondition(CacheInvokeContext context, CacheAnnoConfig cac) {
        // 获取缓存注解中的 condition
        String condition = cac.getCondition();
        try {
            if (cac.getConditionEvaluator() == null) {
                if (CacheConsts.isUndefined(condition)) {
                    // 没有定义 condition 则直接返回 true，表示走缓存
                    cac.setConditionEvaluator(o -> true);
                } else {
                    // 根据 condition 生成条件函数
                    ExpressionEvaluator e = new ExpressionEvaluator(condition, cac.getDefineMethod());
                    cac.setConditionEvaluator((o) -> (Boolean) e.apply(o));
                }
            }
            // 根据条件函数判断是否走缓存
            return cac.getConditionEvaluator().apply(context);
        } catch (Exception e) {
            logger.error("error occurs when eval condition \"" + condition + "\" in " + context.getMethod() + ":" + e.getMessage(), e);
            return false;
        }
    }

    public static boolean evalPostCondition(CacheInvokeContext context, CachedAnnoConfig cac) {
        // 获取缓存注解中的 postCondition
        String postCondition = cac.getPostCondition();
        try {
            if (cac.getPostConditionEvaluator() == null) {
                if (CacheConsts.isUndefined(postCondition)) {
                    // 没有定义 postCondition 直接返回 true，表示拒绝更新缓存，也就是不更新缓存
                    cac.setPostConditionEvaluator(o -> true);
                } else {
                    // 根据 postCondition 生成条件函数
                    ExpressionEvaluator e = new ExpressionEvaluator(postCondition, cac.getDefineMethod());
                    cac.setPostConditionEvaluator((o) -> (Boolean) e.apply(o));
                }
            }
            // 根据条件函数判断是否拒绝更新缓存
            return cac.getPostConditionEvaluator().apply(context);
        } catch (Exception e) {
            logger.error("error occurs when eval postCondition \"" + postCondition + "\" in " + context.getMethod() + ":" + e.getMessage(), e);
            return false;
        }
    }

    public static Object evalKey(CacheInvokeContext context, CacheAnnoConfig cac) {
    	// 获取注解中定义的缓存 key 的 SpEL 表达式
        String keyScript = cac.getKey();
        try {
            if (cac.getKeyEvaluator() == null) {
                if (CacheConsts.isUndefined(keyScript)) { // 1.没有定义 SpEL表达式
                    cac.setKeyEvaluator(o -> {
                        CacheInvokeContext c = (CacheInvokeContext) o;
                        // 直接返回被拦截方法的入参
                        return c.getArgs() == null ? "_$JETCACHE_NULL_KEY$_" : c.getArgs();
                    });
                } else {
                	// 2.定义了 SpEL表达式，生成一个转换函数
                    ExpressionEvaluator e = new ExpressionEvaluator(keyScript, cac.getDefineMethod());
                    cac.setKeyEvaluator((o) -> e.apply(o));
                }
            }
            // 调用生成缓存 Key 的函数，返回本次调用的缓存 Key
            return cac.getKeyEvaluator().apply(context);
        } catch (Exception e) {
            logger.error("error occurs when eval key \"" + keyScript + "\" in " + context.getMethod() + ":" + e.getMessage(), e);
            return null;
        }
    }

    public static Object evalValue(CacheInvokeContext context, CacheUpdateAnnoConfig cac) {
        String valueScript = cac.getValue();
        try {
            if (cac.getValueEvaluator() == null) {
                ExpressionEvaluator e = new ExpressionEvaluator(valueScript, cac.getDefineMethod());
                cac.setValueEvaluator((o) -> e.apply(o));
            }
            return cac.getValueEvaluator().apply(context);
        } catch (Exception e) {
            logger.error("error occurs when eval value \"" + valueScript + "\" in " + context.getMethod() + ":" + e.getMessage(), e);
            return EVAL_FAILED;
        }
    }
}
