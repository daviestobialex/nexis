/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.utilities;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * {@code VirtualThreadExecutor} provides a unified, composable way to execute
 * asynchronous tasks on Java Virtual Threads.
 * <p>
 * It offers:
 * <ul>
 * <li>A global {@link ExecutorService} backed by
 * {@link Executors#newVirtualThreadPerTaskExecutor()} for lightweight,
 * non-blocking tasks.</li>
 * <li>A fluent, chainable API for sequencing multiple async steps (e.g.
 * validation → dispatch → post-processing).</li>
 * <li>Graceful error handling with optional onError callbacks.</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * VirtualThreadExecutor.chain()
 *     .run(() -> pipeline.validate(msg))
 *     .thenRun(() -> dispatcher.dispatch(msg, ctx))
 *     .onError(e -> {
 *         if (e instanceof DropMessageException) return;
 *         e.printStackTrace();
 *     })
 *     .execute();
 * }</pre>
 *
 * <p>
 * Designed for event-driven network frameworks (like Netty) where you must
 * perform blocking or CPU-bound work off the event loop thread without blocking
 * it.
 * </p>
 *
 * @author daviestobialex
 */
public final class VirtualThreadExecutor {

    /**
     * Global executor for lightweight, concurrent virtual-thread tasks.
     */
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private Runnable firstTask;
    private Runnable nextTask;
    private Consumer<Exception> errorHandler;

    private VirtualThreadExecutor() {
    }

    /**
     * Starts a new virtual thread execution chain.
     *
     * @return a new {@code VirtualThreadExecutor} instance for chaining.
     */
    public static VirtualThreadExecutor chain() {
        return new VirtualThreadExecutor();
    }

    /**
     * Runs a single task asynchronously in a virtual thread.
     *
     * @param task task to execute
     */
    public static void submit(Runnable task) {
        EXECUTOR.submit(task);
    }

    /**
     * Runs a supplier asynchronously and returns a {@link CompletableFuture}.
     *
     * @param supplier supplier providing a result
     * @return a {@link CompletableFuture} executed in a virtual thread
     * @param <T> type of result
     */
    public static <T> CompletableFuture<T> supply(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, EXECUTOR);
    }

    /**
     * Defines the first task in the chain.
     *
     * @param task the initial task to run
     * @return this instance for chaining
     */
    public VirtualThreadExecutor run(Runnable task) {
        this.firstTask = task;
        return this;
    }

    /**
     * Defines the second task to run after the first completes.
     *
     * @param task the next task to run
     * @return this instance for chaining
     */
    public VirtualThreadExecutor thenRun(Runnable task) {
        this.nextTask = task;
        return this;
    }

    /**
     * Registers a handler for any exception thrown during execution.
     *
     * @param handler consumer that handles thrown exceptions
     * @return this instance for chaining
     */
    public VirtualThreadExecutor onError(Consumer<Exception> handler) {
        this.errorHandler = handler;
        return this;
    }

    /**
     * Executes the defined chain asynchronously on the global virtual thread
     * pool.
     * <p>
     * Each task is executed in order on a lightweight thread, with exception
     * handling applied globally.
     * </p>
     */
    public void execute() {
        EXECUTOR.submit(() -> {
            try {
                if (firstTask != null) {
                    firstTask.run();
                }
                if (nextTask != null) {
                    nextTask.run();
                }
            } catch (Exception e) {
                if (errorHandler != null) {
                    errorHandler.accept(e);
                } else {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * Gracefully shuts down the global virtual thread executor.
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
    }
}
