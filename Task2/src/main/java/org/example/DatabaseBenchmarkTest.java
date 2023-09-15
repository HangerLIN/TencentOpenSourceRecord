//package org.example;
//
//import org.openjdk.jmh.annotations.*;
//
//import java.sql.ResultSet;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.concurrent.*;
//
//@Fork(1)
//@Warmup(iterations = 3, time = 5)
//@Measurement(iterations = 5, time = 5)
//@State(Scope.Benchmark)
//@BenchmarkMode(Mode.Throughput)
//@OutputTimeUnit(TimeUnit.SECONDS)
//public class DatabaseBenchmarkTest {
//
//    @Param({"1", "2"})
//    private int testOption;
//
//    @Param({"1000"})
//    private int threadCount;
//
//    @Param({"10000"})
//    private int requestCount;
//
//    private ExecutorService dbExecutor;
//
//    @Setup(Level.Trial)
//    public void setup() {
//        if (testOption == 1) {
//            ThreadFactory factory = Thread.ofVirtual().scheduler(Executors.newFixedThreadPool(14)).factory();
//            dbExecutor = Executors.newFixedThreadPool(threadCount, factory);
//        } else if (testOption == 2) {
//            ForkJoinPool fjp = new ForkJoinPool();
//            ThreadFactory factory = Thread.ofVirtual().scheduler(fjp).factory();
//            dbExecutor = new ForkJoinPool(threadCount, (ForkJoinPool.ForkJoinWorkerThreadFactory) factory, null, true);
//        } else {
//            throw new IllegalArgumentException("Invalid test option: " + testOption);
//        }
//        ConnectionPool.initConnectionPool();
//    }
//
//    @TearDown(Level.Trial)
//    public void teardown() {
//        ConnectionPool.closeConnection();
//        dbExecutor.shutdown();
//        try {
//            if (!dbExecutor.awaitTermination(1, TimeUnit.MINUTES)) {
//                dbExecutor.shutdownNow();
//            }
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//            dbExecutor.shutdownNow();
//        }
//    }
//
//    @Benchmark
//    public void testDatabase() throws Exception {
//        List<CompletableFuture<String>> cfList = new ArrayList<>();
//        for (int i = 0; i < requestCount; i++) {
//            CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
//                String result = null;
//                try {
//                    result = execQuery("select * from hello");
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                return result;
//            }, dbExecutor);
//            cfList.add(cf);
//        }
//        CompletableFuture.allOf(cfList.toArray(new CompletableFuture[0])).join();
//    }
//
//
//    public static String execQuery(String sql) throws InterruptedException, ExecutionException {
//        String queryResult = "";
//        try {
//            ConnectionNode node;
//            do {
//                node = ConnectionPool.getConnection();
//            } while (node == null);
//            ResultSet rs = node.stm.executeQuery(sql);
//            while (rs.next()) {
//                int id = rs.getInt("id");
//                String hello = rs.getString("hello");
//                String response = rs.getString("response");
//                queryResult += "id: " + id + " hello:" + hello + " response: " + response + "\n";
//            }
//            rs.close();
//            ConnectionPool.releaseConnection(node);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        return queryResult;
//    }
//
//    public static void testAsyncQueryDemo(){
//        int nThreads = 14;
//        Executor sharedExecutor = Executors.newFixedThreadPool(nThreads);
//        ThreadFactory virtualThreadFactory = Thread.ofVirtual().scheduler(sharedExecutor).factory();
//        CompletableFuture<Void>[] queryTasks = new CompletableFuture[10000];
//        for (int i = 0; i < 10000; i++) {
//            int queryId = i;  // 为了在Lambda中使用循环变量
//            queryTasks[i] = CompletableFuture.supplyAsync(() -> {
//                // 模拟数据库查询
//                System.out.println("Virtual thread running database query #" + queryId + "!");
//                return "Query result for #" + queryId;
//            }, virtualThreadFactory).thenAccept(result -> {
//                System.out.println("Query returned: " + result);
//            });
//        }
//        CompletableFuture.allOf(queryTasks).join();
//    }
//}
