package org.interpss.dstab.control.exc.psse.dc3a;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;

/** IEEE 421.5-2005 Type DC3A rheostatic excitation system. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class Dc3aExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS=1e-12;
    private static final int EFD=0,VSENSE=1,VRH=2;
    private final Dc3aData data;private final String modelName;
    private final double[] state=new double[3],trial=new double[3],oldDerivative=new double[3];
    private double[] active=state;private boolean initialized,esdc3a;
    private double integrationStep,minimumTimeConstantMultiplier=1;
    public double tr,kv,vrmax,vrmin,trh,te,ke,vemin,e1,se1,e2,se2,spdmlt;
    public int exclim;public double reference,outputSignal;
    public Dc3aExciter(String id,String modelName,Dc3aData data,Machine machine){super(id,modelName,"IEEE");this.modelName=modelName;this.esdc3a="ESDC3A".equalsIgnoreCase(modelName);this.data=data;this._data=data;setMachine(machine);}
    public Dc3aData getData(){return data;}public String getModelName(){return modelName;}
    @Override public void configureIntegrationStep(double stepSeconds){configureIntegrationStep(stepSeconds,1);}
    public void configureIntegrationStep(double stepSeconds,double multiplier){integrationStep=stepSeconds;minimumTimeConstantMultiplier=multiplier;}
    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrect();if(tr<0||trh<0||te<=EPS)return false;
        double speed=speedMultiplier(machine),efd0=machine.getEfd()/speed,vt0=machine.getDStabBus().getVoltageMag();
        if(!Double.isFinite(efd0)||!Double.isFinite(vt0))return false;
        double vr0=fieldFeedback(efd0);vrmax=Math.max(vrmax,vr0);vrmin=Math.min(vrmin,vr0);
        double lower=fieldLowerLimit();if(efd0<lower-EPS)return false;
        state[EFD]=efd0;state[VSENSE]=vt0;state[VRH]=vr0;System.arraycopy(state,0,trial,0,3);active=state;
        reference=vt0;outputSignal=efd0*speed;initialized=true;return true;
    }
    private void loadAndCorrect(){
        tr=correctedBypass(data.getTr());kv=Math.max(0,data.getKv());
        vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        trh=Math.max(0,data.getTrh());te=correctedRequired(data.getTe());ke=data.getKe();
        vemin=data.getVemin();e1=data.getE1();se1=data.getSe1();e2=data.getE2();se2=data.getSe2();
        spdmlt=data.getSpdmlt();exclim=data.getExclim();
    }
    private double correctedBypass(double value){double min=minimumTimeConstantMultiplier*integrationStep;if(value>0&&value<.5*min)return 0;if(value>.5*min&&value<min)return min;return value;}
    private double correctedRequired(double value){double min=minimumTimeConstantMultiplier*integrationStep;return value>0&&value<min?min:value;}
    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0)return true;int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);for(int i=0;i<3;i++)trial[i]=state[i]+oldDerivative[i]*dt;constrain(trial);active=trial;}
        else if(stage==1){double[] d=new double[3];derivatives(trial,d,machine);for(int i=0;i<3;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state);active=state;}
        else{double[] d=new double[3];derivatives(state,d,machine);for(int i=0;i<3;i++)state[i]+=d[i]*dt;constrain(state);active=state;}
        outputSignal=active[EFD]*speedMultiplier(machine);return true;
    }
    private void derivatives(double[] x,double[] d,Machine machine){
        Arrays.fill(d,0);double vt=machine.getDStabBus().getVoltageMag(),sensed=tr>EPS?x[VSENSE]:vt,error=reference-sensed;
        d[VSENSE]=tr>EPS?(vt-x[VSENSE])/tr:0;
        double rheostatRate=kv>EPS&&trh>EPS?(vrmax-vrmin)*error/(kv*trh):0;
        if((x[VRH]>=vrmax-EPS&&rheostatRate>0)||(x[VRH]<=vrmin+EPS&&rheostatRate<0))rheostatRate=0;d[VRH]=rheostatRate;
        double vr=regulatorOutput(error,x[VRH]),fieldRate=(vr-fieldFeedback(x[EFD]))/te;
        if(x[EFD]<=fieldLowerLimit()+EPS&&fieldRate<0)fieldRate=0;d[EFD]=fieldRate;
    }
    private double regulatorOutput(double error,double vrh){
        if(kv<=EPS){if(error>0)return vrmax;if(error<0)return vrmin;return clamp(vrh,vrmin,vrmax);}
        if(error>=kv)return vrmax;if(error<=-kv)return vrmin;return clamp(vrh,vrmin,vrmax);
    }
    private double fieldFeedback(double efd){return efd*(ke+Exac1Exciter.saturation(efd,e1,se1,e2,se2));}
    private double fieldLowerLimit(){return esdc3a?(exclim!=0?0:Double.NEGATIVE_INFINITY):vemin;}
    private double speedMultiplier(Machine machine){return esdc3a&&spdmlt==1?machine.getSpeed():1;}
    private void constrain(double[] x){x[VRH]=clamp(x[VRH],vrmin,vrmax);x[EFD]=Math.max(fieldLowerLimit(),x[EFD]);for(int i=0;i<3;i++)if(!Double.isFinite(x[i]))x[i]=0;}
    private static double clamp(double value,double lower,double upper){return Math.max(lower,Math.min(upper,value));}
    public double getSensedVoltage(){return tr>EPS?active[VSENSE]:getMachine().getDStabBus().getVoltageMag();}
    public double getVoltageError(){return reference-getSensedVoltage();}public double getRheostatPosition(){return active[VRH];}
    public double getRegulatorOutput(){return regulatorOutput(getVoltageError(),active[VRH]);}
    public double getInternalFieldVoltage(){return active[EFD];}public double getSaturation(){return Exac1Exciter.saturation(active[EFD],e1,se1,e2,se2);}
    @Override public double getOutput(Machine machine){outputSignal=active[EFD]*speedMultiplier(machine);return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;}@Override public double getRefPoint(){return reference;}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}@Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
