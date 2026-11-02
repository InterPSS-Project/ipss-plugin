package org.interpss.dstab.control.gov.wecc.urgs3t;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;
import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.IntegrationStateMonitor.Stage;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;
import org.interpss.numeric.datatype.Unit.UnitType;

/** WECC gas governor with shared numerical states and accepted deadband histories. */
public final class Urgs3tGovernor extends AbstractGovernor
        implements ICMLStateProvider, StaggeredEulerParticipant, IntegrationStateAccess {
    /** Source-record execution holds the exhaust coordinate, publishes the
     * preceding accepted turbine output, and scales damping with turbine power.
     * Direct Java construction retains the published diagram and machine-base damping. */
    public enum RuntimeConvention { PUBLISHED_DIAGRAM, SOURCE_RECORD }
    public static final List<String> CONSTANT_NAMES = List.of("R", "T1", "T2", "T3", "Lmax", "Kt",
            "Vmax", "Vmin", "Dturb", "Fidle", "Rmax", "Linc", "Tltr", "Ltrat", "A", "B",
            "Db1", "Err", "Db2", "Gv1", "Pgv1", "Gv2", "Pgv2", "Gv3", "Pgv3",
            "Gv4", "Pgv4", "Gv5", "Pgv5", "Ka", "T4", "T5", "MWCap");
    private static final String[] NAMES = {"Governor output", "Engine state", "Exhaust temperature",
            "Valve average", "Governor lead lag"};
    private final double[] c;
    private final RuntimeConvention runtimeConvention;
    private final double[] x = new double[5], old = new double[5], d0 = new double[5], d1 = new double[5];
    private final double[] referenceLeftRates = new double[5];
    private final double[] curveX = new double[5], curveY = new double[5];
    private final IntegrationStateMonitor monitor = new IntegrationStateMonitor(NAMES);
    private int points, mode;
    private double scale, reference, initialReference, lower, upper, powerMemory, powerInputMemory, initialSpeed;
    private double acceptedTurbinePower, publishedTurbinePower;
    private Machine initializedMachine;
    private boolean initialized, predicted, referenceJumpPending;
    private long evaluations, referenceRevision;

    public Urgs3tGovernor(String id, double[] constants) {
        this(id, constants, RuntimeConvention.PUBLISHED_DIAGRAM);
    }

    public Urgs3tGovernor(String id, double[] constants, RuntimeConvention runtimeConvention) {
        super(id, "URGS3T", "WECC");
        this.runtimeConvention = Objects.requireNonNull(runtimeConvention);
        if (constants == null || constants.length != 33)
            throw new IllegalArgumentException("URGS3T requires exactly 33 constants");
        c = constants.clone();
        for (double v : c) if (!Double.isFinite(v)) throw new IllegalArgumentException("URGS3T constants must be finite");
        if (c[0] <= 0 || c[1] <= 0 || c[2] <= 0 || c[3] <= 0 || c[4] <= 0
                || c[6] < c[7] || c[10] < 0 || c[11] <= 0 || c[12] <= 0 || c[13] < 0
                || c[14] < 0 || c[15] <= 0 || c[16] < 0 || c[17] < 0 || c[17] > c[16]
                || c[18] < 0 || c[29] < 0 || c[30] < 0 || c[31] < 0 || c[32] <= 0
                || (c[29] > 0 && c[31] <= 0))
            throw new IllegalArgumentException("Invalid URGS3T time, rating, droop, deadband or limit settings");
        for (int i = 0; i < 5; i++) {
            double a = c[19 + 2*i], b = c[20 + 2*i];
            if (points > 0 && a == 0 && b == 0) continue;
            if (points > 0 && a == curveX[points-1] && b == curveY[points-1]) continue;
            if (points > 0 && (a <= curveX[points-1] || b <= curveY[points-1]))
                throw new IllegalArgumentException("URGS3T valve curve must be strictly increasing");
            curveX[points] = a; curveY[points++] = b;
        }
        if (points < 2) throw new IllegalArgumentException("URGS3T requires an invertible valve curve");
    }

    public double[] getConstants() { return c.clone(); }
    public RuntimeConvention getRuntimeConvention() { return runtimeConvention; }
    public long getStaggeredEvaluationCount() { return evaluations; }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        initialized = predicted = false; monitor.reset(); evaluations = 0;
        if (machine == null || machine != getMachine()) return false;
        double mva = machine.getRating(UnitType.mVA, bus.getNetwork().getBaseKva());
        if (!Double.isFinite(mva) || mva <= 0 || !Double.isFinite(machine.getPm())
                || !Double.isFinite(machine.getSpeed())) return false;
        scale = c[32]/mva;
        if (!Double.isFinite(scale) || scale <= 0) return false;
        double power = machine.getPm()/scale;
        double engine = interpolate(power, curveY, curveX);
        double valve = engine + c[9];
        x[0] = valve; x[1] = (1 - c[14]/c[15])*engine;
        x[2] = engine; x[3] = valve;
        x[4] = c[29] > 0 ? (1 - c[30]/c[31])*valve : 0;
        lower = Math.min(c[7], valve); upper = Math.max(c[6], valve);
        reference = c[29] > 0 ? valve/c[29] : valve;
        initialReference = reference; referenceJumpPending = false; referenceRevision = 0;
        powerMemory = powerInputMemory = curvePower(x); mode = 0; initialSpeed = machine.getSpeed()-1;
        acceptedTurbinePower = publishedTurbinePower = power;
        if (!Double.isFinite(reference) || !Double.isFinite(power)) return false;
        for (double state : x) if (!Double.isFinite(state)) return false;
        initializedMachine = machine; initialized = true;
        derivatives(x, d0);
        for (double rate : d0) if (!Double.isFinite(rate)) { initialized = false; return false; }
        retain(d0, Stage.INITIALIZATION);
        return true;
    }

    private void requireState() {
        if (!initialized || initializedMachine != getMachine() || getMachine().getGovernor() != this)
            throw new IllegalStateException("Initialize URGS3T with its current machine before integration");
    }
    private static void requireStep(double h) {
        if (!Double.isFinite(h) || h < 0) throw new IllegalArgumentException("URGS3T step must be finite and nonnegative");
    }
    private void shared(double[] values, int offset, double h) {
        requireState(); requireStep(h); Objects.checkFromIndexSize(offset, 5, values.length);
        if (predicted) throw new IllegalStateException("Finish the modified-Euler predictor before shared integration");
    }
    @Override public boolean nextStep(double h, DynamicSimuMethod method, Machine machine, int flag) {
        requireState(); requireStep(h);
        if (machine != initializedMachine || method != DynamicSimuMethod.MODIFIED_EULER || (flag != 0 && flag != 1))
            throw new IllegalArgumentException("URGS3T requires its initialized machine and modified-Euler stages");
        if (h == 0) { derivatives(x, d1); retain(d1, flag == 0 ? Stage.PREDICTOR : Stage.CORRECTOR); return true; }
        if (flag == 0) {
            if (predicted) throw new IllegalStateException("URGS3T predictor already pending");
            System.arraycopy(x, 0, old, 0, 5); derivatives(x, d0); retain(d0, Stage.PREDICTOR);
            for (int i = 0; i < 5; i++) x[i] = old[i] + h*d0[i];
            project(); predicted = true;
        } else {
            if (!predicted) throw new IllegalStateException("URGS3T corrector requires its predictor");
            derivatives(x, d1); retain(d1, Stage.CORRECTOR);
            for (int i = 0; i < 5; i++) x[i] = old[i] + .5*h*(d0[i] + d1[i]);
            project(); commit(); predicted = false;
        }
        return true;
    }
    @Override public int staggeredStateCount() { return 5; }
    @Override public void readStaggeredState(double[] values, int offset) {
        shared(values, offset, 0); System.arraycopy(x, 0, values, offset, 5);
    }
    @Override public void evaluateStaggered(double h, double[] rates, int offset) {
        shared(rates, offset, h); derivatives(x, d1); retain(d1, Stage.STAGGERED);
        System.arraycopy(d1, 0, rates, offset, 5); evaluations++;
    }
    @Override public void applyStaggeredState(double[] values, int offset, double h) {
        shared(values, offset, h);
        for (int i = 0; i < 5; i++) if (!Double.isFinite(values[offset+i]))
            throw new IllegalArgumentException("URGS3T states must be finite");
        System.arraycopy(values, offset, x, 0, 5); project();
    }
    @Override public void completeStaggeredStep(double h) {
        requireState(); requireStep(h);
        if (predicted) throw new IllegalStateException("URGS3T predictor pending");
        if (h > 0) commit();
    }
    @Override public boolean useAcceptedEndpointRateAtNetworkDiscontinuity() { return true; }
    @Override public boolean useAcceptedEndpointRateAtInputDiscontinuity() { return true; }

    private int deadbandMode(double u) {
        if (mode == 0) return u > c[16] ? 1 : u < -c[16] ? -1 : 0;
        if (mode > 0 && u <= c[17]) return u < -c[16] ? -1 : 0;
        if (mode < 0 && u >= -c[17]) return u > c[16] ? 1 : 0;
        return mode;
    }
    private double speedInput() {
        double u = getMachine().getSpeed() - 1;
        int m = deadbandMode(u);
        return m > 0 ? u - c[17] : m < 0 ? u + c[17] : 0;
    }
    private double engine(double[] s) { return s[1] + c[14]/c[15]*(s[0] - c[9]); }
    private double curvePower(double[] s) { return interpolate(engine(s), curveX, curveY); }
    private double backlash(double power) {
        if (runtimeConvention == RuntimeConvention.SOURCE_RECORD)
            return power > powerMemory ? power-c[18] : power < powerMemory ? power+c[18] : powerMemory;
        return Math.max(power-c[18], Math.min(power+c[18], powerMemory));
    }
    private void commit() {
        double power = curvePower(x), output = backlash(power);
        publishedTurbinePower = acceptedTurbinePower; acceptedTurbinePower = output;
        if (output != powerMemory) powerInputMemory = power;
        powerMemory = output; mode = deadbandMode(getMachine().getSpeed()-1);
        referenceJumpPending = false;
    }
    private void project() { x[0] = Math.max(lower, Math.min(upper, x[0])); }
    private void derivatives(double[] s, double[] rates) {
        // The droop path acts directly on speed deviation; rating conversion is at the output.
        double input = reference - speedInput()/c[0];
        double demand = c[29] > 0 ? s[4] + c[29]*c[30]/c[31]*input : input;
        double temperatureLimit = c[9] + c[4] + c[5]*(c[4]-s[2]);
        double upperRate = s[0]-s[3] > c[11] ? c[13] : c[10];
        double rate = Math.min(upperRate, Math.min(demand, temperatureLimit)-s[0])/c[1];
        rates[0] = (s[0] >= upper && rate > 0) || (s[0] <= lower && rate < 0) ? 0 : rate;
        rates[1] = ((1-c[14]/c[15])*(s[0]-c[9])-s[1])/(c[15]*c[2]);
        rates[2] = runtimeConvention == RuntimeConvention.SOURCE_RECORD ? 0 : (engine(s)-s[2])/c[3];
        rates[3] = (s[0]-s[3])/c[12];
        rates[4] = c[29] > 0 ? (c[29]*(1-c[30]/c[31])*input-s[4])/c[31] : 0;
    }
    private double interpolate(double value, double[] abscissa, double[] ordinate) {
        int hi = 1;
        while (hi < points-1 && value > abscissa[hi]) hi++;
        return ordinate[hi-1] + (value-abscissa[hi-1])*(ordinate[hi]-ordinate[hi-1])/(abscissa[hi]-abscissa[hi-1]);
    }
    private void retain(double[] rates, Stage stage) {
        monitor.begin(stage);
        for (int i = 0; i < 5; i++) monitor.record(i, x[i], rates[i]);
    }
    @Override public double getOutput(Machine machine) {
        requireState();
        if (machine != initializedMachine) throw new IllegalArgumentException("URGS3T output requires its initialized machine");
        return runtimeConvention == RuntimeConvention.SOURCE_RECORD
                ? scale*(publishedTurbinePower-c[8]*(machine.getSpeed()-1))
                : scale*backlash(curvePower(x)) - c[8]*(machine.getSpeed()-1);
    }
    @Override public void setRefPoint(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("URGS3T reference must be finite");
        reference = value;
    }
    /** A discrete command preserves its accepted left derivative. Continuous
     * upstream controls use {@link #setRefPoint(double)} instead. */
    public void applyReferenceEvent(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("URGS3T reference event must be finite");
        if (value == reference) return;
        if (initialized) {
            requireState();
            if (predicted) throw new IllegalStateException("Finish the predictor before a reference event");
            if (!referenceJumpPending) derivatives(x, referenceLeftRates);
            referenceJumpPending = true; referenceRevision++;
        }
        reference = value;
    }
    @Override public long staggeredInputDiscontinuityVersion() { return referenceRevision; }
    @Override public boolean readStaggeredInputDiscontinuityLeftRates(double[] rates, int offset) {
        shared(rates, offset, 0);
        if (!referenceJumpPending) return false;
        System.arraycopy(referenceLeftRates, 0, rates, offset, 5); return true;
    }
    public double getRefPoint() { return reference; }
    /** Pure observations of the reference, deadband input/output and turbine curve. */
    public void readPublishedVariables(double[] values, int offset) {
        requireState(); Objects.checkFromIndexSize(offset, 5, values.length);
        values[offset] = initialReference; values[offset+1] = initialSpeed; values[offset+2] = speedInput();
        values[offset+3] = curvePower(x); values[offset+4] = backlash(values[offset+3]);
    }
    @Override public Map<String, Double> getNamedStates() { return getIntegrationStates(); }
    @Override public Map<String, Double> getIntegrationStates() {
        var result = new LinkedHashMap<String, Double>();
        for (int i = 0; i < 5; i++) result.put(NAMES[i], x[i]);
        return Map.copyOf(result);
    }
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot() { return monitor.snapshot(); }
    @Override public Map<String, Double> getIntegrationHistories() {
        return Map.of("Power deadband memory", powerMemory, "Power input memory", powerInputMemory,
                "Accepted turbine power", acceptedTurbinePower, "Published turbine power", publishedTurbinePower,
                "Speed deadband mode", (double)mode, "Prepared lower valve limit", lower, "Prepared upper valve limit", upper);
    }
}
