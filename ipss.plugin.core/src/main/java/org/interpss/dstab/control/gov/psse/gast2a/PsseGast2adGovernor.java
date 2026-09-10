package org.interpss.dstab.control.gov.psse.gast2a;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import org.interpss.dstab.control.util.AsymmetricDeadbandBlock;
import org.interpss.dstab.control.util.IntegrationStepAware;
import org.interpss.dstab.control.util.NamedDynamicStateProvider;
import org.interpss.numeric.datatype.Unit.UnitType;

import com.interpss.common.exp.InterpssRuntimeException;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.deqn.AbstractGovernor;
import com.interpss.dstab.mach.Machine;

/** PSS/E GAST2AD gas-turbine governor. */
public class PsseGast2adGovernor extends AbstractGovernor implements IntegrationStepAware, NamedDynamicStateProvider {
    private static final double EPS=1e-9;
    private State state=State.zero(),oldState=State.zero(); private Deriv oldDeriv=Deriv.zero();
    private final Deque<Sample> commandHistory=new ArrayDeque<>(),combustorHistory=new ArrayDeque<>(),exhaustHistory=new ArrayDeque<>();
    private double step,multiplier=1,time,baseScale=1,pref,externalReference,committedTempError,output;
    private double y,t,etd,tcd,tauF,t3,t4,tauT,a,b,t5,bf2,k3,max,min; private boolean initialized;

    public PsseGast2adGovernor(String id,String name,String category){super(id,name,category);_data=new PsseGast2adGovernorData();}
    public PsseGast2adGovernorData getData(){return(PsseGast2adGovernorData)_data;}
    public void setData(PsseGast2adGovernorData data){if(data==null)throw new IllegalArgumentException("GAST2AD data is required");_data=data;}
    @Override public void configureIntegrationStep(double dt){configureIntegrationStep(dt,1);}
    public void configureIntegrationStep(double dt,double m){if(!finite(dt,m)||dt<0||m<0)throw new IllegalArgumentException("invalid GAST2AD integration settings");step=dt;multiplier=m;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine mach){
        if(!validateParameters())return false; prepare(); var d=getData();
        double mva=mach.getRating(UnitType.mVA,bus.getNetwork().getBaseKva());baseScale=d.getTrate()>EPS&&mva>EPS?d.getTrate()/mva:1;
        double pm=mach.getPm()/baseScale,speed=deadband(mach.getSpeed()-1),f2=pm/(1+speed),wf=(f2-d.getAf2()+d.getCf2()*speed)/bf2;
        double lvs=((d.getC()/a+d.getKf())*wf-d.getK6())/k3;max=Math.max(max,lvs);min=Math.min(min,lvs);
        double f1=d.getTr()-d.getAf1()*(1-wf)-d.getBf1()*speed,temp=(d.getK4()+d.getK5())*f1;
        double speedState;
        if(d.getZ()==0){pref=speed;speedState=lvs;}else{double u=lvs/d.getW();pref=speed+u;speedState=u;}
        double tempIntegral=max-t5/tauT*(d.getTc()-temp);
        state=new State(speedState,wf,wf,f1,temp,tempIntegral,wf);oldState=state;externalReference=mach.getPm();committedTempError=d.getTc()-temp;output=pm;time=0;
        commandHistory.clear();combustorHistory.clear();exhaustHistory.clear();commandHistory.add(new Sample(0,k3*lvs));combustorHistory.add(new Sample(0,wf));exhaustHistory.add(new Sample(0,wf));initialized=true;
        return finite(pm,wf,lvs,f1,temp);
    }

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine mach,int flag){
        if(method!=DynamicSimuMethod.MODIFIED_EULER)throw new InterpssRuntimeException("GAST2AD supports MODIFIED_EULER only");if(!initialized)return false;
        if(flag==0){oldState=state;Algebraic x=algebraic(state,time);oldDeriv=derivatives(state,x);state=normalize(oldState.plus(oldDeriv,dt),time+dt);output=mechanical(state);}
        else if(flag==1){Algebraic x=algebraic(state,time+dt);Deriv d=derivatives(state,x);state=normalize(oldState.plusAverage(oldDeriv,d,dt),time+dt);Algebraic end=algebraic(state,time+dt);if(committedTempError>0&&end.tempError<=0){state=state.withTempIntegral(end.speedCommand-t5/tauT*end.tempError);end=algebraic(state,time+dt);}committedTempError=end.tempError;time+=dt;append(commandHistory,time,k3*end.lowSelect);append(combustorHistory,time,state.fuel);append(exhaustHistory,time,end.combustor);output=mechanical(state);}
        else throw new InterpssRuntimeException("GAST2AD invalid integration flag: "+flag);return true;
    }
    @Override public double getOutput(Machine mach){return output*baseScale;}
    @Override public void setRefPoint(double value){
        if(!Double.isFinite(value))throw new IllegalArgumentException("GAST2AD reference must be finite");
        if(!initialized){externalReference=value;return;}
        pref+=(value-externalReference)/(baseScale*getData().getW());
        externalReference=value;
    }

    public boolean validateParameters(){var d=getData();return finite(d.getW(),d.getX(),d.getY(),d.getEtd(),d.getTcd(),d.getTrate(),d.getT(),d.getMaxLimit(),d.getMinLimit(),d.getEcr(),d.getK3(),d.getA(),d.getB(),d.getC(),d.getTauF(),d.getKf(),d.getK5(),d.getK4(),d.getT3(),d.getT4(),d.getTauT(),d.getT5(),d.getAf1(),d.getBf1(),d.getAf2(),d.getBf2(),d.getCf2(),d.getTr(),d.getK6(),d.getTc(),d.getDbH(),d.getDbL())&&Math.abs(d.getW())>EPS&&(d.getZ()==0||d.getZ()==1)&&(d.getZ()!=0||d.getY()>0)&&d.getY()>=0&&d.getA()>0&&d.getB()>0&&d.getTauF()>0&&d.getT3()>0&&d.getT4()>0&&d.getTauT()>0&&d.getBf2()>0&&Math.abs(d.getK3())>EPS&&d.getTcd()>=0&&d.getT()>=0&&d.getEcr()>=0&&d.getEtd()>=0&&d.getTrate()>=0&&d.getDbH()>=0&&d.getDbL()<=0&&d.getDbL()<=d.getDbH();}

    private Algebraic algebraic(State s,double at){var d=getData();double speed=deadband(getMachine().getSpeed()-1),u=pref-speed;double speedCommand;if(d.getZ()==0)speedCommand=s.speedState+d.getW()*d.getX()/y*u;else if(y>EPS)speedCommand=d.getW()*(d.getX()/y*u+(1-d.getX()/y)*s.speedState);else speedCommand=d.getW()*u;speedCommand=clamp(speedCommand,min,max);double combustor=delayed(combustorHistory,at-d.getEcr(),s.fuel),exhaust=delayed(exhaustHistory,at-etd,combustor);double f1=d.getTr()-d.getAf1()*(1-exhaust)-d.getBf1()*speed,shield=d.getK4()*f1+d.getK5()*s.radiationLag,tempError=d.getTc()-s.thermocouple,tempCommand=clamp(s.tempIntegral+t5/tauT*tempError,min,max),low=clamp(Math.min(speedCommand,tempCommand),min,max),delayedCommand=delayed(commandHistory,at-t,k3*low);return new Algebraic(speed,u,speedCommand,tempError,tempCommand,low,delayedCommand,combustor,f1,shield);}
    private Deriv derivatives(State s,Algebraic x){var d=getData();double gov;if(d.getZ()==0)gov=d.getW()/y*x.governorInput;else gov=y>EPS?(x.governorInput-s.speedState)/y:0;double valve=(a*(x.delayedCommand+d.getK6()-d.getKf()*s.fuel)-d.getC()*s.valve)/b,fuel=(s.valve-s.fuel)/tauF,radiation=(x.f1-s.radiationLag)/t3,thermo=(x.shield-s.thermocouple)/t4,tempInt=x.tempError/tauT;if((x.tempCommand>=max&&x.tempError>0)||(x.tempCommand<=min&&x.tempError<0))tempInt=0;double gas=tcd>EPS?(x.combustor-s.gasLag)/tcd:0;return new Deriv(gov,valve,fuel,radiation,thermo,tempInt,gas);}
    private State normalize(State s,double at){return new State((getData().getZ()==1&&y<=EPS)?0:s.speedState,s.valve,s.fuel,s.radiationLag,s.thermocouple,s.tempIntegral,tcd>EPS?s.gasLag:delayed(combustorHistory,at-getData().getEcr(),s.fuel));}
    private double mechanical(State s){var d=getData();double speed=deadband(getMachine().getSpeed()-1),f2=d.getAf2()+bf2*s.gasLag-d.getCf2()*speed;return f2*(1+speed);}
    private double deadband(double v){return AsymmetricDeadbandBlock.apply(v,getData().getDbH(),getData().getDbL());}
    private void prepare(){var d=getData();double m=multiplier*step;y=positive(d.getY(),m);t=half(d.getT(),m);etd=half(d.getEtd(),m);tcd=half(d.getTcd(),m);tauF=positive(d.getTauF(),m);t3=positive(d.getT3(),m);t4=positive(d.getT4(),m);tauT=positive(d.getTauT(),m);a=positive(d.getA(),m);b=positive(d.getB(),m);t5=positive(d.getT5(),m);bf2=positive(d.getBf2(),m);k3=positive(d.getK3(),m);max=Math.max(d.getMaxLimit(),d.getMinLimit());min=Math.min(d.getMaxLimit(),d.getMinLimit());}
    private static double half(double v,double m){if(v>0&&v<.5*m)return 0;return v>0&&v<m?m:v;}private static double positive(double v,double m){return v>0&&v<m?m:v;}
    private static void append(Deque<Sample> h,double at,double value){h.addLast(new Sample(at,value));while(h.size()>10000)h.removeFirst();}
    private static double delayed(Deque<Sample> h,double target,double current){if(h.isEmpty()||target>=h.getLast().time)return current;Sample prev=h.getFirst();if(target<=prev.time)return prev.value;for(Sample next:h){if(next.time>=target){double q=(target-prev.time)/(next.time-prev.time);return prev.value+q*(next.value-prev.value);}prev=next;}return current;}
    public double applySpeedDeadband(double v){return deadband(v);}public double getSpeedCommand(){return algebraic(state,time).speedCommand;}public double getTemperatureCommand(){return algebraic(state,time).tempCommand;}public double getLowValueSelect(){return algebraic(state,time).lowSelect;}public double getValvePosition(){return state.valve;}public double getFuelFlow(){return state.fuel;}public double getRadiationShield(){return state.radiationLag;}public double getThermocouple(){return state.thermocouple;}public double getTurbineDynamics(){return state.gasLag;}public double getEffectiveMaxLimit(){return max;}public double getEffectiveMinLimit(){return min;}public double getGovernorState(){return state.speedState;}public double getEffectiveY(){return y;}public double getEffectiveCommandDelay(){return t;}public double getEffectiveExhaustDelay(){return etd;}public double getEffectiveGasDelay(){return tcd;}
    @Override public Map<String,Double> getNamedStates(){return Map.of("Speed Governor",getSpeedCommand(),"Valve Positioner",getValvePosition(),"Fuel System",getFuelFlow(),"Radiation Shield",getRadiationShield(),"Thermocouple",getThermocouple(),"Temp Control",getTemperatureCommand(),"Turbine Dynamics",getTurbineDynamics());}
    private static boolean finite(double...v){for(double x:v)if(!Double.isFinite(x))return false;return true;}private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private record Sample(double time,double value){}private record Algebraic(double speed,double governorInput,double speedCommand,double tempError,double tempCommand,double lowSelect,double delayedCommand,double combustor,double f1,double shield){}private record Deriv(double speedState,double valve,double fuel,double radiationLag,double thermocouple,double tempIntegral,double gasLag){static Deriv zero(){return new Deriv(0,0,0,0,0,0,0);}}
    private record State(double speedState,double valve,double fuel,double radiationLag,double thermocouple,double tempIntegral,double gasLag){static State zero(){return new State(0,0,0,0,0,0,0);}State withTempIntegral(double v){return new State(speedState,valve,fuel,radiationLag,thermocouple,v,gasLag);}State plus(Deriv d,double h){return new State(speedState+d.speedState*h,valve+d.valve*h,fuel+d.fuel*h,radiationLag+d.radiationLag*h,thermocouple+d.thermocouple*h,tempIntegral+d.tempIntegral*h,gasLag+d.gasLag*h);}State plusAverage(Deriv a,Deriv b,double h){return plus(new Deriv((a.speedState+b.speedState)/2,(a.valve+b.valve)/2,(a.fuel+b.fuel)/2,(a.radiationLag+b.radiationLag)/2,(a.thermocouple+b.thermocouple)/2,(a.tempIntegral+b.tempIntegral)/2,(a.gasLag+b.gasLag)/2),h);}}
}
