# About

This is a work-in-progress remote support tool, designed to allow forwarding ssh and/or vnc connections securely over the internet. 
Currently, only udp hole punching and unencrypted vpn work through linux tun interfaces.

# How it works

It uses UDP hole punching to allow clients to connect through NATs. A publicly accessible server is used to share clients' public ip addresses.

# TODO / Ideas to explore

- Tunnel tcp connection
- Allow peers to choose to accept unknown certificate, display random code
- Prompt peers to accept connection
- Establish encrypted dtls connection with public server, get/send info for udp hole punching
- Create simple confined shell to allow controlling remote computer
- Ask the user to forward ports if udp hole punching doesn't work
- Handle edge cases when two people try to connect to same computer

- Make simple gui to allow the connection details to be entered
- Forward vnc from gui

