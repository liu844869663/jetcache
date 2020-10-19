package com.alicp.jetcache.support;

import java.util.List;

/**
 * Created on 2016/11/29.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class StatInfo {
    /**
     * 所有缓存实例列表
     */
    private List<CacheStat> stats;
    /**
     * 开始时间
     */
    private long startTime;
    /**
     * 结束时间
     */
    private long endTime;

    public List<CacheStat> getStats() {
        return stats;
    }

    public void setStats(List<CacheStat> stats) {
        this.stats = stats;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
}
