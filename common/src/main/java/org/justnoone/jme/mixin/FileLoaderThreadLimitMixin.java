package org.justnoone.jme.mixin;

import org.mtr.core.simulation.FileLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Mixin(value = FileLoader.class, remap = false)
public abstract class FileLoaderThreadLimitMixin {

    @Unique
    private static final int JME_MAX_PARALLEL_FILE_READERS = Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors() / 2));
    @Unique
    private static final ExecutorService JME_SHARED_FILE_LOADER_EXECUTOR = Executors.newFixedThreadPool(JME_MAX_PARALLEL_FILE_READERS, jme$threadFactory());

    @Redirect(
            method = "readMessagePackFromFile",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/Executors;newCachedThreadPool()Ljava/util/concurrent/ExecutorService;"),
            remap = false,
            require = 0
    )
    private ExecutorService jme$useBoundedFileLoaderExecutor() {
        // Return a wrapper that ignores shutdown so MTR's try-with-resources / shutdown()
        // cannot kill the shared pool used by later loads.
        return new ExecutorService() {
            @Override
            public void execute(Runnable command) {
                JME_SHARED_FILE_LOADER_EXECUTOR.execute(command);
            }

            @Override
            public void shutdown() {
            }

            @Override
            public java.util.List<Runnable> shutdownNow() {
                return java.util.Collections.emptyList();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) {
                return true;
            }

            @Override
            public <T> java.util.concurrent.Future<T> submit(java.util.concurrent.Callable<T> task) {
                return JME_SHARED_FILE_LOADER_EXECUTOR.submit(task);
            }

            @Override
            public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
                return JME_SHARED_FILE_LOADER_EXECUTOR.submit(task, result);
            }

            @Override
            public java.util.concurrent.Future<?> submit(Runnable task) {
                return JME_SHARED_FILE_LOADER_EXECUTOR.submit(task);
            }

            @Override
            public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) throws InterruptedException {
                return JME_SHARED_FILE_LOADER_EXECUTOR.invokeAll(tasks);
            }

            @Override
            public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
                return JME_SHARED_FILE_LOADER_EXECUTOR.invokeAll(tasks, timeout, unit);
            }

            @Override
            public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks) throws InterruptedException, java.util.concurrent.ExecutionException {
                return JME_SHARED_FILE_LOADER_EXECUTOR.invokeAny(tasks);
            }

            @Override
            public <T> T invokeAny(java.util.Collection<? extends java.util.concurrent.Callable<T>> tasks, long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException, java.util.concurrent.ExecutionException, java.util.concurrent.TimeoutException {
                return JME_SHARED_FILE_LOADER_EXECUTOR.invokeAny(tasks, timeout, unit);
            }
        };
    }

    @Unique
    private static ThreadFactory jme$threadFactory() {
        final AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            final Thread thread = new Thread(runnable, "magic-file-loader-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
