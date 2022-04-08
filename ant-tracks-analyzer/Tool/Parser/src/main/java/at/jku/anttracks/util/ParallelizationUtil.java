package at.jku.anttracks.util;

import io.micrometer.core.instrument.MeterRegistry;
import javafx.concurrent.Task;

import java.util.Iterator;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class ParallelizationUtil {
    private static Logger LOGGER = Logger.getLogger(ParallelizationUtil.class.getSimpleName());

    private static ExecutorService TASK_EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    // private static ExecutorService EXECUTOR_SERVICE = Executors.newFixedThreadPool(Math.max(1, AVAILABLE_PROCESSORS() / 2));
    private static BlockingQueue<Runnable> priorityFuturesToPerform = new LinkedBlockingQueue<>();
    private static BlockingQueue<Runnable> futuresToPerform = new LinkedBlockingQueue<>();

    static {
        for (int i = 0; i < Consts.getAVAILABLE_PROCESSORS(); i++) {
            Thread taskProcessor = new Thread(() -> {
                Runnable r = null;
                try {
                    if (!priorityFuturesToPerform.isEmpty()) {
                        r = priorityFuturesToPerform.poll(1, TimeUnit.MILLISECONDS);
                    }
                    if (r == null) {
                        r = futuresToPerform.poll(100, TimeUnit.MILLISECONDS);
                    }
                } catch (InterruptedException e) {
                    // TODO inform somebody that task processing thread has been terminated
                    return;
                }
                if (r != null) {
                    r.run();
                }
            }, "ParallelizationUtil - Task Processing Thread #" + (i + 1));
            taskProcessor.start();
        }
    }

    public static <T> void temporaryExecutorServiceBlocking(Iterator<T> forEach, Consumer<T> forEachFunction) {
        temporaryExecutorServiceBlocking(forEach, forEachFunction, Consts.getAVAILABLE_PROCESSORS());
    }

    public static <T> void temporaryExecutorServiceBlocking(Iterator<T> forEach, Consumer<T> forEachFunction, int threadCount) {
        LOGGER.info(String.format("Starting executor service with fixed thread pool of %d threads", threadCount));
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        while (forEach.hasNext()) {
            final T target = forEach.next();
            executorService.submit(() -> forEachFunction.accept(target));
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public static void temporaryExecutorServiceBlocking(BiConsumer<Integer, Integer> forEachFunction) {
        temporaryExecutorServiceBlocking(forEachFunction, Consts.getAVAILABLE_PROCESSORS());
    }

    public static void temporaryExecutorServiceBlocking(BiConsumer<Integer, Integer> forEachFunction, int threadCount) {
        LOGGER.info(String.format("Starting executor service with fixed thread pool of %d threads", threadCount));
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        for (int threadId = 0; threadId < threadCount; threadId++) {
            int finalThreadId = threadId;
            executorService.submit(() -> forEachFunction.accept(finalThreadId, threadCount));
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            //            e.printStackTrace();
            LOGGER.info("Stopped executor service!");
        }
    }

    public static void temporaryExecutorServiceAsync(BiConsumer<Integer, Integer> forEachFunction, Runnable onAllFinished) {
        temporaryExecutorServiceAsync(forEachFunction, onAllFinished, Consts.getAVAILABLE_PROCESSORS());
    }

    public static void temporaryExecutorServiceAsync(BiConsumer<Integer, Integer> forEachFunction, Runnable onAllFinished, int threadCount) {
        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("temporaryExecutorServiceAsync");
        LOGGER.info(String.format("Starting executor service with fixed thread pool of %d threads", threadCount));
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        for (int threadId = 0; threadId < threadCount; threadId++) {
            int finalThreadId = threadId;
            executorService.submit(() -> forEachFunction.accept(finalThreadId, threadCount));
        }
        executorService.shutdown();
        createFinishThread(onAllFinished, executorService).start();
        //m.end();
    }

    public static <T> void temporaryExecutorServiceAsync(Iterator<T> forEach, Consumer<T> forEachFunction, Runnable onAllFinished) {
        temporaryExecutorServiceAsync(forEach, forEachFunction, onAllFinished, Consts.getAVAILABLE_PROCESSORS());
    }

    public static <T> void temporaryExecutorServiceAsync(Iterator<T> forEach, Consumer<T> forEachFunction, Runnable onAllFinished, int threadCount) {
        LOGGER.info(String.format("Starting executor service with fixed thread pool of %d threads", threadCount));
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        while (forEach.hasNext()) {
            final T target = forEach.next();
            executorService.submit(() -> forEachFunction.accept(target));
        }
        executorService.shutdown();
        createFinishThread(onAllFinished, executorService).start();
    }

    private static Thread createFinishThread(Runnable onAllFinished, ExecutorService executorService) {
        return new Thread(() -> {
            try {
                executorService.awaitTermination(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
                if (onAllFinished != null) {
                    onAllFinished.run();
                }
            } catch (InterruptedException e) {
                //                e.printStackTrace();
                LOGGER.info("Stopped executor service!");
            }
        });
    }

    public static Thread submitThread(String name, Runnable r) {
        return submitThread(name, r, null);
    }

    public static Thread submitThread(String name, Runnable r, MeterRegistry registry) {
        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("executorservice.submit.thread");
        LOGGER.info("Thread submitted: " + name);
        Thread t = new Thread(r);
        t.start();
        //m.end();
        //if (registry != null) {
        //    registry.timer(m.getStat()).record(m.getDuration(), TimeUnit.NANOSECONDS);
        //}
        return t;
    }

    public static void submitFuture(String name, Runnable r) {
        submitFuture(name, r, null);
    }

    public static void submitFuture(String name, Runnable r, boolean priority) {
        submitFuture(name, r, null, priority);
    }

    public static void submitFuture(String name, Runnable r, MeterRegistry registry) {
        submitFuture(name, r, registry, false);
    }

    public static void submitFuture(String name, Runnable r, MeterRegistry registry, boolean priority) {
        try {
            //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("executorservice.submit.future");
            LOGGER.info("Future submitted: " + name);

            if (priority) {
                priorityFuturesToPerform.put(r);
            } else {
                futuresToPerform.put(r);
            }

            //m.end();
            //if (registry != null) {
            //    registry.timer(m.getStat()).record(m.getDuration(), TimeUnit.NANOSECONDS);
            //}
            //return ret;
        } catch (InterruptedException e) {
            e.printStackTrace();
            //return null;
        }
    }

    public static <T> Future<T> submitTask(Task<T> task) {
        return submitTask(task, null, false);
    }

    public static <T> Future<T> submitTask(Task<T> task, boolean waitForCompletion) {
        return submitTask(task, null, waitForCompletion);
    }

    public static <T> Future<T> submitTask(Task<T> task, MeterRegistry registry, boolean waitForCompletion) {
        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("executorservice.submit.task");
        LOGGER.info("Task submitted: " + task.getClass().getName() + " - " + (task.getTitle() != null && !task.getTitle().equals("") ?
                                                                              task.getTitle() :
                                                                              "No title") + " - " + (task.getMessage() != null && !task.getMessage().equals("") ?
                                                                                                     task.getMessage() :
                                                                                                     "No message"));
        Future<T> f;
        final CountDownLatch doneLatch = new CountDownLatch(1);
        if (waitForCompletion) {
            f = TASK_EXECUTOR_SERVICE.submit(() -> {
                try {
                    task.run();
                    return task.get();
                } finally {
                    doneLatch.countDown();
                }
            });
        } else {
            f = TASK_EXECUTOR_SERVICE.submit(() -> {
                task.run();
                return task.get();
            });
        }

        //m.end();
        //if (registry != null) {
        //    registry.timer(m.getStat()).record(m.getDuration(), TimeUnit.NANOSECONDS);
        //}

        if (waitForCompletion) {
            try {
                doneLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return f;
    }
}