#!/usr/bin/python3

# To test: run server on one computer, and virtualbox with nat on two others
# Currently linux-only
# TODO forward packets using TUN adapter
# TODO Authenticate with auth server and destination
# TODO expire entries in auth server after timeout
# TODO kill connection if keepalive not kept or can't connect
# TODO Fall back on upnp, or ask the user to forward ports
# TODO Make simple gui
# TODO Forward some kind of ssh/vnc connection automatically

from twisted.internet import reactor
from peer import PeerConnection
from tunnel import Tunnel

conn = PeerConnection(input("What is your username?"),
                      input("Who would you like to connect to?"),
                      ('172.16.1.216', 44000),
                      lambda data: tunnel_thread.receive_tunnel_data(data))
tunnel_thread = Tunnel(conn)
tunnel_thread.start()

reactor.listenUDP(44000, conn)
reactor.run()
