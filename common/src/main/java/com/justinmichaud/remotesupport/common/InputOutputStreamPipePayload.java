package com.justinmichaud.remotesupport.common;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class InputOutputStreamPipePayload extends WorkerThreadManager.WorkerThreadPayload {
    private final InputStream in;
    private final OutputStream out;
    private final boolean blocking;

    public InputOutputStreamPipePayload(InputStream in, OutputStream out) {
       this(in, out, true);
    }

    public InputOutputStreamPipePayload(InputStream in, OutputStream out, boolean blocking) {
        super("InputOutputStream Pipe");
        this.in = in;
        this.out = out;
        this.blocking = blocking;
    }

    @Override
    public void tick() throws Exception {
        int b = in.read(); //TODO buffering

        // Our nonblocking circular buffer will return -1 when there is no more data left to read, but
        // there still may be more data in the future

        if (b >= 0) {
            out.write(b);
        }
        else if (blocking) throw new IOException("End of Stream");
    }
}
