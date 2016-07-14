#!/usr/bin/python3
import socket
import ssl


bindsocket = socket.socket()
ssl_bindsocket = ssl.wrap_socket(bindsocket,
                                 server_side=True,
                                 keyfile="data/server_key",
                                 certfile="data/server_cert",
                                 ssl_version=ssl.PROTOCOL_TLSv1_2)
ssl_bindsocket.bind(('', 12345))
ssl_bindsocket.listen(5)

newsocket, fromaddr = ssl_bindsocket.accept()
line = newsocket.recv(65535)

while line:
    print(repr(line))
    line = newsocket.recv(65535)
