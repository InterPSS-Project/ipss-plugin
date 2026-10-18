package org.interpss.dstab.renewable;

import static org.interpss.dstab.renewable.Repcgfmc1Data.Parameter.*;
import java.util.*;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Fifteen-state hybrid plant controller. P/Q references use plant MVA base;
 * measurements enter on system MVA base. This kernel does not resolve buses,
 * import records or attach a dynamic network device. */
public final class Repcgfmc1Kernel implements ICMLStateProvider {
    private static final List<String> NAMES=List.of("FrequencyMeasured","FrequencyReferenceLag","RemoteVoltageMeasured",
            "TerminalVoltageMeasured","TerminalVoltageDelay","VoltageReferenceLag","BranchActiveMeasured","BranchReactiveMeasured",
            "VoltageIntegral","VoltageProportionalLag","ActiveIntegral","ActiveReferenceLag","ReactiveIntegral","ReactiveReferenceLag","FfrPower");
    public record Input(double terminalVoltage,double remoteVoltage,double frequencyVoltage,double frequency,
                        double branchActive,double branchReactive) {
        public Input {finite(terminalVoltage,remoteVoltage,frequencyVoltage,frequency,branchActive,branchReactive);
            if(terminalVoltage<0||remoteVoltage<0||frequencyVoltage<0)throw new IllegalArgumentException("Nonnegative plant voltage magnitudes required");}
    }
    public record References(double frequency,double activeBranch,double reactiveBranch,double voltage,double activeAux,double reactiveAux) {
        public References {finite(frequency,activeBranch,reactiveBranch,voltage,activeAux,reactiveAux);}
    }
    public record Output(double active,double reactive,double voltage,double frequency,double activeTarget,double reactiveTarget,
                         double activeLoss,double reactiveLoss,double compensatedVoltage,double voltageControl,double droop,
                         double filteredFrequency,double ffrPower,double ffrStartTime) {}
    private enum FfrPhase { IDLE,HOLD,RAMP }
    private final Repcgfmc1Data data;
    private final double[] x=new double[15];
    private double[] old,first;
    private References refs;
    private double factor,rLoss,xLoss,vCorrection,tff,step,clock,ffrStart,ffrLow,ffrHigh,ffrPolarity;
    private FfrPhase ffrPhase=FfrPhase.IDLE;
    private boolean initialized,idleAtStart,nominalStepPending;

    public Repcgfmc1Kernel(Repcgfmc1Data data){this.data=Objects.requireNonNull(data);}
    public void initialize(Repcgfmc1Initialization.Measurements measurements,double nominalStep) {
        initializeWithFilterTime(measurements,data.frequencyFilterTime(nominalStep));
    }
    /** Network initialization has no timestep; apply the frequency minimum at
     * the first actual predictor without resetting states or references. */
    public void initialize(Repcgfmc1Initialization.Measurements measurements) {
        initializeWithFilterTime(measurements,value(TFF));nominalStepPending=true;
    }
    private void initializeWithFilterTime(Repcgfmc1Initialization.Measurements measurements,double filterTime) {
        var initial=Repcgfmc1Initialization.initialize(data,measurements);
        tff=filterTime;nominalStepPending=false;factor=measurements.systemMva()/initial.plantMva();
        rLoss=initial.resistance();xLoss=initial.reactance();vCorrection=initial.voltageCorrection();
        refs=new References(1,initial.pTarget(),initial.qTarget(),initial.remoteVoltageReference(),0,0);
        Arrays.fill(x,0);old=first=null;clock=ffrStart=0;ffrPhase=FfrPhase.IDLE;
        ffrLow=value(FFRLOW);ffrHigh=value(FFRHIGH);
        x[0]=x[1]=1;x[2]=initial.remoteVoltage();
        if(data.voltageReferenceFlag()==0)x[3]=x[4]=measurements.terminalVoltage();
        x[5]=measurements.terminalVoltage();x[6]=initial.pTarget();x[7]=initial.qTarget();x[8]=initial.voltageIntegral();
        if(data.voltageFlag()==1&&value(KIVC)==0)x[9]=initial.qTarget();
        x[10]=initial.activeIntegral();x[11]=initial.activeReference();x[12]=initial.reactiveIntegral();x[13]=initial.reactiveReference();
        initialized=true;
    }
    public References references(){requireInitialized();return refs;}
    public void setReferences(References references){requireInitialized();refs=Objects.requireNonNull(references);}
    /** Runtime threshold changes are explicit; the immutable input contract is retained. */
    public void setFastFrequencyThresholds(double lower,double upper){requireInitialized();finite(lower,upper);if(lower>upper)throw new IllegalArgumentException("Ordered FFR thresholds required");ffrLow=lower;ffrHigh=upper;}
    public void step(double dt,Input input){step(dt,input,0);step(dt,input,1);}
    public void step(double dt,Input input,int stage) {
        requireInitialized();Objects.requireNonNull(input);finite(dt);
        if(dt<0||stage<0||stage>1)throw new IllegalArgumentException("Plant step requires dt >= 0 and stage 0/1");
        if(dt==0){refresh(input);sampleFfr(input);refresh(input);return;}
        if(stage==1&&(old==null||step!=dt))throw new IllegalStateException("Matching plant predictor required before corrector");
        if(stage==0&&nominalStepPending){tff=data.frequencyFilterTime(dt);nominalStepPending=false;}
        refresh(input);
        if(stage==0){
            if(ffrPhase==FfrPhase.HOLD&&clock>=ffrStart+value(TFFR))ffrPhase=FfrPhase.RAMP;
            idleAtStart=ffrPhase==FfrPhase.IDLE;step=dt;old=x.clone();first=rates(input);advance(old,first,dt);
        } else {
            double[] end=rates(input),average=new double[15];for(int i=0;i<15;i++)average[i]=.5*(first[i]+end[i]);
            advance(old,average,dt);old=first=null;clock+=dt;
            if(ffrPhase==FfrPhase.RAMP&&x[14]==0){ffrPhase=FfrPhase.IDLE;ffrStart=0;}
            else if(idleAtStart)sampleFfr(input);
        }
        refresh(input);finite(x);
    }
    private void sampleFfr(Input input) {
        // A predictor network iteration is not an accepted event endpoint.
        // Defer its discrete trigger until the corrector, so a zero-time query
        // cannot create a jump that the cached start state would overwrite.
        if(old!=null||!data.fastFrequencyEnabled()||ffrPhase!=FfrPhase.IDLE)return;
        double frequency=filteredFrequency(input),power=frequency<ffrLow?value(PFFRLOW):frequency>ffrHigh?value(PFFRHIGH):0;
        if(power!=0){x[14]=power;ffrPolarity=Math.signum(power);ffrStart=clock;ffrPhase=FfrPhase.HOLD;}
    }
    private double[] rates(Input input) {
        var out=output(input);double error=voltageError();double[] rate=new double[15];
        rate[0]=clip((input.frequency()-x[0])/tff,value(FRMIN),value(FRMAX));
        rate[1]=lag(x[1],clip(out.filteredFrequency(),value(FREQMIN),value(FREQMAX)),value(TFREFD));
        if(data.voltageFlag()==1||data.voltageReferenceFlag()==1)rate[2]=lag(x[2],input.remoteVoltage(),value(TVF));
        if(data.voltageReferenceFlag()==0){rate[3]=lag(x[3],input.terminalVoltage(),value(TVF));rate[4]=lag(x[4],x[3],value(TVD));}
        double voltage=clip((data.voltageReferenceFlag()==1?out.compensatedVoltage()+vCorrection:x[4]),value(VREFMIN),value(VREFMAX));
        rate[5]=lag(x[5],voltage,value(TVREFD));rate[6]=lag(x[6],input.branchActive()*factor,value(TPF));rate[7]=lag(x[7],input.branchReactive()*factor,value(TQF));
        if(data.voltageFlag()==1){
            rate[8]=bounded(x[8],value(KIVC)*error,value(QVCMIN),value(QVCMAX));
            rate[9]=bounded(x[9],lag(x[9],data.effectiveVoltageGain()*error,value(TVC)),value(QVCMIN),value(QVCMAX));
        }
        rate[10]=bounded(x[10],value(KIP)*clip(out.activeTarget()-x[6],value(PRERMIN),value(PRERMAX)),value(PERRMIN),value(PERRMAX));
        rate[11]=lag(x[11],out.activeTarget()+out.activeLoss()+x[10],value(TPD));
        rate[12]=bounded(x[12],value(KIQ)*clip(out.reactiveTarget()-x[7],value(QRERMIN),value(QRERMAX)),value(QERRMIN),value(QERRMAX));
        rate[13]=lag(x[13],out.reactiveTarget()+out.reactiveLoss()+x[12],value(TQD));
        if(ffrPhase==FfrPhase.RAMP)rate[14]=-ffrPolarity*value(FFRR);
        return rate;
    }
    private void advance(double[] start,double[] rate,double dt) {
        for(int i=0;i<15;i++)x[i]=start[i]+dt*rate[i];
        if(data.voltageFlag()==1){x[8]=clip(x[8],value(QVCMIN),value(QVCMAX));x[9]=clip(x[9],value(QVCMIN),value(QVCMAX));}
        x[10]=clip(x[10],value(PERRMIN),value(PERRMAX));x[12]=clip(x[12],value(QERRMIN),value(QERRMAX));
        if(ffrPhase==FfrPhase.RAMP&&(start[14]==0||x[14]*start[14]<=0))x[14]=0;
    }
    private void refresh(Input input) {
        if(value(TVF)==0&&(data.voltageFlag()==1||data.voltageReferenceFlag()==1))x[2]=input.remoteVoltage();
        if(data.voltageReferenceFlag()==0){if(value(TVF)==0)x[3]=input.terminalVoltage();if(value(TVD)==0)x[4]=x[3];}
        if(value(TPF)==0)x[6]=input.branchActive()*factor;if(value(TQF)==0)x[7]=input.branchReactive()*factor;
        if(data.voltageFlag()==1&&value(TVC)==0)x[9]=clip(data.effectiveVoltageGain()*voltageError(),value(QVCMIN),value(QVCMAX));
        var out=output(input);
        if(value(TFREFD)==0)x[1]=clip(out.filteredFrequency(),value(FREQMIN),value(FREQMAX));
        if(value(TVREFD)==0)x[5]=clip(data.voltageReferenceFlag()==1?out.compensatedVoltage()+vCorrection:x[4],value(VREFMIN),value(VREFMAX));
        if(value(TPD)==0)x[11]=out.activeTarget()+out.activeLoss()+x[10];if(value(TQD)==0)x[13]=out.reactiveTarget()+out.reactiveLoss()+x[12];
    }
    public Output output(Input input) {
        requireInitialized();Objects.requireNonNull(input);
        double f=filteredFrequency(input),error=deadband(refs.frequency()-f,value(DBFL),value(DBFH));
        double droop=clip(error*(error>=0?value(DUP):value(DDN)),value(PFMIN),value(PFMAX));
        double p=clip(refs.activeBranch()+droop+refs.activeAux()+(data.fastFrequencyEnabled()?x[14]:0),value(PREFMIN),value(PREFMAX));
        double voltageControl=data.voltageFlag()==1?clip(x[8]+x[9],value(QVCMIN),value(QVCMAX)):0;
        double q=clip((data.voltageFlag()==1?voltageControl:refs.reactiveBranch())+refs.reactiveAux(),value(QREFMIN),value(QREFMAX));
        double squared=p*p+q*q,compensated=data.voltageReferenceFlag()==1?new Complex(x[2],0).add(new Complex(rLoss,xLoss).multiply(new Complex(p,-q))).abs():0;
        return new Output(x[11],x[13],x[5],x[1],p,q,rLoss*squared,xLoss*squared,compensated,voltageControl,droop,f,x[14],ffrStart);
    }
    private double voltageError(){return deadband(clip(refs.voltage()-x[2],value(VERRMIN),value(VERRMAX)),value(DBVL),value(DBVH));}
    private double filteredFrequency(Input input){return input.frequencyVoltage()>=value(VFTH)?x[0]:refs.frequency();}
    public double[] states(){requireInitialized();return x.clone();}
    public double time(){return clock;}
    @Override public Map<String,Double> getNamedStates(){requireInitialized();var result=new LinkedHashMap<String,Double>();for(int i=0;i<15;i++)result.put(NAMES.get(i),x[i]);return Collections.unmodifiableMap(result);}
    private double value(Repcgfmc1Data.Parameter p){return data.value(p);}
    private void requireInitialized(){if(!initialized)throw new IllegalStateException("Initialize hybrid plant controller first");}
    private static double lag(double state,double input,double time){return time>0?(input-state)/time:0;}
    private static double bounded(double state,double rate,double lo,double hi){return state>=hi&&rate>0||state<=lo&&rate<0?0:rate;}
    private static double deadband(double value,double lo,double hi){return value>hi?value-hi:value<lo?value-lo:0;}
    private static double clip(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
    private static void finite(double... values){for(double v:values)if(!Double.isFinite(v))throw new IllegalArgumentException("Finite hybrid plant values required");}
}
