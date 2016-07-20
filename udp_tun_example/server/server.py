#!/usr/bin/python3
import json
import uuid

import time
from twisted.internet import reactor, protocol


mappings = dict()
timeout_seconds = 5


class Echo(protocol.DatagramProtocol):

    def datagramReceived(self, data, addr):
        data = data.decode('ascii')
        if not data.startswith("map:"):
            print("Rejecting packet from", addr)
            return

        id = data[4:]
        if len(id) == 0:
            return

        mapping = {
            "id": id,
            "host": addr[0],
            "port": addr[1],
            "time": time.time()
        }
        mappings[id] = mapping

        print("Mapped", addr, "to", id)

        expired = {k:v for k,v in mappings.items() if time.time()-v["time"] > timeout_seconds}
        for id in expired:
            del mappings[id]
            print("Lost connection to", id)

        self.transport.write(json.dumps(mappings).encode('ascii'), addr)


reactor.listenUDP(40000, Echo())
reactor.run()
