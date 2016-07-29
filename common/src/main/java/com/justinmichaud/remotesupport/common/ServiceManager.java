package com.justinmichaud.remotesupport.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceManager {

    public final WorkerThreadManager workerThreadManager;
    public final ControlService controlService;

    private final Logger logger;

    private final Socket peerSocket;
    private final WorkerThreadManager.WorkerThreadGroup workerThreadGroup;
    private final ConcurrentHashMap<Integer, Service> services = new ConcurrentHashMap<>();
    private final CircularByteBuffer inputBuffer, outputBuffer;

    private final Runnable onServiceManagerStopped;

    private class EventLoopPayload extends WorkerThreadManager.WorkerThreadPayload {

        public EventLoopPayload() {
            super("Service Manager Event Loop");
        }

        private void handleIncoming() throws IOException {
            if (peerSocket.isClosed() || !peerSocket.isConnected())
                throw new IOException("Peer socket is closed");

            if (inputBuffer.getAvailable() <= 3) return;
            InputStream in = inputBuffer.getInputStream();
            in.mark(3);

            int id = in.read();
            int length = ((in.read()&0xFF) << 8) | (in.read()&0xFF);

            in.reset();

            if (inputBuffer.getAvailable() < 3 + length) return;
            skip(in, 3);

            Service s = getService(id);
            if (s == null) {
                logger.error("Unknown service id: {}. Skipping.", id);
                skip(in, length);
            }
            else s.readDataFromTunnel(length, in);
        }

        private void skip(InputStream in, long n) throws IOException {
            long skipped = 0;
            while (skipped < n)
                skipped += in.skip(n - skipped);
        }

        private void handleOutgoing() throws IOException {
            for (Service s : services.values()) {
                s.writeDataToTunnel(outputBuffer.getOutputStream());
            }
        }

        @Override
        public void tick() throws Exception {
            //These must not block
            handleIncoming();
            handleOutgoing();
        }
    }

    public ServiceManager(Socket peerSocket, Runnable onServiceManagerStopped) throws IOException {
        this.logger = LoggerFactory.getLogger("[Service Manager]");
        this.peerSocket = peerSocket;
        this.onServiceManagerStopped = onServiceManagerStopped;

        inputBuffer = new CircularByteBuffer(CircularByteBuffer.INFINITE_SIZE, false);
        outputBuffer = new CircularByteBuffer(CircularByteBuffer.INFINITE_SIZE, false);

        workerThreadManager = new WorkerThreadManager(this::stop);

        controlService = new ControlService(this);
        services.put(0, controlService);

        workerThreadGroup = workerThreadManager.makeGroup("Service Manager", this::stop);
        workerThreadGroup.addWorkerThread(new InputOutputStreamPipePayload(peerSocket.getInputStream(),
                inputBuffer.getOutputStream(), false));
        workerThreadGroup.addWorkerThread(new InputOutputStreamPipePayload(outputBuffer.getInputStream(),
                peerSocket.getOutputStream(), false));
        workerThreadGroup.addWorkerThread(new EventLoopPayload());
    }

    public Service getService(int id) {
        return services.get(id);
    }

    public Service addService(Service s) throws IOException {
        logger.info("Adding local service {}", s.id);

        if (s.id == 0)
            throw new IllegalArgumentException("Cannot add service with id 0 - this is reserved by the control service");

        if (services.containsKey(s.id)) {
            throw new RuntimeException("Overwriting existing service!");
        }

        services.put(s.id, s);
        return s;
    }

    public void removeStoppedService(Service stopped) {
        Service s = getService(stopped.id);
        if (s == null)
            logger.debug("Error removing stopped service " + stopped.id + " - Service does not exist");
        else if (s != stopped)
            logger.debug("Error removing stopped service " + stopped.id + " - Service id does not match");
        else if (s.isRunning())
            logger.debug("Error removing stopped service " + stopped.id + " - Service is still running");
        else {
            services.remove(s.id);

            if (s.id == 0) {
                logger.debug("Removing control service");
                stop();
            }
        }
    }

    public void stop() {
        if (!isRunning()) return;
        logger.debug("Stopping service manager");

        services.values().forEach(Service::stop);
        if (onServiceManagerStopped != null) onServiceManagerStopped.run();
    }

    public boolean isRunning() {
        return (services.size() > 0);
    }
}
