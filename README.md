# About

This is a work-in-progress remote support tool, designed to allow forwarding any tcp connection securely over the internet without having to set up port forwarding.

![screenshot 1](https://raw.githubusercontent.com/jtjj222/remote-support/master/images/screen1.png)

# Status

Currently, it can forward any tcp connection over a channel encrypted by TLSv1.2, with public key authentication, through certain types of NATs.
Symmetric NATs do not work without setting up port forwarding. See the wikipedia page on [udp hole punching](https://en.wikipedia.org/wiki/UDP_hole_punching) for more information.

[Downloads](https://github.com/jtjj222/remote-support/releases).

# How it works

It uses UDP hole punching to allow clients to connect through a network address translator.
The server/ subproject must be running on a publicly accessible server, so that the clients can learn each other's public ip addresses.
Each client then tries to connect directly to the other, attempting to re-use the routing rules from the public connection.
This project uses Netty and Barchart UDT to provide a reliable stream over UDP, and BouncyCastle to provide encryption.

# Left to implement

- Add persistent forwarded connections (i.e that reconnect when they close)
- Clean up code
- Make error handling more friendly, fix service closing exceptions
- Add separate logs per service, error handling + separate ui panes per service
- Make the ui more user friendly
- Establish encrypted connection with public server, authenticate aliases
- Ask the user to forward ports if udp hole punching doesn't work, and allow connecting directly to another user
- Allow running as daemon
- Launch vnc and ssh server remotely
- Make CLI or HTTP Ui
