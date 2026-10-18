package org.interpss.dstab.renewable;

import java.util.*;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import static org.interpss.dstab.renewable.ReaxdData.Parameter.*;

/** Two-state downstream hybrid command interface, on machine MVA base.
 * The published limits clip outputs, not lag storage. Network binding,
 * eligibility and participation calculation belong to the plant coordinator. */
public final class ReaxdKernel implements ICMLStateProvider {
    /** Weighted increments from HplntdAggregation; do not apply sharing twice. */
    public record Input(double activeIncrement,double reactiveIncrement) {
        public Input { finite(activeIncrement,reactiveIncrement); }
        public static Input from(HplntdAggregation.Dispatch dispatch) {
            if(!dispatch.enabled())throw new IllegalArgumentException("An ignored dispatch requires output hold, not a zero input");
            return new Input(dispatch.activeIncrement(),dispatch.reactiveIncrement());
        }
    }
    public record Output(double active,double reactive) {}
    private final ReaxdData data;
    private double active,reactive,initialActive,initialReactive,oldActive,oldReactive,rateActive,rateReactive,pendingStep;
    private boolean initialized,pending;
    public ReaxdKernel(ReaxdData data){this.data=Objects.requireNonNull(data);}
    public void initialize(double activeCommand,double reactiveCommand) {
        finite(activeCommand,reactiveCommand);
        // Out-of-range initialization needs an independently specified policy;
        // do not silently expand limits or alter the solved initial command.
        if(activeCommand<data.value(PMINREF)||activeCommand>data.value(PMAXREF)
                ||reactiveCommand<data.value(QMINREF)||reactiveCommand>data.value(QMAXREF))
            throw new IllegalArgumentException("Initial downstream command outside supported limits");
        active=initialActive=activeCommand;reactive=initialReactive=reactiveCommand;
        initialized=true;pending=false;
    }
    public Output output(){ready();return new Output(clip(active,data.value(PMINREF),data.value(PMAXREF)),clip(reactive,data.value(QMINREF),data.value(QMAXREF)));}
    public double[] states(){ready();return new double[]{active,reactive};}
    @Override public Map<String,Double> getNamedStates(){ready();var result=new LinkedHashMap<String,Double>();result.put("ActiveCommandLag",active);result.put("ReactiveCommandLag",reactive);return Collections.unmodifiableMap(result);}
    public void step(double dt,Input input){step(dt,input,0);step(dt,input,1);}
    /** Explicit modified-Euler stages. The corrector accepts endpoint inputs;
     * no hidden implicit solve, state clipping or downstream double advance. */
    public void step(double dt,Input input,int stage) {
        ready();Objects.requireNonNull(input);finite(dt);
        if(dt<=0||stage<0||stage>1)throw new IllegalArgumentException("Positive step and stage zero/one required");
        if(stage==0&&pending)throw new IllegalStateException("Complete the pending downstream corrector first");
        if(stage==1&&(!pending||dt!=pendingStep))throw new IllegalStateException("Matching downstream predictor required");
        double p=(initialActive+input.activeIncrement()-active)/data.activeTime(dt);
        double q=(initialReactive+input.reactiveIncrement()-reactive)/data.reactiveTime(dt);
        finite(p,q);
        double nextP,nextQ;
        if(stage==0){nextP=active+dt*p;nextQ=reactive+dt*q;}
        else{nextP=oldActive+.5*dt*(rateActive+p);nextQ=oldReactive+.5*dt*(rateReactive+q);}
        finite(nextP,nextQ);
        if(stage==0){oldActive=active;oldReactive=reactive;rateActive=p;rateReactive=q;pendingStep=dt;pending=true;}
        else pending=false;
        active=nextP;reactive=nextQ;
    }
    private void ready(){if(!initialized)throw new IllegalStateException("Initialize downstream interface first");}
    private static double clip(double x,double lo,double hi){return Math.max(lo,Math.min(hi,x));}
    private static void finite(double... values){for(double v:values)if(!Double.isFinite(v))throw new IllegalArgumentException("Finite downstream values required");}
}
