package org.example;

import java.util.concurrent.*;

public class CoroutineDemo {
    public static void main(String[] args) {
        ForkJoinPool fjpScheduler = new ForkJoinPool(4);
        ThreadFactory virtualThreadFactory = Thread.ofVirtual().scheduler(fjpScheduler).factory();
        Executor fixp = Executors.newFixedThreadPool(4);

        Executor virtualExecutor = task -> {
            Thread virtualThread = virtualThreadFactory.newThread(task);
            virtualThread.start();
        };


        CompletableFuture<Void> coroutine = CompletableFuture.runAsync(() -> {
            CompletableFuture<Void>[] tasks = new CompletableFuture[10];
            for (int i = 0; i < 10; i++) {
                final int taskId = i;
                tasks[i] = CompletableFuture.runAsync(() -> {
                    // 模拟一些需要在固定线程池中执行的数据库查询操作
                    System.out.println("Executing task #" + taskId + " in thread: " + Thread.currentThread().getName());
                }, fixp);
            }
            // 等待内部的数据库查询结束
            CompletableFuture.allOf(tasks).join();

        }, virtualExecutor);

        // 等待协程完成
        coroutine.join();

        // 关闭上面的两个线程池
        ((ExecutorService) fixp).shutdown();
        fjpScheduler.shutdown();
    }
}
