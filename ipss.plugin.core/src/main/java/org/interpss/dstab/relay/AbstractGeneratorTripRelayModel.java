package org.interpss.dstab.relay;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.DynamicBusDeviceType;
import com.interpss.dstab.device.DynamicDevice;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;
import com.interpss.dstab.relay.IRelayModel;

/**
 * Shared PSS/E FRQTPAT/VTGTPAT generator-trip lifecycle.
 *
 * <p>The solver calls {@code nextStep} for both modified-Euler stages but calls
 * {@code afterStep} exactly once for each accepted step. Relay timing is thus
 * advanced in {@code afterStep}, preventing a two-times-fast timer. Pickup is
 * reset while the measured signal returns inside the band; once picked up, the
 * breaker delay is latched and runs to completion.</p>
 */
public abstract class AbstractGeneratorTripRelayModel extends DynamicBusDeviceImpl
        implements IRelayModel, ICMLStateProvider, com.interpss.dstab.controller.runtime.AcceptedStepDevice {
    @Override public final long getAcceptedNetworkActionVersion() { return physicalTripPerformed ? 1L : 0L; }
    private static final Logger log = LoggerFactory.getLogger(AbstractGeneratorTripRelayModel.class);
    private static final double TIME_EPS = 1.0e-12;

    public static final String STATE_TIMER_MEMORY = "Timer memory";
    public static final String STATUS_PICKED_UP = "RelayPickedUp";
    public static final String STATUS_TRIPPED = "RelayTripped";
    public static final String STATUS_ACTION_TIME = "RelayActionTime";

    private final String modelName;
    private final GeneratorTripRelayData data;
    private final BaseDStabBus<?, ?> monitoredBus;
    private final BaseDStabBus<?, ?> targetBus;
    private final DStabGen targetGenerator;
    private final Hashtable<String, Object> states = new Hashtable<>();

    private double timerMemory;
    private double elapsedTime;
    private double breakerElapsedTime;
    private double lastAcceptedStep;
    private double actionTime = Double.NaN;
    private boolean intervalBeganInBand;
    private boolean pickedUp;
    private boolean tripped;
    private boolean physicalTripPerformed;

    protected AbstractGeneratorTripRelayModel(String modelName, int sourceBusNumber,
            BaseDStabBus<?, ?> monitoredBus, BaseDStabBus<?, ?> targetBus,
            DStabGen targetGenerator, GeneratorTripRelayData data) {
        if (monitoredBus == null || targetBus == null || targetGenerator == null) {
            throw new IllegalArgumentException("generator-trip relay buses and target generator are required");
        }
        this.modelName = modelName;
        this.monitoredBus = monitoredBus;
        this.targetBus = targetBus;
        this.targetGenerator = targetGenerator;
        this.data = data;
        String relayId = modelName + "_" + sourceBusNumber + "_"
                + monitoredBus.getId() + "_" + targetBus.getId() + "_" + targetGenerator.getId();
        setId(relayId);
        setName(modelName);
        setExtendedDeviceId(relayId);
        setDeviceType(DynamicBusDeviceType.DYNAMIC_LOAD);
        setDStabBus(monitoredBus);
        monitoredBus.getDynamicBusDeviceList().add(this);
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID, relayId);
    }

    public final GeneratorTripRelayData getData() {
        return data;
    }

    public final BaseDStabBus<?, ?> getMonitoredBus() {
        return monitoredBus;
    }

    public final BaseDStabBus<?, ?> getTargetBus() {
        return targetBus;
    }

    public final DStabGen getTargetGenerator() {
        return targetGenerator;
    }

    public final boolean isPickedUp() {
        return pickedUp;
    }

    /** An operation can be a no-op when another relay or event already removed
     * the target. Retain that distinction for causal equipment-loss diagnostics. */
    public final boolean didDisconnectTarget() { return physicalTripPerformed; }

    public final boolean isTripped() {
        return tripped;
    }

    public final double getTimerMemory() {
        return timerMemory;
    }

    public final double getActionTime() {
        return actionTime;
    }

    /** Current monitored value in the native units of the PSS/E thresholds. */
    public abstract double getMonitoredValue();

    @Override
    public boolean initStates(BaseDStabBus<?, ?> bus) {
        if (bus != monitoredBus || monitoredBus.getNetwork() == null
                || targetBus.getNetwork() != monitoredBus.getNetwork()) {
            return false;
        }
        reset();
        double value=getMonitoredValue();
        if(!Double.isFinite(value))return false;
        // A source case can start outside an entered protection band. Retain its
        // thresholds and timer behavior, but expose that even an undisturbed run
        // can operate this relay; do not silently enlarge a supplied limit.
        if(targetGenerator.isActive()&&(value<data.lowerThreshold()||value>data.upperThreshold()))
            log.warn("{} {} initializes outside its protection band: monitoredBus={}, target={}/{}, value={}, band=[{}, {}], pickup={} s, breaker={} s",
                modelName,getId(),monitoredBus.getId(),targetBus.getId(),targetGenerator.getId(),value,
                data.lowerThreshold(),data.upperThreshold(),data.pickupTime(),data.breakerTime());
        return true;
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        if (!Double.isFinite(dt) || dt < 0.0) return false;
        if (flag == 0 && dt > 0.0 && !pickedUp && !tripped) {
            // The predictor starts from the accepted or post-event sample. If
            // the next outside sample occurs at the corrector endpoint, pickup
            // starts there and the preceding interval contributes no time.
            intervalBeganInBand = !isOutsideBand(getMonitoredValue());
        }
        return true;
    }

    private boolean isOutsideBand(double value) {
        return value < data.lowerThreshold() || value > data.upperThreshold();
    }

    @Override
    public boolean afterStep(double dt) {
        if (!Double.isFinite(dt) || dt < 0.0) return false;
        // Algebraic event-side solves do not integrate a relay or consume its
        // next positive time interval. Predictor/corrector calls likewise leave
        // the timer untouched; only an accepted physical step reaches here.
        if (dt == 0.0 || tripped) return true;
        double value = getMonitoredValue();
        if (!Double.isFinite(value)) return false;
        elapsedTime += dt;
        lastAcceptedStep = dt;
        boolean outsideBand = isOutsideBand(value);
        boolean beganInBand = intervalBeganInBand;
        intervalBeganInBand = false;

        if (!pickedUp) {
            if (!outsideBand || beganInBand) {
                timerMemory = 0.0;
                breakerElapsedTime = 0.0;
                if (!outsideBand || data.pickupTime() > 0.0) return true;
            }
            double outsideInterval = beganInBand ? 0.0 : dt;
            timerMemory += outsideInterval;
            if (!delayReached(timerMemory, data.pickupTime(), dt)) return true;
            pickedUp = true;
            // A positive pickup stage ends at this accepted endpoint. Its
            // rounding remainder is not elapsed breaker time. With zero
            // pickup, credit only the time since the first outside sample.
            breakerElapsedTime = data.pickupTime() == 0.0 ? outsideInterval : 0.0;
        } else {
            timerMemory += dt;
            breakerElapsedTime += dt;
        }

        if (data.breakerTime() == 0.0
                || breakerElapsedTime > 0.0
                    && delayReached(breakerElapsedTime, data.breakerTime(), dt)) {
            return action(elapsedTime);
        }
        return true;
    }

    /**
     * The sampled relay selects the nearest accepted tick independently for
     * pickup and breaker delay. Strictly waiting until elapsed >= delay adds a
     * whole tick for 6 * 0.0033333 < 0.02, and summing both entered delays also
     * changes two independently rounded stages (20.4+20.4 ms at 1 ms is 40 ms;
     * 20.6+20.6 ms is 42 ms). The half-current-step comparison implements that
     * sampled contract without modifying either source parameter or advancing
     * time during an algebraic solve. TIME_EPS only covers double arithmetic;
     * it is not a tunable timer tolerance. A positive stage still needs one
     * accepted interval even when its entered delay is less than half a step.
     */
    private static boolean delayReached(double elapsed, double delay, double dt) {
        return elapsed + 0.5 * dt + TIME_EPS >= delay;
    }

    @Override
    public boolean action(double time) {
        if (tripped) return true;
        // Independent threshold rows keep independent operation histories, but
        // a second relay must not repeat an already completed physical trip or
        // rebuild the matrix (which would introduce another angle discontinuity).
        if(!targetGenerator.isActive()) {
            tripped=true;actionTime=time;
            log.info("{} {} operated for already-disconnected generator {}/{} at t={} s",
                modelName,getId(),targetBus.getId(),targetGenerator.getId(),time);
            return true;
        }
        var previous=targetBus.captureGeneratorStatusChange();
        DynamicDevice dynamicDevice = targetGenerator.getDynamicGenDevice();
        targetGenerator.setStatus(false);
        if (dynamicDevice != null) dynamicDevice.setStatus(false);
        // Use the same sequence-cache transition as a scheduled generator trip.
        // Reinitializing all equivalents here reconverts unrelated colocated load
        // at its disturbed voltage, changing more than the tripped Norton source.
        targetBus.refreshGeneratorStatusChange(previous);
        @SuppressWarnings("unchecked")
        BaseDStabNetwork<?, ?> network = (BaseDStabNetwork<?, ?>) targetBus.getNetwork();
        try {
            // Rebuilding a bare no-fault matrix would clear an ongoing bus/branch
            // fault before its scheduled clearing time. Restore every active fault
            // through the established topology rebuild, without replaying actions.
            new com.interpss.dstab.algo.defaultImpl.DynamicEventProcessor().rebuildCurrentTopology(network);
        } catch(org.interpss.numeric.exp.IpssNumericException failure) {
            log.error("Could not restore active faults after relay {} disconnected {}/{}",getId(),
                targetBus.getId(),targetGenerator.getId(),failure);
            return false; // The physical step must fail; no partially advanced retry.
        }
        physicalTripPerformed=true;
        tripped = true;
        actionTime = time;
        log.info("{} tripped generator {}/{} at t={} s", modelName,
                targetBus.getId(), targetGenerator.getId(), time);
        return true;
    }

    @Override
    public boolean isActionTime(double time) {
        return !tripped && pickedUp
                && (data.breakerTime() == 0.0 || breakerElapsedTime > 0.0
                    && delayReached(breakerElapsedTime, data.breakerTime(), lastAcceptedStep));
    }

    @Override
    public boolean reset() {
        timerMemory = 0.0;
        elapsedTime = 0.0;
        breakerElapsedTime = 0.0;
        lastAcceptedStep = 0.0;
        actionTime = Double.NaN;
        intervalBeganInBand = false;
        pickedUp = false;
        tripped = false;
        physicalTripPerformed=false;
        return true;
    }

    @Override
    public Object getOutputObject() {
        return Complex.ZERO;
    }

    @Override
    public boolean updateAttributes(boolean netChange) {
        return true;
    }

    @Override
    public Map<String, Double> getNamedStates() {
        Map<String, Double> snapshot = new LinkedHashMap<>();
        snapshot.put(STATE_TIMER_MEMORY, timerMemory);
        return Map.copyOf(snapshot);
    }

    @Override
    public Hashtable<String, Object> getStates(Object ref) {
        states.put(STATE_TIMER_MEMORY, timerMemory);
        states.put(STATUS_PICKED_UP, pickedUp);
        states.put(STATUS_TRIPPED, tripped);
        states.put(STATUS_ACTION_TIME, actionTime);
        return states;
    }
}
