package org.interpss.dstab.renewable;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.controller.runtime.IntegrationStateAccess;
import com.interpss.dstab.controller.runtime.IntegrationStateMonitor;
import com.interpss.dstab.controller.runtime.IntegrationStepAware;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.device.DynamicGenDevice;
import com.interpss.dstab.device.impl.DynamicBusDeviceImpl;

/** Nine-state grid-following voltage source with inner current PI controls and PLL. */
public final class RegccModel extends DynamicBusDeviceImpl
        implements DynamicGenDevice, RenewableConverterHost, IntegrationStepAware, ICMLStateProvider, IntegrationStateAccess {
    private static final int IQ=0,IP=1,V=2,QRATE=3,PRATE=4,EQ=5,ED=6,PLL=7,ANGLE=8;
    private static final String[] NAMES={"Reactive current PI integral","Active current PI integral",
            "Voltage measurement filter","Reactive current rate limiter","Active current or power rate limiter",
            "Internal q-axis voltage","Internal d-axis voltage","PLL frequency integral","PLL angle"};
    private final RegccData data;
    private final double[] state=new double[9],trial=new double[9],dx0=new double[9],dx1=new double[9];
    private final double[] local=new double[9],localTrial=new double[9],localRate=new double[9];
    private double[] active=state;
    private final IntegrationStateMonitor monitor=new IntegrationStateMonitor(NAMES);
    private final Hashtable<String,Object> outputs=new Hashtable<>();
    private DStabGen parentGen;
    private RenewableElectricalController controller;
    private double deviceScale=1.,initialP,initialQ,ipcmd,iqcmd,p,q,networkStep=.0005,effectiveTe,baseAngularFrequency;
    private int localSubsteps=1;
    private boolean initialized,predictorPending;
    private Complex predictorVoltage;
    private double predictorIp,predictorIq,predictorInterval;

    public RegccModel(DStabGen generator,BaseDStabBus<?,?> bus,String id,RegccData data) {
        this.data=java.util.Objects.requireNonNull(data);
        java.util.Objects.requireNonNull(generator);java.util.Objects.requireNonNull(bus);
        setParentGen(generator);setDStabBus(bus);setId(id);setExtendedDeviceId("REGCC_"+id+"@"+bus.getId());
    }
    @Override public void configureIntegrationStep(double dt) {
        if (!Double.isFinite(dt) || dt<=0) throw new IllegalArgumentException("Positive REGCC integration step required");
        if(predictorPending)throw new IllegalStateException("Cannot reconfigure REGCC between integration stages");
        networkStep=dt;effectiveTe=Math.max(data.te(),2*dt);
        if(controller!=null)controller.configureIntegrationStep(dt);
    }
    /** Configure before initialization. Parameter preparation continues to use the network interval. */
    public void setLocalSubsteps(int count) {
        if(initialized)throw new IllegalStateException("Configure REGCC local substeps before initialization");
        if(count<1 || count>128 || (count&(count-1))!=0)
            throw new IllegalArgumentException("REGCC local substeps must be a power of two from 1 through 128");
        localSubsteps=count;
    }
    public int getLocalSubsteps(){return localSubsteps;}
    public double getNetworkStep(){return networkStep;}
    public double getLocalStep(){return networkStep/localSubsteps;}
    public double getRecoveryTime(){return 4*networkStep;}
    @Override public boolean initStates(BaseDStabBus<?,?> bus) {
        if(bus==null || parentGen.getGen()==null || impedance().abs()<1e-12
                || (parentGen.getXfrZ()!=null && parentGen.getXfrZ().abs()>0))return false;
        double base=bus.getNetwork().getBaseMva();
        baseAngularFrequency=2.*Math.PI*bus.getNetwork().getFrequency();
        deviceScale=parentGen.getMvaBase()>0?base/parentGen.getMvaBase():1.;
        initialP=p=parentGen.getGen().getReal()*deviceScale;
        initialQ=q=parentGen.getGen().getImaginary()*deviceScale;
        double v=bus.getVoltageMag();if(v<.01)return false;
        state[IQ]=q/v;state[IP]=p/v;state[V]=v;state[QRATE]=q/v;
        state[PRATE]=data.rateFlag()==1?p:p/v;state[ANGLE]=bus.getVoltageAng();state[PLL]=0.;
        Complex z=parentGen.getPosGenZ();
        state[ED]=v+state[IP]*z.getReal()+state[IQ]*z.getImaginary();
        state[EQ]=state[IP]*z.getImaginary()-state[IQ]*z.getReal();
        ipcmd=p/v;iqcmd=q/v;
        predictorPending=false;
        if(controller!=null){controller.configureIntegrationStep(networkStep);controller.initialize(p,q,v);}
        configureIntegrationStep(networkStep);System.arraycopy(state,0,trial,0,9);active=state;
        monitor.reset();rates(state,bus.getVoltage(),ipcmd,iqcmd,dx0);
        observe(IntegrationStateMonitor.Stage.INITIALIZATION,state,dx0);
        outputs.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());initialized=finite(state);return initialized;
    }
    @Override public boolean nextStep(double dt,DynamicSimuMethod method,int flag) {
        if(!isActive() || !parentGen.isActive())return true;
        validateStage(dt,method,flag);
        if(controller!=null) {
            controller.step(dt,p,q,getDStabBus().getVoltageMag(),getDStabBus().getFreq(),flag);
            ipcmd=controller.getIpcmd();iqcmd=-controller.getIqcmd();
        }
        return nextStepWithSignals(dt,method,flag,getDStabBus().getVoltage(),ipcmd,iqcmd);
    }
    /** Prescribed complex voltage and positive-injection d/q current commands. */
    public boolean nextStepWithSignals(double dt,DynamicSimuMethod method,int flag,Complex voltage,double ip,double iq) {
        validateStage(dt,method,flag);
        if(voltage==null || voltage.isNaN() || voltage.isInfinite() || !Double.isFinite(ip) || !Double.isFinite(iq))return false;
        double h=dt/localSubsteps;
        if(flag==0) {
            predictorVoltage=voltage;predictorIp=ip;predictorIq=iq;predictorInterval=dt;predictorPending=true;
            System.arraycopy(state,0,trial,0,9);
            // The outer predictor holds the sampled network input. Current feedback is live.
            for(int sub=0;sub<localSubsteps;sub++) {
                rates(trial,voltage,ip,iq,dx0);observe(IntegrationStateMonitor.Stage.PREDICTOR,trial,dx0);
                for(int i=0;i<9;i++)trial[i]+=h*dx0[i];normalize(trial,voltage,ip,iq);
            }
            active=trial;
        } else {
            if(localSubsteps==1) {
                rates(trial,voltage,ip,iq,dx1);observe(IntegrationStateMonitor.Stage.CORRECTOR,trial,dx1);
                for(int i=0;i<9;i++)state[i]+=.5*dt*(dx0[i]+dx1[i]);normalize(state,voltage,ip,iq);
            } else {
                System.arraycopy(state,0,local,0,9);
                // Interpolate the two sampled outer endpoints at local stage times.
                // No local network solves or additional upstream history updates occur.
                // Re-evaluate all nine coupled equations at each local trial coordinate.
                for(int sub=0;sub<localSubsteps;sub++) {
                    double a=(double)sub/localSubsteps,b=(double)(sub+1)/localSubsteps;
                    Complex va=interpolate(predictorVoltage,voltage,a),vb=interpolate(predictorVoltage,voltage,b);
                    double ipa=predictorIp+a*(ip-predictorIp),iqa=predictorIq+a*(iq-predictorIq);
                    double ipb=predictorIp+b*(ip-predictorIp),iqb=predictorIq+b*(iq-predictorIq);
                    rates(local,va,ipa,iqa,localRate);
                    for(int i=0;i<9;i++)localTrial[i]=local[i]+h*localRate[i];
                    normalize(localTrial,va,ipa,iqa);
                    rates(localTrial,vb,ipb,iqb,dx1);observe(IntegrationStateMonitor.Stage.CORRECTOR,localTrial,dx1);
                    for(int i=0;i<9;i++)local[i]+=.5*h*(localRate[i]+dx1[i]);
                    normalize(local,vb,ipb,iqb);
                }
                System.arraycopy(local,0,state,0,9);
            }
            active=state;predictorPending=false;
        }
        return finite(active);
    }
    private void validateStage(double dt,DynamicSimuMethod method,int flag) {
        if(method!=DynamicSimuMethod.MODIFIED_EULER)throw new IllegalArgumentException("REGCC requires modified Euler");
        if(dt<=0 || !Double.isFinite(dt))throw new IllegalArgumentException("Positive REGCC integration interval required");
        if(flag!=0 && flag!=1)throw new IllegalArgumentException("REGCC integration flag must be zero or one");
        if(flag==1 && (!predictorPending || dt!=predictorInterval))
            throw new IllegalStateException("REGCC corrector requires its matching predictor");
    }
    private void rates(double[] x,Complex voltage,double ip,double iq,double[] dx) {
        rates(x,voltage,physicalCurrent(x,voltage).multiply(deviceScale),ip,iq,dx);
    }
    private void rates(double[] x,Complex voltage,Complex measuredCurrent,double ip,double iq,double[] dx) {
        Complex rotation=rotation(-x[ANGLE]),vlocal=voltage.multiply(rotation);
        Complex current=measuredCurrent.multiply(rotation);
        double measuredV=Math.max(.01,data.tfltr()>0?x[V]:voltage.abs());
        double ipRate=data.rateFlag()==1?ip*measuredV:ip;
        // Preparation uses H, independently of the actual local integration interval h.
        dx[QRATE]=reactiveRate((iq-x[QRATE])/(4*networkStep));
        dx[PRATE]=activeRate((ipRate-x[PRATE])/(4*networkStep));
        dx[V]=data.tfltr()>0?(voltage.abs()-x[V])/data.tfltr():0.;
        double desiredId=data.rateFlag()==1?x[PRATE]/measuredV:x[PRATE];
        double errorD=desiredId-current.getReal(),errorQ=x[QRATE]+current.getImaginary();
        // Integral-state bounds are distinct from algebraic P/Q-priority clipping.
        // No output-error back-calculation is present in the native diagram.
        dx[IP]=boundedIntegralRate(x[IP],data.kii()*errorD);
        dx[IQ]=boundedIntegralRate(x[IQ],data.kii()*errorQ);
        double id=clamp(x[IP]+data.kip()*errorD,-data.imax(),data.imax());
        double iqOut=clamp(x[IQ]+data.kip()*errorQ,-data.imax(),data.imax());
        Complex target=vlocal.add(new Complex(id,-iqOut).multiply(parentGen.getPosGenZ()));
        dx[EQ]=(target.getImaginary()-x[EQ])/effectiveTe;
        dx[ED]=(target.getReal()-x[ED])/effectiveTe;
        // The published PLL integral coordinate is frequency deviation on the network base.
        dx[PLL]=measuredV<data.vpllfrz()?0.:data.kipll()*vlocal.getImaginary()/baseAngularFrequency;
        if((x[PLL]>=data.wmax()/baseAngularFrequency && dx[PLL]>0)
                || (x[PLL]<=data.wmin()/baseAngularFrequency && dx[PLL]<0))dx[PLL]=0.;
        dx[ANGLE]=clamp(data.kppll()*vlocal.getImaginary()+baseAngularFrequency*x[PLL],data.wmin(),data.wmax());
    }
    private double reactiveRate(double rate) {
        if(initialQ>0)return Math.min(data.iqrmax(),rate);
        if(initialQ<0)return Math.max(data.iqrmin(),rate);
        return rate;
    }
    private double activeRate(double rate) {
        return initialP>=0?Math.min(data.rrpwr(),rate):Math.max(-data.rrpwr(),rate);
    }
    private void normalize(double[] x,Complex voltage,double ip,double iq) {
        if(data.tfltr()==0)x[V]=voltage.abs();
        x[IQ]=clamp(x[IQ],-data.imax(),data.imax());
        x[IP]=clamp(x[IP],-data.imax(),data.imax());
        x[PLL]=clamp(x[PLL],data.wmin()/baseAngularFrequency,data.wmax()/baseAngularFrequency);
    }
    private double boundedIntegralRate(double value,double rate) {
        return (value>=data.imax() && rate>0) || (value<=-data.imax() && rate<0)?0.:rate;
    }
    private Complex physicalCurrent(double[] x,Complex voltage) {
        if(controller!=null && controller.isConverterBlocked())return Complex.ZERO;
        Complex rotation=rotation(x[ANGLE]);
        Complex current=new Complex(x[ED],x[EQ]).multiply(rotation).subtract(voltage).divide(impedance());
        Complex local=current.multiply(deviceScale).multiply(rotation.conjugate());
        double id=local.getReal(),iq=local.getImaginary();
        if(data.pqFlag()==1){id=activeCurrentLimit(id,data.imax());double limit=remaining(id);iq=clamp(iq,-limit,limit);}
        else {iq=clamp(iq,-data.imax(),data.imax());double limit=remaining(iq);id=activeCurrentLimit(id,limit);}
        return new Complex(id,iq).multiply(rotation).divide(deviceScale);
    }
    private Complex impedance(){return parentGen.getPosGenZ().multiply(parentGen.getZMultiFactor());}
    @Override public Object getOutputObject() {
        if(!isActive() || !parentGen.isActive())return Complex.ZERO;
        Complex v=getDStabBus().getVoltage();return physicalCurrent(active,v).add(v.divide(impedance()));
    }
    @Override public boolean updateAttributes(boolean netChange) {
        if(!isActive() || !parentGen.isActive()){p=0.;q=0.;return true;}
        Complex v=getDStabBus().getVoltage(),s=v.multiply(physicalCurrent(active,v).conjugate()).multiply(deviceScale);
        p=netChange?.5*(p+s.getReal()):s.getReal();q=netChange?.5*(q+s.getImaginary()):s.getImaginary();
        return Double.isFinite(p)&&Double.isFinite(q);
    }
    @Override public boolean afterStep(double dt){return Double.isFinite(dt)&&dt>=0.;}
    @Override public Hashtable<String,Object> getStates(Object ref) {
        outputs.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());
        outputs.put("REGCC_P",p);outputs.put("REGCC_Q",q);outputs.put("REGCC_ANGLE",active[ANGLE]);return outputs;
    }
    private Map<String,Double> stateMap(){var result=new LinkedHashMap<String,Double>();for(int i=0;i<9;i++)result.put(NAMES[i],active[i]);return java.util.Collections.unmodifiableMap(result);}
    @Override public Map<String,Double> getNamedStates(){
        var named=new LinkedHashMap<>(stateMap());
        if(controller instanceof ICMLStateProvider provider)provider.getNamedStates().forEach((k,v)->named.put("Electrical."+k,v));
        return java.util.Collections.unmodifiableMap(named);
    }
    @Override public Map<String,Double> getIntegrationStates(){return stateMap();}
    @Override public IntegrationStateMonitor.Snapshot getStateDerivativeSnapshot(){return monitor.snapshot();}
    private void observe(IntegrationStateMonitor.Stage stage,double[] x,double[] dx){monitor.begin(stage);for(int i=0;i<9;i++)monitor.record(i,x[i],dx[i]);}
    @Override public DStabGen getParentGen(){return parentGen;}
    @Override public void setParentGen(DStabGen gen){parentGen=gen;if(gen!=null)gen.setDynamicGenDevice(this);}
    public void setActiveElectricalController(RenewableElectricalController value){controller=value;}
    public RenewableElectricalController getActiveElectricalController(){return controller;}
    public RegccData getData(){return data;}
    public double[] getStateSnapshot(){return active.clone();}
    public double getEffectiveTe(){return effectiveTe;}
    public double getP(){return p;}
    public double getQ(){return q;}
    /** Equation diagnostic using prescribed machine-base terminal current; no state is advanced. */
    public double[] evaluateDerivatives(double[] coordinates,Complex voltage,Complex measuredCurrent,
            double activeCommand,double reactiveCommand) {
        if(coordinates.length!=9 || !finite(coordinates))throw new IllegalArgumentException("Nine finite REGCC coordinates required");
        double[] result=new double[9];rates(coordinates,voltage,measuredCurrent,activeCommand,reactiveCommand,result);return result;
    }
    /** Fixed upstream commands; reactive current is positive for reactive injection. */
    public void setCommands(double activeCommand,double reactiveCommand){ipcmd=activeCommand;iqcmd=reactiveCommand;}
    private double remaining(double priority){return Math.sqrt(Math.max(0.,data.imax()*data.imax()-priority*priority));}
    private double activeCurrentLimit(double value,double maximum){return initialP>=0?clamp(value,0.,maximum):clamp(value,-maximum,0.);}
    private static Complex rotation(double angle){return new Complex(Math.cos(angle),Math.sin(angle));}
    private static Complex interpolate(Complex a,Complex b,double fraction) {
        return new Complex(a.getReal()+fraction*(b.getReal()-a.getReal()),
                a.getImaginary()+fraction*(b.getImaginary()-a.getImaginary()));
    }
    private static double clamp(double value,double min,double max){return Math.max(min,Math.min(max,value));}
    private static boolean finite(double[] x){for(double v:x)if(!Double.isFinite(v))return false;return true;}
}
