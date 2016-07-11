#!/usr/bin/python3

# VM Setup:
# Client A and Client B each have NATing set up on the virtualbox network adapter
# Host is 10.0.2.2
import json
from enum import Enum

import time
from twisted.internet import reactor, protocol
from twisted.internet.task import LoopingCall


class PeerConnection(protocol.DatagramProtocol):

    class State(Enum):
        # TODO Authenticate with server and destination
        # TODO expire entries in auth server
        # TODO kill connection if keepalive not kept
        # TODO forward udp packets from local port
        authenticating = 1
        waiting_for_dest_authenticate = 2
        connecting_dest = 3
        acknowledging_dest_connect = 4
        connected = 5

    def __init__(self):
        super(PeerConnection, self).__init__()

        self.state = self.State.authenticating
        self._last_state = self.state

        self.user_id = input("What is your username?")
        self.dest = input("Who would you like to connect to?")
        self.auth_server = ('172.16.1.216', 44000)
        self.mapping = dict()

        self._event_loop_call = LoopingCall(PeerConnection.event_loop, self)

    def startProtocol(self):
        self._event_loop_call.start(0.1)

    def datagramReceived(self, data, addr):
        if addr == self.auth_server:
            self.packet_from_auth(data)
        elif self.dest in self.mapping and addr == self._dest_addr():
            self.packet_from_dest(data)

    def event_loop(self):
        if self.state != self._last_state:
            self.state_changed()

        if self.state == self.State.authenticating:
            self.authenticating()
        elif self.state == self.State.waiting_for_dest_authenticate:
            self.waiting_for_dest_authenticate()
        elif self.state == self.State.connecting_dest:
            self.connecting_dest()
        elif self.state == self.State.acknowledging_dest_connect:
            self.acknowledging_dest_connect()
        elif self.state == self.State.connected:
            self.connected()
        else:
            raise ValueError("Unknown State")

    def state_changed(self):
        print("State changed from", self._last_state, "to", self.state)
        self._last_state = self.state
        if self.state == self.State.connected:
            self._event_loop_call.stop()
            self._event_loop_call.start(5)

    def authenticating(self):
        """Authenticate with the public server and tell it our ip/username"""
        self.transport.write("map:{0}".format(self.user_id).encode('ascii'), self.auth_server)

    def waiting_for_dest_authenticate(self):
        """Wait for our destination to authenticate with the public server"""
        self.transport.write("map:{0}".format(self.user_id).encode('ascii'), self.auth_server)

    def connecting_dest(self):
        """Tell the destination that we are connecting, wait to hear back"""
        self._send_packet_to_dest("connect:{0}".format(self.user_id).encode('ascii'))

    def acknowledging_dest_connect(self):
        """The destination has connected to us"""
        self._send_packet_to_dest("connect:{0}".format(self.user_id).encode('ascii'))
        self._send_packet_to_dest("connect_ack:{0}".format(self.user_id).encode('ascii'))

    def connected(self):
        """We are connected, keep the connection alive"""
        self._send_packet_to_dest("heartbeat:{0}".format(self.user_id).encode('ascii'))
        self._send_packet_to_dest("My Custom Data from {0} at {1}".format(self.user_id, time.time()).encode('ascii'))

    def packet_from_auth(self, data):
        self.mapping = json.loads(data.decode('ascii'))
        if self.state == self.State.authenticating:
            self.state = self.State.waiting_for_dest_authenticate

        if self.dest in self.mapping and self.state == self.State.waiting_for_dest_authenticate:
            self.state = self.State.connecting_dest

    def packet_from_dest(self, data):
        if self._is_valid_connect(data) and self.state == self.State.connecting_dest:
            self.state = self.State.acknowledging_dest_connect
        elif self._is_valid_connect_ack(data):
            self.state = self.State.connected
        elif self._is_valid_heartbeat(data):
            self.state = self.State.connected
        elif self.state == self.State.connected:
            self.receive_tunnel_data(data)

    def send_tunnel_data(self, data):
        self.transport.write(data)

    def receive_tunnel_data(self, data):
        print(data)

    def _is_valid_connect(self, data):
        try:
            d = data.decode('ascii')
        except TypeError:
            return False
        return d.startswith("connect:") and d[8:] == self.dest

    def _is_valid_connect_ack(self, data):
        try:
            d = data.decode('ascii')
        except TypeError:
            return False
        return d.startswith("connect_ack:") and d[12:] == self.dest

    def _is_valid_heartbeat(self, data):
        try:
            d = data.decode('ascii')
        except TypeError:
            return False
        return d.startswith("heartbeat:") and d[10:] == self.dest

    def _send_packet_to_dest(self, data):
        self.transport.write(data, self._dest_addr())

    def _dest_addr(self):
        return self.mapping[self.dest]["host"], self.mapping[self.dest]["port"]


reactor.listenUDP(44000, PeerConnection())
reactor.run()
