package com.justinmichaud.remotesupport.common;

import java.io.*;

/**
 * Represents a bidirectional stream of data over a single stream
 */
public class Service {

    public final static int MAX_ID = 255;

    protected final int id;

    // Out buffer is data that came from our output stream, to the tunnel
    // In buffer is data from the tunnel, to our input stream
    // In buffer is read by our inputstream, out buffer is written to by our outputstream
    // read/write to and from tunnel are nonblocking

    protected CircularByteBuffer inBuffer, outBuffer;
    protected byte[] buf = new byte[65535];

    protected ServiceManager serviceManager;

    public Service(int id, ServiceManager serviceManager) {
        this.id = id;
        inBuffer = new CircularByteBuffer(CircularByteBuffer.INFINITE_SIZE, false);
        outBuffer = new CircularByteBuffer(CircularByteBuffer.INFINITE_SIZE, false);
        this.serviceManager = serviceManager;
    }

    public void readDataFromTunnel(int size, InputStream in) throws IOException {
        System.out.println("Service " + id + " reading data from tunnel");

        for (int i = 0; i<size; i++) {
            inBuffer.getOutputStream().write(in.read());
        }
    }

    public void writeDataToTunnel(OutputStream out) throws IOException {
        int read = outBuffer.getInputStream().read(buf);
        if (read <= 0) return;

        System.out.println("Service " + id + " writing data to tunnel");

        // [1 byte - Magic] [1 byte - service id] [2 bytes - size] [data]
        out.write(ServiceManager.MAGIC_DATA);
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

    public void close() throws IOException {
        System.out.println("Closing service - internal - " + id);
        getOutputStream().close();
        getInputStream().close();
    }

    public boolean isOpen() {
        return !inBuffer.outputStreamClosed && !inBuffer.inputStreamClosed
                && !outBuffer.outputStreamClosed && !outBuffer.inputStreamClosed;
    }
}
