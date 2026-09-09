package org.interpss.dstab.control.exc.psse.dc4c;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.mach.Machine;

/** IEEE 421.5-2016 DC4C dc-commutator excitation system with PID regulator. */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class Dc4cExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS=1e-12;
    private static final int SWITCH_A=1;
    private static final int EFD=0,VSENSE=1,PI_I=2,DERIV_LAG=3,VR=4,FB_LAG=5;
    private final Dc4cData data;
    private final double[] state=new double[6],trial=new double[6],oldDerivative=new double[6];
    private double[] active=state;
    private boolean initialized,hasVuel,hasVoel,hasVsclSum,hasVsclUel,hasVsclOel;
    private double integrationStep,minimumTimeConstantMultiplier=1;
    private double vuel,voel,vsclSum,vsclUel,vsclOel;

    public int oel,uel,scl,sw1;
    public double tr,kpr,kir,kdr,tdr,vrmax,vrmin,ka,ta,ke,te,kf,tf,vemin;
    public double e1,se1,e2,se2,kp,ki,xl,thetaP,kc1,vbmax,spdmlt,reference,outputSignal;

    public Dc4cExciter(String id,Dc4cData data,Machine machine){
        super(id,"DC4C","PSS/E");this.data=data;this._data=data;setMachine(machine);
    }
    public Dc4cData getData(){return data;}
    public void setVuel(double v){vuel=v;hasVuel=true;} public double getVuel(){return vuel;}
    public void setVoel(double v){voel=v;hasVoel=true;} public double getVoel(){return voel;}
    public void setVsclSum(double v){vsclSum=v;hasVsclSum=true;}
    public void setVsclUel(double v){vsclUel=v;hasVsclUel=true;}
    public void setVsclOel(double v){vsclOel=v;hasVsclOel=true;}
    @Override public void configureIntegrationStep(double step){configureIntegrationStep(step,1);}
    public void configureIntegrationStep(double step,double multiplier){integrationStep=step;minimumTimeConstantMultiplier=multiplier;}

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        loadAndCorrectParameters();
        if(tr<0||tdr<0||ta<0||te<0||tf<0||ka<=EPS||vbmax<0||!validSelector(oel)
                ||!validSelector(uel)||!validSelector(scl)||(sw1!=1&&sw1!=2))return false;
        double speed=safeSpeed(machine),efd0=machine.getEfd()/(spdmlt!=0?speed:1);
        if(!Double.isFinite(efd0)||efd0<vemin-EPS)return false;
        double vfe0=fieldFeedback(efd0),vb0=availableSupply(machine,vfe0);
        if(!Double.isFinite(vb0)||vb0<=EPS)return false;
        double vr0=vfe0/vb0,vt=machine.getDStabBus().getVoltageMag();
        if(!Double.isFinite(vr0)||vt<=EPS)return false;
        vrmax=Math.max(vrmax,vr0);vrmin=Math.min(vrmin,vr0);
        state[EFD]=efd0;state[VSENSE]=vt;state[PI_I]=vr0/ka;state[DERIV_LAG]=0;
        state[VR]=vr0;state[FB_LAG]=efd0;
        System.arraycopy(state,0,trial,0,state.length);active=state;
        reference=vt-stabilizerSignal(machine)-directUel()+directOel()-directScl();
        outputSignal=outputFromInternal(efd0,machine);initialized=true;return true;
    }

    private void loadAndCorrectParameters(){
        oel=data.getOel();uel=data.getUel();scl=data.getScl();sw1=data.getSw1();
        tr=correctedBypass(data.getTr());kpr=data.getKpr();kir=data.getKir();kdr=data.getKdr();
        tdr=correctedBypass(data.getTdr());vrmax=Math.max(data.getVrmax(),data.getVrmin());
        vrmin=Math.min(data.getVrmax(),data.getVrmin());ka=data.getKa();ta=correctedMinimum(data.getTa());
        ke=data.getKe();te=correctedMinimum(data.getTe());kf=data.getKf();tf=correctedBypass(data.getTf());
        vemin=data.getVemin();e1=data.getE1();se1=data.getSe1();e2=data.getE2();se2=data.getSe2();
        kp=data.getKp();ki=data.getKi();xl=data.getXl();thetaP=data.getThetaP();
        kc1=data.getKc1();vbmax=data.getVbmax();spdmlt=data.getSpdmlt();
    }
    private double minimumResolvedTimeConstant(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedBypass(double v){double min=minimumResolvedTimeConstant();
        if(v>0&&v<.5*min)return 0;if(v>.5*min&&v<min)return min;return v;}
    private double correctedMinimum(double v){double min=minimumResolvedTimeConstant();return v>0&&v<min?min:v;}

    @Override public boolean nextStep(double dt,DynamicSimuMethod method,Machine machine,int flag){
        if(!initialized||dt<0)return false;if(dt==0)return true;
        int stage=method==DynamicSimuMethod.MODIFIED_EULER?flag:2;
        if(stage==0){derivatives(state,oldDerivative,machine);add(state,oldDerivative,dt,trial);constrain(trial);active=trial;}
        else if(stage==1){double[] d=new double[6];derivatives(trial,d,machine);for(int i=0;i<6;i++)state[i]+=.5*(oldDerivative[i]+d[i])*dt;constrain(state);active=state;}
        else{double[] d=new double[6];derivatives(state,d,machine);for(int i=0;i<6;i++)state[i]+=d[i]*dt;constrain(state);active=state;}
        outputSignal=outputFromInternal(field(active,machine),machine);return true;
    }
    private void derivatives(double[] x,double[] dx,Machine machine){
        Arrays.fill(dx,0);Algebraic a=algebraics(x,machine);double vt=machine.getDStabBus().getVoltageMag();
        dx[VSENSE]=lagDerivative(vt,x[VSENSE],tr);dx[DERIV_LAG]=lagDerivative(a.error,x[DERIV_LAG],tdr);
        double integralRate=kir*a.error;
        if((a.pidUnlimited>=vrmax/ka-EPS&&integralRate>0)||(a.pidUnlimited<=vrmin/ka+EPS&&integralRate<0))integralRate=0;
        dx[PI_I]=integralRate;
        double vrRate=ta>EPS?(ka*a.gatedPid-x[VR])/ta:0;
        if((x[VR]>=vrmax-EPS&&vrRate>0)||(x[VR]<=vrmin+EPS&&vrRate<0))vrRate=0;dx[VR]=vrRate;
        double efdRate=te>EPS?(a.efe-fieldFeedback(x[EFD]))/te:0;
        if(x[EFD]<=vemin+EPS&&efdRate<0)efdRate=0;dx[EFD]=efdRate;
        dx[FB_LAG]=lagDerivative(x[EFD],x[FB_LAG],tf);
    }
    private Algebraic algebraics(double[] x,Machine machine){
        double vt=machine.getDStabBus().getVoltageMag(),sensed=tr>EPS?x[VSENSE]:vt;
        double field=te>EPS?Math.max(vemin,x[EFD]):solveAlgebraicField(x,machine);
        double feedback=tf>EPS?kf*(field-x[FB_LAG])/tf:0;
        double error=reference-sensed+stabilizerSignal(machine)+directUel()-directOel()+directScl()-feedback;
        double derivative=tdr>EPS?kdr*(error-x[DERIV_LAG])/tdr:0;
        double unlimited=kpr*error+x[PI_I]+derivative;
        double pid=clamp(unlimited,vrmin/ka,vrmax/ka),gated=pid;
        if(uel==2&&hasVuel)gated=Math.max(gated,vuel);
        if(scl==2&&hasVsclUel)gated=Math.max(gated,vsclUel);
        if(oel==2&&hasVoel)gated=Math.min(gated,voel);
        if(scl==2&&hasVsclOel)gated=Math.min(gated,vsclOel);
        double vr=ta>EPS?clamp(x[VR],vrmin,vrmax):clamp(ka*gated,vrmin,vrmax);
        double vfe=fieldFeedback(field),supply=availableSupply(machine,vfe),efe=vr*supply;
        return new Algebraic(sensed,field,vfe,error,unlimited,pid,gated,vr,supply,efe,feedback);
    }
    private double solveAlgebraicField(double[] x,Machine machine){
        double value=Math.max(vemin,Double.isFinite(x[EFD])?x[EFD]:vemin);
        for(int i=0;i<40;i++){double residual=fieldResidual(value,x,machine);if(Math.abs(residual)<1e-11)break;
            double h=1e-6*Math.max(1,Math.abs(value));double slope=(fieldResidual(value+h,x,machine)-fieldResidual(value-h,x,machine))/(2*h);
            if(!Double.isFinite(slope)||Math.abs(slope)<=EPS)break;double next=value-residual/slope;
            if(!Double.isFinite(next))break;value=Math.max(vemin,next);}
        return Math.max(vemin,value);
    }
    private double fieldResidual(double field,double[] x,Machine machine){
        double vfe=fieldFeedback(field);
        double sensed=tr>EPS?x[VSENSE]:machine.getDStabBus().getVoltageMag();
        double feedback=tf>EPS?kf*(field-x[FB_LAG])/tf:0;
        double error=reference-sensed+stabilizerSignal(machine)+directUel()-directOel()+directScl()-feedback;
        double derivative=tdr>EPS?kdr*(error-x[DERIV_LAG])/tdr:0;
        double gated=clamp(kpr*error+x[PI_I]+derivative,vrmin/ka,vrmax/ka);
        if(uel==2&&hasVuel)gated=Math.max(gated,vuel);if(scl==2&&hasVsclUel)gated=Math.max(gated,vsclUel);
        if(oel==2&&hasVoel)gated=Math.min(gated,voel);if(scl==2&&hasVsclOel)gated=Math.min(gated,vsclOel);
        double vr=ta>EPS?clamp(x[VR],vrmin,vrmax):clamp(ka*gated,vrmin,vrmax);
        return vr*availableSupply(machine,vfe)-vfe;
    }
    private double compoundSource(Machine machine){
        Complex angle=new Complex(Math.cos(Math.toRadians(thetaP)),Math.sin(Math.toRadians(thetaP)));
        Complex kpPhasor=angle.multiply(kp),vt=machine.getDStabBus().getVoltage();
        Complex it=machine.getIxy().divide(machine.getIMultiFactor());
        return kpPhasor.multiply(vt).add(Complex.I.multiply(new Complex(ki,0).add(kpPhasor.multiply(xl))).multiply(it)).abs();
    }
    private double selectedSource(Machine machine){return sw1==SWITCH_A?compoundSource(machine):Math.abs(kp);}
    private double availableSupply(Machine machine,double vfe){double source=selectedSource(machine);if(source<=EPS)return 0;
        return clamp(source*Exac1Exciter.rectifierFactor(kc1*vfe/source),0,vbmax);}
    private double fieldFeedback(double efd){return efd*(ke+Exac1Exciter.saturation(efd,e1,se1,e2,se2));}
    private void constrain(double[] x){if(ta>EPS)x[VR]=clamp(x[VR],vrmin,vrmax);if(te>EPS)x[EFD]=Math.max(vemin,x[EFD]);for(int i=0;i<x.length;i++)if(!Double.isFinite(x[i]))x[i]=0;}
    private double directUel(){return uel<2&&hasVuel?vuel:0;} private double directOel(){return oel<2&&hasVoel?voel:0;}
    private double directScl(){return scl<2&&hasVsclSum?vsclSum:0;}
    private double outputFromInternal(double efd,Machine machine){return spdmlt!=0?efd*safeSpeed(machine):efd;}
    private static boolean validSelector(int v){return v>=0&&v<=2;} private static double lagDerivative(double in,double x,double t){return t>EPS?(in-x)/t:0;}
    private static void add(double[] x,double[] d,double dt,double[] y){for(int i=0;i<x.length;i++)y[i]=x[i]+d[i]*dt;}
    private static double clamp(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static double safeSpeed(Machine m){double s=m.getSpeed();return Double.isFinite(s)&&Math.abs(s)>EPS?s:1;}
    private static double stabilizerSignal(Machine m){return m.getStabilizer()==null?0:m.getStabilizer().getOutput(m);}

    public double getSensedVoltage(){return algebraics(active,getMachine()).sensed;}
    public double getInternalFieldVoltage(){return algebraics(active,getMachine()).field;}
    public double getFieldFeedback(){return algebraics(active,getMachine()).vfe;}
    public double getPidError(){return algebraics(active,getMachine()).error;}
    public double getPidIntegralState(){return active[PI_I];}
    public double getProportionalIntegralOutput(){return kpr*getPidError()+active[PI_I];}
    public double getPidUnlimitedOutput(){return algebraics(active,getMachine()).pidUnlimited;}
    public double getPidOutput(){return algebraics(active,getMachine()).pid;}
    public double getDerivativeOutput(){Algebraic a=algebraics(active,getMachine());
        return tdr>EPS?kdr*(a.error-active[DERIV_LAG])/tdr:0;}
    public double getGatedPidOutput(){return algebraics(active,getMachine()).gatedPid;}
    public double getRegulatorOutput(){return algebraics(active,getMachine()).vr;}
    public double getCompoundSource(){return compoundSource(getMachine());}
    public double getSelectedSource(){return selectedSource(getMachine());}
    public double getAvailableSupply(){return algebraics(active,getMachine()).supply;}
    public double getExciterInput(){return algebraics(active,getMachine()).efe;}
    public double getFeedbackOutput(){return algebraics(active,getMachine()).feedback;}
    @Override public double getOutput(Machine machine){outputSignal=outputFromInternal(field(active,machine),machine);return outputSignal;}
    private double field(double[] x,Machine machine){return algebraics(x,machine).field;}
    @Override public void setRefPoint(double value){reference=value;} @Override public double getRefPoint(){return reference;}
    private record Algebraic(double sensed,double field,double vfe,double error,double pidUnlimited,
            double pid,double gatedPid,double vr,double supply,double efe,double feedback){}
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
