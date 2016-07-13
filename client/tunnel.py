import threading
from pytun import TunTapDevice
from queue import Queue

from twisted.internet import reactor

from peer import PeerConnection

import hashlib


class TunnelInputThread(threading.Thread):
    def __init__(self, tunnel):
        super(TunnelInputThread, self).__init__()
        self.tunnel = tunnel
        self.setDaemon(True)

    def run(self):
        while self.tunnel.running:
            data = self.tunnel.tun.read(self.tunnel.tun.mtu)
            print("Sending data of length", len(data), ":", hashlib.md5(data).hexdigest(), ":", " ".join('{:02x}'.format(x) for x in data[:5]), "...", " ".join('{:02x}'.format(x) for x in data[-5:]))
            if len(data) > self.tunnel.tun.mtu:
                print("Rejected received packet that was too large")
                continue
            self.tunnel.receive_tap_data(len(data).to_bytes(2, byteorder='big') + data)


class Tunnel(threading.Thread):
    def __init__(self, peer):
        super(Tunnel, self).__init__()

        self.running = False
        self.input_thread = TunnelInputThread(self)

        self._peer = peer
        self.tun = TunTapDevice()
        self._data_to_process = Queue()
        self._data_to_send = Queue()

        self._current_packet = bytearray()
        self._current_packet_bytes_remaining = 0

        self.tun.addr = '10.8.0.{0}'.format(self._peer.user_id)
        self.tun.dstaddr = '10.8.0.{0}'.format(self._peer.dest_id)
        self.tun.netmask = '255.255.255.0'
        self.tun.mtu = 576
        self.tun.up()

        self.setDaemon(True)

    def run(self):
        while not self._peer.state == self._peer.State.connected:
            pass  # Wait for startup

        self.running = True
        self.input_thread.start()

        while reactor.running and self._peer.State.connected:
            while not self._data_to_process.empty():
                data = self._data_to_process.get()
                for byte in data:
                    self._current_packet.append(byte)
                    self._current_packet_bytes_remaining -= 1

                    if self._current_packet_bytes_remaining == -2:  # Size of next packet was written
                        self._current_packet_bytes_remaining = int.from_bytes(self._current_packet, byteorder='big')
                        self._current_packet.clear()

                    if self._current_packet_bytes_remaining == 0:
                        self.tun.write(bytes(self._current_packet))
                        print("Received data of length", len(self._current_packet), ":", hashlib.md5(bytes(self._current_packet)).hexdigest(), ":", " ".join('{:02x}'.format(x) for x in self._current_packet[:5]), "...", " ".join('{:02x}'.format(x) for x in self._current_packet[-5:]))
                        self._current_packet.clear()  # Wait for size of next packet
                if self._current_packet_bytes_remaining != 0:
                    print("Rejecting packet from tunnel - did not fit inside one udp packet. Adjust MTU")
                    self._current_packet.clear()
                    self._current_packet_bytes_remaining = 0

            while not self._data_to_send.empty():
                data = self._data_to_send.get()
                reactor.callFromThread(PeerConnection.send_tunnel_data,
                                       self._peer, data)

        self.running = False
        self.tun.close()

    def receive_peer_data(self, data):
        """Handle incoming data from the peer"""
        self._data_to_process.put(data)

    def receive_tap_data(self, data):
        """Handle incoming data from this computer"""
        self._data_to_send.put(data)

