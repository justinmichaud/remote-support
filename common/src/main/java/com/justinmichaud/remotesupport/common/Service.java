package com.justinmichaud.remotesupport.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Represents a bidirectional stream of data over a single stream
 */
public abstract class Service {

    public final static int MAX_ID = 255;

    protected final int id;

    // Out buffer is data that came from our output stream, to the tunnel
    // In buffer is data from the tunnel, to our input stream
    // In buffer is read by our inputstream, out buffer is written to by our outputstream
    // read/write to and from tunnel are nonblocking

    protected final CircularByteBuffer inBuffer, outBuffer;
    protected final byte[] buf = new byte[65535];

    protected final ServiceManager serviceManager;
    protected final WorkerThreadManager.WorkerThreadGroup workerThreadGroup;

    protected final Logger logger;

    public Service(int id, ServiceManager serviceManager) {
        if (id > MAX_ID) throw new IllegalArgumentException("Id is greater than max service id");

        this.id = id;
        this.serviceManager = serviceManager;
        logger = LoggerFactory.getLogger("Service " + id + ": " + getClass().getSimpleName());

        workerThreadGroup = serviceManager.workerThreadManager.makeGroup("Service " + id, this::stop);
        inBuffer = new CircularByteBuffer(CircularByteBuffer.INFINITE_SIZE, false);
        outBuffer = new CircularByteBuffer(CircularByteBuffer.INFINITE_SIZE, false);
    }

    public void readDataFromTunnel(int size, InputStream in) throws IOException {
        for (int i = 0; i<size; i++) {
            inBuffer.getOutputStream().write(in.read());
        }
    }

    public void writeDataToTunnel(OutputStream out) throws IOException {
        if (outBuffer.getAvailable() <= 0) return;
        int read = outBuffer.getInputStream().read(buf);
        if (read <= 0) return;

        // [1 byte - service id] [2 bytes - size] [data]
        out.write(id);
        out.write((read >> 8) & 0xFF);
        out.write(read & 0xFF);
        out.write(buf, 0, read);
    }

    // Write data to the tunnel
    protected OutputStream getOutputStream() {
        return outBuffer.getOutputStream();
    }

    // Read data from the tunnel
    protected InputStream getInputStream() {
        return inBuffer.getInputStream();
    }

    public void stop() {
        if (!isRunning()) return;

        logger.debug("Stopping service");
        workerThreadGroup.stop();
        try {
            if (!outBuffer.outputStreamClosed) outBuffer.getOutputStream().close();
            if (!outBuffer.inputStreamClosed) outBuffer.getInputStream().close();
            if (!inBuffer.outputStreamClosed) inBuffer.getOutputStream().close();
            if (!inBuffer.inputStreamClosed) inBuffer.getInputStream().close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        serviceManager.removeStoppedService(this);
    }

    public boolean isRunning() {
        return (workerThreadGroup.isRunning()
                || !inBuffer.inputStreamClosed
                || !inBuffer.outputStreamClosed
                || !outBuffer.inputStreamClosed
                || !outBuffer.outputStreamClosed);
    }
}
