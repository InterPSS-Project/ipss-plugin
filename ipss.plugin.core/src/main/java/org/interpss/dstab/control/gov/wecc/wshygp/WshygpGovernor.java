package org.interpss.dstab.control.gov.wecc.wshygp;

import java.util.LinkedHashMap;
import java.util.Map;

import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;

/** WECC GP hydro-governor compatibility realization. */
public final class WshygpGovernor extends AbstractGovernor implements ICMLStateProvider {
    private static final double EPS = 1.0e-10;
    private double[] preparedCurve;
    private State state = State.zero();
    private State oldState = State.zero();
    private Derivative predictor = Derivative.zero();
    private double reference;
    private double baseScale = 1.0;
    private double output;
    private boolean initialized;
    private double effectiveGateMaximum;

    public WshygpGovernor(String id, String name, String category) {
        super(id, name, category);
        _data = new WshygpGovernorData();
    }
    public WshygpGovernorData getData() { return (WshygpGovernorData) _data; }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        initialized = false;
        if (!validateParameters()) return false;
        effectiveGateMaximum = c(13);
        double machineMva = machine.getRating(UnitType.mVA,
                bus.getNetwork().getBaseKva());
        double trate = c(29);
        baseScale = trate > EPS && machineMva > EPS ? trate / machineMva : 1.0;
        double pm0 = machine.getPm() / baseScale;
        double pe0 = machine.getPe() / baseScale;
        double gate0 = restoreDeadband(inverseGateCurve(pm0), c(15));
        if (!finite(pm0, pe0, gate0)) return false;
        double feedback = c(8) > EPS ? pe0 : gate0;
        reference = c(7) * feedback;
        state = new State(0.0, gate0, 0.0, 0.0, gate0, pe0, pm0);
        oldState = state;
        output = pm0;
        initialized = true;
        return true;
    }

    @Override public boolean nextStep(double dt, DynamicSimuMethod method,
            Machine machine, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER) {
            throw new InterpssRuntimeException("WSHYGP supports MODIFIED_EULER only");
        }
        if (!initialized || !Double.isFinite(dt) || dt < 0.0) return false;
        // A settled network event requests a zero-time observation, not a new
        // predictor. Leave oldState/predictor untouched so an observation between
        // the two physical stages cannot overwrite integration history. This
        // governor's output depends only on its retained turbine/gate states.
        if (dt == 0.0 && (flag == 0 || flag == 1)) return finite(output);
        if (flag == 0) {
            oldState = state;
            predictor = derivatives(oldState, dt);
            state = normalize(advance(oldState, predictor, dt));
        } else if (flag == 1) {
            Derivative corrected = derivatives(state, dt);
            state = normalize(correct(oldState, predictor, corrected, dt));
        } else throw new InterpssRuntimeException("WSHYGP invalid integration flag: " + flag);
        output = turbineOutput(state);
        return finite(output);
    }

    private Derivative derivatives(State s, double dt) {
        double error = controllerError(s);
        double filtered = c(2) > EPS ? s.inputFilter : error;
        double inputFilterRate = c(2) > EPS ? (error - s.inputFilter) / c(2) : 0.0;
        double derivativeRate = c(4) > EPS ? (filtered - s.derivativeState) / c(4) : 0.0;
        double controller = c(6) * filtered + s.integral
                + (c(4) > EPS ? c(5) / c(4) * (filtered - s.derivativeState) : 0.0);
        double integralRate = c(3) * filtered;
        double servoTarget = c(9) * (controller - s.gate);
        double valveRateState = c(10) > EPS
                ? (servoTarget - s.valveSpeed) / c(10) : 0.0;
        double requestedRate = c(10) > EPS ? s.valveSpeed : servoTarget;
        double gateRate = clamp(requestedRate, -Math.abs(c(12)), Math.abs(c(11)));
        if ((s.gate >= effectiveGateMaximum && gateRate > 0.0)
                || (s.gate <= c(14) && gateRate < 0.0)) gateRate = 0.0;
        double powerRate = c(8) > EPS
                ? (machinePower() - s.generatorPower) / c(8) : 0.0;
        double gatePower = gateCurve(applyDeadband(s.gate, c(15)));
        double turbineTime = c(27) * c(28);
        double turbineRate = turbineTime > EPS
                ? (gatePower - s.turbineState) / turbineTime : 0.0;
        return new Derivative(inputFilterRate, integralRate, derivativeRate,
                valveRateState, gateRate, powerRate, turbineRate);
    }

    private double controllerError(State s) {
        double feedback = c(8) > EPS ? s.generatorPower : s.gate;
        return reference - applyDeadband(getMachine().getSpeed() - 1.0, c(0))
                - c(7) * feedback;
    }
    private double machinePower() { return getMachine().getPe() / baseScale; }
    private State normalize(State s) {
        return new State(c(2) > EPS ? s.inputFilter : controllerError(s),
                s.integral, c(4) > EPS ? s.derivativeState : 0.0,
                c(10) > EPS ? s.valveSpeed : 0.0,
                clamp(s.gate, c(14), effectiveGateMaximum),
                c(8) > EPS ? s.generatorPower : machinePower(),
                c(27) * c(28) > EPS ? s.turbineState
                        : gateCurve(applyDeadband(s.gate, c(15))));
    }
    private double turbineOutput(State s) {
        double gatePower = gateCurve(applyDeadband(s.gate, c(15)));
        double denominator = c(27);
        if (c(27) * c(28) <= EPS || Math.abs(denominator) <= EPS) return gatePower;
        return s.turbineState + c(26) / denominator * (gatePower - s.turbineState);
    }
    // Zero pairs after the first point are unused capacity, not a return to
    // zero gate. Repeated abscissae retain the entered vertical segment.
    private double[] enteredCurve(boolean inverse) {
        double[] values=new double[10];int count=0;
        for(int i=0;i<5;i++) {
            double x=c(16+2*i),y=c(17+2*i);
            if(count>0&&(x==0&&y==0||x==values[2*count-2]&&y==values[2*count-1]))continue;
            if(count>0&&(x<values[2*count-2]||y<values[2*count-1]))
                throw new IllegalArgumentException("Hydro gate curve must be ordered");
            values[2*count++]=x;values[2*count-1]=y;
        }
        if(count<2)throw new IllegalArgumentException("Hydro gate curve needs two points");
        double[] result=java.util.Arrays.copyOf(values,count*2);
        if(inverse)for(int i=0;i<count;i++){double x=result[2*i];result[2*i]=result[2*i+1];result[2*i+1]=x;}
        return result;
    }
    private double evaluateCurve(double value,boolean inverse) {
        // Validate/reinitialize after data edits; evaluation allocates no arrays.
        if(preparedCurve==null)preparedCurve=enteredCurve(false);
        double[] curve=preparedCurve;int points=curve.length/2,xi=inverse?1:0,yi=inverse?0:1;
        if(value<=curve[xi])return curve[yi];
        for(int i=1;i<points;i++)if(value<=curve[2*i+xi]) {
            double lo=curve[2*i-2+xi],hi=curve[2*i+xi];
            return hi==lo?curve[2*i+yi]:curve[2*i-2+yi]+(value-lo)*(curve[2*i+yi]-curve[2*i-2+yi])/(hi-lo);
        }
        return curve[curve.length-2+yi];
    }
    public double gateCurve(double gate){return evaluateCurve(gate,false);}
    public double inverseGateCurve(double power){return evaluateCurve(power,true);}
    public boolean validateParameters() {
        for (int i = 0; i < 30; i++) if (!Double.isFinite(c(i))) return false;
        return c(2) >= 0.0 && c(4) >= 0.0 && c(8) >= 0.0 && c(10) >= 0.0
                && c(11) >= 0.0 && c(12) >= 0.0 && c(13) >= c(14)
                && validCurve() && c(27) >= 0.0
                && c(28) >= 0.0 && c(29) >= 0.0;
    }

    private boolean validCurve(){try{preparedCurve=enteredCurve(false);return true;}catch(IllegalArgumentException invalid){return false;}}
    @Override public double getOutput(Machine machine) { return output * baseScale; }
    @Override public void setRefPoint(double value) { reference = value / baseScale; }
    /** Explicit initial policy changes runtime travel only; retain the entered curve,
     * constants, reference, states and modified-Euler predictor history. */
    public void applyInitialUpperTravelLimit(double value) {
        if (!initialized) throw new IllegalStateException("Initialize before applying travel policy");
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Travel cap must be finite");
        effectiveGateMaximum = value;
    }
    public double getEffectiveMaximum() { return effectiveGateMaximum; }
    public double getGatePosition() { return state.gate; }
    public double getTurbineOutput() { return output; }
    @Override public Map<String, Double> getNamedStates() {
        if (!initialized) return Map.of();
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("Output, Td", state.inputFilter);
        values.put("Integrator state", state.integral);
        values.put("Derivative state", state.derivativeState);
        values.put("Valve speed", state.valveSpeed);
        values.put("Gate position", state.gate);
        values.put("Generator power", state.generatorPower);
        values.put("Turbine", state.turbineState);
        return Map.copyOf(values);
    }

    private double c(int index) { return getData().get(index); }
    private static double applyDeadband(double value, double width) {
        double band = Math.abs(width);
        if (value > band) return value - band;
        if (value < -band) return value + band;
        return 0.0;
    }
    private static double restoreDeadband(double value, double width) {
        if (value > 0.0) return value + Math.abs(width);
        if (value < 0.0) return value - Math.abs(width);
        return 0.0;
    }
    private static double interpolate(double value, double x1, double y1,
            double x2, double y2) {
        return y1 + (y2-y1) * (value-x1) / (x2-x1);
    }
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
    private static State advance(State s, Derivative d, double dt) {
        return new State(s.inputFilter+dt*d.inputFilter, s.integral+dt*d.integral,
                s.derivativeState+dt*d.derivativeState,
                s.valveSpeed+dt*d.valveSpeed, s.gate+dt*d.gate,
                s.generatorPower+dt*d.generatorPower,
                s.turbineState+dt*d.turbineState);
    }
    private static State correct(State s, Derivative a, Derivative b, double dt) {
        return advance(s, a.add(b), .5*dt);
    }
    private record State(double inputFilter, double integral, double derivativeState,
            double valveSpeed, double gate, double generatorPower, double turbineState) {
        static State zero() { return new State(0,0,0,0,0,0,0); }
    }
    private record Derivative(double inputFilter, double integral, double derivativeState,
            double valveSpeed, double gate, double generatorPower, double turbineState) {
        static Derivative zero() { return new Derivative(0,0,0,0,0,0,0); }
        Derivative add(Derivative o) { return new Derivative(inputFilter+o.inputFilter,
                integral+o.integral, derivativeState+o.derivativeState,
                valveSpeed+o.valveSpeed, gate+o.gate,
                generatorPower+o.generatorPower, turbineState+o.turbineState); }
    }
}
