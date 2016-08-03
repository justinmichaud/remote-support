# About

This is a work-in-progress remote support tool, designed to allow forwarding ssh and/or vnc connections securely over the internet.

# How it works

It uses UDP hole punching to allow clients to connect through a network address translator.
The server/ subproject must be running on a publicly accessible server, so that the clients can learn each other's public ip addresses.

# Left to implement

- Make simple gui to allow the connection details to be entered

- Ask the user to forward ports if udp hole punching doesn't work
- Handle edge cases when two people try to connect to same computer
- Launch vnc and ssh server from gui

- Establish encrypted connection with public server, authenticate aliases
- Improve performance
- Replace threaded portions with nio (and possibly barchart udt with netty) to get around the limitations/bugs/performance issues from having multiple threads that can't be interrupted when doing socket reads

