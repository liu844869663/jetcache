package com.alicp.jetcache.embedded;

import com.alicp.jetcache.support.JetCacheExecutor;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 执行任务：定时清理（每分钟） LinkedHashMapCache 缓存实例中过期的缓存数据
 *
 * Created on 2017/2/28.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
class Cleaner {

    /**
     * 存放弱引用对象，以防内存溢出
     * 如果被弱引用的对象只被当前弱引用对象关联时，gc 时被弱引用的对象则会被回收（取决于被弱引用的对象是否还与其他强引用对象关联）
     *
     * 个人理解：当某个 LinkedHashMapCache 强引用对象没有被其他对象（除了这里）引用时，我们应该让这个对象被回收，
     * 但是由于这里使用的也是强引用，这个对象被其他强引用对象关联了，不可能被回收，存在内存溢出的危险，
     * 所以这里使用了弱引用对象，如果被弱引用的对象没有被其他对象（除了这里）引用时，这个对象会被回收
     *
     * 举个例子：如果我们往一个 Map<Object, Object> 中存放一个key-value键值对
     * 假设对应的键已经不再使用被回收了，那我们无法再获取到对应的值，也无法被回收，占有一定的内存，存在风险
     */
    static LinkedList<WeakReference<LinkedHashMapCache>> linkedHashMapCaches = new LinkedList<>();

    static {
        // 创建一个线程池，1个核心线程
        ScheduledExecutorService executorService = JetCacheExecutor.defaultExecutor();
        // 起一个循环任务一直清理 linkedHashMapCaches 过期的数据（每隔60秒）
        executorService.scheduleWithFixedDelay(() -> run(), 60, 60, TimeUnit.SECONDS);
    }

    static void add(LinkedHashMapCache cache) {
        synchronized (linkedHashMapCaches) {
            // 创建一个弱引用对象，并添加到清理对象中
            linkedHashMapCaches.add(new WeakReference<>(cache));
        }
    }

    static void run() {
        synchronized (linkedHashMapCaches) {
            Iterator<WeakReference<LinkedHashMapCache>> it = linkedHashMapCaches.iterator();
            while (it.hasNext()) {
                WeakReference<LinkedHashMapCache> ref = it.next();
                // 获取被弱引用的对象（强引用）
                LinkedHashMapCache c = ref.get();
                if (c == null) { // 表示被弱引用的对象被标记成了垃圾，则移除
                    it.remove();
                } else {
                    c.cleanExpiredEntry();
                }
            }
        }
    }

}
