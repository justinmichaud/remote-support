# About

This is a work-in-progress remote support tool, designed to allow forwarding ssh and/or vnc connections securely over the internet. 
Currently, only udp hole punching and unencrypted vpn work through linux tun interfaces.

# How it works

It uses UDP hole punching to allow clients to connect through NATs. A publicly accessible server is used to share clients' public ip addresses.

# Left to implement

- Do udp hole punching
- Establish encrypted connection with public server, authenticate aliases

- Fix bug causing vnc to work but ssh to fail - I suspect it has something to do with the buffering

- Improve performance
- Make simple gui to allow the connection details to be entered
- Ask the user to forward ports if udp hole punching doesn't work
- Handle edge cases when two people try to connect to same computer
- Forward vnc from gui

- Replace threaded portions with nio (and possibly barchart udt with netty) to get around the limitations/bugs from having multiple threads that can't be interrupted when doing socket reads

