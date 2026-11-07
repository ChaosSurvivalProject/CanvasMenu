package com.chaossurvival.canvasmenu.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * 虚拟线程封装（Java 21）。
 *
 * <p>更新检查、下载、Release 上传全部经由此处调度，<strong>绝不阻塞渲染线程</strong>。
 * 任务内异常被吞掉并记 debug 日志，保证「网络失败静默降级」。</p>
 */
public final class Async {

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private Async() {
    }

    /**
     * 在虚拟线程上执行一个无返回值任务。
     *
     * @param task 要执行的任务
     */
    public static void run(Runnable task) {
        EXECUTOR.submit(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                Log.debug("Async task failed: {}", t.toString());
            }
        });
    }

    /**
     * 在虚拟线程上执行一个有返回值任务。
     *
     * @param supplier 结果供应者
     * @param <T>      结果类型
     * @return 可组合的 Future
     */
    public static <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, EXECUTOR);
    }

    /** 关闭执行器，由 shutdown hook 调用。 */
    public static void shutdown() {
        EXECUTOR.shutdown();
    }
}
