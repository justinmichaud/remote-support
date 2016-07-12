import threading
from queue import Queue

from twisted.internet import reactor

from peer import PeerConnection


class TunnelInputThread(threading.Thread):
    def __init__(self, tunnel):
        super(TunnelInputThread, self).__init__()
        self.tunnel = tunnel
        self.setDaemon(True)

    def run(self):
        while self.tunnel.running:
            text = input(">")
            self.tunnel.receive_tap_data(text.encode('ascii'))


class Tunnel(threading.Thread):
    def __init__(self, peer):
        super(Tunnel, self).__init__()

        self.running = True
        self.input_thread = TunnelInputThread(self)

        self._peer = peer
        self._data_to_process = Queue()
        self._data_to_send = Queue()

        self.setDaemon(True)

    def run(self):
        while not self._peer.state == self._peer.State.connected:
            pass  # Wait for startup

        self.input_thread.start()

        while reactor.running and self._peer.State.connected:
            while not self._data_to_process.empty():
                print("{0}: {1}".format(self._peer.dest_id, self._data_to_process.get().decode('ascii')))

            while not self._data_to_send.empty():
                data = self._data_to_send.get()
                reactor.callFromThread(PeerConnection.send_tunnel_data,
                                       self._peer, data)

        self.running = False

    def receive_tunnel_data(self, data):
        """Handle incoming data from the peer"""
        self._data_to_process.put(data)

    def receive_tap_data(self, data):
        """Handle incoming data from this computer"""
        self._data_to_send.put(data)

