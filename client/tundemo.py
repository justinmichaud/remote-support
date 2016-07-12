#!/usr/bin/python3

from pytun import TunTapDevice

tun = TunTapDevice()

print("Created tun adapter", tun.name)
tun.addr = '10.8.0.1'
tun.dstaddr = '10.8.0.2'
tun.netmask = '255.255.255.0'
tun.mtu = 1500
tun.up()

try:
    while True:
        print("Packet", tun.read(tun.mtu))
except KeyboardInterrupt:
    pass

print("Closing")
tun.close()
