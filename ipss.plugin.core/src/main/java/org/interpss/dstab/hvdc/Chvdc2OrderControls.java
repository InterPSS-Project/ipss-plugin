package org.interpss.dstab.hvdc;

import static org.interpss.dstab.hvdc.Chvdc2Parameters.Constant.*;
import java.util.LinkedHashMap;
import java.util.Map;
import com.interpss.dstab.controller.runtime.IntegrationStateAccess;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor;
import com.interpss.dstab.controller.runtime.StaggeredEulerParticipant;

/** Shared measurement, voltage-dependent current-order and inverter angle-bound equations.
 * Physical terminal inputs refresh without advancing state. Numerical storage and optional
 * published algebraic observations are distinct when an entered lag time is zero. */
public final class Chvdc2OrderControls implements StaggeredEulerParticipant, IntegrationStateAccess {
    private static final int SIZE=10;
    private static final String[] NAMES={"Measured rectifier current (pu)","Measured inverter current (pu)",
            "Rectifier DC limiter voltage (pu)","Inverter DC limiter voltage (pu)",
            "Inverter order lag (pu)","Inverter error lag (pu)","Measured rectifier AC voltage (pu)",
            "Measured inverter AC voltage (pu)","Rectifier AC current limit (pu)","Inverter AC current limit (pu)"};
    private final double inverseCurrent,inverseVoltage,ratedCurrent,kRef,kMax,kCos,cosMax,transformerFactor,
            dcV1,dcV2,dcI1,dcI2,dcSlope,downR,downI,errorMin,errorMax,currentMin,currentMax,enteredAcReference;
    private final boolean acEnabled;
    private final double[] inverseTime=new double[SIZE],state=new double[SIZE],effective=new double[SIZE],
            input=new double[6],rate=new double[SIZE],origin=new double[SIZE],firstRate=new double[SIZE],candidate=new double[SIZE];
    private final IntegrationStateMonitor monitor=new IntegrationStateMonitor(NAMES);
    private double referenceCurrent,auxiliary,acReference,cosineReference,orderR,orderI,pendingStep;
    private boolean inputReady,initialized,pending;

    /** Transformer ratio and tap use the static converter's values; their quotient scales measured AC voltage. */
    public Chvdc2OrderControls(Chvdc2Parameters p,double inverterTransformerRatio,double inverterTap) {
        java.util.Objects.requireNonNull(p);positive(inverterTransformerRatio);positive(inverterTap);
        transformerFactor=inverterTransformerRatio/inverterTap;positive(transformerFactor);
        ratedCurrent=p.value(I_DC_RATED_A);inverseCurrent=1/ratedCurrent;inverseVoltage=1/p.value(V_DC_RATED_KV);
        positive(inverseCurrent);positive(inverseVoltage);
        kRef=p.value(K_REF);kMax=p.value(K_MAX);kCos=p.value(K_COS);cosMax=p.value(COS_MIN_I);
        dcV1=p.value(V_DC_1_PU);dcV2=p.value(V_DC_2_PU);dcI1=p.value(I_DC_1_PU);dcI2=p.value(I_DC_2_PU);
        dcSlope=(dcI2-dcI1)/(dcV2-dcV1);finite(dcSlope);
        inverseTime[0]=inverseTime[1]=inverseTime[6]=inverseTime[7]=inverseOrZero(p.value(T_MEAS_SEC));
        inverseTime[2]=inverseOrZero(p.value(T_UP_R_SEC));inverseTime[3]=inverseOrZero(p.value(T_UP_I_SEC));
        downR=inverseOrZero(p.value(T_DOWN_R_SEC));downI=inverseOrZero(p.value(T_DOWN_I_SEC));
        inverseTime[4]=inverseOrZero(p.value(T_REF_SEC));inverseTime[5]=inverseOrZero(p.value(T_MAX_SEC));
        inverseTime[8]=inverseTime[9]=inverseOrZero(p.value(T_VD_SEC));
        errorMin=p.value(MIN_C_I);errorMax=p.value(MAX_C_I);
        currentMin=p.value(I_MIN_LIMIT_PU);currentMax=p.value(I_MAX_LIMIT_PU);
        enteredAcReference=p.value(V_AC_REF_PU);acEnabled=p.acVoltageCurrentLimitEnabled();
        voltageErrorMin=p.value(MIN_ERROR_PU);voltageErrorMax=p.value(MAX_ERROR_PU);
    }
    private final double voltageErrorMin,voltageErrorMax;
    /** Currents/orders/auxiliary in amperes, DC voltages in kV, terminal AC voltage in bus pu. */
    public void setInput(double rectifierAmperes,double inverterAmperes,double rectifierKv,double inverterKv,
            double rectifierAcPu,double inverterAcPu,double referenceAmperes,double auxiliaryAmperes) {
        finite(rectifierAmperes);finite(inverterAmperes);finite(rectifierKv);finite(inverterKv);
        finite(rectifierAcPu);finite(inverterAcPu);finite(referenceAmperes);finite(auxiliaryAmperes);
        double ir=rectifierAmperes*inverseCurrent,ii=inverterAmperes*inverseCurrent,
                vr=rectifierKv*inverseVoltage,vi=inverterKv*inverseVoltage,ref=referenceAmperes*inverseCurrent,aux=auxiliaryAmperes*inverseCurrent;
        finite(ir);finite(ii);finite(vr);finite(vi);finite(ref);finite(aux);finite(ref+aux);
        input[0]=ir;input[1]=ii;input[2]=vr;input[3]=vi;input[4]=rectifierAcPu;input[5]=inverterAcPu;
        referenceCurrent=ref;auxiliary=aux;inputReady=true;
    }
    public void initialize(double solvedInverterAngleDegrees) {
        if(!inputReady)throw new IllegalStateException("Supply physical terminal inputs before initialization");
        finite(solvedInverterAngleDegrees);
        if(solvedInverterAngleDegrees<90||solvedInverterAngleDegrees>180)
            throw new IllegalArgumentException("Inverter firing angle must be in [90,180] degrees");
        double initialOrderLag=kRef*(referenceCurrent+auxiliary);
        double initialCosine=-Math.cos(Math.toRadians(solvedInverterAngleDegrees))
                +kCos*initialOrderLag/Math.max(.05,input[5]*transformerFactor);
        finite(initialOrderLag);finite(initialCosine);
        state[0]=input[0];state[1]=input[1];state[2]=input[2];state[3]=input[3];
        state[6]=input[4];state[7]=input[5];state[8]=state[9]=acEnabled?currentMax:0;
        acReference=Math.min(enteredAcReference,Math.min(input[4],input[5]));
        state[4]=initialOrderLag;state[5]=0;cosineReference=initialCosine;
        initialized=true;pending=false;monitor.reset();
    }
    private double curve(double v){return v<=dcV1?dcI1:v>=dcV2?dcI2:dcI1+dcSlope*(v-dcV1);}
    private void calculate(double[] x) {
        System.arraycopy(x,0,effective,0,SIZE);
        if(inverseTime[0]==0){effective[0]=input[0];effective[1]=input[1];effective[6]=input[4];effective[7]=input[5];}
        if(downR==0&&input[2]<effective[2])effective[2]=input[2];
        if(downI==0&&input[3]<effective[3])effective[3]=input[3];
        orderR=Math.min(referenceCurrent,curve(effective[2]));orderI=Math.min(referenceCurrent,curve(effective[3]));
        if(acEnabled){orderR=Math.min(orderR,clip(effective[8],currentMin,currentMax));orderI=Math.min(orderI,clip(effective[9],currentMin,currentMax));}
        if(inverseTime[4]==0)effective[4]=kRef*(orderI+auxiliary);
        effective[5]=clip(inverseTime[5]==0?kMax*(effective[1]-orderI-auxiliary):effective[5],errorMin,errorMax);
    }
    private void rates() {
        calculate(state);
        rate[0]=(input[0]-effective[0])*inverseTime[0];rate[1]=(input[1]-effective[1])*inverseTime[1];
        double dr=input[2]-effective[2],di=input[3]-effective[3];
        rate[2]=dr*(dr>0?inverseTime[2]:downR);rate[3]=di*(di>0?inverseTime[3]:downI);
        rate[4]=(kRef*(orderI+auxiliary)-effective[4])*inverseTime[4];
        rate[5]=nonWindup(effective[5],(kMax*(effective[1]-orderI-auxiliary)-effective[5])*inverseTime[5],errorMin,errorMax);
        rate[6]=(input[4]-effective[6])*inverseTime[6];rate[7]=(input[5]-effective[7])*inverseTime[7];
        rate[8]=acEnabled?nonWindup(state[8],clip(effective[6]-acReference,voltageErrorMin,voltageErrorMax)*inverseTime[8],currentMin,currentMax):0;
        rate[9]=acEnabled?nonWindup(state[9],clip(effective[7]-acReference,voltageErrorMin,voltageErrorMax)*inverseTime[9],currentMin,currentMax):0;
        for(double f:rate)finite(f);
    }
    private void project(double[] x) {
        calculate(x);
        if(inverseTime[0]==0){x[0]=effective[0];x[1]=effective[1];x[6]=effective[6];x[7]=effective[7];}
        if(downR==0)x[2]=effective[2];if(downI==0)x[3]=effective[3];
        if(inverseTime[4]==0)x[4]=effective[4];x[5]=effective[5];
        if(acEnabled){x[8]=clip(x[8],currentMin,currentMax);x[9]=clip(x[9],currentMin,currentMax);}
        for(double value:x)finite(value);
    }
    public double rectifierCurrentOrderAmperes(){ready();calculate(state);return ratedCurrent*orderR;}
    public double inverterCurrentOrderAmperes(){ready();calculate(state);return ratedCurrent*orderI;}
    public double inverterMaximumDegrees(){
        ready();calculate(state);double cosine=cosineReference-kCos*effective[4]/Math.max(.05,effective[7]*transformerFactor)-effective[5];
        return Math.toDegrees(Math.acos(-clip(cosine,0,cosMax)));
    }
    public double acReferencePu(){ready();return acReference;}
    public double cosineReference(){ready();return cosineReference;}
    /** Fully bypassed measurements publish their algebraic values. The asymmetric DC filters
     * retain stored coordinates because their positive up-time still owns a dynamic state,
     * including when the down-time is zero. Observing a downward input does not commit it. */
    public void readPublishedStates(double[] values,int offset){
        ready();buffer(values,offset);calculate(state);System.arraycopy(effective,0,values,offset,SIZE);
        values[offset+2]=state[2];values[offset+3]=state[3];
    }
    public void step(double seconds,int stage) {
        ready();interval(seconds);if(stage!=0&&stage!=1)throw new IllegalArgumentException("Predictor or corrector required");if(seconds==0)return;
        if(stage==0&&pending||stage==1&&(!pending||seconds!=pendingStep))throw new IllegalStateException("Matching order-control stages required");
        rates();for(int i=0;i<SIZE;i++){candidate[i]=stage==0?state[i]+seconds*rate[i]:origin[i]+.5*seconds*(firstRate[i]+rate[i]);finite(candidate[i]);}
        project(candidate);record(stage==0?IntegrationStateMonitor.Stage.PREDICTOR:IntegrationStateMonitor.Stage.CORRECTOR);
        if(stage==0){System.arraycopy(state,0,origin,0,SIZE);System.arraycopy(rate,0,firstRate,0,SIZE);pendingStep=seconds;}
        System.arraycopy(candidate,0,state,0,SIZE);pending=stage==0;
    }
    public void cancelPrediction(){ready();if(pending){System.arraycopy(origin,0,state,0,SIZE);pending=false;monitor.reset();}}
    /** Exact rollback for the enclosing controller owner; never re-project a saved coordinate. */
    void restoreCoordinates(double[] values,int offset){System.arraycopy(values,offset,state,0,SIZE);pending=false;monitor.reset();}
    @Override public int staggeredStateCount(){return SIZE;}
    @Override public void readStaggeredState(double[] values,int offset){ready();buffer(values,offset);System.arraycopy(state,0,values,offset,SIZE);}
    @Override public void evaluateStaggered(double seconds,double[] values,int offset){ready();exclusive();interval(seconds);buffer(values,offset);rates();record(IntegrationStateMonitor.Stage.STAGGERED);System.arraycopy(rate,0,values,offset,SIZE);}
    @Override public void applyStaggeredState(double[] values,int offset,double seconds){ready();exclusive();interval(seconds);buffer(values,offset);for(int i=0;i<SIZE;i++)finite(values[offset+i]);System.arraycopy(values,offset,candidate,0,SIZE);project(candidate);System.arraycopy(candidate,0,state,0,SIZE);}
    @Override public Map<String,Double> getIntegrationStates(){ready();Map<String,Double> result=new LinkedHashMap<>();for(int i=0;i<SIZE;i++)result.put(NAMES[i],state[i]);return java.util.Collections.unmodifiableMap(result);}
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot(){return monitor.snapshot();}
    private void record(IntegrationStateMonitor.Stage stage){monitor.begin(stage);for(int i=0;i<SIZE;i++)monitor.record(i,state[i],rate[i]);}
    private void ready(){if(!initialized)throw new IllegalStateException("Initialize order controls first");}
    private void exclusive(){if(pending)throw new IllegalStateException("Finish or cancel local prediction before shared integration");}
    private static void buffer(double[] v,int offset){java.util.Objects.checkFromIndexSize(offset,SIZE,v.length);}
    private static double clip(double x,double lo,double hi){return Math.max(lo,Math.min(hi,x));}
    private static double nonWindup(double x,double f,double lo,double hi){return x<=lo&&f<0||x>=hi&&f>0?0:f;}
    private static void finite(double x){if(!Double.isFinite(x))throw new IllegalArgumentException("Finite order-control value required");}
    private static void positive(double x){finite(x);if(x<=0)throw new IllegalArgumentException("Positive order-control coefficient required");}
    private static double inverseOrZero(double x){double result=x==0?0:1/x;finite(result);return result;}
    private static void interval(double x){finite(x);if(x<0)throw new IllegalArgumentException("Nonnegative interval required");}
}
