/**
 * Created on 2019/6/7.
 */
package com.alicp.jetcache.anno.support;

import com.alicp.jetcache.CacheConfigException;
import com.alicp.jetcache.anno.KeyConvertor;
import com.alicp.jetcache.support.FastjsonKeyConvertor;

import java.util.function.Function;

/**
 * @author <a href="mailto:areyouok@gmail.com">huangli</a>
 */
public class DefaultKeyConvertorParser implements KeyConvertorParser {

	/**
	 * 生成Cache的key转换器函数，目前仅支持FASTJSON
	 */
	@Override
	public Function<Object, Object> parseKeyConvertor(String convertor) {
		if (convertor == null) {
			return null;
		}
		if (KeyConvertor.FASTJSON.equalsIgnoreCase(convertor)) {
			// 返回FastjsonKeyConvertor实例(单例模式)
			return FastjsonKeyConvertor.INSTANCE;
		} else if (KeyConvertor.NONE.equalsIgnoreCase(convertor)) {
			return null;
		}
		throw new CacheConfigException("not supported:" + convertor);
	}
}
