package org.interpss.dstab.hvdc;

import static org.interpss.dstab.hvdc.Chvdc2Parameters.Constant.*;
import java.util.Map;
import org.interpss.dstab.control.util.IntegrationStateAccess;
import org.interpss.dstab.control.util.IntegrationStateMonitor;
import org.interpss.dstab.control.util.StaggeredEulerParticipant;

/** Shared RAML washout state and explicitly accepted protection history.
 * Network trials may refresh inputs and evaluate rates without committing a timer.
 * This component selects protection modes; the electrical owner must implement
 * the shorted-inverter circuit while retaining line current. */
public final class Chvdc2Protection implements StaggeredEulerParticipant, IntegrationStateAccess {
    private static final String STATE_NAME="RAML washout lag (deg)";
    private static final int IDLE=0,HOLD=1,WAIT_RECOVERY=2;
    private final boolean ramlEnabled;
    private final double minimumAngle,ramAngle,inverseRamTime,enteredRamVoltage,ramHold,
            failureAngle,recoveryVoltage,failureHold;
    private final Frame frame=new Frame(),saved=new Frame();
    private final IntegrationStateMonitor monitor=new IntegrationStateMonitor(STATE_NAME);
    private double preparedRamVoltage,origin,firstRate,pendingStep;
    private boolean initialized,pending,transaction;

    /** Scalar storage is copied into a preallocated checkpoint only at transaction boundaries. */
    private static final class Frame {
        double state,rectifierVoltage,inverterVoltage,gamma,time,ramStart,ramMarker,failureTime,leftRate,
                acceptedRectifierVoltage,acceptedInverterVoltage,acceptedGamma;
        int ramPhase;
        boolean failed,hadFailureTransition;
        long acceptance=-1,revision;
        void copyFrom(Frame f) {
            state=f.state;rectifierVoltage=f.rectifierVoltage;inverterVoltage=f.inverterVoltage;gamma=f.gamma;
            time=f.time;ramStart=f.ramStart;ramMarker=f.ramMarker;failureTime=f.failureTime;leftRate=f.leftRate;
            acceptedRectifierVoltage=f.acceptedRectifierVoltage;acceptedInverterVoltage=f.acceptedInverterVoltage;acceptedGamma=f.acceptedGamma;
            ramPhase=f.ramPhase;failed=f.failed;hadFailureTransition=f.hadFailureTransition;acceptance=f.acceptance;revision=f.revision;
        }
    }
    public Chvdc2Protection(Chvdc2Parameters parameters) {
        java.util.Objects.requireNonNull(parameters);
        ramlEnabled=parameters.rectifierMinimumAngleLogicEnabled();
        minimumAngle=parameters.value(ALPHA_MIN_R_DEG);ramAngle=parameters.value(ALPHA_MAX_RAM_DEG);
        inverseRamTime=1/parameters.value(T_RAM_SEC);finite(inverseRamTime);
        enteredRamVoltage=parameters.value(V_RAM_PU);ramHold=parameters.value(T_TIMER_RAM_SEC);
        failureAngle=parameters.value(GAMMA_CF_DEG);recoveryVoltage=parameters.value(V_AC_UCF_PU);failureHold=parameters.value(T_CF_SEC);
        if(ramlEnabled&&(minimumAngle<0||ramAngle<minimumAngle||ramAngle>parameters.value(ALPHA_MAX_R_DEG)))
            throw new IllegalArgumentException("RAML angle bounds must fit the rectifier firing-angle limits");
    }
    /** Preparation retains entered settings and initializes protection inactive, including negative startup time. */
    public void initialize(double rectifierVoltagePu,double inverterVoltagePu,double extinctionDegrees,double time) {
        if(transaction)throw new IllegalStateException("Finish the protection transaction before reinitialization");
        voltage(rectifierVoltagePu);voltage(inverterVoltagePu);finite(extinctionDegrees);finite(time);
        preparedRamVoltage=Math.min(enteredRamVoltage,rectifierVoltagePu);
        frame.state=0;frame.rectifierVoltage=rectifierVoltagePu;frame.inverterVoltage=inverterVoltagePu;frame.gamma=extinctionDegrees;
        frame.acceptedRectifierVoltage=rectifierVoltagePu;frame.acceptedInverterVoltage=inverterVoltagePu;frame.acceptedGamma=extinctionDegrees;
        frame.time=time;frame.ramStart=time;frame.ramMarker=0;frame.failureTime=time;frame.leftRate=0;
        frame.ramPhase=IDLE;frame.failed=false;frame.hadFailureTransition=false;frame.acceptance=-1;frame.revision=0;
        initialized=true;pending=false;monitor.reset();
    }
    /** Rectifier input is the measured/filtered AC voltage; inverter recovery uses terminal AC voltage. */
    public void setInput(double measuredRectifierPu,double terminalInverterPu,double extinctionDegrees) {
        ready();voltage(measuredRectifierPu);voltage(terminalInverterPu);finite(extinctionDegrees);
        frame.rectifierVoltage=measuredRectifierPu;frame.inverterVoltage=terminalInverterPu;frame.gamma=extinctionDegrees;
    }
    /** Accept a converged boundary using a caller-owned monotonic sequence number.
     * Distinct events may share a timestamp, but a repeated sequence must have identical time/inputs.
     * Each acceptance executes at most one commutation transition; a released short cannot
     * immediately re-trigger within the same accepted boundary. */
    public void acceptBoundary(double time,long acceptance) {
        ready();exclusive();finite(time);
        if(acceptance<0||acceptance<frame.acceptance||time<frame.time)
            throw new IllegalArgumentException("Protection acceptance sequence and time must be monotonic");
        finite(time-frame.time);
        if(acceptance==frame.acceptance) {
            if(time!=frame.time||frame.rectifierVoltage!=frame.acceptedRectifierVoltage
                    ||frame.inverterVoltage!=frame.acceptedInverterVoltage||frame.gamma!=frame.acceptedGamma)
                throw new IllegalArgumentException("Repeated protection acceptance has changed boundary inputs");
            return;
        }
        int phase=frame.ramPhase;double start=frame.ramStart,marker=frame.ramMarker,failureTime=frame.failureTime;
        boolean failed=frame.failed,hadTransition=frame.hadFailureTransition;
        if(ramlEnabled) {
            if(phase==IDLE&&frame.rectifierVoltage<preparedRamVoltage){phase=HOLD;start=time;marker=time;}
            if(phase==HOLD) {
                double elapsed=time-start;finite(elapsed);
                if(elapsed>ramHold){phase=WAIT_RECOVERY;marker=time;}
            }
            if(phase==WAIT_RECOVERY&&frame.rectifierVoltage>preparedRamVoltage)phase=IDLE;
        }
        if(failureAngle>0) {
            if(!failed&&frame.gamma<failureAngle){failed=true;failureTime=time;hadTransition=true;}
            else if(failed) {
                double elapsed=time-failureTime;finite(elapsed);
                if(elapsed>failureHold&&frame.inverterVoltage>=recoveryVoltage){failed=false;failureTime=time;hadTransition=true;}
            }
        }
        boolean discontinuity=(phase==IDLE)!=(frame.ramPhase==IDLE)||failed!=frame.failed;
        long revision=discontinuity?Math.incrementExact(frame.revision):frame.revision;
        double leftRate=discontinuity?rate():frame.leftRate;
        frame.ramPhase=phase;frame.ramStart=start;frame.ramMarker=marker;frame.failed=failed;
        frame.failureTime=failureTime;frame.hadFailureTransition=hadTransition;frame.revision=revision;frame.leftRate=leftRate;
        frame.time=time;frame.acceptance=acceptance;
        frame.acceptedRectifierVoltage=frame.rectifierVoltage;frame.acceptedInverterVoltage=frame.inverterVoltage;frame.acceptedGamma=frame.gamma;
    }
    public void beginTransaction(){ready();exclusive();if(transaction)throw new IllegalStateException("Protection transaction already open");saved.copyFrom(frame);transaction=true;}
    public void commitTransaction(){ready();exclusive();if(!transaction)throw new IllegalStateException("No protection transaction");transaction=false;}
    /** Restore numerical state, inputs, accepted flags/clocks and discontinuity revision together. */
    public void rollbackTransaction(){ready();if(!transaction)throw new IllegalStateException("No protection transaction");frame.copyFrom(saved);transaction=false;pending=false;monitor.reset();}
    public boolean ramlActive(){ready();return frame.ramPhase!=IDLE;}
    public boolean inverterShorted(){ready();return frame.failed;}
    public double preparedRamVoltagePu(){ready();return preparedRamVoltage;}
    /** Electrical evaluation uses the previously accepted extinction angle; acceptBoundary
     * publishes the freshly calculated angle only for the next boundary. Input refresh and
     * rejected network iterations must not advance this history. */
    public double acceptedInverterExtinctionDegrees(){ready();return frame.acceptedGamma;}
    public double rectifierMinimumDegrees(){ready();return ramlActive()?Math.max(minimumAngle,Math.min(ramAngle,ramAngle-frame.state)):minimumAngle;}
    public double ramlTriggerTime(){ready();return frame.ramStart;}
    public double ramlTimerMarker(){ready();return frame.ramMarker;}
    public boolean hasCommutationTransition(){ready();return frame.hadFailureTransition;}
    public double commutationTransitionTime(){ready();return frame.failureTime;}
    private double rate(){double result=ramlEnabled?((frame.ramPhase==IDLE?0:ramAngle)-frame.state)*inverseRamTime:0;finite(result);return result;}
    public void step(double seconds,int stage) {
        ready();interval(seconds);if(stage!=0&&stage!=1)throw new IllegalArgumentException("Predictor or corrector required");if(seconds==0)return;
        if(stage==0&&pending||stage==1&&(!pending||seconds!=pendingStep))throw new IllegalStateException("Matching protection integration stages required");
        double f=rate(),candidate=stage==0?frame.state+seconds*f:origin+.5*seconds*(firstRate+f);finite(candidate);
        record(stage==0?IntegrationStateMonitor.Stage.PREDICTOR:IntegrationStateMonitor.Stage.CORRECTOR,f);
        if(stage==0){origin=frame.state;firstRate=f;pendingStep=seconds;}
        frame.state=candidate;pending=stage==0;
    }
    public void cancelPrediction(){ready();if(pending){frame.state=origin;pending=false;monitor.reset();}}
    @Override public int staggeredStateCount(){return 1;}
    @Override public long staggeredInputDiscontinuityVersion(){return frame.revision;}
    @Override public boolean readStaggeredInputDiscontinuityLeftRates(double[] values,int offset){ready();buffer(values,offset);if(frame.revision==0)return false;values[offset]=frame.leftRate;return true;}
    @Override public void readStaggeredState(double[] values,int offset){ready();buffer(values,offset);values[offset]=frame.state;}
    @Override public void evaluateStaggered(double seconds,double[] values,int offset){ready();exclusive();interval(seconds);buffer(values,offset);double f=rate();record(IntegrationStateMonitor.Stage.STAGGERED,f);values[offset]=f;}
    @Override public void applyStaggeredState(double[] values,int offset,double seconds){ready();exclusive();interval(seconds);buffer(values,offset);finite(values[offset]);frame.state=values[offset];}
    @Override public Map<String,Double> getIntegrationStates(){ready();return Map.of(STATE_NAME,frame.state);}
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot(){return monitor.snapshot();}
    @Override public Map<String,Double> getIntegrationHistories(){
        ready();return Map.of("RAML active",ramlActive()?1.:0.,"RAML trigger time (s)",frame.ramStart,
                "RAML timer marker (s)",frame.ramMarker,"Inverter shorted",frame.failed?1.:0.,
                "Commutation transition time (s)",frame.failureTime,"Commutation transition observed",frame.hadFailureTransition?1.:0.,"Accepted time (s)",frame.time,
                "Accepted inverter extinction angle (deg)",frame.acceptedGamma);
    }
    private void record(IntegrationStateMonitor.Stage stage,double rate){monitor.begin(stage);monitor.record(0,frame.state,rate);}
    private void ready(){if(!initialized)throw new IllegalStateException("Initialize protection first");}
    private void exclusive(){if(pending)throw new IllegalStateException("Finish or cancel protection prediction first");}
    private static void buffer(double[] values,int offset){java.util.Objects.checkFromIndexSize(offset,1,values.length);}
    private static void finite(double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Finite protection value required");}
    private static void voltage(double value){finite(value);if(value<0)throw new IllegalArgumentException("Nonnegative protection voltage required");}
    private static void interval(double value){finite(value);if(value<0)throw new IllegalArgumentException("Nonnegative integration interval required");}
}
