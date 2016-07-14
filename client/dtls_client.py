#!/usr/bin/python3
import socket
import ssl

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
ssl_sock = ssl.wrap_socket(s,
                           ca_certs="data/server_cert",
                           cert_reqs=ssl.CERT_REQUIRED,
                           ssl_version=ssl.PROTOCOL_TLSv1_2)
ssl_sock.connect(('127.0.0.1', 12345))
print(repr(ssl_sock.getpeername()))
print(ssl_sock.cipher())
print(ssl_sock.getpeercert())

line = input()

while line:
    ssl_sock.write(line.encode('ascii'))
    line = input()

ssl_sock.shutdown(socket.SHUT_RDWR)
ssl_sock.close()
