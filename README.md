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

- Fix ui code mess
- Fix service closing exceptions
- Fix error edge cases and make error handling ui more friendly
- Fix logging, add separate logs per service, error handling + separate ui panes
- Make the public connection gui more user friendly
- Add persistent forwarded connections (i.e that reconnect when they close)
- Use netty for public connection / server
- Establish encrypted connection with public server, authenticate aliases
- Ask the user to forward ports if udp hole punching doesn't work, and allow connecting directly to another user
- Launch vnc and ssh server remotely
- Make CLI or HTTP Ui
