# About

This is a work-in-progress remote support tool, designed to allow forwarding any tcp connection securely over the internet without having to set up port forwarding.

![screenshot 1](https://raw.githubusercontent.com/justinmichaud/remote-support/master/images/screen1.png)

# Status

Currently, it can forward any tcp connection over a channel encrypted by TLSv1.2, with public key authentication, through certain types of NATs. It is still very rough around the edges.
Symmetric NATs do not work without setting up port forwarding. See the wikipedia page on [udp hole punching](https://en.wikipedia.org/wiki/UDP_hole_punching) for more information.

[Downloads](https://github.com/justinmichaud/remote-support/releases).

# How to use
1) Make sure you are not working from a corporate network. If you are, you will need to set up port forwarding, which is not currently supported.

2) Download and run the server application on a computer accessible to both of the clients, with port 40000 open.

3) Download and run the client application by extracting the zip and launching the shell script (linux/mac) or the .bat file (windows). Enter the public ip address or domain for the discovery server you set up in part 2. The application will connect to this address on port 40000 to negotiate a connection with your partner, but no data from the tunnel will be sent to the discovery server once your connection is established.

![screenshot 2](https://raw.githubusercontent.com/justinmichaud/remote-support/master/images/screen2.png)

4) Connect to your partner, or leave blank if your partner will connect to you

![screenshot 3](https://raw.githubusercontent.com/justinmichaud/remote-support/master/images/screen3.png)

5) Verify your X509 Certificate fingerprints

![screenshot 4](https://raw.githubusercontent.com/justinmichaud/remote-support/master/images/screen4.png)

6)You are now connected. Enter a local port to be forwarded to a remote port. A single tcp connection on this port will be forwarded to the remote computer. Once the connection closes, you will have to re-open the port.

![screenshot 5](https://raw.githubusercontent.com/justinmichaud/remote-support/master/images/screen5.png)

In the example above, to ssh in to the remote computer you would use
    ssh [remote-user]@localhost -p 2222
to connect to an ssh server on the remote machine on port 22.

# How it works

It uses UDP hole punching to allow clients to connect through a network address translator.
The server/ subproject must be running on a publicly accessible server, so that the clients can learn each other's public ip addresses.
Each client then tries to connect directly to the other, attempting to re-use the port mapping from the public connection.
This project uses Netty and Barchart UDT to provide a reliable stream over UDP, and BouncyCastle to provide encryption.

# Left to implement

- Fix bug where the client hangs when closing
- Fix bug where close doesn't work when connecting to peer
- Change close button text on public screen to cancel

- Add persistent forwarded connections (i.e that reconnect when they close)
- Clean up code
- Make error handling more friendly, fix service closing exceptions
- Add separate logs per service, error handling + separate ui panes per service
- Make the ui more user friendly
- Establish encrypted connection with public server, authenticate aliases
- Ask the user to forward ports if udp hole punching doesn't work, and allow connecting directly to another user
- Allow running as daemon, so you can enable unattended access
- Launch vnc and ssh server remotely
- Allow file uploading or other admin tasks directly
