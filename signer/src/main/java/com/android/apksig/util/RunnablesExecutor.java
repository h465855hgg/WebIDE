


package com.android.apksig.util;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadPoolExecutor;

public interface RunnablesExecutor {
    static final RunnablesExecutor SINGLE_THREADED = p -> p.createRunnable().run();

    static final RunnablesExecutor MULTI_THREADED = new RunnablesExecutor() {
        private final int PARALLELISM = Math.min(32, Runtime.getRuntime().availableProcessors());
        private final int QUEUE_SIZE = 4;

        @Override
        public void execute(RunnablesProvider provider) {
            final ExecutorService mExecutor =
                    new ThreadPoolExecutor(PARALLELISM, PARALLELISM,
                            0L, MILLISECONDS,
                            new ArrayBlockingQueue<>(QUEUE_SIZE),
                            new ThreadPoolExecutor.CallerRunsPolicy());

            Phaser tasks = new Phaser(1);

            for (int i = 0; i < PARALLELISM; ++i) {
                Runnable task = () -> {
                    Runnable r = provider.createRunnable();
                    r.run();
                    tasks.arriveAndDeregister();
                };
                tasks.register();
                mExecutor.execute(task);
            }

            // Waiting for the tasks to complete.
            tasks.arriveAndAwaitAdvance();

            mExecutor.shutdownNow();
        }
    };

    void execute(RunnablesProvider provider);
}
