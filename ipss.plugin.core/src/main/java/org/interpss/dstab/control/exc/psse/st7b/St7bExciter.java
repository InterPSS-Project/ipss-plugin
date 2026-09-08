package org.interpss.dstab.control.exc.psse.st7b;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.ICMLMachineVoltageProvider;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;

/** IEEE 421.5-2005 ST7B excitation system and shared ST7 equation engine. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public class St7bExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS=1e-12;
    private static final int VSENSE=0,INPUT_LL=1,REG_LL=2,FEEDBACK=3,EFIELD=4;
    private final St7bData data;private final String modelName;
    private final double[] state=new double[5],trial=new double[5],oldDerivative=new double[5];
    private double[] active=state;private boolean initialized,vuelConfigured,voelConfigured;
    private double integrationStep,minimumTimeConstantMultiplier=1,ta;
    public int oel,uel;public double tr,tg,tf,vmax,vmin,kpa,vrmax,vrmin,kh,kl,tc,tb,kia,tia;
    public double reference,outputSignal,vuel,voel,vdroop,vscl;

    public St7bExciter(String id,St7bData data,Machine machine){this(id,"ST7B",data,machine);}
    protected St7bExciter(String id,String modelName,St7bData data,Machine machine){
        super(id,modelName,"IEEE");this.modelName=modelName;this.data=data;this._data=data;setMachine(machine);
    }
    public St7bData getData(){return data;}public String getModelName(){return modelName;}
    @Override public void configureIntegrationStep(double stepSeconds){configureIntegrationStep(stepSeconds,1);}
    public void configureIntegrationStep(double stepSeconds,double multiplier){integrationStep=stepSeconds;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrect();
        if(tr<0||tf<0||tb<0||tia<=EPS||kpa<=EPS||kia<=EPS||ta<0||!finiteParameters())return false;
        double vt0=bus.getVoltageMag(),sensed0=sensingVoltage(machine),efd0=machine.getEfd();
        if(vt0<=EPS||!Double.isFinite(sensed0)||!Double.isFinite(efd0))return false;
        double feedback0=kia*efd0,regulator0=efd0+feedback0;
        vrmax=Math.max(vrmax,Math.max(efd0/vt0,(regulator0+kl*feedback0)/vt0));
        vrmin=Math.min(vrmin,Math.min(efd0/vt0,(regulator0+kh*feedback0)/vt0));
        double error0=regulator0/kpa;
        double vrefFb0=sensed0+error0-stabilizerSignal(machine);
        vmax=Math.max(vmax,vrefFb0);vmin=Math.min(vmin,vrefFb0);
        double direct=(oel==1&&voelConfigured?voel:0)+(uel==1&&vuelConfigured?vuel:0);
        reference=vrefFb0-vdroop-vscl-direct;
        state[VSENSE]=sensed0;state[INPUT_LL]=transferState(sensed0,sensed0,tg,tf);
        state[REG_LL]=transferState(regulator0,regulator0,tc,tb);
        state[FEEDBACK]=feedback0;state[EFIELD]=efd0;
        System.arraycopy(state,0,trial,0,state.length);active=state;outputSignal=efd0;initialized=true;return true;
    }
    private void loadAndCorrect(){
        oel=normalizeSelector(data.getOel());uel=normalizeSelector(data.getUel());tr=correctedTransducer(data.getTr());
        tg=data.getTg();tf=data.getTf();vmax=Math.max(data.getVmax(),data.getVmin());vmin=Math.min(data.getVmax(),data.getVmin());
        kpa=data.getKpa();vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        kh=data.getKh();kl=data.getKl();tc=data.getTc();tb=data.getTb();kia=data.getKia();tia=data.getTia();
        ta=hasFiringController()?correctedFiring(rawFiringTimeConstant()):0;
        if(!voelConfigured)voel=oel>=2?Double.POSITIVE_INFINITY:0;
        if(!vuelConfigured)vuel=uel>=2?Double.NEGATIVE_INFINITY:0;
    }
    protected boolean hasFiringController(){return false;}
    protected double rawFiringTimeConstant(){return 0;}
    private static int normalizeSelector(int value){return value>=1&&value<=3?value:0;}
    private double minimumTime(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedTransducer(double value){double minimum=minimumTime();
        if(value>0&&value<.25*minimum)return 0;if(value>.25*minimum&&value<.5*minimum)return .5*minimum;return value;}
    private double correctedFiring(double value){double minimum=minimumTime();
        if(value>0&&value<.5*minimum)return 0;if(value>.5*minimum&&value<minimum)return minimum;return value;}
    private boolean finiteParameters(){double[] values={tr,tg,tf,vmax,vmin,kpa,vrmax,vrmin,kh,kl,tc,tb,kia,tia,ta};
        for(double value:values)if(!Double.isFinite(value))return false;return true;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0){outputSignal=output(active,machine);return true;}
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial,machine);active=trial;}
        else if(stage==1){double[] correctedDerivative=new double[state.length];derivatives(trial,correctedDerivative,machine);
            for(int i=0;i<state.length;i++)state[i]+=.5*(oldDerivative[i]+correctedDerivative[i])*dt;
            constrain(state,machine);active=state;}
        else{double[] derivative=new double[state.length];derivatives(state,derivative,machine);add(state,derivative,dt,state);constrain(state,machine);active=state;}
        outputSignal=output(active,machine);return true;
    }
    private void derivatives(double[] x,double[] derivative,Machine machine){
        Arrays.fill(derivative,0);Algebraic a=algebraics(x,machine);
        derivative[VSENSE]=tr>EPS?(sensingVoltage(machine)-x[VSENSE])/tr:0;
        derivative[INPUT_LL]=transferDerivative(a.sensed,x[INPUT_LL],tg,tf);
        derivative[REG_LL]=transferDerivative(a.regulator,x[REG_LL],tc,tb);
        derivative[FEEDBACK]=(kia*a.efd-x[FEEDBACK])/tia;
        if(ta>EPS)derivative[EFIELD]=(a.preField-x[EFIELD])/ta;
    }
    private Algebraic algebraics(double[] x,Machine machine){
        double vt=machine.getDStabBus().getVoltageMag();
        double sensed=tr>EPS?x[VSENSE]:sensingVoltage(machine);
        double inputLeadLag=transferOutput(sensed,x[INPUT_LL],tg,tf);
        double rawReference=reference+vdroop+vscl;
        if(oel==1&&voelConfigured)rawReference+=voel;if(uel==1&&vuelConfigured)rawReference+=vuel;
        double inputLowGate=oel==2?Math.min(rawReference,voel):rawReference;
        double inputHighGate=uel==2?Math.max(inputLowGate,vuel):inputLowGate;
        double referenceFeedback=clamp(inputHighGate,vmin,vmax);
        double error=referenceFeedback+stabilizerSignal(machine)-inputLeadLag;
        double amplifier=kpa*error,feedback=x[FEEDBACK];
        double lower=vt*vrmin-kh*feedback,upper=vt*vrmax-kl*feedback;
        double regulator=Math.min(Math.max(amplifier,lower),upper);
        double secondLeadLag=transferOutput(regulator,x[REG_LL],tc,tb);
        double preField=secondLeadLag-feedback;
        if(oel==3)preField=Math.min(preField,voel);if(uel==3)preField=Math.max(preField,vuel);
        preField=clamp(preField,vt*vrmin,vt*vrmax);
        double efd=ta>EPS?clamp(x[EFIELD],vt*vrmin,vt*vrmax):preField;
        return new Algebraic(sensed,inputLeadLag,referenceFeedback,error,amplifier,regulator,
                secondLeadLag,feedback,preField,efd);
    }
    private double output(double[] x,Machine machine){return algebraics(x,machine).efd;}
    private void constrain(double[] x,Machine machine){
        if(ta>EPS){double vt=machine.getDStabBus().getVoltageMag();x[EFIELD]=clamp(x[EFIELD],vt*vrmin,vt*vrmax);}
        for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;
    }
    private static double transferState(double input,double output,double lead,double lag){return lag>EPS?output-lead/lag*input:0;}
    private static double transferOutput(double input,double stateValue,double lead,double lag){return lag>EPS?lead/lag*input+stateValue:input;}
    private static double transferDerivative(double input,double stateValue,double lead,double lag){return lag>EPS?((1-lead/lag)*input-stateValue)/lag:0;}
    private static void add(double[] x,double[] derivative,double dt,double[] result){for(int i=0;i<x.length;i++)result[i]=x[i]+derivative[i]*dt;}
    private static double clamp(double value,double lower,double upper){return Math.max(lower,Math.min(upper,value));}
    private static double stabilizerSignal(Machine machine){return machine.getStabilizer()==null?0:machine.getStabilizer().getOutput(machine);}
    private static double sensingVoltage(Machine machine){
        if(machine instanceof ICMLMachineVoltageProvider provider){double value=provider.getCmlMachineVoltage();if(Double.isFinite(value))return value;}
        return machine.getDStabBus().getVoltageMag();
    }

    public void setVuel(double value){vuel=value;vuelConfigured=true;}public void setVoel(double value){voel=value;voelConfigured=true;}
    public void clearVuel(){vuelConfigured=false;}public void clearVoel(){voelConfigured=false;}
    public void setVdroop(double value){vdroop=value;}public void setVscl(double value){vscl=value;}
    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getInputLeadLag(){return algebraics(active,getMachine()).inputLeadLag;}
    public double getReferenceFeedback(){return algebraics(active,getMachine()).referenceFeedback;}
    public double getVoltageError(){return algebraics(active,getMachine()).error;}
    public double getAmplifierOutput(){return algebraics(active,getMachine()).amplifier;}
    public double getRegulatorOutput(){return algebraics(active,getMachine()).regulator;}
    public double getSecondLeadLagOutput(){return algebraics(active,getMachine()).secondLeadLag;}
    public double getFeedbackOutput(){return algebraics(active,getMachine()).feedback;}
    public double getPreFiringField(){return algebraics(active,getMachine()).preField;}
    public double getInternalFieldVoltage(){return output(active,getMachine());}
    public double getFiringTimeConstant(){return ta;}public double[] getStateSnapshot(){return active.clone();}
    @Override public double getOutput(Machine machine){outputSignal=output(active,machine);return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;}@Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double inputLeadLag,double referenceFeedback,double error,
            double amplifier,double regulator,double secondLeadLag,double feedback,double preField,double efd){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
