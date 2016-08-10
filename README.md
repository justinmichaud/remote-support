# About

This is a work-in-progress remote support tool, designed to allow forwarding ssh and/or vnc connections securely over the internet.

![screenshot 1](https://raw.githubusercontent.com/jtjj222/remote-support/master/images/screen1.png)

# Status

Currently, it can forward any tcp connection over a channel encrypted by TLSv1.2, with public key authentication, through certain types of NATs.
It is not ready for production yet, and is very buggy. While it should work on other platforms, it has only been tested on Linux.

# How it works

It uses UDP hole punching to allow clients to connect through a network address translator.
The server/ subproject must be running on a publicly accessible server, so that the clients can learn each other's public ip addresses.
Each client then tries to connect directly to the other, attempting to re-use the routing rules from the public connection.
This project uses Barchart UDT to provide reliable UDP data transfer, and BouncyCastle to provide encryption.

# Left to implement

- Replace threaded portions with nio + netty to get around the limitations/bugs/performance issues from the current setup:
    - Fix service not closing when waiting to accept connection
    - Fix backlog of messages before connection is accepted causing permanent bottleneck (buffer?) + remove separate event loop
    - Create control handler to chunk data for each service, add/remove remote services
    - Replace existing tunnel code in ui
- Make the gui more user friendly
- Establish encrypted connection with public server, authenticate aliases
- Ask the user to forward ports if udp hole punching doesn't work, and possibly allow using a relay server
- Make CLI using charva
- Launch vnc and ssh server remotely
