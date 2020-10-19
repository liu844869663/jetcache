/**
 * Created on  13-09-12 19:02
 */
package com.alicp.jetcache.embedded;

import com.alicp.jetcache.CacheResultCode;
import com.alicp.jetcache.CacheValueHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class LinkedHashMapCache<K, V> extends AbstractEmbeddedCache<K, V> {

    private static Logger logger = LoggerFactory.getLogger(LinkedHashMapCache.class);

    public LinkedHashMapCache(EmbeddedCacheConfig<K, V> config) {
        super(config);
        // 将缓存实例添加至 Cleaner
        addToCleaner();
    }

    protected void addToCleaner() {
        Cleaner.add(this);
    }

    @Override
    protected InnerMap createAreaCache() {
        return new LRUMap(config.getLimit(), this);
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        if (clazz.equals(LinkedHashMap.class)) {
            return (T) innerMap;
        }
        throw new IllegalArgumentException(clazz.getName());
    }

    public void cleanExpiredEntry() {
        ((LRUMap) innerMap).cleanExpiredEntry();
    }

    /**
     * 用于本地缓存类型为 linkedhashmap 缓存实例存储缓存数据
     */
    final class LRUMap extends LinkedHashMap implements InnerMap {

        /**
         * 允许的最大缓存数量
         */
        private final int max;
        /**
         * 缓存实例锁
         */
        private Object lock;

        public LRUMap(int max, Object lock) {
            super((int) (max * 1.4f), 0.75f, true);
            this.max = max;
            this.lock = lock;
        }

        /**
         * 当元素大于最大值时移除最老的元素
         *
         * @param eldest 最老的元素
         * @return 是否删除
         */
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > max;
        }

        /**
         * 清理过期的元素
         */
        void cleanExpiredEntry() {
            synchronized (lock) { // 占有当前缓存实例这把锁
                for (Iterator it = entrySet().iterator(); it.hasNext();) {
                    Map.Entry en = (Map.Entry) it.next();
                    Object value = en.getValue();
                    if (value != null && value instanceof CacheValueHolder) {
                        CacheValueHolder h = (CacheValueHolder) value;
                        /*
                         * 缓存的数据已经失效了则删除
                         * 为什么不对 expireAfterAccess 进行判断，取最小值，疑问？？？？
                         */
                        if (System.currentTimeMillis() >= h.getExpireTime()) {
                            it.remove();
                        }
                    } else {
                        // assert false
                        if (value == null) {
                            logger.error("key " + en.getKey() + " is null");
                        } else {
                            logger.error("value of key " + en.getKey() + " is not a CacheValueHolder. type=" + value.getClass());
                        }
                    }
                }
            }
        }

        @Override
        public Object getValue(Object key) {
            synchronized (lock) {
                return get(key);
            }
        }

        @Override
        public Map getAllValues(Collection keys) {
            Map values = new HashMap();
            synchronized (lock) {
                for (Object key : keys) {
                    Object v = get(key);
                    if (v != null) {
                        values.put(key, v);
                    }
                }
            }
            return values;
        }

        @Override
        public void putValue(Object key, Object value) {
            synchronized (lock) {
                put(key, value);
            }
        }

        @Override
        public void putAllValues(Map map) {
            synchronized (lock) {
                Set<Map.Entry> set = map.entrySet();
                for (Map.Entry en : set) {
                    put(en.getKey(), en.getValue());
                }
            }
        }

        @Override
        public boolean removeValue(Object key) {
            synchronized (lock) {
                return remove(key) != null;
            }
        }

        @Override
        public void removeAllValues(Collection keys) {
            synchronized (lock) {
                for (Object k : keys) {
                    remove(k);
                }
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean putIfAbsentValue(Object key, Object value) {
            /*
             * 如果缓存 key 不存在，或者对应的 value 已经失效则放入，否则返回 false
             */
            synchronized (lock) {
                CacheValueHolder h = (CacheValueHolder) get(key);
                if (h == null || parseHolderResult(h).getResultCode() == CacheResultCode.EXPIRED) {
                    put(key, value);
                    return true;
                } else {
                    return false;
                }
            }
        }
    }


}

