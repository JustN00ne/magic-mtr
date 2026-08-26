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
        return JME_SHARED_FILE_LOADER_EXECUTOR;
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
