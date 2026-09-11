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
        implements IRelayModel, ICMLStateProvider {
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
    private double actionTime = Double.NaN;
    private boolean pickedUp;
    private boolean tripped;

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
        return Double.isFinite(getMonitoredValue());
    }

    @Override
    public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        return Double.isFinite(dt) && dt >= 0.0;
    }

    @Override
    public boolean afterStep(double dt) {
        if (!Double.isFinite(dt) || dt < 0.0) return false;
        elapsedTime += dt;
        if (tripped) return true;

        double value = getMonitoredValue();
        if (!Double.isFinite(value)) return false;
        boolean outsideBand = value < data.lowerThreshold() || value > data.upperThreshold();

        if (!pickedUp) {
            if (!outsideBand) {
                timerMemory = 0.0;
                return true;
            }
            timerMemory += dt;
            if (timerMemory + TIME_EPS >= data.pickupTime()) pickedUp = true;
        } else {
            timerMemory += dt;
        }

        if (pickedUp && timerMemory + TIME_EPS >= data.pickupTime() + data.breakerTime()) {
            return action(elapsedTime);
        }
        return true;
    }

    @Override
    public boolean action(double time) {
        if (tripped) return true;
        DynamicDevice dynamicDevice = targetGenerator.getDynamicGenDevice();
        targetGenerator.setStatus(false);
        if (dynamicDevice != null) dynamicDevice.setStatus(false);
        targetBus.resetSeqEquivLoad();
        @SuppressWarnings("unchecked")
        BaseDStabNetwork<?, ?> network = (BaseDStabNetwork<?, ?>) targetBus.getNetwork();
        network.formYMatrix4DStab();
        network.setYMatrixDirty(true);
        tripped = true;
        actionTime = time;
        log.info("{} tripped generator {}/{} at t={} s", modelName,
                targetBus.getId(), targetGenerator.getId(), time);
        return true;
    }

    @Override
    public boolean isActionTime(double time) {
        return !tripped && pickedUp
                && timerMemory + TIME_EPS >= data.pickupTime() + data.breakerTime();
    }

    @Override
    public boolean reset() {
        timerMemory = 0.0;
        elapsedTime = 0.0;
        actionTime = Double.NaN;
        pickedUp = false;
        tripped = false;
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
