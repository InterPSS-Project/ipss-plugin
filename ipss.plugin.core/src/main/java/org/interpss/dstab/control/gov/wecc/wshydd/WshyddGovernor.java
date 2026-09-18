package org.interpss.dstab.control.gov.wecc.wshydd;

import java.util.LinkedHashMap;
import java.util.Map;

import org.interpss.numeric.datatype.Unit.UnitType;
import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;

/** WECC double-derivative hydro-governor compatibility realization. */
public final class WshyddGovernor extends AbstractGovernor implements ICMLStateProvider {
    private static final double EPS = 1.0e-10;
    private State state = State.zero(), oldState = State.zero();
    private Derivative predictor = Derivative.zero();
    private double reference, baseScale = 1.0, output;
    private boolean initialized;

    public WshyddGovernor(String id, String name, String category) {
        super(id, name, category); _data = new WshyddGovernorData();
    }
    public WshyddGovernorData getData() { return (WshyddGovernorData) _data; }

    @Override public boolean initStates(BaseDStabBus<?, ?> bus, Machine machine) {
        if (!validateParameters()) return false;
        double machineMva = machine.getRating(UnitType.mVA, bus.getNetwork().getBaseKva());
        baseScale = c(29) > EPS && machineMva > EPS ? c(29) / machineMva : 1.0;
        double pm0 = machine.getPm()/baseScale, pe0 = machine.getPe()/baseScale;
        double gate0 = restoreDeadband(inverseGateCurve(pm0), c(15));
        if (!finite(pm0, pe0, gate0)) return false;
        reference = c(7) * (c(8) > EPS ? pe0 : gate0);
        state = new State(0, 0, 0, 0, gate0, 0, gate0, pe0, pm0);
        oldState = state; output = pm0; initialized = true;
        return true;
    }

    @Override public boolean nextStep(double dt, DynamicSimuMethod method,
            Machine machine, int flag) {
        if (method != DynamicSimuMethod.MODIFIED_EULER)
            throw new InterpssRuntimeException("WSHYDD supports MODIFIED_EULER only");
        if (!initialized || dt <= 0.0) return false;
        if (flag == 0) {
            oldState = state; predictor = derivatives(oldState);
            state = normalize(advance(oldState, predictor, dt));
        } else if (flag == 1) {
            Derivative corrected = derivatives(state);
            state = normalize(advance(oldState, predictor.add(corrected), .5*dt));
        } else throw new InterpssRuntimeException("WSHYDD invalid integration flag: " + flag);
        output = turbineOutput(state);
        return finite(output);
    }

    private Derivative derivatives(State s) {
        double speed = applyDeadband(1.0-getMachine().getSpeed(), c(0));
        double filtered = c(2)>EPS ? s.inputFilter : speed;
        double inputDot = c(2)>EPS ? (speed-s.inputFilter)/c(2) : 0.0;
        double k1Dot = c(4)>EPS ? (filtered-s.k1State)/c(4) : 0.0;
        double kdFirstDot = c(4)>EPS ? (filtered-s.kdFirst)/c(4) : 0.0;
        double firstHighPass = c(4)>EPS ? (filtered-s.kdFirst)/c(4) : 0.0;
        double kdSecondDot = c(4)>EPS ? (firstHighPass-s.kdSecond)/c(4) : 0.0;
        double firstDerivative = c(4)>EPS ? c(3)/c(4)*(filtered-s.k1State) : 0.0;
        double secondDerivative = c(4)>EPS ? c(5)/c(4)*(firstHighPass-s.kdSecond) : 0.0;
        double feedback = c(8)>EPS ? s.generatorPower : s.controlValve;
        double cvDot = c(6)*(reference + firstDerivative + secondDerivative-c(7)*feedback);
        double servoTarget = c(9)*(s.controlValve-s.gate);
        double valveDot = c(10)>EPS ? (servoTarget-s.valveSpeed)/c(10) : 0.0;
        double requestedRate = c(10)>EPS ? s.valveSpeed : servoTarget;
        double gateDot = clamp(requestedRate, -Math.abs(c(12)), Math.abs(c(11)));
        if ((s.gate>=c(13)&&gateDot>0)||(s.gate<=c(14)&&gateDot<0)) gateDot=0;
        double peDot = c(8)>EPS ? (getMachine().getPe()/baseScale-s.generatorPower)/c(8):0;
        double gatePower = gateCurve(applyDeadband(s.gate,c(15)));
        double turbineTime=c(27)*c(28);
        double turbineDot=turbineTime>EPS?(gatePower-s.turbineState)/turbineTime:0;
        return new Derivative(inputDot,k1Dot,kdFirstDot,kdSecondDot,cvDot,
                valveDot,gateDot,peDot,turbineDot);
    }
    private State normalize(State s) {
        double speed=applyDeadband(1.0-getMachine().getSpeed(),c(0));
        double gate=clamp(s.gate,c(14),c(13));
        return new State(c(2)>EPS?s.inputFilter:speed, c(4)>EPS?s.k1State:0,
                c(4)>EPS?s.kdFirst:0,c(4)>EPS?s.kdSecond:0,s.controlValve,
                c(10)>EPS?s.valveSpeed:0,gate,c(8)>EPS?s.generatorPower:getMachine().getPe()/baseScale,
                c(27)*c(28)>EPS?s.turbineState:gateCurve(applyDeadband(gate,c(15))));
    }
    private double turbineOutput(State s) {
        double gatePower=gateCurve(applyDeadband(s.gate,c(15)));
        return c(27)*c(28)<=EPS||Math.abs(c(27))<=EPS ? gatePower
                : s.turbineState+c(26)/c(27)*(gatePower-s.turbineState);
    }
    public double gateCurve(double gate) {
        double[] x={c(16),c(18),c(20),c(22),c(24)}, y={c(17),c(19),c(21),c(23),c(25)};
        if(gate<=x[0])return y[0];
        for(int i=1;i<x.length;i++)if(gate<=x[i])return lerp(gate,x[i-1],y[i-1],x[i],y[i]);
        return y[4];
    }
    public double inverseGateCurve(double power) {
        double[] x={c(17),c(19),c(21),c(23),c(25)}, y={c(16),c(18),c(20),c(22),c(24)};
        if(power<=x[0])return y[0];
        for(int i=1;i<x.length;i++)if(power<=x[i])return lerp(power,x[i-1],y[i-1],x[i],y[i]);
        return y[4];
    }
    public boolean validateParameters(){
        for(int i=0;i<30;i++)if(!Double.isFinite(c(i)))return false;
        return c(2)>=0&&c(4)>=0&&c(8)>=0&&c(10)>=0&&c(11)>=0&&c(12)>=0
                &&c(13)>=c(14)&&c(16)<c(18)&&c(18)<c(20)&&c(20)<c(22)&&c(22)<c(24)
                &&c(17)<=c(19)&&c(19)<=c(21)&&c(21)<=c(23)&&c(23)<=c(25)
                &&c(27)>=0&&c(28)>=0&&c(29)>=0;
    }
    @Override public double getOutput(Machine machine){return output*baseScale;}
    @Override public void setRefPoint(double value){reference=value/baseScale;}
    public double getGatePosition(){return state.gate;}
    public double getTurbineOutput(){return output;}
    @Override public Map<String,Double> getNamedStates(){
        if(!initialized)return Map.of(); Map<String,Double> m=new LinkedHashMap<>();
        m.put("Output, Td",state.inputFilter);m.put("K1 state",state.k1State);
        m.put("KD first",state.kdFirst);m.put("KD second",state.kdSecond);
        m.put("CV",state.controlValve);m.put("Valve speed",state.valveSpeed);
        m.put("Gate position",state.gate);m.put("Generator power",state.generatorPower);
        m.put("Turbine",state.turbineState);return Map.copyOf(m);
    }
    private double c(int i){return getData().get(i);}
    private static double applyDeadband(double v,double w){double b=Math.abs(w);return v>b?v-b:v< -b?v+b:0;}
    private static double restoreDeadband(double v,double w){return v>0?v+Math.abs(w):v<0?v-Math.abs(w):0;}
    private static double lerp(double v,double x1,double y1,double x2,double y2){return y1+(y2-y1)*(v-x1)/(x2-x1);}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static boolean finite(double...v){for(double x:v)if(!Double.isFinite(x))return false;return true;}
    private static State advance(State s,Derivative d,double h){return new State(s.inputFilter+h*d.inputFilter,s.k1State+h*d.k1State,s.kdFirst+h*d.kdFirst,s.kdSecond+h*d.kdSecond,s.controlValve+h*d.controlValve,s.valveSpeed+h*d.valveSpeed,s.gate+h*d.gate,s.generatorPower+h*d.generatorPower,s.turbineState+h*d.turbineState);}
    private record State(double inputFilter,double k1State,double kdFirst,double kdSecond,double controlValve,double valveSpeed,double gate,double generatorPower,double turbineState){static State zero(){return new State(0,0,0,0,0,0,0,0,0);}}
    private record Derivative(double inputFilter,double k1State,double kdFirst,double kdSecond,double controlValve,double valveSpeed,double gate,double generatorPower,double turbineState){static Derivative zero(){return new Derivative(0,0,0,0,0,0,0,0,0);}Derivative add(Derivative o){return new Derivative(inputFilter+o.inputFilter,k1State+o.k1State,kdFirst+o.kdFirst,kdSecond+o.kdSecond,controlValve+o.controlValve,valveSpeed+o.valveSpeed,gate+o.gate,generatorPower+o.generatorPower,turbineState+o.turbineState);}}
}
