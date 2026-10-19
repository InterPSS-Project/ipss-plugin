package org.interpss.dstab.renewable;

import static org.interpss.dstab.renewable.HplntdData.Parameter.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;

/** Thirteen-state HPLNTDU equation kernel, with default modified-Euler stages
 * and the shared opt-in staggered solver boundary.
 * All powers/current/impedance use plant base. Network resolution, import and
 * mechanically switched shunt dispatch are separate attachment responsibilities. */
public final class HplntdKernel implements ICMLStateProvider,StaggeredEulerParticipant {
    public record Input(Complex regulatedVoltage, Complex branchCurrent, double active,
                        double reactive, double frequencyDeviation, double frequencyVoltage) {
        public Input {
            Objects.requireNonNull(regulatedVoltage);Objects.requireNonNull(branchCurrent);
            finite(regulatedVoltage.getReal(),regulatedVoltage.getImaginary(),branchCurrent.getReal(),
                    branchCurrent.getImaginary(),active,reactive,frequencyDeviation,frequencyVoltage);
            if(frequencyVoltage<0)throw new IllegalArgumentException("Nonnegative frequency measurement voltage required");
        }
    }
    public record References(double voltage,double reactive,double frequency,double active,
                             double powerFactor,double voltageAux,double activeAux) {
        public References { finite(voltage,reactive,frequency,active,powerFactor,voltageAux,activeAux); }
    }
    /** active/reactive are increments; activeCommand/reactiveCommand are absolute. */
    public record Output(double active,double reactive,double reactiveError,double frequencyError,
                         double activePi,double reactivePi,boolean frozen,double recoveryStart,double frequencyDeadband,
                         double activeCommand,double reactiveCommand) {}
    private final HplntdData data;
    private double initialActive,initialReactive;
    private double[] x=new double[13],old,first;
    private int referenceMode;
    private References references;
    private double clock,step,recoveryStart=Double.NaN;
    private boolean initialized,wasLow;
    private Input staggeredInput;

    public HplntdKernel(HplntdData data) { this.data=Objects.requireNonNull(data); }
    public void initialize(HplntdInitialization.Measurements measurements) {
        var initial=HplntdInitialization.initialize(data,measurements);
        x=initial.states();referenceMode=initial.effectiveReferenceFlag();
        initialActive=measurements.activeCommand();initialReactive=measurements.reactiveCommand();
        references=new References(initial.voltageReference(),initial.reactiveReference(),0,
                initial.plantReference(),initial.powerFactorReference(),0,0);
        old=first=null;clock=step=0;recoveryStart=Double.NaN;wasLow=false;staggeredInput=null;initialized=true;
    }
    public References references(){requireInitialized();return references;}
    public void setReferences(References value){requireInitialized();references=Objects.requireNonNull(value);}
    /** Supply the coordinator's frozen measurement snapshot before evaluation.
     * The shared solver owns derivative history and event averaging; this
     * boundary never selects a different default integration method. */
    public void setStaggeredInput(Input value){requireInitialized();staggeredInput=Objects.requireNonNull(value);}
    @Override public int staggeredStateCount(){return 13;}
    @Override public void readStaggeredState(double[] values,int offset){requireInitialized();System.arraycopy(x,0,values,offset,13);}
    @Override public void evaluateStaggered(double dt,double[] derivatives,int offset){
        requireStaggered(dt);step=dt;refresh(staggeredInput);
        System.arraycopy(rates(staggeredInput,clock),0,derivatives,offset,13);
    }
    @Override public void applyStaggeredState(double[] values,int offset,double dt){
        requireStaggered(dt);Objects.checkFromIndexSize(offset,13,values.length);
        double[] trial=Arrays.copyOfRange(values,offset,offset+13);finite(trial);
        if(frozen(clock))for(int index:new int[]{2,3,5,6})trial[index]=x[index];
        x=trial;
    }
    @Override public void completeStaggeredStep(double dt){completeStaggeredStep(dt,clock+dt);}
    /** Commit using the enclosing simulation's accepted clock. An external
     * reference can round its clock independently of its integration step;
     * derivative integration still uses dt, never a fitted state or timer. */
    public void completeStaggeredStep(double dt,double acceptedTime){
        requireStaggered(dt);finite(acceptedTime);
        if(acceptedTime<=clock||Math.abs(acceptedTime-(clock+dt))>dt*.1)
            throw new IllegalArgumentException("Accepted plant endpoint must advance by the integration step");
        clock=acceptedTime;updateRecovery();refresh(staggeredInput);
    }
    private void requireStaggered(double dt){
        requireInitialized();finite(dt);
        if(dt<=0||!Double.isFinite(2*dt))throw new IllegalArgumentException("Positive finite staggered step required");
        if(old!=null)throw new IllegalStateException("Complete the modified-Euler corrector before using the staggered boundary");
        if(staggeredInput==null)throw new IllegalStateException("Set the coordinator measurement snapshot first");
    }
    public double time(){return clock;}
    public double[] states(){requireInitialized();return x.clone();}
    @Override public Map<String,Double> getNamedStates(){
        requireInitialized();var result=new LinkedHashMap<String,Double>();
        for(int i=0;i<13;i++)result.put(HplntdInitialization.STATE_NAMES.get(i),x[i]);
        return Collections.unmodifiableMap(result);
    }
    public void step(double dt,Input input){step(dt,input,0);step(dt,input,1);}
    public void step(double dt,Input input,int stage) {
        requireInitialized();Objects.requireNonNull(input);finite(dt);
        if(dt<0||stage<0||stage>1)throw new IllegalArgumentException("HPLNTDU step requires dt >= 0 and stage zero/one");
        if(dt==0){refresh(input);return;}
        if(stage==0&&old!=null)throw new IllegalStateException("Complete the pending HPLNTDU corrector first");
        if(stage==1&&(old==null||step!=dt))throw new IllegalStateException("Matching HPLNTDU predictor required");
        if(stage==0)step=dt;
        refresh(input);
        if(stage==0){old=x.clone();first=rates(input,clock);advance(old,first,dt);}
        else {
            double[] end=rates(input,clock+dt),average=new double[13];
            for(int i=0;i<13;i++)average[i]=.5*(first[i]+end[i]);
            advance(old,average,dt);old=first=null;clock+=dt;
            updateRecovery();
        }
        refresh(input);finite(x);
    }
    private void updateRecovery(){
        if((x[7]<v(VFRZL)||x[7]>v(VFRZH))){wasLow=true;recoveryStart=Double.NaN;}
        else if(wasLow&&Double.isNaN(recoveryStart))recoveryStart=clock;
        if(wasLow&&!Double.isNaN(recoveryStart)&&clock>=recoveryStart+v(TFRZ)){wasLow=false;recoveryStart=Double.NaN;}
    }
    private boolean frozen(double time){
        return (x[7]<v(VFRZL)||x[7]>v(VFRZH))||wasLow&&(Double.isNaN(recoveryStart)||time<recoveryStart+v(TFRZ));
    }
    private double[] rates(Input in,double time){
        double[] rate=new double[13];var out=outputAt(in,time);boolean freeze=out.frozen();
        if(referenceMode==1&&data.compensationFlag()==1)rate[0]=lag(x[0],compensated(in),v(TFLTR));
        if(referenceMode!=1)rate[1]=lag(x[1],in.reactive(),v(TFLTR));
        if(!freeze){
            // Native saturation discriminator: the integral storage is bounded
            // independently of the clipped proportional-plus-integral output.
            rate[2]=nonWindup(v(KI)*out.reactiveError(),x[2],v(QMIN),v(QMAX));
            rate[3]=lag(x[3],out.reactivePi(),v(TFV));
            rate[5]=nonWindup(v(KIG)*activeError(out.frequencyError()),out.activePi(),data.activePiMinimum(),data.activePiMaximum());
            rate[6]=clip(lag(x[6],activeTarget(out),v(TG)),v(DPRMIN2),v(DPRMAX2));
        }
        if(data.feedbackFlag()==1||referenceMode==2)rate[4]=lag(x[4],in.active(),v(TP));
        rate[7]=lag(x[7],in.regulatedVoltage().abs(),v(TFLTR));
        if(referenceMode==1&&data.compensationFlag()==0)rate[8]=lag(x[8],v(KC)*in.reactive(),v(TC));
        if(v(TFRQ)>=2*step)rate[9]=clip(lag(x[9],measuredFrequency(in),v(TFRQ)),v(FRMIN),v(FRMAX));
        double slew=data.referenceSlewTime(step);
        rate[10]=clip((references.active()-x[10])/slew,v(DPRMIN),v(DPRMAX));
        if(referenceMode==0)rate[11]=clip((clip(references.reactive(),v(QREFMIN),v(QREFMAX))-x[11])/slew,v(DQREFMIN),v(DQREFMAX));
        rate[12]=clip((leadLag(out.reactivePi())-x[12])/slew,v(QRMIN),v(QRMAX));
        return rate;
    }
    private void advance(double[] start,double[] rate,double dt){
        for(int i=0;i<13;i++)x[i]=start[i]+dt*rate[i];
    }
    private void refresh(Input in){
        if(v(TFLTR)==0){
            x[7]=in.regulatedVoltage().abs();
            if(referenceMode==1&&data.compensationFlag()==1)x[0]=compensated(in);
            if(referenceMode!=1)x[1]=in.reactive();
        }
        if(v(TP)==0&&(data.feedbackFlag()==1||referenceMode==2))x[4]=in.active();
        if(v(TC)==0&&referenceMode==1&&data.compensationFlag()==0)x[8]=v(KC)*in.reactive();
        if(v(TFRQ)<2*step||v(TFRQ)==0)x[9]=measuredFrequency(in);
        var out=outputAt(in,clock);
        if(!out.frozen()){
            if(v(TFV)==0)x[3]=out.reactivePi();
            if(v(TG)==0)x[6]=activeTarget(out);
        }
    }
    public Output output(Input in){requireInitialized();Objects.requireNonNull(in);return outputAt(in,clock);}
    private Output outputAt(Input in,double time){
        double qError=reactiveError();
        double qPi=clip(v(KP)*qError+x[2],v(QMIN),v(QMAX));
        double rawFrequency=deadband(references.frequency()-x[9],v(FDBD1),v(FDBD2));
        double fError=clip(rawFrequency,v(DFMIN),v(DFMAX));
        double pPi=clip(v(KPG)*activeError(fError)+x[5],data.activePiMinimum(),data.activePiMaximum());
        return new Output(x[6]-initialActive,x[12]-initialReactive,qError,fError,pPi,qPi,
                frozen(time),Double.isNaN(recoveryStart)?0:recoveryStart,rawFrequency,x[6],x[12]);
    }
    private double reactiveError(){
        double error=switch(referenceMode){
            case 1 -> clip(references.voltage(),v(VREFMIN),v(VREFMAX))+references.voltageAux()
                    -(data.compensationFlag()==1?x[0]:x[7]+x[8]);
            case 0 -> x[11]-x[1];
            case 2 -> {
                double pf=references.powerFactor();
                double magnitude=clip(Math.abs(pf),pf>=0?v(PFMAX):-v(PFMIN),1);
                yield Math.copySign(Math.tan(Math.acos(magnitude))*Math.abs(x[4]),pf)-x[1];
            }
            default -> throw new IllegalStateException("Invalid HPLNTDU reference mode");
        };
        return clip(deadband(error,v(DBD1),v(DBD2)),v(EMIN),v(EMAX));
    }
    private double activeRequest(double frequencyError){
        double limited=clip(frequencyError,v(DFMIN),v(DFMAX));
        double droop=limited*(limited>=0?v(DUP):v(DDN));
        return x[10]+references.activeAux()+droop;
    }
    private double activeError(double frequencyError){return clip(activeRequest(frequencyError)-(data.feedbackFlag()==1?x[4]:0),v(PFEMIN),v(PFEMAX));}
    private double activeTarget(Output out){return clip(out.activePi()+(data.feedforwardFlag()==1?activeRequest(out.frequencyError()):0),v(PMIN),v(PMAX));}
    private double measuredFrequency(Input in){return in.frequencyVoltage()<v(VFRQ)?0:in.frequencyDeviation();}
    private double compensated(Input in){return in.regulatedVoltage().subtract(new Complex(v(RC),v(XC)).multiply(in.branchCurrent())).abs();}
    private double leadLag(double input){return v(TFV)>0?v(TFT)/v(TFV)*input+(1-v(TFT)/v(TFV))*x[3]:input;}
    private double v(HplntdData.Parameter p){return data.value(p);}
    private static double lag(double state,double input,double time){return time>0?(input-state)/time:0;}
    private static double deadband(double value,double lo,double hi){return value>hi?value-hi:value<lo?value-lo:0;}
    private static double clip(double value,double lo,double hi){return Math.max(lo,Math.min(hi,value));}
    /** Suppress outward integration; a proportional jump must not reset integral storage. */
    private static double nonWindup(double rate,double output,double lo,double hi){return output>=hi&&rate>0||output<=lo&&rate<0?0:rate;}
    private static void finite(double... values){for(double value:values)if(!Double.isFinite(value))throw new IllegalArgumentException("Finite HPLNTDU values required");}
    private void requireInitialized(){if(!initialized)throw new IllegalStateException("Initialize HPLNTDU kernel first");}
}
