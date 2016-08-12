package com.justinmichaud.remotesupport.client.ui;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ConnectionClosedForm {
    public JPanel root;
    private JTextArea txtConsole;
    private JButton btnClose;

    public ConnectionClosedForm(JFrame frame, JTextAreaOutputStream txtOut) {
        frame.setTitle("Connection closed");
        txtOut.changeDestination(txtConsole);
        btnClose.addActionListener(actionEvent -> {
            frame.dispose();
            System.exit(0);
        });
    }
}
