
package at.jku.anttracks.util;

import javafx.concurrent.Task;

import java.util.concurrent.Future;

public class ThreadUtil {

    private static final int SHORT_DEFERRED_PERIOD = 50;
    private static final int NORMAL_DEFERRED_PERIOD = 100;
    private static final int LONG_DEFERRED_PERIOD = 500;

    public enum DeferredPeriod {
        SHORT,
        NORMAL,
        LONG
    }

    public static Task<Void> runDeferred(Runnable runnable, DeferredPeriod deferredPeriod) {
        switch (deferredPeriod) {
            case LONG:
                return runDeferred(runnable, SHORT_DEFERRED_PERIOD);
            case NORMAL:
                return runDeferred(runnable, NORMAL_DEFERRED_PERIOD);
            case SHORT:
                return runDeferred(runnable, LONG_DEFERRED_PERIOD);
        }
        return null;
    }

    private static Task<Void> runDeferred(Runnable runnable, int milliseconds) {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Thread.sleep(milliseconds);
                runnable.run();
                return null;
            }
        };
        startTask(task);
        return task;
    }

    public static <T> Future<T> startTask(Task<T> task) {
        return ParallelizationUtil.submitTask(task, false);
    }

    public static <T> Future<T> startTask(Task<T> task, boolean waitForCompletion) {
        return ParallelizationUtil.submitTask(task, waitForCompletion);
    }
}
