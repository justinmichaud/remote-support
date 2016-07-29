package com.justinmichaud.remotesupport.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class InputOutputStreamPipePayload extends WorkerThreadManager.WorkerThreadPayload {
    private final InputStream in;
    private final OutputStream out;

    private byte[] buf = new byte[8024];

    public InputOutputStreamPipePayload(InputStream in, OutputStream out) {
        super("InputOutputStream Pipe");
        this.in = in;
        this.out = out;
    }

    @Override
    public void tick() throws Exception {
        int read = in.read(buf);

        if (read > 0) {
            out.write(buf, 0, read);
        }
        else if (read < 0) {
            throw new IOException("End of stream");
        }
    }
}
