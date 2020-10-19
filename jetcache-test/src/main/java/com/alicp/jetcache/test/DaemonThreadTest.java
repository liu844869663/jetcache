package com.alicp.jetcache.test;

import java.io.IOException;

/**
 * @author jingping.liu
 * @date 2020-09-29
 * @description Daemon Thread Test
 */
public class DaemonThreadTest {
    public static void main(String[] args) throws IOException {
        /*
         * Java 中的线程有两种类型：用户线程和守护线程，区别
         * 主线程结束后，用户线程还会继续运行，JVM存活
         * 如果没有用户线程，全是守护线程，那么JVM结束，例如垃圾回收线程就是一个经典的守护线程
         */
        Thread thread = new Thread(() -> {
           while (true){
               try {
                   Thread.sleep(10000);
               } catch (InterruptedException e) {
                   e.printStackTrace();
               }
           }
        });
        // 设置为false，程序则是死循环，主线程结束也不会退出
        // 设置为true，程序接收到输入，主线程结束后该线程也结束
        thread.setDaemon(false);
        thread.start();
        System.out.println("isDaemon: " + thread.isDaemon());
        System.in.read(); // 接受输入，使程序在此停顿，一旦接收到用户输入，main线程结束，守护线程自动结束
    }
}
