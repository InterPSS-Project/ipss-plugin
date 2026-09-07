package org.interpss.dstab.control.exc.psse.dc4b;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;

/** IEEE 421.5 DC4B / PSS/E ESDC4B excitation system. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class Dc4bExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS=1e-12;
    private static final int EFD=0,VSENSE=1,PID_I=2,PID_D_LAG=3,VR=4,FB_LAG=5;
    private final Dc4bData data;
    private final String modelName;
    private final boolean speedMultiplier;
    private final double[] state=new double[6],trial=new double[6],oldDerivative=new double[6];
    private double[] active=state;
    private boolean initialized,hasVuel,hasVoel;
    private double integrationStep,minimumTimeConstantMultiplier=1,vuel,voel;

    public int oel,uel;
    public double tr,kp,ki,kd,td,vrmax,vrmin,ka,ta,ke,te,kf,tf,vemin,e1,se1,e2,se2;
    public double reference,outputSignal;

    public Dc4bExciter(String id,String modelName,Dc4bData data,Machine machine) {
        super(id,modelName,"PSS/E"); this.data=data; this._data=data;
        this.modelName=modelName;
        speedMultiplier="ESDC4B".equalsIgnoreCase(modelName)&&data.getSpdmlt()==1;
        setMachine(machine);
    }
    public Dc4bData getData(){return data;}
    public String getModelName(){return modelName;}
    public void setVuel(double value){vuel=value;hasVuel=true;} public double getVuel(){return vuel;}
    public void setVoel(double value){voel=value;hasVoel=true;} public double getVoel(){return voel;}
    @Override public void configureIntegrationStep(double step){configureIntegrationStep(step,1);}
    public void configureIntegrationStep(double step,double multiplier){integrationStep=step;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine) {
        loadAndCorrectParameters();
        if(tr<0||td<0||ta<0||te<0||tf<0||ka<=EPS)return false;
        double speed=safeSpeed(machine),efd0=machine.getEfd()/(speedMultiplier?speed:1);
        if(!Double.isFinite(efd0))return false;
        applyAutomaticDcParameters(efd0);
        double vr0=fieldFeedback(efd0),vt=machine.getDStabBus().getVoltageMag();
        if(!Double.isFinite(vr0)||vt<=EPS)return false;
        double normalizedVr=vr0/vt;
        vrmax=Math.max(vrmax,normalizedVr); vrmin=Math.min(vrmin,normalizedVr);
        state[EFD]=efd0;state[VSENSE]=vt;state[PID_I]=vr0/(ka*vt);
        state[PID_D_LAG]=0;state[VR]=vr0;state[FB_LAG]=efd0;
        System.arraycopy(state,0,trial,0,state.length);active=state;
        reference=state[VSENSE]-stabilizerSignal(machine)-directUel()+directOel();
        outputSignal=outputFromInternal(efd0,machine);initialized=true;return true;
    }

    private void loadAndCorrectParameters(){
        oel=data.getOel();uel=data.getUel();tr=correctedBypass(data.getTr());
        kp=data.getKp();ki=data.getKi();kd=data.getKd();td=correctedBypass(data.getTd());
        vrmax=Math.max(data.getVrmax(),data.getVrmin());vrmin=Math.min(data.getVrmax(),data.getVrmin());
        ka=data.getKa();ta=correctedMinimum(data.getTa());ke=data.getKe();te=correctedMinimum(data.getTe());
        kf=data.getKf();tf=correctedBypass(data.getTf());vemin=data.getVemin();
        e1=data.getE1();se1=data.getSe1();e2=data.getE2();se2=data.getSe2();
    }
    private void applyAutomaticDcParameters(double efd0){
        if(data.getVrmax()==0){vrmax=ke<=0?se2*e2:se2+ke;vrmin=-vrmax;}
        if(data.getKe()==0)ke=vrmax/(10*efd0)-saturation(efd0);
    }
    private double minimumResolvedTimeConstant(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedBypass(double v){double min=minimumResolvedTimeConstant();
        if(v>0&&v<.5*min)return 0;if(v>.5*min&&v<min)return min;return v;}
    private double correctedMinimum(double v){double min=minimumResolvedTimeConstant();return v>0&&v<min?min:v;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0)return true;
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);for(int i=0;i<6;i++)trial[i]=state[i]+oldDerivative[i]*dt;constrain(trial,machine);active=trial;}
        else if(stage==1){double[] corrected=new double[6];derivatives(trial,corrected,machine);for(int i=0;i<6;i++)state[i]+=.5*(oldDerivative[i]+corrected[i])*dt;constrain(state,machine);active=state;}
        else {double[] d=new double[6];derivatives(state,d,machine);for(int i=0;i<6;i++)state[i]+=d[i]*dt;constrain(state,machine);active=state;}
        outputSignal=outputFromInternal(field(active,machine),machine);return true;
    }
    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);double vt=machine.getDStabBus().getVoltageMag();
        dx[VSENSE]=lagDerivative(vt,x[VSENSE],tr);dx[PID_D_LAG]=lagDerivative(a.error,x[PID_D_LAG],td);
        double integralRate=ki*a.error;
        if((a.pidOutput>=vrmax/ka-EPS&&integralRate>0)||(a.pidOutput<=vrmin/ka+EPS&&integralRate<0))integralRate=0;
        dx[PID_I]=integralRate;
        double vrRate=ta>EPS?(ka*vt*a.gatedPid-x[VR])/ta:0,low=vt*vrmin,high=vt*vrmax;
        if((x[VR]>=high&&vrRate>0)||(x[VR]<=low&&vrRate<0))vrRate=0;dx[VR]=vrRate;
        double fieldRate=te>EPS?(a.regulator-fieldFeedback(x[EFD]))/te:0;
        if(x[EFD]<=vemin&&fieldRate<0)fieldRate=0;dx[EFD]=fieldRate;
        dx[FB_LAG]=lagDerivative(x[EFD],x[FB_LAG],tf);
    }
    private Algebraic algebraics(double[] x,Machine machine){
        double vt=machine.getDStabBus().getVoltageMag(),sensed=tr>EPS?x[VSENSE]:vt;
        double feedback=tf>EPS?kf*(x[EFD]-x[FB_LAG])/tf:0;
        double error=reference-sensed+stabilizerSignal(machine)+directUel()-directOel()-feedback;
        double derivative=td>EPS?kd*(error-x[PID_D_LAG])/td:0;
        double unlimited=kp*error+x[PID_I]+derivative;
        double pid=clamp(unlimited,vrmin/ka,vrmax/ka),gated=pid;
        if(uel==2&&hasVuel)gated=Math.max(gated,vuel);
        if(oel==2&&hasVoel)gated=Math.min(gated,voel);
        double regulator=ta>EPS?clamp(x[VR],vt*vrmin,vt*vrmax):clamp(ka*vt*gated,vt*vrmin,vt*vrmax);
        return new Algebraic(sensed,error,unlimited,pid,gated,regulator,feedback);
    }
    private double field(double[] x,Machine machine){return te>EPS?Math.max(vemin,x[EFD]):solveAlgebraicField(algebraics(x,machine).regulator,x[EFD]);}
    private double solveAlgebraicField(double regulator,double initial){
        double value=Math.max(vemin,initial);
        for(int i=0;i<30;i++){double residual=regulator-fieldFeedback(value);if(Math.abs(residual)<1e-11)break;
            double h=1e-6*Math.max(1,Math.abs(value));double slope=(fieldFeedback(value+h)-fieldFeedback(value-h))/(2*h);
            if(!Double.isFinite(slope)||Math.abs(slope)<=EPS)break;double next=value+residual/slope;if(!Double.isFinite(next))break;value=Math.max(vemin,next);}
        return Math.max(vemin,value);
    }
    private void constrain(double[] x,Machine machine){double vt=machine.getDStabBus().getVoltageMag();if(ta>EPS)x[VR]=clamp(x[VR],vt*vrmin,vt*vrmax);if(te>EPS)x[EFD]=Math.max(vemin,x[EFD]);}
    private double saturation(double efd){return Exac1Exciter.saturation(efd,e1,se1,e2,se2);}
    private double fieldFeedback(double efd){return efd*(ke+saturation(efd));}
    private double outputFromInternal(double efd,Machine machine){return speedMultiplier?efd*safeSpeed(machine):efd;}
    private double directUel(){return uel<2&&hasVuel?vuel:0;} private double directOel(){return oel<2&&hasVoel?voel:0;}
    private static double lagDerivative(double input,double state,double t){return t>EPS?(input-state)/t:0;}
    private static double clamp(double value,double low,double high){return Math.max(low,Math.min(high,value));}
    private static double safeSpeed(Machine m){double s=m.getSpeed();return Double.isFinite(s)&&Math.abs(s)>EPS?s:1;}
    private static double stabilizerSignal(Machine m){return m.getStabilizer()==null?0:m.getStabilizer().getOutput(m);}

    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getPidUnlimitedOutput(){return algebraics(active,getMachine()).pidUnlimited;}
    public double getPidOutput(){return algebraics(active,getMachine()).pidOutput;}
    public double getGatedPidOutput(){return algebraics(active,getMachine()).gatedPid;}
    public double getRegulatorOutput(){return algebraics(active,getMachine()).regulator;}
    public double getFeedbackOutput(){return algebraics(active,getMachine()).feedback;}
    public double getInternalFieldVoltage(){return field(active,getMachine());}
    @Override public double getOutput(Machine machine){outputSignal=outputFromInternal(field(active,machine),machine);return outputSignal;}
    @Override public void setRefPoint(double value){reference=value;} @Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double error,double pidUnlimited,double pidOutput,double gatedPid,double regulator,double feedback){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
