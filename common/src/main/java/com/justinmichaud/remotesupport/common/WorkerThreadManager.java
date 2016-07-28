package com.justinmichaud.remotesupport.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class WorkerThreadManager {

    private final ArrayList<WorkerThreadGroup> groups = new ArrayList<>();
    private final Runnable onThreadManagerClosed;
    private final Logger logger;

    public WorkerThreadManager(Runnable onThreadManagerClosed) {
        this.logger = LoggerFactory.getLogger("[Worker Thread Manager]");
        this.onThreadManagerClosed = onThreadManagerClosed;
    }

    public static abstract class WorkerThreadPayload {

        protected final Logger logger;
        protected final String name;

        public WorkerThreadPayload(String name) {
            this.name = name;
            logger = LoggerFactory.getLogger("[Worker Thread Payload: " + name + "]");
        }

        public void start() throws Exception {}

        public void tick() throws Exception {}

        public void stop() throws Exception {}
    }

    public class WorkerThreadGroup {
        private final ArrayList<WorkerThread> threads = new ArrayList<>();
        private final Runnable onGroupStoppedCallback;
        private final String name;

        private volatile boolean running = true;

        private final Logger logger;

        public WorkerThreadGroup(String name, Runnable onGroupStoppedCallback) {
            this.logger = LoggerFactory.getLogger("[WorkerThreadGroup " + name + "]");
            this.onGroupStoppedCallback = onGroupStoppedCallback;
            this.name = name;
        }

        public synchronized WorkerThread addWorkerThread(WorkerThreadPayload payload) {
            logger.debug("Adding worker thread " + payload.name);
            String threadName = name + "." + payload.name + "-" + threads.size();

            WorkerThread thread = new WorkerThread(threadName, payload, this);
            threads.add(thread);
            return thread;
        }

        public synchronized void stop() {
            if (!running) return;
            logger.debug("Stopping thread group");

            this.running = false;
            threads.forEach(WorkerThread::interrupt);
            onGroupStopped(this);
            if (onGroupStoppedCallback != null) onGroupStoppedCallback.run();
        }

        public synchronized boolean isRunning() {
            return running;
        }
    }

    private class WorkerThread {

        private final String name;
        private final Logger logger;

        private final Thread thread;
        private final WorkerThreadGroup group;
        private final WorkerThreadPayload payload;

        private class WorkerThreadRunnable implements Runnable {
            @Override
            public void run() {
                logger.debug("Starting worker thread.");
                try {
                    payload.start();
                } catch (Exception e) {
                    logger.debug("Error while starting worker thread: {}", e);
                }
                while (group.running) {
                    try {
                        payload.tick();
                    } catch (Exception e) {
                        logger.debug("Error while running worker thread: {}", e);
                        group.stop();
                    }
                }
                try {
                    payload.stop();
                } catch (Exception e) {
                    logger.debug("Error while stopping worker thread: {}", e);
                }
                logger.debug("Worker thread stopped.");
            }
        }

        public WorkerThread(String name, WorkerThreadPayload payload, WorkerThreadGroup group) {
            this.name = name;
            this.group = group;
            this.logger = LoggerFactory.getLogger("[Worker Thread " + group.name + "." + name + "]");
            this.payload = payload;
            this.thread = new Thread(new WorkerThreadRunnable());
            this.thread.setName(name);
            this.thread.setDaemon(false); //TODO Temporary, to make sure that all threads close
            this.thread.setUncaughtExceptionHandler((t, e) -> {
                logger.debug("Uncaught exception in thread " + t.getName() + ":", e);
                System.out.println("***Exiting due to an uncaught exception!***");
                System.exit(1);
            });
            this.thread.start();
        }

        public void interrupt() {
            thread.interrupt();
        }
    }

    private synchronized void onGroupStopped(WorkerThreadGroup stopped) {
        if (!groups.remove(stopped))
            throw new RuntimeException("Groups list did not contain stopped group " + stopped.name);

        if (groups.size() == 0) {
            logger.debug("Thread Manager Stopped");
            if (onThreadManagerClosed != null)
                onThreadManagerClosed.run();
        }
    }

    public synchronized WorkerThreadGroup makeGroup(String name, Runnable onClose) {
        WorkerThreadGroup group = new WorkerThreadGroup(name, onClose);
        groups.add(group);
        return group;
    }

    public synchronized void stop() {
        logger.debug("Stopping thread manager");
        groups.forEach(WorkerThreadGroup::stop);
    }

}
