package com.justinmichaud.remotesupport.client.ui;

import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;

// http://stackoverflow.com/questions/14706674/system-out-println-to-jtextarea
public class JTextAreaOutputStream extends OutputStream {
    private JTextArea destination;

    public JTextAreaOutputStream(JTextArea destination) {
        if (destination == null)
            throw new IllegalArgumentException("Destination is null");

        this.destination = destination;
    }

    @Override
    public synchronized void write(byte[] buffer, int offset, int length) throws IOException {
        final String text = new String(buffer, offset, length);
        SwingUtilities.invokeLater(() -> destination.append(text));
    }

    @Override
    public synchronized void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    public synchronized void changeDestination(JTextArea newDest) {
        newDest.append(destination.getText());
        destination = newDest;
    }
}