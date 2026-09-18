package org.interpss.dstab.control.uel.psse.uel2c;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.exc.UnderExcitationLimiterTarget;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.core.net.Network;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.dynLoad.impl.DynLoadModelImpl;
import com.interpss.dstab.mach.Machine;

/** IEEE 421.5-2016 UEL2C under-excitation limiter. */
public final class Uel2cUnderExcitationLimiter extends DynLoadModelImpl implements ICMLStateProvider {
    private static final double EPS=1e-12;
    private static final int V=0,P=1,Q=2,I=3,FB=4,LL1=5,LL2=6,QREF=7,GAIN=8;
    private final Uel2cData data;
    private final Machine machine;
    private final UnderExcitationLimiterTarget exciter;
    private final Hashtable<String,Object> outputStates=new Hashtable<>();
    private final double[] state=new double[9],trial=new double[9],oldDerivative=new double[9];
    private double[] active=state;
    private double output;
    private boolean initialized;

    public Uel2cUnderExcitationLimiter(BaseDStabBus<?,?> bus,Machine machine,String generatorId,Uel2cData data){
        if(bus==null||machine==null||data==null)throw new IllegalArgumentException("UEL2C requires a bus, machine, and data");
        if(!(machine.getExciter() instanceof UnderExcitationLimiterTarget target))throw new IllegalArgumentException("UEL2C requires a compatible exciter");
        this.data=data;this.machine=machine;this.exciter=target;setId(generatorId);setName("UEL2C");
        setExtendedDeviceId("UEL2C_"+generatorId+"@"+bus.getId());setLoadPercent(0);setLoadFactor(0);
        setLoadPQ(Complex.ZERO);setInitLoadPQ(Complex.ZERO);setEquivY(Complex.ZERO);setNortonCurInj(Complex.ZERO);
        setCompensateShuntY(Complex.ZERO);setCurrInj2Net(Complex.ZERO);setDStabBus(bus);
    }

    @Override public boolean initStates(){return initStates(getDStabBus());}
    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine ignored){return initStates(bus);}
    @Override public boolean initStates(BaseDStabBus<?,?> bus,Network<?,?> ignored){return initStates(bus);}
    @Override public boolean initStates(BaseDStabBus<?,?> bus){
        if(bus==null||bus!=getDStabBus()||machine.getExciter()!=exciter)return false;
        Complex initial=machine.getParentGen().getGen();
        initializeWithSignals(bus.getVoltageMag(),initial.getReal(),initial.getImaginary(),
                exciter.getUelStabilizingSignal(),exciter.getUelReferenceFeedbackSignal());
        outputStates.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());
        return initialized;
    }

    /** Initialize all nine states from public prescribed terminal signals. */
    public void initializeWithSignals(double vt,double pt,double qt,double vf,double vfb){
        state[V]=vt;double u=voltageBias(vt);state[P]=pt*inversePower(u,data.k1());state[Q]=qt;
        double normalizedQ=limitAt(state[P])*power(u,data.k2());state[QREF]=normalizedQ;
        state[I]=data.vuimin();state[FB]=data.kfb()*vfb;state[LL1]=data.vuimin();state[LL2]=data.vuimin();
        state[GAIN]=adjustableGain(vt,pt,qt);System.arraycopy(state,0,trial,0,state.length);active=state;
        output=output(state,new Signals(vt,pt,qt,vf,vfb));exciter.setVuel(output);initialized=finite(state)&&Double.isFinite(output);
    }

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,int flag){
        return nextStepWithSignals(dt,method,flag,currentSignals());
    }
    public boolean nextStepWithSignals(double dt,DynamicSimuMethod method,int flag,double vt,double pt,double qt,double vf,double vfb){
        return nextStepWithSignals(dt,method,flag,new Signals(vt,pt,qt,vf,vfb));
    }
    private boolean nextStepWithSignals(double dt,DynamicSimuMethod method,int flag,Signals signals){
        if(method!=DynamicSimuMethod.MODIFIED_EULER)throw new InterpssRuntimeException("UEL2C supports MODIFIED_EULER only");
        if(!initialized||dt<0||!Double.isFinite(dt))return false;
        if(flag==0){derivatives(state,signals,oldDerivative);for(int i=0;i<9;i++)trial[i]=state[i]+dt*oldDerivative[i];normalize(trial,signals);active=trial;}
        else if(flag==1){double[] corrected=new double[9];derivatives(trial,signals,corrected);for(int i=0;i<9;i++)state[i]+=.5*dt*(oldDerivative[i]+corrected[i]);normalize(state,signals);active=state;}
        else throw new InterpssRuntimeException("UEL2C invalid integration flag: "+flag);
        output=output(active,signals);exciter.setVuel(output);return finite(active)&&Double.isFinite(output);
    }

    private void derivatives(double[] x,Signals s,double[] dx){
        for(int i=0;i<9;i++)dx[i]=0;double u=voltageBias(filteredOrInput(x[V],s.vt,data.tuv()));
        double pInput=s.pt*inversePower(u,data.k1());double qReference=limitAt(filteredOrInput(x[P],pInput,data.tup()))*power(u,data.k2());
        double gainInput=adjustableGain(s.vt,s.pt,s.qt);
        dx[V]=lagDerivative(x[V],s.vt,data.tuv());dx[P]=lagDerivative(x[P],pInput,data.tup());
        dx[Q]=lagDerivative(x[Q],s.qt,data.tuq());dx[QREF]=lagDerivative(x[QREF],qReference,data.tqref());
        dx[GAIN]=lagDerivative(x[GAIN],gainInput,data.tadj());dx[FB]=lagDerivative(x[FB],data.kfb()*s.vfb,data.tul());
        Algebraic a=algebraic(x,s);double di=data.kui()*a.error;
        if((a.rawPi>=data.vuimax()&&di>0)||(a.rawPi<=data.vuimin()&&di<0))di=0;dx[I]=di;
        dx[LL1]=lagDerivative(x[LL1],a.piPlusFeedback,data.tu2());
        dx[LL2]=lagDerivative(x[LL2],a.firstLimited,data.tu4());
    }

    private Algebraic algebraic(double[] x,Signals s){
        double u=voltageBias(filteredOrInput(x[V],s.vt,data.tuv()));
        double directQref=limitAt(filteredOrInput(x[P],s.pt*inversePower(u,data.k1()),data.tup()))*power(u,data.k2());
        double qfb=filteredOrInput(x[Q],s.qt,data.tuq());double qref=filteredOrInput(x[QREF],directQref,data.tqref());
        double gain=data.gainMode()==1?data.kfix():filteredOrInput(x[GAIN],adjustableGain(s.vt,s.pt,s.qt),data.tadj());
        if(Math.abs(gain)<EPS)gain=Math.copySign(EPS,gain==0?1:gain);
        double error=(qref-qfb+data.kuf()*s.vf)/gain;double rawPi=data.kul()*error+x[I];
        double pi=clamp(rawPi,data.vuimin(),data.vuimax());double sum=pi+filteredOrInput(x[FB],data.kfb()*s.vfb,data.tul());
        double first=clamp(leadLag(sum,x[LL1],data.tu1(),data.tu2()),data.vuelmin2(),data.vuelmax2());
        double second=clamp(leadLag(first,x[LL2],data.tu3(),data.tu4()),data.vuelmin1(),data.vuelmax1());
        return new Algebraic(error,rawPi,sum,first,second);
    }
    private double output(double[] x,Signals s){return clamp(algebraic(x,s).secondLimited,data.vulmin(),data.vulmax());}
    private void normalize(double[] x,Signals s){
        if(data.tuv()<=EPS)x[V]=s.vt;if(data.tup()<=EPS){double u=voltageBias(x[V]);x[P]=s.pt*inversePower(u,data.k1());}
        if(data.tuq()<=EPS)x[Q]=s.qt;if(data.tqref()<=EPS){double u=voltageBias(x[V]);x[QREF]=limitAt(x[P])*power(u,data.k2());}
        if(data.tadj()<=EPS)x[GAIN]=adjustableGain(s.vt,s.pt,s.qt);if(data.tul()<=EPS)x[FB]=data.kfb()*s.vfb;
        if(data.tu2()<=EPS)x[LL1]=algebraic(x,s).piPlusFeedback;if(data.tu4()<=EPS)x[LL2]=algebraic(x,s).firstLimited;
    }
    private Signals currentSignals(){
        Complex vt=machine.getDStabBus().getVoltage();Complex it=machine.getIxy().divide(machine.getIMultiFactor());
        Complex power=vt.multiply(it.conjugate());return new Signals(vt.abs(),power.getReal(),power.getImaginary(),
                exciter.getUelStabilizingSignal(),exciter.getUelReferenceFeedbackSignal());
    }
    private double limitAt(double p){
        double query=p<0&&data.thirdQuadrantMode()==0?-p:p;double[] ps=data.pPoints(),qs=data.qPoints();int n=data.pointCount();
        int hi=1;if(query<=ps[0])hi=1;else if(query>=ps[n-1])hi=n-1;else while(hi<n-1&&query>ps[hi])hi++;
        int lo=hi-1;return qs[lo]+(qs[hi]-qs[lo])*(query-ps[lo])/(ps[hi]-ps[lo]);
    }
    private double voltageBias(double v){if(data.vbias()<0)return v;if(v>1)return v;if(v>data.vbias())return 1;return data.vbias()>EPS?v/data.vbias():v;}
    private double adjustableGain(double v,double p,double q){double axis=v*v/data.xq()+q;return axis/Math.max(EPS,Math.hypot(axis,p));}
    private static double filteredOrInput(double state,double input,double time){return time>EPS?state:input;}
    private static double lagDerivative(double state,double input,double time){return time>EPS?(input-state)/time:0;}
    private static double leadLag(double input,double state,double lead,double lag){return lag>EPS?lead/lag*input+(1-lead/lag)*state:input;}
    private static double power(double value,int exponent){return exponent==0?1:exponent==1?value:value*value;}
    private static double inversePower(double value,int exponent){double safe=Math.max(EPS,Math.abs(value));return 1/power(safe,exponent);}
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
    private static boolean finite(double[] values){for(double v:values)if(!Double.isFinite(v))return false;return true;}

    @Override public double getOutput(){return output;}@Override public Object getOutputObject(){return Complex.ZERO;}
    @Override public boolean updateAttributes(boolean netChange){return true;}@Override public boolean afterStep(double dt){return true;}
    @Override public Hashtable<String,Object> getStates(Object ref){outputStates.put("UEL2C_OUTPUT",output);outputStates.put("UEL2C_ERROR",algebraic(active,currentSignals()).error);return outputStates;}
    @Override public Map<String,Double> getNamedStates(){Map<String,Double> result=new LinkedHashMap<>();
        result.put("Voltage filter",active[V]);result.put("Real power filter",active[P]);result.put("Reactive power filter",active[Q]);
        result.put("Integrator",active[I]);result.put("Reference feedback",active[FB]);result.put("First lead-lag",active[LL1]);
        result.put("Second lead-lag",active[LL2]);result.put("Reactive power reference",active[QREF]);result.put("Adjustable gain",active[GAIN]);return Map.copyOf(result);}
    public Uel2cData getData(){return data;}public double[] getStateSnapshot(){return active.clone();}
    public double getError(double vt,double pt,double qt,double vf,double vfb){return algebraic(active,new Signals(vt,pt,qt,vf,vfb)).error;}
    public double getNormalizedReactiveReference(){double u=voltageBias(active[V]);return limitAt(active[P])*power(u,data.k2());}
    private record Signals(double vt,double pt,double qt,double vf,double vfb){}
    private record Algebraic(double error,double rawPi,double piPlusFeedback,double firstLimited,double secondLimited){}
}
