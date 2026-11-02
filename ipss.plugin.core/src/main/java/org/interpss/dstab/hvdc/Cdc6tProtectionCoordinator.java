package org.interpss.dstab.hvdc;

import org.interpss.dstab.hvdc.Cdc6tNormalRuntime.OperatingMode;

/** Single-owner protection history with reusable working copies for network/event trials.
 * The enclosing solver must accept only a successful, accepted physical-time boundary;
 * successful predictor-network convergence alone is not permission to commit history. */
public final class Cdc6tProtectionCoordinator {
    private final Cdc6tRectifierProtection rectifier,workingRectifier;
    private final Cdc6tInverterProtection inverter,workingInverter;
    private final Cdc6tBypassProtection bypass,workingBypass;
    private final Cdc6tModeSwitch modeSwitch,workingModeSwitch;
    private final Cdc6tRecoveryRamp ramp;
    // Blocking origin (0 none, 1 rectifier, 2 inverter), recovery (0 none, 1 block, 2 bypass),
    // restart time, voltage factor, current factor.
    private final double[] accepted={0,0,Double.NaN,1,1},working=new double[5];
    private double acceptedTime=Double.NEGATIVE_INFINITY,trialTime;
    private boolean open,failed,observed;

    /** Takes independent copies so caller-owned protection objects cannot mutate this owner. */
    public Cdc6tProtectionCoordinator(Cdc6tRectifierProtection rectifier,Cdc6tInverterProtection inverter,
            Cdc6tBypassProtection bypass,Cdc6tModeSwitch modeSwitch,Cdc6tRecoveryRamp ramp){
        this.rectifier=java.util.Objects.requireNonNull(rectifier).workingCopy();
        this.inverter=java.util.Objects.requireNonNull(inverter).workingCopy();
        this.bypass=java.util.Objects.requireNonNull(bypass).workingCopy();
        this.modeSwitch=java.util.Objects.requireNonNull(modeSwitch).workingCopy();
        this.ramp=java.util.Objects.requireNonNull(ramp);
        workingRectifier=this.rectifier.workingCopy();workingInverter=this.inverter.workingCopy();
        workingBypass=this.bypass.workingCopy();workingModeSwitch=this.modeSwitch.workingCopy();
        reset();
    }
    /** Explicit reinitialization; do not call during an open trial. */
    public void reset(){
        if(open)throw new IllegalStateException("Discard or accept the open protection trial before resetting");
        rectifier.resetHistory();inverter.resetHistory();bypass.resetHistory();modeSwitch.resetHistory();
        accepted[0]=0;accepted[1]=0;accepted[2]=Double.NaN;accepted[3]=1;accepted[4]=1;
        acceptedTime=Double.NEGATIVE_INFINITY;failed=false;observed=false;
    }
    public void beginTrial(double physicalTime){
        if(open)throw new IllegalStateException("Protection trial already open");
        if(!Double.isFinite(physicalTime)||physicalTime<acceptedTime)throw new IllegalArgumentException("Finite nondecreasing physical time required");
        workingRectifier.copyHistoryFrom(rectifier);workingInverter.copyHistoryFrom(inverter);
        workingBypass.copyHistoryFrom(bypass);workingModeSwitch.copyHistoryFrom(modeSwitch);
        System.arraycopy(accepted,0,working,0,5);trialTime=physicalTime;
        if(working[1]!=0){
            double elapsed=physicalTime-working[2];
            working[3]=ramp.voltageFactor(elapsed);
            working[4]=working[1]==1?ramp.currentFactor(elapsed):1;
            // Completion changes the discrete control regime. A predictor may
            // prepare endpoint factors but must retain the left-hand regime
            // until the accepted network owner explicitly observes this time.
        }
        open=true;failed=false;observed=false;
    }
    /** Explicit working-history observation, called by the event/network owner, never by an output getter.
     * Repeated observations at one time may discover a transition, then a new electrical regime.
     * An exception poisons this trial; it must be discarded. */
    public void observe(double rectifierAcPu,double inverterAcPu,double inverterDcVolts){
        requireTrial();
        try{
            nonnegative(rectifierAcPu);nonnegative(inverterAcPu);nonnegative(inverterDcVolts);
            if(working[1]!=0&&working[3]==1&&working[4]==1)working[1]=0;
            boolean wasBlocked=working[0]!=0;
            if(working[0]==1){
                workingRectifier.accept(trialTime,rectifierAcPu);
                if(!workingRectifier.isBlocked())unblock();
            } else if(working[0]==2){
                workingInverter.accept(trialTime,inverterAcPu);
                if(!workingInverter.isBlocked())unblock();
            } else {
                workingRectifier.accept(trialTime,rectifierAcPu);
                if(workingRectifier.isBlocked())block(1);
                else {
                    workingInverter.accept(trialTime,inverterAcPu);
                    if(workingInverter.isBlocked())block(2);
                }
            }
            // A newly released block already owns its voltage/current recovery program.
            if(working[0]==0&&!wasBlocked){
                boolean wasBypassed=workingBypass.isBypassed();
                workingBypass.accept(trialTime,inverterAcPu,inverterDcVolts,working[3],working[1]==1);
                if(!wasBypassed&&workingBypass.isBypassed())working[1]=0;
                else if(wasBypassed&&!workingBypass.isBypassed()){
                    working[1]=2;working[2]=trialTime;working[3]=ramp.voltageFactor(0);working[4]=1;
                }
            }
            workingModeSwitch.accept(trialTime,inverterDcVolts,working[0]!=0,workingBypass.isBypassed());
            observed=true;
        } catch(RuntimeException failure){failed=true;throw failure;}
    }
    private void block(int origin){
        working[0]=origin;working[1]=0;
        workingBypass.resetHistory(); // Blocking supersedes an active inverter bypass.
        if(origin==1)workingInverter.resetHistory();else workingRectifier.resetHistory();
    }
    private void unblock(){
        working[0]=0;working[1]=1;working[2]=trialTime;
        working[3]=ramp.voltageFactor(0);working[4]=ramp.currentFactor(0);
        workingRectifier.resetHistory();workingInverter.resetHistory();workingBypass.resetHistory();
    }
    public OperatingMode operatingMode(){
        double[] values=open?working:accepted;
        if(open&&failed)throw new IllegalStateException("Discard failed protection trial");
        if(values[0]!=0)return OperatingMode.BLOCKED;
        if((open?workingBypass:bypass).isBypassed())return OperatingMode.BYPASSED;
        if(values[1]!=0)return OperatingMode.RECOVERING;
        return (open?workingModeSwitch:modeSwitch).isSwitched()?OperatingMode.POWER_TO_CURRENT:OperatingMode.NORMAL;
    }
    public double voltageFactor(){return factors()[3];}
    public double currentFactor(){return factors()[4];}
    private double[] factors(){if(open&&failed)throw new IllegalStateException("Discard failed protection trial");return open?working:accepted;}
    /** Commit only after the enclosing integration/event owner accepts this physical boundary. */
    public void acceptTrial(){
        requireTrial();if(!observed)throw new IllegalStateException("Observe the electrical boundary before accepting");
        rectifier.copyHistoryFrom(workingRectifier);inverter.copyHistoryFrom(workingInverter);
        bypass.copyHistoryFrom(workingBypass);modeSwitch.copyHistoryFrom(workingModeSwitch);
        System.arraycopy(working,0,accepted,0,5);acceptedTime=trialTime;open=false;
    }
    public void discardTrial(){if(!open)throw new IllegalStateException("No protection trial to discard");open=false;failed=false;}
    /** Committed history only: clock, coordinator coordinates, then rectifier/inverter/bypass/mode histories. */
    public void readAcceptedHistory(double[] output,int offset){
        java.util.Objects.checkFromIndexSize(offset,23,output.length);
        output[offset]=acceptedTime;System.arraycopy(accepted,0,output,offset+1,5);
        rectifier.readHistory(output,offset+6);inverter.readHistory(output,offset+11);
        bypass.readHistory(output,offset+16);modeSwitch.readHistory(output,offset+21);
    }
    private void requireTrial(){if(!open||failed)throw new IllegalStateException("Open valid protection trial required");}
    private static void nonnegative(double x){if(!Double.isFinite(x)||x<0)throw new IllegalArgumentException("Nonnegative finite electrical input required");}
}
