# About

This is a work-in-progress remote support tool, designed to allow forwarding ssh and/or vnc connections securely over the internet. 
Currently, only udp hole punching and unencrypted vpn work through linux tun interfaces.

# How it works

It uses UDP hole punching to allow clients to connect through NATs. A publicly accessible server is used to share clients' public ip addresses.

# TODO / Ideas to explore

- Encrypt connections using java + tls (bouncycastle) + utp
- Replace tun adapter with tcp tunnel. Send data from local tcp connection over encrypted tls+utp (stream over udp) connection, where it is sent to remote tcp connection
- Authenticate with public server and destination
- Use public server to share client certificates, tell client when it is receiving support, and establish tun ip addresses using that
- Prompt client receiving support to accept connection + random confirmation code
- Ask the user to forward ports if udp hole punching doesn't work
- Handle edge cases when two people try to connect to same computer

- Make simple gui to allow the connection details to be entered
- Forward ssh and vnc from gui

