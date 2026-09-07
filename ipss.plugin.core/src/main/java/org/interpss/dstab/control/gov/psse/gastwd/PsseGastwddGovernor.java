package org.interpss.dstab.control.gov.psse.gastwd;

import java.util.ArrayDeque;
import java.util.Deque;

import org.interpss.dstab.control.util.AsymmetricDeadbandBlock;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;

/**
 * Woodward GASTWD gas-turbine governor with the PSS/E GASTWDD asymmetric
 * frequency deadband.  The realization follows the published PowerWorld/PTI
 * block diagram, including the three transport delays and temperature
 * controller low-value selection.
 */
public class PsseGastwddGovernor extends AbstractGovernor implements IntegrationStepAware {
    private static final double EPS = 1e-9;
    private State state = State.zero(), oldState = State.zero();
    private Deriv oldDeriv = Deriv.zero();
    private final Deque<Sample> fuelCommandHistory = new ArrayDeque<>();
    private final Deque<Sample> combustorHistory = new ArrayDeque<>();
    private final Deque<Sample> exhaustHistory = new ArrayDeque<>();
    private double dtSetting, multiplier = 1.0, time, baseScale = 1.0;
    private double t, ecr, etd, tcd, tauF, t3, t4, tauT;
    private double effectiveA, effectiveB, effectiveT5, effectiveBf2;
    private double maxLimit, minLimit, speedReference, committedError, committedTempError, output;
    private boolean initialized;

    public PsseGastwddGovernor(String id, String name, String category) {
        super(id, name, category); _data = new PsseGastwddGovernorData();
    }
    public PsseGastwddGovernorData getData(){return (PsseGastwddGovernorData)_data;}
    public void setData(PsseGastwddGovernorData data) {
        if (data == null) throw new IllegalArgumentException("GASTWDD data is required");
        _data = data;
    }
    @Override public void configureIntegrationStep(double dt){configureIntegrationStep(dt,1.0);}
    public void configureIntegrationStep(double dt,double m){
        if(!finite(dt,m)||dt<0||m<0) throw new IllegalArgumentException("invalid GASTWDD integration settings");
        dtSetting=dt; multiplier=m;
    }

    @Override public boolean initStates(BaseDStabBus<?,?> bus, Machine mach) {
        if(!validateParameters()) return false;
        prepareEffective();
        var d=getData();
        double mva=mach.getRating(UnitType.mVA,bus.getNetwork().getBaseKva());
        baseScale=d.getTrate()>EPS&&mva>EPS?d.getTrate()/mva:1.0;
        double pm=mach.getPm()/baseScale, pe=mach.getPe()/baseScale;
        double speed=deadband(mach.getSpeed()-1.0);
        double f2=pm/(1.0+speed);
        double wf=(f2-d.getAf2()+d.getCf2()*speed)/effectiveBf2;
        double lvs=((d.getC()/effectiveA+d.getKf())*wf-d.getK6())/d.getK3();
        maxLimit=Math.max(maxLimit,lvs); minLimit=Math.min(minLimit,lvs);
        double f1=d.getTr()-d.getAf1()*(1.0-wf)-d.getBf1()*speed;
        double temperature=(d.getK4()+d.getK5())*f1;
        double tempIntegral = lvs - effectiveT5/tauT*(d.getTc()-temperature);
        state=new State(d.getKdroop()*pe,lvs,wf,wf,f1,temperature,tempIntegral,wf);
        oldState=state; output=pm; committedError=0; committedTempError=d.getTc()-temperature;
        speedReference=speed+d.getKdroop()*pe;
        time=0; fuelCommandHistory.clear(); combustorHistory.clear(); exhaustHistory.clear();
        fuelCommandHistory.add(new Sample(0,d.getK3()*lvs));
        combustorHistory.add(new Sample(0,wf)); exhaustHistory.add(new Sample(0,wf));
        initialized=true; return finite(pm,pe,wf,lvs,f1,temperature);
    }

    @Override public boolean nextStep(double dt, DynamicSimuMethod method, Machine mach, int flag) {
        if(method!=DynamicSimuMethod.MODIFIED_EULER) throw new InterpssRuntimeException("GASTWDD supports MODIFIED_EULER only");
        if(!initialized)return false;
        if(flag==0){oldState=state; Algebraic a=algebraic(oldState,dt,time); oldDeriv=derivatives(oldState,a); state=normalize(oldState.plus(oldDeriv,dt)); output=mechanical(state);}
        else if(flag==1){Algebraic a=algebraic(state,dt,time+dt); Deriv d=derivatives(state,a); state=normalize(oldState.plusAverage(oldDeriv,d,dt)); Algebraic end=algebraic(state,dt,time+dt); if(committedTempError>0&&end.tempError<=0){state=state.withTempIntegral(end.speedCommand-effectiveT5/tauT*end.tempError);end=algebraic(state,dt,time+dt);} committedError=end.error; committedTempError=end.tempError; time+=dt; append(fuelCommandHistory,time,getData().getK3()*end.lowSelect); append(combustorHistory,time,state.fuel); append(exhaustHistory,time,end.combustor); output=mechanical(state);}
        else throw new InterpssRuntimeException("GASTWDD invalid integration flag: "+flag);
        return true;
    }
    @Override public double getOutput(Machine mach){return output*baseScale;}
    @Override public void setRefPoint(double value){speedReference=value/baseScale;}

    public boolean validateParameters(){var d=getData(); return finite(d.getKdroop(),d.getKp(),d.getKi(),d.getKd(),d.getEtd(),d.getTcd(),d.getTrate(),d.getT(),d.getMaxLimit(),d.getMinLimit(),d.getEcr(),d.getK3(),d.getA(),d.getB(),d.getC(),d.getTauF(),d.getKf(),d.getK5(),d.getK4(),d.getT3(),d.getT4(),d.getTauT(),d.getT5(),d.getAf1(),d.getBf1(),d.getAf2(),d.getBf2(),d.getCf2(),d.getTr(),d.getK6(),d.getTc(),d.getTd(),d.getDbH(),d.getDbL())&&d.getA()>0&&d.getB()>0&&d.getTauF()>0&&d.getT3()>0&&d.getT4()>0&&d.getTauT()>0&&d.getBf2()>0&&Math.abs(d.getK3())>EPS&&d.getTd()>=0&&d.getTcd()>=0&&d.getT()>=0&&d.getEcr()>=0&&d.getEtd()>=0&&d.getTrate()>=0&&d.getDbH()>=0&&d.getDbL()<=0&&d.getDbL()<=d.getDbH();}

    private Algebraic algebraic(State s,double dt,double at){var d=getData(); double speed=deadband(getMachine().getSpeed()-1.0); double pe=getMachine().getPe()/baseScale; double droop=td()>EPS?s.powerDroop:d.getKdroop()*pe; double error=speedReference-speed-droop; double derivative=dt>EPS?d.getKd()*(error-committedError)/dt:0; double speedCmd=clamp(d.getKp()*error+s.pidIntegral+derivative,minLimit,maxLimit); double combustor=delayed(combustorHistory,at-ecr,s.fuel); double exhaust=delayed(exhaustHistory,at-etd,combustor); double f1=d.getTr()-d.getAf1()*(1-exhaust)-d.getBf1()*speed; double shield=d.getK4()*f1+d.getK5()*s.radiationLag; double tempError=d.getTc()-s.thermocouple; double tempCmd=s.tempIntegral+effectiveT5/tauT*tempError; double low=clamp(Math.min(speedCmd,tempCmd),minLimit,maxLimit); double delayedCmd=delayed(fuelCommandHistory,at-t,d.getK3()*low); return new Algebraic(speed,pe,error,speedCmd,tempError,tempCmd,low,delayedCmd,combustor,f1,shield);}
    private Deriv derivatives(State s,Algebraic x){var d=getData(); double pd=td()>EPS?(d.getKdroop()*x.pe-s.powerDroop)/td():0; double pi=d.getKi()*x.error; if((x.speedCommand>=maxLimit&&x.error>0)||(x.speedCommand<=minLimit&&x.error<0))pi=0; double valve=(effectiveA*(x.delayedCommand+d.getK6()-d.getKf()*s.fuel)-d.getC()*s.valve)/effectiveB; double fuel=(s.valve-s.fuel)/tauF; double radiation=(x.f1-s.radiationLag)/t3; double thermo=(x.shield-s.thermocouple)/t4; double ti=x.tempError/tauT; double gas=tcd>EPS?(x.combustor-s.gasLag)/tcd:0; return new Deriv(pd,pi,valve,fuel,radiation,thermo,ti,gas);}
    private State normalize(State s){return new State(td()>EPS?s.powerDroop:0,s.pidIntegral,s.valve,s.fuel,s.radiationLag,s.thermocouple,s.tempIntegral,tcd>EPS?s.gasLag:delayed(combustorHistory,time-ecr,s.fuel));}
    private double mechanical(State s){var d=getData(); double speed=deadband(getMachine().getSpeed()-1); double f2=d.getAf2()+effectiveBf2*s.gasLag-d.getCf2()*speed; return f2*(1+speed);}
    private double deadband(double v){return AsymmetricDeadbandBlock.apply(v,getData().getDbH(),getData().getDbL());}
    private void prepareEffective(){var d=getData(); double min=multiplier*dtSetting; t=half(d.getT(),min); ecr=half(d.getEcr(),min); etd=d.getEtd(); tcd=half(d.getTcd(),min); tauF=positive(d.getTauF(),min); t3=positive(d.getT3(),min); t4=positive(d.getT4(),min); tauT=positive(d.getTauT(),min); effectiveA=positive(d.getA(),min); effectiveB=positive(d.getB(),min); effectiveT5=positive(d.getT5(),min); effectiveBf2=positive(d.getBf2(),min); maxLimit=Math.max(d.getMaxLimit(),d.getMinLimit()); minLimit=Math.min(d.getMaxLimit(),d.getMinLimit());}
    private double td(){return half(getData().getTd(),multiplier*dtSetting);} private static double half(double v,double m){if(v>0&&v<.5*m)return 0;return v>0&&v<m?m:v;} private static double positive(double v,double m){return v>0&&v<m?m:v;}
    private static void append(Deque<Sample> h,double time,double value){h.addLast(new Sample(time,value)); while(h.size()>10000)h.removeFirst();}
    private static double delayed(Deque<Sample> h,double target,double current){if(h.isEmpty()||target>=h.getLast().time)return current; Sample prev=h.getFirst(); if(target<=prev.time)return prev.value; for(Sample next:h){if(next.time>=target){double q=(target-prev.time)/(next.time-prev.time); return prev.value+q*(next.value-prev.value);}prev=next;}return current;}
    public double getFuelFlow(){return state.fuel;} public double getValvePosition(){return state.valve;} public double getSpeedCommand(){return algebraic(state,Math.max(dtSetting,EPS),time).speedCommand;} public double getTemperatureCommand(){return algebraic(state,Math.max(dtSetting,EPS),time).tempCommand;} public double getLowValueSelect(){return algebraic(state,Math.max(dtSetting,EPS),time).lowSelect;} public double getEffectiveMaxLimit(){return maxLimit;} public double getEffectiveMinLimit(){return minLimit;} public double applySpeedDeadband(double v){return deadband(v);}
    private static boolean finite(double...v){for(double x:v)if(!Double.isFinite(x))return false;return true;} private static double clamp(double x,double lo,double hi){return Math.max(lo,Math.min(hi,x));}
    private record Sample(double time,double value){}
    private record Algebraic(double speed,double pe,double error,double speedCommand,double tempError,double tempCommand,double lowSelect,double delayedCommand,double combustor,double f1,double shield){}
    private record Deriv(double powerDroop,double pidIntegral,double valve,double fuel,double radiationLag,double thermocouple,double tempIntegral,double gasLag){static Deriv zero(){return new Deriv(0,0,0,0,0,0,0,0);}}
    private record State(double powerDroop,double pidIntegral,double valve,double fuel,double radiationLag,double thermocouple,double tempIntegral,double gasLag){static State zero(){return new State(0,0,0,0,0,0,0,0);} State withTempIntegral(double v){return new State(powerDroop,pidIntegral,valve,fuel,radiationLag,thermocouple,v,gasLag);} State plus(Deriv d,double h){return new State(powerDroop+d.powerDroop*h,pidIntegral+d.pidIntegral*h,valve+d.valve*h,fuel+d.fuel*h,radiationLag+d.radiationLag*h,thermocouple+d.thermocouple*h,tempIntegral+d.tempIntegral*h,gasLag+d.gasLag*h);} State plusAverage(Deriv a,Deriv b,double h){return plus(new Deriv((a.powerDroop+b.powerDroop)/2,(a.pidIntegral+b.pidIntegral)/2,(a.valve+b.valve)/2,(a.fuel+b.fuel)/2,(a.radiationLag+b.radiationLag)/2,(a.thermocouple+b.thermocouple)/2,(a.tempIntegral+b.tempIntegral)/2,(a.gasLag+b.gasLag)/2),h);}}
}
