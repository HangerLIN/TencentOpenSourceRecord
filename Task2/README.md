# FixedThreadPool，ForkJoinPool 的性能比较

## 火焰图

见html 文件



## 结果预测

### FixedThreadPool的实现机制

1. **ReentrantLock**:
   
    - 在 `FixedThreadPool` 中, `ReentrantLock` 主要用于保护线程池的状态和任务队列的一致性。每次有新任务提交时，或者有线程完成任务时，都可能需要通过锁来保证状态的正确更新和任务的正确分配。
    - `ReentrantLock` 提供了公平和非公平两种模式。公平模式下，等待最长时间的线程会优先获取锁，而非公平模式下，尝试获取锁的线程可能会直接插队，这样通常会提高吞吐量但可能会降低公平性和实时性。
    
2. **LockSupport**:
    - `LockSupport` 提供了基本的线程阻塞和唤醒功能。`FixedThreadPool` 中可能会利用 `LockSupport.park()` 和 `LockSupport.unpark(Thread)` 方法来挂起和唤醒线程。
    
      
    
3. **AbstractQueuedSynchronizer (AQS)**:
    - AQS是一个用于构建锁和同步器的框架，它维护了一个FIFO的等待队列和一个状态值。`ReentrantLock` 是基于 AQS 实现的。
    - AQS提供了独占模式和共享模式。在独占模式下，每次只有一个线程能够执行，而在共享模式下，多个线程可以同时执行。

### ForkJoinPool的实现机制

1. **Unsafe的park/unpark机制**:
    - `ForkJoinPool` 利用 `Unsafe` 类提供的 `park` 和 `unpark` 方法来挂起和唤醒线程。它们是轻量级的同步方法，效率通常比传统的 `Object.wait` 和 `Object.notify` 方法更高。
    - `park/unpark` 机制提供了一种高效的方式来控制线程的执行和调度，特别是在高并发、高任务负载的情况下。

2. **工作窃取算法**:
    - `ForkJoinPool` 的核心是工作窃取算法，每个线程维护自己的工作队列，当自己的工作队列空了，它会尝试从其他线程的工作队列中窃取任务来执行。
    - 工作窃取算法可以有效地利用系统资源，特别是在多核环境下，它能够确保所有的处理器核心都能得到充分的利用，而不会有些线程闲置而有些线程过载。

3. **递归任务分解**:
    - `ForkJoinPool` 通过递归分解任务为子任务，然后并行执行子任务，从而实现任务的并行处理。这种递归分解和并行执行的方式非常适合处理可以拆分为独立子任务的大规模计算或数据处理任务。





## 性能分析

### 代码

```java
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1)
public class TestCompleteFutureVirtual {
    @Param("10000")
    private int taskCount;
    @Param("14")
    private int poolSize;

    private ExecutorService fixedThreadPool;
    private ForkJoinPool forkJoinPool;
    private ForkJoinPool fjpScheduler;  // CoroutineDemo's scheduler
    private Executor virtualExecutor;  // CoroutineDemo's virtual executor
    private static HikariDataSource dataSource;

    @Setup(Level.Trial)
    public void setup() {
        fixedThreadPool = Executors.newFixedThreadPool(poolSize);
        forkJoinPool = new ForkJoinPool(poolSize);

        // CoroutineDemo setup
        fjpScheduler = new ForkJoinPool(4);
        ThreadFactory virtualThreadFactory = Thread.ofVirtual().scheduler(fjpScheduler).factory();
        virtualExecutor = task -> {
            Thread virtualThread = virtualThreadFactory.newThread(task);
            virtualThread.start();
        };

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/zm");
        config.setUsername("root");
        config.setPassword("88888888");
        config.setMaximumPoolSize(poolSize * 2);
        dataSource = new HikariDataSource(config);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        fixedThreadPool.shutdown();
        forkJoinPool.shutdown();
        fjpScheduler.shutdown();  // CoroutineDemo shutdown

        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Benchmark
    public void testForkJoinPool() throws ExecutionException, InterruptedException {
        testWithExecutor(forkJoinPool);
    }

    @Benchmark
    public void testFixedThreadPool() throws ExecutionException, InterruptedException {
        testWithExecutor(fixedThreadPool);
    }

    @Benchmark
    public void testCoroutineDemoLogic() throws ExecutionException, InterruptedException {
            testWithExecutor(virtualExecutor);
    }


        private void testWithExecutor (Executor executor) throws ExecutionException, InterruptedException {
            CompletableFuture<?>[] futures = new CompletableFuture[taskCount];
            for (int i = 0; i < taskCount; i++) {
                CompletableFuture<Void> future = new CompletableFuture<>();
                if (executor == virtualExecutor) {
                    // 如果当前的执行器是虚拟执行器，则直接运行任务，而不是在新的虚拟线程中运行它
                    try {
                        operateMysql("select * from hello");
                        future.complete(null);
                    } catch (Throwable e) {
                        future.completeExceptionally(e);
                    }
                } else {
                    Thread thread = Thread.ofVirtual().scheduler(executor).start(() -> {
                        try {
                            operateMysql("select * from hello");
                            future.complete(null);
                        } catch (Throwable e) {
                            future.completeExceptionally(e);
                        }
                    });
                }
                futures[i] = future;
            }
            CompletableFuture.allOf(futures).join();
        }


        private void operateMysql (String sql){
            try {
                Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    int id = rs.getInt("id");
                    String hello = rs.getString("hello");
                    String response = rs.getString("response");
                }
                rs.close();
                conn.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
}
```





```bash
Benchmark                                         (poolSize)  (taskCount)  Mode  Cnt       Score        Error  Units
TestCompleteFutureVirtual.testCoroutineDemoLogic          14        10000  avgt    5  530083.552 ± 115275.195  us/op
TestCompleteFutureVirtual.testFixedThreadPool             14        10000  avgt    5   67010.741 ±   1623.461  us/op
TestCompleteFutureVirtual.testForkJoinPool                14        10000  avgt    5   64868.965 ±    447.714  us/op
```

每个测试的参数设置为 `poolSize = 14` 和 `taskCount = 10000`。结果是以`us/op`（微秒每操作）为单位给出的，较低的`us/op`值表示更好的性能。

> `FixedThreadPool`和`ForkJoinPool`的优化实现和适应本测试的任务性质可能是它们比`virtualExecutor`表现更好的主要原因。

1. **Coroutine Demo Logic**:
   - 平均时间: `530083.552 us/op`
   - 这个测试结果显示，自定义的`virtualExecutor`是三者中最慢的，平均每操作需要大约530毫秒。
2. **FixedThreadPool**:
   - 平均时间: `67010.741 us/op`
   - `FixedThreadPool`的表现明显优于`virtualExecutor`，每操作平均需要67毫秒，这是一个相对较好的结果。
3. **ForkJoinPool**:
   - 平均时间: `64868.965 us/op`
   - `ForkJoinPool`的性能介于其他两者之间，每操作平均需要65毫秒，稍微优于`FixedThreadPool`。

### 分析:

- **性能比较**:
  - `FixedThreadPool` 和 `ForkJoinPool` 的性能相当，而自定义的 `virtualExecutor` 性能较差。
  - 在这个基准测试中，`FJP` 是最优的执行器，其次是 `FTP`。
- **可能的性能影响因素**:
  - `virtualExecutor` 的实现可能不如Java的内置线程池优化，导致其性能较差。
  - 数据库连接池的配置和管理也可能影响每个执行器的性能。
  - 其他系统和JVM的配置也可能影响测试结果。
- **推荐**:
  - 如果目标是提高性能，可能会考虑使用 `FixedThreadPool` 或 `ForkJoinPool` 而不是自定义的 `virtualExecutor`。

### 结论：

尽管`FJP`的性能略高，但这两种方法的差异非常小，几乎可以忽略。



## 总结

1. **FixedThreadPool**:
   - `FixedThreadPool`作为协程调度器，提供了一种并发执行协程的能力。它通过维护一个固定数量的线程来处理提交的任务。然而，由于线程数量是固定的，`FixedThreadPool`在面对变化的负载时可能无法动态调整线程资源。这可能导致在高负载情况下资源不足，而在低负载情况下资源闲置，从而可能影响系统的资源利用效率和性能表现。
2. **ForkJoinPool**:
   - `ForkJoinPool`是为递归和可拆分任务设计的协程调度器，它具有任务拆分、并行执行和动态负载平衡的能力。`ForkJoinPool`通过工作窃取算法和分治思想来实现任务的并行执行。当一个线程完成其任务队列中的所有任务时，它可以“窃取”其他线程队列中的任务来执行。这种动态负载平衡机制使得`ForkJoinPool`能够在变化的负载条件下，更高效地利用线程资源，并且能够通过并行执行来加速任务处理。此外，`ForkJoinPool`可以动态地创建或挂起线程，以适应不同的负载条件，从而进一步提高资源利用效率。

### 使用场景

1. **使用FixedThreadPool的场景**:
   - **短期任务执行**: 大量的短期、独立的任务。`testFixedThreadPool`方法通过`FixedThreadPool`执行MySQL查询，这是一个典型的短期任务执行场景。
   - **控制线程资源**: 如果需要防止过多的线程创建导致系统资源耗尽，`FixedThreadPool`能提供固定数量的线程来满足这个需求。
2. **使用ForkJoinPool的场景**:
   - **递归和分治任务**: `ForkJoinPool`特别适合处理可以递归拆分为较小子任务的任务。如果您的任务可以自然地分解为较小的、可以并行执行的子任务，`ForkJoinPool`是一个很好的选择，但是在本处没有体现？？？
   - **并行数据处理和计算**: ForkJoinPool`可以通过并行执行和动态负载平衡来提高任务的执行效率。
3. **使用Virtual Executor的场景**:
   - **协程调度**: 在`testCoroutineDemoLogic`方法中，创建了一个`virtualExecutor`来执行MySQL查询。如果利用了Java的虚拟线程和协程特性，`virtualExecutor`可以作为协程的执行器来提供非阻塞的任务执行和调度。虽然这里使用了协程，但是效率却特别低。
