package org.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.openjdk.jmh.annotations.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1)
public class TestCompleteFuture {
    @Param("10000")
    private int taskCount;
    @Param("14")
    private int poolSize;

    private ExecutorService fixedThreadPool;
    private ForkJoinPool forkJoinPool;
    private static HikariDataSource dataSource;

    @Setup(Level.Trial)
    public void setup() {
        fixedThreadPool = Executors.newFixedThreadPool(poolSize);
        forkJoinPool = new ForkJoinPool(poolSize);

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

    private void testWithExecutor(Executor executor) throws ExecutionException, InterruptedException {
        CompletableFuture<?>[] futures = new CompletableFuture[taskCount];
        for (int i = 0; i < taskCount; i++) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            Thread thread = Thread.ofVirtual().scheduler(executor).start(() -> {
                try {
                    operateMysql("select * from hello");
                    future.complete(null);
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            });
            futures[i] = future;
        }
        CompletableFuture.allOf(futures).join();
    }

    private void operateMysql(String sql) {
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
