package org.interpss.dstab.control.exc.psse.exac4;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** IEEE Type AC4 high-initial-response excitation system (PSS/E EXAC4). */
@AnController(input="mach.vt", output="this.outputSignal", refPoint="this.reference", display={})
public final class Exac4Exciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS=1e-12;
    private static final int EFIELD=0, VSENSE=1, VLL=2;
    private final Exac4Data data;
    private final double[] state=new double[3], trial=new double[3], oldDerivative=new double[3];
    private double[] active=state;
    private boolean initialized;
    private double integrationStep, minimumTimeConstantMultiplier=1;

    public double tr,vimax,vimin,tc,tb,ka,ta,vrmax,vrmin,kc;
    public double reference,outputSignal,vuel,voel;

    public Exac4Exciter(String id,Exac4Data data,Machine machine){
        super(id,"EXAC4","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }
    public Exac4Data getData(){return data;}
    @Override public void configureIntegrationStep(double stepSeconds){configureIntegrationStep(stepSeconds,1);}
    public void configureIntegrationStep(double stepSeconds,double multiplier){integrationStep=stepSeconds;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrect();
        if(tr<0||tb<0||ta<0||ka<=EPS||kc<0)return false;
        double vt0=bus.getVoltageMag(),ifd0=exciterIfd(machine),efd0=machine.getEfd();
        if(!Double.isFinite(vt0)||!Double.isFinite(efd0))return false;
        double uncompensatedField=efd0+kc*ifd0;
        vrmax=Math.max(vrmax,uncompensatedField);vrmin=Math.min(vrmin,uncompensatedField);
        double vi0=efd0/ka;vimax=Math.max(vimax,vi0);vimin=Math.min(vimin,vi0);
        state[EFIELD]=efd0;state[VSENSE]=vt0;state[VLL]=transferState(vi0,vi0,tc,tb);
        System.arraycopy(state,0,trial,0,3);active=state;
        reference=vi0+vt0-stabilizerSignal(machine)-vuel-voel;
        outputSignal=efd0;initialized=true;return true;
    }

    private void loadAndCorrect(){
        tr=correctedBypass(data.getTr());vimax=Math.max(data.getVimax(),data.getVimin());
        vimin=Math.min(data.getVimax(),data.getVimin());tc=data.getTc();tb=correctedBypass(data.getTb());
        ka=data.getKa();ta=correctedRequired(data.getTa());vrmax=Math.max(data.getVrmax(),data.getVrmin());
        vrmin=Math.min(data.getVrmax(),data.getVrmin());kc=data.getKc();
    }
    private double minTime(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedBypass(double value){double min=minTime();if(value>0&&value<.5*min)return 0;if(value>.5*min&&value<min)return min;return value;}
    private double correctedRequired(double value){double min=minTime();return value>0&&value<min?min:value;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0){outputSignal=fieldOutput(active,machine);return true;}
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial,machine);active=trial;}
        else if(stage==1){double[] d=new double[3];derivatives(trial,d,machine);for(int i=0;i<3;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state,machine);active=state;}
        else{double[] d=new double[3];derivatives(state,d,machine);add(state,d,dt,state);constrain(state,machine);active=state;}
        outputSignal=fieldOutput(active,machine);return true;
    }

    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);
        dx[VSENSE]=tr>EPS?(machine.getDStabBus().getVoltageMag()-x[VSENSE])/tr:0;
        dx[VLL]=transferDerivative(a.vi,x[VLL],tc,tb);
        if(ta>EPS){double rate=(ka*a.vll-x[EFIELD])/ta;
            if((x[EFIELD]>=a.upper-EPS&&rate>0)||(x[EFIELD]<=a.lower+EPS&&rate<0))rate=0;
            dx[EFIELD]=rate;
        }
    }
    private Algebraic algebraics(double[] x,Machine machine){
        double vt=machine.getDStabBus().getVoltageMag(),sensed=tr>EPS?x[VSENSE]:vt;
        double error=reference+stabilizerSignal(machine)+vuel+voel-sensed;
        double vi=clamp(error,vimin,vimax),vll=transferOutput(vi,x[VLL],tc,tb);
        double compensation=kc*exciterIfd(machine),upper=vrmax-compensation,lower=vrmin-compensation;
        if(upper<lower){double midpoint=.5*(upper+lower);upper=midpoint;lower=midpoint;}
        return new Algebraic(sensed,error,vi,vll,lower,upper);
    }
    private double fieldOutput(double[] x,Machine machine){Algebraic a=algebraics(x,machine);return clamp(ta>EPS?x[EFIELD]:ka*a.vll,a.lower,a.upper);}
    private void constrain(double[] x,Machine machine){Algebraic a=algebraics(x,machine);if(ta>EPS)x[EFIELD]=clamp(x[EFIELD],a.lower,a.upper);for(int i=0;i<3;i++)if(!Double.isFinite(x[i]))x[i]=0;}
    private static double transferState(double input,double output,double tc,double tb){return tb>EPS?output-tc/tb*input:0;}
    private static double transferOutput(double input,double x,double tc,double tb){return tb>EPS?tc/tb*input+x:input;}
    private static double transferDerivative(double input,double x,double tc,double tb){return tb>EPS?((1-tc/tb)*input-x)/tb:0;}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private static double clamp(double value,double lower,double upper){return Math.max(lower,Math.min(upper,value));}
    private static double stabilizerSignal(Machine machine){return machine.getStabilizer()==null?0:machine.getStabilizer().getOutput(machine);}
    private static double exciterIfd(Machine machine){double v=machine.calculateIfd(MachineIfdBase.EXCITER);return Double.isFinite(v)?v:0;}

    public void setVuel(double value){vuel=value;} public void setVoel(double value){voel=value;}
    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getVoltageError(){return algebraics(active,getMachine()).error;}
    public double getLimitedError(){return algebraics(active,getMachine()).vi;}
    public double getLeadLagOutput(){return algebraics(active,getMachine()).vll;}
    public double getDynamicLowerLimit(){return algebraics(active,getMachine()).lower;}
    public double getDynamicUpperLimit(){return algebraics(active,getMachine()).upper;}
    public double getInternalFieldVoltage(){return active[EFIELD];}
    @Override public double getOutput(Machine machine){outputSignal=fieldOutput(active,machine);return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;} @Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double error,double vi,double vll,double lower,double upper){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
