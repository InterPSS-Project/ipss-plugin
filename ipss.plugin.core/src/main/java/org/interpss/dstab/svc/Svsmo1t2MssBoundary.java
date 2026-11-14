package org.interpss.dstab.svc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.interpss.fadapter.builder.FixedShuntAdmittance;

import com.interpss.core.aclf.ShuntCompensator;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;

/** Accepted-step control of the individually named SVSMO1 fixed shunts. */
final class Svsmo1t2MssBoundary {
    private static final Logger LOG = LogManager.getLogger(Svsmo1t2MssBoundary.class);
    private static final double TIME_EPS = 1e-12;

    private static final class Bank {
        final int slot;
        final BaseDStabBus<?, ?> bus;
        final ShuntCompensator device;
        final Complex nominal;
        boolean active;
        double openedAt;

        Bank(int slot, BaseDStabBus<?, ?> bus, ShuntCompensator device, Complex nominal) {
            this.slot = slot;
            this.bus = bus;
            this.device = device;
            this.nominal = nominal;
        }
    }

    private final BaseDStabNetwork<?, ?> network;
    private final List<Bank> banks;
    private final double[] pickup = new double[4];
    private final double[] threshold;
    private final double[] delay;
    private final double breakerDelay;
    private final double dischargeDelay;
    private int pendingDirection;
    private double breakerDeadline;
    private double clock;
    private long operationCount;

    Svsmo1t2MssBoundary(BaseDStabBus<?, ?> host, String controllerId, Svsmo1t2Data data) {
        network = (BaseDStabNetwork<?, ?>) host.getNetwork();
        double base = network.getBaseMva();
        // Use susceptance in system pu. The DYR represents thresholds in
        // MVAr at one pu voltage, independent of the instantaneous SVC VAr.
        threshold = new double[] {data.bLargeCapMvar()/base, data.bLargeIndMvar()/base,
                data.bSmallCapMvar()/base, data.bSmallIndMvar()/base};
        delay = new double[] {data.largeDelay(), data.largeDelay(), data.smallDelay(), data.smallDelay()};
        breakerDelay = data.mssBreakerTime();
        dischargeDelay = data.capacitorDischargeTime();
        banks = resolveBanks(host, controllerId, data);
    }

    private List<Bank> resolveBanks(BaseDStabBus<?, ?> host, String controllerId, Svsmo1t2Data data) {
        if (data.mssSwitchingFlag() == 0) return List.of();
        var result = new ArrayList<Bank>();
        var seen = new HashSet<Svsmo1t2Data.MssDevice>();
        for (int i = 0; i < data.mssDevices().size(); i++) {
            var key = data.mssDevices().get(i);
            if (key.busNumber() == 0) continue;
            if (!seen.add(key)) {
                LOG.warn("SVSMO1 {} at {} ignores duplicate MSS slot {}: {}/{}",
                        controllerId, host.getId(), i + 1, key.busNumber(), key.id());
                continue;
            }
            var bus = network.getDStabBus("Bus" + key.busNumber());
            var devices = bus == null ? List.<ShuntCompensator>of() : bus.getCompensatorList().stream()
                    .filter(device -> key.id().equals(device.getId())).toList();
            var device = devices.size() == 1 ? devices.getFirst() : null;
            var nominal = FixedShuntAdmittance.find(device).orElse(Complex.ZERO);
            if (nominal.getImaginary() == 0.) {
                LOG.warn("SVSMO1 {} at {} ignores MSS slot {}: no unique fixed shunt with nominal B at {}/{}",
                        controllerId, host.getId(), i + 1, key.busNumber(), key.id());
                continue;
            }
            result.add(new Bank(i + 1, bus, device, nominal));
        }
        return List.copyOf(result);
    }

    void initialize() {
        for (Bank bank : banks) {
            bank.active = bank.device.isActive();
            // An initially open bank is available without a simulated
            // discharge interval. Track the interval only once it opens.
            bank.openedAt = Double.NEGATIVE_INFINITY;
        }
        clock = 0.;
        operationCount = 0;
        resetRequest();
    }

    void advance(double dt, double susceptance) {
        if (banks.isEmpty() || dt == 0.) return;
        if (!Double.isFinite(dt) || dt < 0. || !Double.isFinite(susceptance)) {
            throw new IllegalArgumentException("Finite SVSMO1 MSS timestep and susceptance required");
        }
        clock += dt;
        for (Bank bank : banks) {
            boolean inService = bank.device.isActive();
            if (bank.active && !inService) bank.openedAt = clock;
            bank.active = inService;
        }
        updatePickup(susceptance);
        if (pendingDirection == 0) latchRequest();
        if (pendingDirection != 0 && clock + TIME_EPS >= breakerDeadline) operate();
    }

    private void updatePickup(double susceptance) {
        for (int i = 0; i < pickup.length; i++) {
            boolean demand = i % 2 == 0 ? susceptance > threshold[i] : susceptance < threshold[i];
            if (!demand) pickup[i] = Double.POSITIVE_INFINITY;
            else if (!Double.isFinite(pickup[i])) pickup[i] = clock;
        }
    }

    private void latchRequest() {
        for (int i = 0; i < pickup.length; i++) {
            double deadline = pickup[i] + delay[i];
            if (clock + TIME_EPS >= deadline) {
                pendingDirection = i % 2 == 0 ? 1 : -1;
                // Round only the physical endpoint, preserving the elapsed
                // pickup interval while the breaker is travelling.
                breakerDeadline = deadline + breakerDelay;
                return;
            }
        }
    }

    private void operate() {
        for (Bank bank : banks) {
            double b = bank.nominal.getImaginary();
            double deltaB = bank.active ? -b : b;
            if (deltaB * pendingDirection <= 0. || !bank.bus.isActive()) continue;
            if (b > 0. && !bank.active && clock + TIME_EPS < bank.openedAt + dischargeDelay) continue;
            FixedMssBank.apply(network, bank.bus, bank.device, bank.nominal, !bank.active);
            bank.active = !bank.active;
            if (!bank.active) bank.openedAt = clock;
            operationCount++;
            // A reactor insertion retains the qualified negative demand,
            // allowing another ready slot to operate at the next endpoint.
            if (b > 0. || pendingDirection > 0) resetRequest();
            return;
        }
        resetRequest();
    }

    private void resetRequest() {
        Arrays.fill(pickup, Double.POSITIVE_INFINITY);
        pendingDirection = 0;
        breakerDeadline = Double.POSITIVE_INFINITY;
    }

    Map<String, Double> getNamedStates() {
        if (banks.isEmpty()) return Map.of();
        var result = new LinkedHashMap<String, Double>();
        result.put("time", clock);
        result.put("pendingDirection", (double) pendingDirection);
        result.put("operations", (double) operationCount);
        for (Bank bank : banks) {
            result.put("slot." + bank.slot + ".inService", bank.device.isActive() ? 1. : 0.);
        }
        return Map.copyOf(result);
    }
}
