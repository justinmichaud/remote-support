package com.justinmichaud.remotesupport.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class LocalTunnel {

    private volatile boolean running = true;
    private Thread inputThread, outputThread;

    private class PipeRunnable implements Runnable {

        private InputStream in;
        private OutputStream out;

        public PipeRunnable(InputStream in, OutputStream out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            try {
                while (running) out.write(in.read());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public LocalTunnel(InputStream localIn, OutputStream localOut, InputStream peerIn, OutputStream peerOut)
            throws IOException {
        inputThread = new Thread(new PipeRunnable(localIn, peerOut));
        inputThread.setDaemon(true);
        outputThread = new Thread(new PipeRunnable(peerIn, localOut));
        outputThread.setDaemon(true);

        inputThread.start();
        outputThread.start();
    }

    public void stop() {
        running = false;
    }

}
