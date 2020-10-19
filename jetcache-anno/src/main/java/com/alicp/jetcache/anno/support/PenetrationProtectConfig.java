/**
 * Created on 2018/4/27.
 */
package com.alicp.jetcache.anno.support;

import java.time.Duration;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class PenetrationProtectConfig {
    /**
     * 当缓存未命中时是否开启保护模式（同一个JVM中同一个key只有一个线程去加载，其它线程等待结果）
     */
    private boolean penetrationProtect;
    /**
     * 等待的超时时间
     */
    private Duration penetrationProtectTimeout;

    public boolean isPenetrationProtect() {
        return penetrationProtect;
    }

    public void setPenetrationProtect(boolean penetrationProtect) {
        this.penetrationProtect = penetrationProtect;
    }

    public Duration getPenetrationProtectTimeout() {
        return penetrationProtectTimeout;
    }

    public void setPenetrationProtectTimeout(Duration penetrationProtectTimeout) {
        this.penetrationProtectTimeout = penetrationProtectTimeout;
    }
}
