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

    private class EventLoopPayload extends WorkerThreadManager.WorkerThreadPayload {

        private void handleIncoming() throws IOException {
            if (inputBuffer.getAvailable() <= 3) return;
            InputStream in = inputBuffer.getInputStream();
            in.mark(3);

            int id = in.read();
            int length = ((in.read()&0xFF) << 8) | (in.read()&0xFF);

            if (inputBuffer.getAvailable() < 3 + length) {
                in.reset();
                return;
            }

            Service s = getService(id);
            if (s == null) {
                logger.error("Unknown service id: {}. Skipping.", id);
                long skipped = 0;
                while (skipped < length)
                    skipped += in.skip(length - skipped);
            }
            else s.readDataFromTunnel(length, in);
        }

        private void handleOutgoing() throws IOException {
            for (Service s : services.values()) {
                s.writeDataToTunnel(outputBuffer.getOutputStream());
            }
        }

        @Override
        public void tick() throws Exception {
            if (getService(0) != controlService) throw new IOException("The control service is not running");
            //These are nonblocking
            handleIncoming();
            handleOutgoing();
        }
    }

    public ServiceManager(Socket peerSocket) throws IOException {
        this.logger = LoggerFactory.getLogger("Service Manager");
        this.peerSocket = peerSocket;

        inputBuffer = new CircularByteBuffer(CircularByteBuffer.INFINITE_SIZE, false);
        outputBuffer = new CircularByteBuffer(CircularByteBuffer.INFINITE_SIZE, false);

        Runnable endConnection = () -> {
            try {
                peerSocket.close();
            } catch (IOException e) {}
        };

        workerThreadManager = new WorkerThreadManager(endConnection);

        controlService = new ControlService(this);
        services.put(0, controlService);

        workerThreadGroup = workerThreadManager.makeGroup("Service Manager", endConnection);
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
            logger.warn("Warning: overwriting existing service!");
            stopService(s.id);
        }
        services.put(s.id, s);
        return s;
    }

    public void stopService(int id) throws IOException {
        logger.info("Stopping local service " + id);
        synchronized (services) {
            getService(id).stop();
            removeService(id);
        }
    }

    public void removeService(int id) {
        logger.debug("Removing local service " + id);
        if (id == 0) {
            logger.info("Control service stopped - ending connection");
            try {
                peerSocket.close();
            } catch (IOException e) {}
        }
        services.remove(id);
    }
}
