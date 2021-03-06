package com.justinmichaud.remotesupport.client.ui;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.justinmichaud.remotesupport.client.ConnectionEventHandler;
import com.justinmichaud.remotesupport.client.discovery.DiscoveryConnection;
import com.justinmichaud.remotesupport.client.tunnel.ServiceManager;
import io.netty.channel.ChannelFuture;
import org.bouncycastle.util.io.TeeOutputStream;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.PrintStream;
import java.net.InetSocketAddress;

public class ConnectForm {

    private JPanel root;
    private JTextField txtDiscoveryServer;
    private JButton connectButton;
    private JTextArea txtConsole;
    private JTextField txtUsername;
    private JButton closeButton;

    public ConnectForm(JFrame frame) {
        frame.setTitle("Remote Support");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(500, 450));
        frame.pack();
        frame.setLocationRelativeTo(null); // Move to center of screen
        frame.setVisible(true);

        txtDiscoveryServer.setText("localhost");

        JTextAreaOutputStream txtOut = new JTextAreaOutputStream(txtConsole);
        System.setOut(new PrintStream(new TeeOutputStream(System.out, txtOut)));

        connectButton.addActionListener(e -> {
            ConnectionEventHandler eh = new ConnectionEventHandler() {
                @Override
                protected String prompt(String msg) {
                    return JOptionPane.showInputDialog(
                            frame,
                            msg,
                            "Question",
                            JOptionPane.PLAIN_MESSAGE);
                }

                @Override
                public void onPeerConnected(ServiceManager serviceManager) {
                    super.onPeerConnected(serviceManager);

                    frame.setContentPane(new PeerForm(frame, this, txtOut).root);
                    frame.pack();
                }

                @Override
                public void onDiscoveryConnectionClosed() {
                    super.onDiscoveryConnectionClosed();

                    connectButton.setEnabled(true);
                }

                @Override
                public void onPeerConnectionClosed() {
                    super.onPeerConnectionClosed();

                    frame.setContentPane(new ConnectionClosedForm(frame, txtOut).root);
                    frame.pack();
                }
            };

            if (txtUsername.getText().length() == 0) {
                eh.log("You must specify a username!");
                return;
            }
            if (txtDiscoveryServer.getText().length() == 0) {
                eh.log("You must specify a discovery server!");
                return;
            }

            frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    eh.close();
                }
            });

            closeButton.addActionListener(actionEvent -> {
                eh.close();
            });

            connectButton.setEnabled(false);
            InetSocketAddress addr = new InetSocketAddress(txtDiscoveryServer.getText(), 40000);

            new Thread(() -> DiscoveryConnection.runDiscoveryConnection(addr, txtUsername.getText(), eh)).start();
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | IllegalAccessException
                | InstantiationException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        JFrame frame = new JFrame("Remote Support");
        frame.setContentPane(new ConnectForm(frame).root);
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        root = new JPanel();
        root.setLayout(new GridLayoutManager(4, 3, new Insets(10, 10, 10, 10), -1, -1));
        root.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(-4473925)), "Remote Support"));
        final JLabel label1 = new JLabel();
        label1.setText("Discovery Server");
        root.add(label1, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtDiscoveryServer = new JTextField();
        root.add(txtDiscoveryServer, new GridConstraints(0, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        txtConsole = new JTextArea();
        txtConsole.setEditable(false);
        root.add(txtConsole, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_WANT_GROW, null, new Dimension(150, 50), null, 0, false));
        connectButton = new JButton();
        connectButton.setText("Connect");
        root.add(connectButton, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Username");
        root.add(label2, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        txtUsername = new JTextField();
        root.add(txtUsername, new GridConstraints(1, 1, 1, 2, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        closeButton = new JButton();
        closeButton.setText("Close");
        root.add(closeButton, new GridConstraints(3, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final Spacer spacer1 = new Spacer();
        root.add(spacer1, new GridConstraints(3, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return root;
    }
}
