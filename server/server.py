#!/usr/bin/python3
import json
import uuid

from twisted.internet import reactor, protocol


mappings = dict()


class Echo(protocol.DatagramProtocol):

    def datagramReceived(self, data, addr):
        data = data.decode('ascii')
        print("Received packet from ", addr, ":", data)
        if not data.startswith("map:"):
            return

        id = data[4:]
        if len(id) == 0:
            return

        print("id:", id)

        mapping = {
            "id": id,
            "host": addr[0],
            "port": addr[1]
        }
        mappings[id] = mapping
        self.transport.write(json.dumps(mappings).encode('ascii'), addr)


reactor.listenUDP(40000, Echo())
reactor.run()
