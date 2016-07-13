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
        self._packet_id = 0

    def run(self):
        while self.tunnel.running:
            data = self.tunnel.tun.read(self.tunnel.tun.mtu)
            print("Sending data of length", len(data), ":", hashlib.md5(data).hexdigest(), ":", " ".join('{:02x}'.format(x) for x in data[:5]), "...", " ".join('{:02x}'.format(x) for x in data[-5:]))
                        
            # Data becomes packets with the following structure:
            # 00 [size of this packet, 2 bytes] [data]
            # if it fits in one packet, or
            # 01 [size of this packet, 2 bytes] [packet id, 1 byte] [size of total packet, 2 bytes] [data]
            # if it is the first of packets to be broken up, or
            # 02 [size of this packet, 2 bytes] [packet id, 1 byte] [order number, 2 bytes] [data]
            # if it is continuing
                        
            if len(data) <= self.tunnel.peer.mtu - 3:
                self.tunnel.receive_tap_data((0).to_bytes(1, byteorder='big') 
                    + len(data).to_bytes(2, byteorder='big') 
                    + data)
            else:
                chunksize = self.tunnel.peer.mtu - 6
                packets = []
                packets.append(bytearray())
                i = 0
                
                while i < len(data):
                    p = i//chunksize
                    
                    if packets[p] == None:
                        packets[p] = bytearray()
                    
                    packets[p].append(data[i])
                    i += 1
                
                self.tunnel.receive_tap_data((1).to_bytes(1, byteorder='big')
                    + len(packets[0]).to_bytes(2, byteorder='big') 
                    + self._packet_id.to_bytes(1, byteorder='big') 
                    + len(data).to_bytes(2, byteorder='big')
                    + bytes(packets[0]))
                    
                for i in range(1, len(packets)):
                    self.tunnel.receive_tap_data((1).to_bytes(1, byteorder='big')
                        + len(packets[i]).to_bytes(2, byteorder='big')
                        + self._packet_id.to_bytes(1, byteorder='big') 
                        + i.to_bytes(2, byteorder='big')
                        + bytes(packets[i]))
                    
                self._packet_id += 1
                if self._packet_id > 255:
                    self._packet_id = 0           


class Tunnel(threading.Thread):
    def __init__(self, peer):
        super(Tunnel, self).__init__()

        self.running = False
        self.input_thread = TunnelInputThread(self)

        self.peer = peer
        self.tun = TunTapDevice()
        self._data_to_process = Queue()
        self._data_to_send = Queue()

        self._packets_to_assemble = dict()

        self.tun.addr = '10.8.0.{0}'.format(self.peer.user_id)
        self.tun.dstaddr = '10.8.0.{0}'.format(self.peer.dest_id)
        self.tun.netmask = '255.255.255.0'
        self.tun.mtu = 65535
        self.tun.up()

        self.setDaemon(True)

    def run(self):
        while not self.peer.state == self.peer.State.connected:
            pass  # Wait for startup

        self.running = True
        self.input_thread.start()

        while reactor.running and self.peer.State.connected:
            while not self._data_to_process.empty():
                data = self._data_to_process.get()
                if len(data) <= 6:
                    print("Recieved packet that is too small")
                    continue
                
                magic = int.from_bytes(data[0:1], byteorder='big')
                size = int.from_bytes(data[1:3], byteorder='big')
                expected_size = len(data) - 3 if magic == 0 else len(data) - 6
                
                if (expected_size != size):
                    print("Packet does not contain the data that size field says it should:", expected_size, size)
                    continue
                
                if magic == 0:
                    self._inject_packet(bytes(data[3:]))
                elif magic == 1:
                    packet_id = int.from_bytes(data[3:4], byteorder='big')
                    packet = {}
                    packet["total_size"] = int.from_bytes(data[4:6], byteorder='big')
                    packet["pieces"] = {}
                    packet["pieces"][0] = data[6:]    
                    
                    if packet_id in self._packets_to_assemble:
                        print("Droping old packet as it was not assembled before a new packet with its id came")
                    
                    self._packets_to_assemble[packet_id] = packet                    
                elif magic == 2:
                    packet_id = int.from_bytes(data[3:4], byteorder='big')
                    order = int.from_bytes(data[4:6], byteorder='big')
                    
                    if not packet_id in self._packets_to_assemble:
                        print("Packet portion came in before the first packet - dropping")
                        continue
                    
                    self._packets_to_assemble[packet_id]["pieces"][order] = data[6:]
                    
                    current_length = 0
                    for piece in self._packets_to_assemble[packet_id]["pieces"]:
                        current_length += len(piece)
                    
                    if current_length == self._packets_to_assemble[packet_id]["total_size"]:
                        packet = bytearray()
                        for piece in self._packets_to_assemble[packet_id]["pieces"]:
                            packet.append(piece)
                        self._inject_packet(bytes(packet))
                else:
                    print("Invalid magic number for packet:", magic)                
                
            while not self._data_to_send.empty():
                data = self._data_to_send.get()
                reactor.callFromThread(PeerConnection.send_tunnel_data,
                                       self.peer, data)

        self.running = False
        self.tun.close()

    def receive_peer_data(self, data):
        """Handle incoming data from the peer"""
        self._data_to_process.put(data)

    def receive_tap_data(self, data):
        """Handle incoming data from this computer"""
        self._data_to_send.put(data)
    
    def _inject_packet(self, data):
        self.tun.write(data)
        print("Received data of length", len(data), ":", hashlib.md5(data).hexdigest(), ":", " ".join('{:02x}'.format(x) for x in data[:5]), "...", " ".join('{:02x}'.format(x) for x in data[-5:]))

