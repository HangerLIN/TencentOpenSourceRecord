package org.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.openjdk.jmh.annotations.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.concurrent.*;

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
