package com.alicp.jetcache.autoconfigure;

import com.alicp.jetcache.CacheBuilder;
import com.alicp.jetcache.anno.CacheConsts;
import com.alicp.jetcache.external.ExternalCacheBuilder;

/**
 * Created on 2016/11/29.
 *
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public abstract class ExternalCacheAutoInit extends AbstractCacheAutoInit {
    public ExternalCacheAutoInit(String... cacheTypes) {
        super(cacheTypes);
    }

    /**
     * 设置远程缓存 CacheBuilder 构造器的相关配置
     *
     * @param builder 构造器
     * @param ct      配置信息
     */
    @Override
    protected void parseGeneralConfig(CacheBuilder builder, ConfigTree ct) {
        super.parseGeneralConfig(builder, ct);
        ExternalCacheBuilder ecb = (ExternalCacheBuilder) builder;
        // 设置远程缓存 key 的前缀
        ecb.setKeyPrefix(ct.getProperty("keyPrefix"));
        /*
         * 根据配置创建缓存数据的编码函数和解码函数
         */
        ecb.setValueEncoder(configProvider.parseValueEncoder(ct.getProperty("valueEncoder", CacheConsts.DEFAULT_SERIAL_POLICY)));
        ecb.setValueDecoder(configProvider.parseValueDecoder(ct.getProperty("valueDecoder", CacheConsts.DEFAULT_SERIAL_POLICY)));
    }
}
