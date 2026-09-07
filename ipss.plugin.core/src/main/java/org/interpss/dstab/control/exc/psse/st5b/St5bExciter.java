package org.interpss.dstab.control.exc.psse.st5b;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** IEEE 421.5-2005 ST5B static potential-source excitation system. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class St5bExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS=1e-12;
    private static final int EFD=0,VSENSE=1,N1=2,N2=3,U1=4,U2=5,O1=6,O2=7;
    private final St5bData data;
    private final String modelName;
    private final double[] state=new double[8],trial=new double[8],oldDerivative=new double[8];
    private double[] active=state;
    private boolean initialized,hasVuel,hasVoel;
    private double vuel,voel,integrationStep,minimumTimeConstantMultiplier=1.0;
    public double tr,tc1,tb1,tc2,tb2,kr,vrmax,vrmin,t1,kc;
    public double tuc1,tub1,tuc2,tub2,toc1,tob1,toc2,tob2;
    public double reference,outputSignal;

    public St5bExciter(String id,String modelName,St5bData data,Machine machine){
        super(id,modelName,"IEEE");this.modelName=modelName;this.data=data;this._data=data;setMachine(machine);
    }
    public String getModelName(){return modelName;}
    public St5bData getData(){return data;}
    public void setVuel(double value){vuel=value;hasVuel=true;}
    public void setVoel(double value){voel=value;hasVoel=true;}
    public double getVuel(){return vuel;}public double getVoel(){return voel;}
    @Override public void configureIntegrationStep(double stepSeconds){configureIntegrationStep(stepSeconds,1.0);}
    public void configureIntegrationStep(double stepSeconds,double multiplier){integrationStep=stepSeconds;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrect();
        if(tr<0||tb1<0||tb2<0||tub1<0||tub2<0||tob1<0||tob2<0||t1<0||kr<=EPS)return false;
        double efd0=machine.getEfd(),vt0=machine.getDStabBus().getVoltageMag();
        double ifd0=fieldCurrent(machine),vr0=efd0+kc*ifd0;
        if(!Double.isFinite(vr0)||!Double.isFinite(vt0))return false;
        vrmax=Math.max(vrmax,vr0);vrmin=Math.min(vrmin,vr0);
        double path0=vr0/kr;
        state[EFD]=efd0;state[VSENSE]=vt0;
        for(int i=N1;i<=O2;i++)state[i]=path0;
        System.arraycopy(state,0,trial,0,state.length);active=state;
        reference=path0+vt0;outputSignal=efd0;initialized=true;return true;
    }
    private void loadAndCorrect(){
        tr=correctedBypass(data.getTr());tc1=data.getTc1();tb1=correctedBypass(data.getTb1());
        tc2=data.getTc2();tb2=correctedBypass(data.getTb2());kr=data.getKr();
        vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        t1=data.getT1();kc=data.getKc();tuc1=data.getTuc1();tub1=correctedBypass(data.getTub1());
        tuc2=data.getTuc2();tub2=correctedBypass(data.getTub2());toc1=data.getToc1();
        tob1=correctedBypass(data.getTob1());toc2=data.getToc2();tob2=correctedBypass(data.getTob2());
    }
    private double correctedBypass(double value){
        double minimum=minimumTimeConstantMultiplier*integrationStep;
        if(value>0&&value<.5*minimum)return 0;
        if(value>.5*minimum&&value<minimum)return minimum;
        return value;
    }

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0)return true;
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);for(int i=0;i<8;i++)trial[i]=state[i]+oldDerivative[i]*dt;constrain(trial,machine);active=trial;}
        else if(stage==1){double[] d=new double[8];derivatives(trial,d,machine);for(int i=0;i<8;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state,machine);active=state;}
        else{double[] d=new double[8];derivatives(state,d,machine);for(int i=0;i<8;i++)state[i]+=d[i]*dt;constrain(state,machine);active=state;}
        outputSignal=algebraics(active,machine).efd;return true;
    }
    private void derivatives(double[] x,double[] d,Machine machine){
        Arrays.fill(d,0);Algebraic a=algebraics(x,machine);
        d[VSENSE]=lagDerivative(machine.getDStabBus().getVoltageMag(),x[VSENSE],tr);
        leadLagDerivative(x,d,N1,a.input,tc1,tb1);leadLagDerivative(x,d,N2,a.normal1,tc2,tb2);
        leadLagDerivative(x,d,U1,a.input,tuc1,tub1);leadLagDerivative(x,d,U2,a.under1,tuc2,tub2);
        leadLagDerivative(x,d,O1,a.input,toc1,tob1);leadLagDerivative(x,d,O2,a.over1,toc2,tob2);
        double rate=t1>EPS?(a.finalInput-x[EFD])/t1:0;
        double upper=machine.getDStabBus().getVoltageMag()*vrmax,lower=machine.getDStabBus().getVoltageMag()*vrmin;
        if((x[EFD]>=upper-EPS&&rate>0)||(x[EFD]<=lower+EPS&&rate<0))rate=0;d[EFD]=rate;
    }
    private void leadLagDerivative(double[] x,double[] d,int index,double input,double tc,double tb){
        if(tb<=EPS)return;double rate=(input-x[index])/tb;
        double output=leadLagOutput(input,x[index],tc,tb);
        double scale=1-tc/tb;
        if((output>=vrmax/kr-EPS&&scale*rate>0)||(output<=vrmin/kr+EPS&&scale*rate<0))rate=0;
        d[index]=rate;
    }
    private Algebraic algebraics(double[] x,Machine machine){
        double vt=machine.getDStabBus().getVoltageMag(),sensed=tr>EPS?x[VSENSE]:vt;
        double error=reference-sensed;
        double gated=error;if(hasVuel)gated=Math.max(gated,vuel);if(hasVoel)gated=Math.min(gated,voel);
        double input=gated+stabilizerSignal(machine);
        double normal1=limitedLeadLag(input,x[N1],tc1,tb1),normal2=limitedLeadLag(normal1,x[N2],tc2,tb2);
        double under1=limitedLeadLag(input,x[U1],tuc1,tub1),under2=limitedLeadLag(under1,x[U2],tuc2,tub2);
        double over1=limitedLeadLag(input,x[O1],toc1,tob1),over2=limitedLeadLag(over1,x[O2],toc2,tob2);
        int selector=hasVoel&&voel<error?1:hasVuel&&vuel>error?-1:0;
        double selected=selector>0?over2:selector<0?under2:normal2;
        double vr=clamp(kr*selected,vrmin,vrmax),finalInput=vr-kc*fieldCurrent(machine);
        double efd=t1>EPS?x[EFD]:clamp(finalInput,vt*vrmin,vt*vrmax);
        return new Algebraic(sensed,error,gated,input,normal1,normal2,under1,under2,over1,over2,selector,vr,finalInput,efd);
    }
    private double limitedLeadLag(double input,double stateValue,double tc,double tb){
        return clamp(leadLagOutput(input,stateValue,tc,tb),vrmin/kr,vrmax/kr);
    }
    private static double leadLagOutput(double input,double stateValue,double tc,double tb){
        return tb>EPS?(tc/tb)*input+(1-tc/tb)*stateValue:input;
    }
    private void constrain(double[] x,Machine machine){
        double vt=machine.getDStabBus().getVoltageMag();x[EFD]=clamp(x[EFD],vt*vrmin,vt*vrmax);
        for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;
    }
    private static double lagDerivative(double input,double value,double timeConstant){return timeConstant>EPS?(input-value)/timeConstant:0;}
    private static double fieldCurrent(Machine machine){double v=machine.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}
    private static double stabilizerSignal(Machine machine){return machine.getStabilizer()==null?0:machine.getStabilizer().getOutput(machine);}
    private static double clamp(double value,double lower,double upper){return Math.max(lower,Math.min(upper,value));}

    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getVoltageError(){return algebraics(active,getMachine()).error;}
    public double getGatedError(){return algebraics(active,getMachine()).gated;}
    public int getSelectedPath(){return algebraics(active,getMachine()).selector;}
    public double getSelectedPathOutput(){Algebraic a=algebraics(active,getMachine());return a.selector>0?a.over2:a.selector<0?a.under2:a.normal2;}
    public double getRegulatorOutput(){return algebraics(active,getMachine()).vr;}
    public double getFinalLagInput(){return algebraics(active,getMachine()).finalInput;}
    @Override public double getOutput(Machine machine){outputSignal=algebraics(active,machine).efd;return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;}@Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double error,double gated,double input,double normal1,double normal2,
            double under1,double under2,double over1,double over2,int selector,double vr,double finalInput,double efd){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
