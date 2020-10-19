package com.alicp.jetcache.test.external;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author jingping.liu
 * @date 2020-09-30
 * @description Lettuce Redis Java Client Test，参考：https://www.cnblogs.com/throwable/p/11601538.html
 */
public class LettuceTest {

    private static StatefulRedisConnection<String, String> CONNECTION;
    private static RedisClient CLIENT;

    @Before
    public void before(){
        // 连接信息
        RedisURI uri = RedisURI.create("redis-sentinel://192.250.110.170:26379,192.250.110.171:26379,192.250.110.172:26379/0#mymaster");
        // 创建客户端
        CLIENT = RedisClient.create(uri);
        // 创建线程安全的连接
        CONNECTION = CLIENT.connect();
    }

    @Test
    public void sync(){
        // 同步命令
        RedisCommands<String, String> redisCommands = CONNECTION.sync();
        // 执行set命令，30s后expire
        String result = redisCommands.setex("name", 30 ,"liujingping");
        Assert.assertEquals("OK", result);
        // 执行get命令
        result = redisCommands.get("name");
        Assert.assertEquals("liujingping", result);
    }

    @Test
    public void async() throws ExecutionException, InterruptedException {
        // 异步命令
        RedisAsyncCommands<String, String> redisCommands = CONNECTION.async();
        RedisFuture<String> future = redisCommands.setex("name", 30 ,"liujingping");
        future.thenAccept(result -> System.out.println("==============" + result) );
        future.get();
    }

    @Test
    public void async2() throws ExecutionException, InterruptedException {
        // 异步命令
        RedisAsyncCommands<String, String> redisCommands = CONNECTION.async();
        CompletableFuture<Void> future = (CompletableFuture<Void>) redisCommands.setex("name", 30 ,"liujingping")
                .thenAcceptBoth(redisCommands.get("name"), (s, g) -> {
                    System.out.println("SET==============" + s);
                    System.out.println("GET==============" + g);
                });
        future.get();
    }

    @Test
    public void reactiveReturnMono() throws InterruptedException {
        // 反应式API
        RedisReactiveCommands<String, String> redisCommands = CONNECTION.reactive();
        // 执行的命令返回结果数量大于1返回Flux，否则返回Mono
        redisCommands.setex("name", 30 ,"liujingping").block();
        redisCommands.get("name")
                .subscribe(v -> System.out.println("GET==============" + v)); // 处理流中的元素
        Thread.sleep(1000);
    }

    @Test
    public void reactiveReturnFlux() throws InterruptedException {
        RedisReactiveCommands<String, String> redisCommands = CONNECTION.reactive();
        redisCommands.sadd("city", "SH", "SZ").block();
        // 获取集合元素
        redisCommands.smembers("city").subscribe(System.out::println);
        // 删除集合元素
        redisCommands.srem("city", "SH", "SZ").block();
        Thread.sleep(1000);
    }

    @Test
    public void reactiveFunctional() throws InterruptedException {
        RedisReactiveCommands<String, String> redisCommands = CONNECTION.reactive();
        // 开启一个事务
        redisCommands.multi().doOnSuccess(r -> {
            // 添加一个计数值
            redisCommands.set("counter", "1").doOnNext(System.out::println)
                    .subscribe(); // 触发流
            // 计数值加1
            redisCommands.incr("counter").doOnNext(c -> System.out.println(c)).subscribe();
            // 删除计数值
            redisCommands.del("counter").doOnNext(System.out::println).subscribe();
        }).flatMap(s -> redisCommands.exec()) // 执行事务
                .doOnNext(transactionResult -> System.out.println("Discarded: " + transactionResult.wasDiscarded()))
                .subscribe();
        Thread.sleep(1000);
    }

    @After
    public void after(){
        // 关闭连接
        CONNECTION.close();
        // 关闭客户端
        CLIENT.shutdown();
    }

}
