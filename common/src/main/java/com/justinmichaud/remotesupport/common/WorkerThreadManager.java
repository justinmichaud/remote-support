package com.justinmichaud.remotesupport.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class WorkerThreadManager {

    private final ConcurrentHashMap<String, WorkerThreadGroup> groups = new ConcurrentHashMap<>();
    private final Runnable onAllGroupsClosed;
    private final Logger logger;

    public WorkerThreadManager(Runnable onAllGroupsClosed) {
        this.logger = LoggerFactory.getLogger("Worker Thread Manager");
        this.onAllGroupsClosed = onAllGroupsClosed;
    }

    public static abstract class WorkerThreadPayload {

        protected final Logger logger;

        public WorkerThreadPayload() {
            logger = LoggerFactory.getLogger("Worker Thread Payload: " + getClass().getName());
        }

        public void start() throws Exception {}

        public void tick() throws Exception {}

        // By default, if one thread in a group stops, the others do too
        public void stop(WorkerThreadManager.WorkerThreadGroup group) {
            group.stop();
        }
    }

    public class WorkerThreadGroup {
        private final ConcurrentHashMap<String, WorkerThread> threads = new ConcurrentHashMap<>();
        private final Runnable onGroupStoppedCallback;
        private final String name;

        private final Logger logger;

        public WorkerThreadGroup(String name, Runnable onGroupStoppedCallback) {
            this.logger = LoggerFactory.getLogger("WorkerThreadGroup " + name);
            logger.debug("Created");
            this.onGroupStoppedCallback = onGroupStoppedCallback;
            this.name = name;
        }

        public synchronized WorkerThread addWorkerThread(WorkerThreadPayload payload) {
            logger.debug("Adding worker thread of type {}", payload.getClass().getName());

            String threadName = payload.getClass().getName() + "-";
            int i = 0;
            while (threads.containsKey(threadName + i)) i++;
            threadName += i;

            WorkerThread thread = new WorkerThread(threadName, payload, this);
            threads.put(threadName, thread);
            thread.start();
            return thread;
        }

        public synchronized void stop() {
            logger.debug("Asked to stop group");
            threads.values().forEach(WorkerThread::stop);
        }

        private synchronized void onThreadStopped(WorkerThread stopped) {
            logger.debug("Removing thread {} from thread group", stopped.name);

            WorkerThread thread = threads.get(stopped.name);
            if (thread == null) {
                logger.debug("Error removing this thread from the list of threads - " +
                        "No thread exists with this name {}", stopped.name);
            }
            else if (thread != stopped)
                logger.debug("Error removing this thread from the list of threads - " +
                        "A different thread already exists with the same name {}", stopped.name);
            else
                threads.remove(stopped.name);

            boolean allStopped = true;
            for (WorkerThread t : threads.values()) {
                if (!t.isRunning()) {
                    allStopped = false;
                    break;
                }
            }
            if (allStopped) logger.debug("Not all stopped threads have been removed from the thread group yet.");
            else logger.debug("ALL stopped threads have been removed from the thread group!");

            if (threads.size() == 0) {
                logger.debug("Group is stopping");
                onGroupStopped(this);
                if (onGroupStoppedCallback != null) onGroupStoppedCallback.run();
            }
        }
    }

    private class WorkerThread {

        private final String name;
        private final Logger logger;

        private final Thread thread;
        private final WorkerThreadGroup group;
        private final WorkerThreadPayload payload;

        private volatile boolean running = false;

        private class WorkerThreadRunnable implements Runnable {
            @Override
            public void run() {
                logger.debug("Starting worker thread.");
                try {
                    payload.start();
                } catch (Exception e) {
                    logger.debug("Error while starting worker thread: {}", e);
                }
                while (running) {
                    try {
                        payload.tick();
                    } catch (Exception e) {
                        logger.debug("Error while running worker thread: {}", e);
                        running = false;
                    }
                }
                try {
                    payload.stop(group);
                } catch (Exception e) {
                    logger.debug("Error while stopping worker thread: {}", e);
                }
                logger.debug("Worker thread stopped.");

                group.onThreadStopped(WorkerThread.this);
            }
        }

        public WorkerThread(String name, WorkerThreadPayload payload, WorkerThreadGroup group) {
            this.name = name;
            this.group = group;
            this.logger = LoggerFactory.getLogger(getClass());
            this.payload = payload;
            this.thread = new Thread(new WorkerThreadRunnable());
            this.thread.setName(name);
            this.thread.setDaemon(false); //TODO Temporary, to make sure that all threads close
        }

        public void start() {
            if (running) logger.warn("Starting thread that is already running!");
            running = true;
            this.thread.start();
        }

        public void stop() {
            this.running = false;
            thread.interrupt();
        }

        public boolean isRunning() {
            return this.running;
        }
    }

    private void onGroupStopped(WorkerThreadGroup stopped) {
        WorkerThreadGroup group = groups.get(stopped.name);
        if (group == null) {
            logger.debug("Error removing this thread group from the list of groups - " +
                    "No group exists with this name {}", stopped.name);
        }
        else if (group != stopped)
            logger.debug("Error removing this thread group from the list of groups - " +
                    "A different group already exists with the same name {}", stopped.name);
        else
            groups.remove(stopped.name);

        if (groups.size() == 0 && onAllGroupsClosed != null) onAllGroupsClosed.run();
    }

    public WorkerThreadGroup makeGroup(String name) {
        return makeGroup(name, null);
    }

    public WorkerThreadGroup makeGroup(String name, Runnable onClose) {
        if (groups.containsKey(name)) throw new RuntimeException("WorkerThreadGroup already exists - " + name);
        WorkerThreadGroup group = new WorkerThreadGroup(name, onClose);
        groups.put(name, group);
        return group;
    }

}
