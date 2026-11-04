package org.interpss.dstab.svc;

import org.eclipse.emf.common.notify.impl.AdapterImpl;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;

/** Network-owned immutable ownership snapshot shared by prepared MSS boundaries. */
final class Svsmo3t2PeerRegistry extends AdapterImpl {
    private final BaseDStabNetwork<?, ?> network;
    private final BaseDStabBus<?, ?>[] buses;
    private final int[] offsets;
    private final Object[] devices;
    private final Svsmo3t2Model[] peers;

    private Svsmo3t2PeerRegistry(BaseDStabNetwork<?, ?> network) {
        this.network = network;
        int size = network.getBusList().size(), deviceCount = 0, peerCount = 0;
        buses = new BaseDStabBus<?, ?>[size];
        offsets = new int[size + 1];
        for (int i = 0; i < size; i++) {
            var bus = network.getBusList().get(i);
            buses[i] = bus;
            offsets[i] = deviceCount;
            var attached = bus.getDynamicBusDeviceList();
            deviceCount += attached.size();
            for (int j = 0; j < attached.size(); j++)
                if (attached.get(j) instanceof Svsmo3t2Model) peerCount++;
        }
        offsets[size] = deviceCount;
        devices = new Object[deviceCount];
        peers = new Svsmo3t2Model[peerCount];
        int nextPeer = 0;
        for (int i = 0; i < size; i++) {
            var attached = buses[i].getDynamicBusDeviceList();
            for (int j = 0; j < attached.size(); j++) {
                var device = attached.get(j);
                devices[offsets[i] + j] = device;
                if (device instanceof Svsmo3t2Model model) peers[nextPeer++] = model;
            }
        }
    }

    static Svsmo3t2PeerRegistry acquire(BaseDStabNetwork<?, ?> network) {
        Svsmo3t2PeerRegistry previous = null;
        for (var adapter : network.eAdapters())
            if (adapter instanceof Svsmo3t2PeerRegistry registry) { previous = registry; break; }
        if (previous != null && previous.network == network && previous.matchesOwnership()) return previous;
        var prepared = new Svsmo3t2PeerRegistry(network);
        if (previous != null) network.eAdapters().remove(previous);
        network.eAdapters().add(prepared);
        return prepared;
    }

    private boolean matchesOwnership() {
        var currentBuses = network.getBusList();
        if (currentBuses.size() != buses.length) return false;
        for (int i = 0; i < buses.length; i++) {
            if (currentBuses.get(i) != buses[i]) return false;
            var attached = buses[i].getDynamicBusDeviceList();
            if (attached.size() != offsets[i + 1] - offsets[i]) return false;
            for (int j = 0; j < attached.size(); j++)
                if (attached.get(j) != devices[offsets[i] + j]) return false;
        }
        return true;
    }

    void preflight() {
        if (!matchesOwnership())
            throw new IllegalStateException("Device ownership changed; reinitialize MSS before switching");
        for (int i = 0; i < buses.length; i++)
            for (int j = offsets[i]; j < offsets[i + 1]; j++)
                if (devices[j] instanceof Svsmo3t2Model peer) {
                    if (peer.getDStabBus() != buses[i])
                        throw new IllegalStateException("Controller host changed; reinitialize MSS before switching");
                    if (peer.isActive() && buses[i].isActive()) peer.checkNetworkEventReady();
                }
    }

    void acceptPreparedEvent() {
        for (var peer : peers)
            if (peer.isActive() && peer.getDStabBus().isActive()) peer.acceptPreparedNetworkEvent();
    }
}
