package org.interpss.dstab.dynLoad.impl;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.Perc1Data;

import com.interpss.core.aclf.AclfLoad;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.dynLoad.impl.DynLoadModelImpl;

/** PERC1 aggregated power-electronic reconnecting and ceasing load. */
public final class Perc1Model extends DynLoadModelImpl implements ICMLStateProvider {
    private static final double EPS = 1e-9;
    private static final int PCTRL=0,QCTRL=1,DP=2,DQ=3,WFILT=4,VFILT=5,IP=6,IQ=7;
    private final Perc1Data data;
    private final AclfLoad parentLoad;
    private final Hashtable<String,Object> states = new Hashtable<>();
    private final double[] x = new double[8], old = new double[8], k0 = new double[8];
    private double initialVoltage, pInitialModel, qInitialModel, systemBaseMva, deviceBaseMva;
    private double fracOn = 1.0, elapsed;
    private double logicVoltage;
    private Mode mode = Mode.MONITOR;
    private Timer timer = Timer.NONE;
    private double timerStart, rampStart;

    private enum Mode { MONITOR, CEASED, RAMP }
    private enum Timer { NONE, CEASE, DELAY, RECONNECT }

    public Perc1Model(BaseDStabBus<?,?> bus, AclfLoad parentLoad, String id, Perc1Data data) {
        this.parentLoad = parentLoad;
        this.data = data;
        setId(id);
        setDStabBus(bus);
        setExtendedDeviceId("PERC1_" + id + "@" + bus.getId());
        bus.getDynLoadModelList().add(this);
    }

    @Override public boolean initStates() {
        BaseDStabBus<?,?> bus=getDStabBus();
        if(bus==null||parentLoad==null)return false;
        initialVoltage=Math.max(.01,bus.getVoltageMag());
        Complex initial=parentLoad.getLoad(initialVoltage);
        if(initial==null||initial.getReal()<=EPS)return false;
        systemBaseMva=bus.getNetwork().getBaseMva();
        double lfm=data.lfm()<.001?.8:data.lfm();
        deviceBaseMva=initial.getReal()*systemBaseMva/lfm;
        setMvaBase(deviceBaseMva);
        double scale=systemBaseMva/deviceBaseMva;
        pInitialModel=initial.getReal()*scale;
        qInitialModel=initial.getImaginary()*scale;
        x[PCTRL]=pInitialModel;x[QCTRL]=qInitialModel;
        x[DP]=initialVoltage;x[DQ]=initialVoltage;x[WFILT]=0;x[VFILT]=initialVoltage;
        x[IP]=clamp(pInitialModel/initialVoltage,data.ipmin(),data.ipmax());
        x[IQ]=clamp(qInitialModel/initialVoltage,data.iqmin(),data.iqmax());
        setInitLoadPQ(initial);setLoadPQ(initial);
        // A stand-alone PERC1 is attached to one concrete load record, so it
        // represents all of that record even before the network-level dynamic
        // initialization has populated BaseDStabBus.initLoad.
        setLoadPercent(100.0);
        setEquivY(initial.conjugate().divide(initialVoltage*initialVoltage));
        fracOn=1;elapsed=0;logicVoltage=initialVoltage;mode=Mode.MONITOR;timer=Timer.NONE;
        states.put(DStabOutSymbol.OUT_SYMBOL_BUS_DEVICE_ID,getExtendedDeviceId());
        return finiteAll(x);
    }

    @Override public boolean nextStep(double dt, DynamicSimuMethod method, int flag) {
        Inputs u=inputs();
        if(flag==0){
            System.arraycopy(x,0,old,0,x.length);
            derivatives(old,u,dt,k0);
            for(int i=0;i<x.length;i++)x[i]=bypassed(i,dt)?bypassValue(i,u):old[i]+dt*k0[i];
        }else{
            double[] k1=new double[8];derivatives(x,u,dt,k1);
            for(int i=0;i<x.length;i++)x[i]=bypassed(i,dt)?bypassValue(i,u):old[i]+.5*dt*(k0[i]+k1[i]);
        }
        x[IP]=clamp(x[IP],data.ipmin(),data.ipmax());x[IQ]=clamp(x[IQ],data.iqmin(),data.iqmax());
        return finiteAll(x);
    }

    private Inputs inputs(){
        double v=Math.max(.01,getDStabBus().getVoltageMag());
        double frequency=v<.7?0:getDStabBus().getFreq()-1;
        return new Inputs(v,frequency);
    }

    private void derivatives(double[] s,Inputs u,double dt,double[] d){
        double dp=washout(data.kvp(),data.tvp(),u.v,s[DP]);
        double dq=washout(data.kvq(),data.tvq(),u.v,s[DQ]);
        double pInput=pInitialModel+dp+data.kdroop()*deadband(s[WFILT],data.dbfl(),data.dbfh());
        double qInput=qInitialModel+dq;
        d[PCTRL]=rate(pInput,s[PCTRL],data.tbp());d[QCTRL]=rate(qInput,s[QCTRL],data.tbq());
        d[DP]=rate(u.v,s[DP],data.tvp());d[DQ]=rate(u.v,s[DQ],data.tvq());
        d[WFILT]=rate(u.frequency,s[WFILT],data.tf());d[VFILT]=rate(u.v,s[VFILT],data.tv());
        double vf=Math.max(.01,s[VFILT]);
        double pc=leadLag(pInput,s[PCTRL],data.tap(),data.tbp());
        double qc=leadLag(qInput,s[QCTRL],data.taq(),data.tbq());
        double ipcmd=clamp(pc*Math.pow(vf/initialVoltage,data.nP())/vf,data.ipmin(),data.ipmax())*fracOn;
        double iqcmd=clamp(qc*Math.pow(vf/initialVoltage,data.nQ())/vf,data.iqmin(),data.iqmax())*fracOn;
        double currentLag=Math.max(data.tt(),4*dt);
        d[IP]=rate(ipcmd,s[IP],currentLag);d[IQ]=rate(iqcmd,s[IQ],currentLag);
    }

    private boolean bypassed(int i,double dt){return switch(i){
        case PCTRL -> data.tbp()<=EPS;case QCTRL -> data.tbq()<=EPS;
        case DP -> data.tvp()<=EPS;case DQ -> data.tvq()<=EPS;
        case WFILT -> data.tf()<=EPS;case VFILT -> data.tv()<=EPS;
        default -> false;};}
    private double bypassValue(int i,Inputs u){return switch(i){
        case PCTRL -> pInitialModel+washout(data.kvp(),data.tvp(),u.v,x[DP])+data.kdroop()*deadband(x[WFILT],data.dbfl(),data.dbfh());
        case QCTRL -> qInitialModel+washout(data.kvq(),data.tvq(),u.v,x[DQ]);
        case DP,DQ,VFILT -> u.v;case WFILT -> u.frequency;default -> x[i];};}

    @Override public Complex getPosSeqEquivY(){return getEquivY();}
    @Override public Complex getNortonCurInj(){
        Complex v=getDStabBus().getVoltage();double theta=v.getArgument();
        double scale=deviceBaseMva/systemBaseMva*(1.0+accumulatedLoadChangeFactor);
        Complex iload=new Complex(scale*(x[IP]*Math.cos(theta)+x[IQ]*Math.sin(theta)),
                scale*(x[IP]*Math.sin(theta)-x[IQ]*Math.cos(theta)));
        Complex power=v.multiply(iload.conjugate());setLoadPQ(power);
        return getEquivY().multiply(v).subtract(iload);
    }
    @Override public Object getOutputObject(){return getNortonCurInj();}
    @Override public boolean updateAttributes(boolean netChange){getNortonCurInj();return true;}

    @Override public boolean afterStep(double dt){
        double stepStart=elapsed,previousVoltage=logicVoltage;
        elapsed+=dt;logicVoltage=Math.max(.01,x[VFILT]);
        updateCeaseReconnect(logicVoltage,previousVoltage,stepStart,dt);return true;
    }
    private void updateCeaseReconnect(double v,double previousVoltage,double stepStart,double dt){
        double cease=clamp(data.fcease(),0,1),target=1-cease+Math.max(0,data.frecon())*cease;
        if(cease<=EPS)return;
        if(mode==Mode.RAMP&&timer!=Timer.DELAY){
            if(data.tramp()<=EPS||elapsed+EPS>=rampStart+data.tramp()){fracOn=target;mode=Mode.MONITOR;}
            else fracOn=1-cease+(target-(1-cease))*(elapsed-rampStart)/data.tramp();
        }
        if(mode!=Mode.CEASED){
            if(timer!=Timer.DELAY){
                if(v>=data.vcease())timer=Timer.NONE;
                else if(timer!=Timer.CEASE){
                    timer=Timer.CEASE;
                    timerStart=data.tv()>EPS
                            ?thresholdCrossingTime(previousVoltage,v,data.vcease(),stepStart,dt)
                            :elapsed;
                }
            }
            if(timer==Timer.CEASE&&elapsed-timerStart+EPS>=Math.max(0,data.tcease())){timer=Timer.DELAY;timerStart=elapsed;}
            if(timer==Timer.DELAY&&elapsed-timerStart+EPS>=Math.max(0,data.tdelay())){timer=Timer.NONE;mode=Mode.CEASED;fracOn=1-cease;}
        }else{
            double reconnectVoltage=Math.max(data.vcease(),data.vrecon());
            if(v<reconnectVoltage)timer=Timer.NONE;
            else if(timer!=Timer.RECONNECT){
                timer=Timer.RECONNECT;
                timerStart=data.tv()>EPS
                        ?thresholdCrossingTime(previousVoltage,v,reconnectVoltage,stepStart,dt)
                        :elapsed;
            }
            if(timer==Timer.RECONNECT&&elapsed-timerStart+EPS>=Math.max(0,data.trecon())){
                timer=Timer.NONE;
                if(data.tramp()<=EPS){mode=Mode.MONITOR;fracOn=target;}
                else{mode=Mode.RAMP;rampStart=elapsed;}
            }
        }
    }

    private static double thresholdCrossingTime(double previous,double current,
            double threshold,double stepStart,double dt){
        double span=current-previous;
        if(Math.abs(span)<=EPS)return stepStart;
        double fraction=(threshold-previous)/span;
        return stepStart+dt*clamp(fraction,0,1);
    }

    @Override public Map<String,Double> getNamedStates(){
        Map<String,Double> m=new LinkedHashMap<>();m.put("PLeadLag",x[PCTRL]);
        m.put("QLeadLag",x[QCTRL]);m.put("PWashout",x[DP]);m.put("QWashout",x[DQ]);
        m.put("wFilt",x[WFILT]);m.put("VFilt",x[VFILT]);m.put("Ip",x[IP]);m.put("Iq",x[IQ]);
        m.put("FracOn",fracOn);return Map.copyOf(m);
    }
    @Override public Hashtable<String,Object> getStates(Object ref){states.putAll(getNamedStates());states.put("PERC1_FracOn",fracOn);return states;}
    @Override public boolean changeLoad(double factor){if(factor< -1)return false;accumulatedLoadChangeFactor=Math.max(-1,accumulatedLoadChangeFactor+factor);return true;}
    public Perc1Data getData(){return data;}public double getFractionOn(){return fracOn;}
    public String getOperatingMode(){return mode.name();}public String getTimerMode(){return timer.name();}

    private static double rate(double input,double state,double t){return t<=EPS?0:(input-state)/t;}
    private static double washout(double k,double t,double input,double lag){return t<=EPS?0:k*(input-lag)/t;}
    private static double leadLag(double input,double lag,double ta,double tb){return tb<=EPS?input:(ta/tb)*input+(1-ta/tb)*lag;}
    private static double deadband(double x,double lo,double hi){return x<lo?x-lo:x>hi?x-hi:0;}
    private static double clamp(double x,double a,double b){return Math.max(Math.min(a,b),Math.min(Math.max(a,b),x));}
    private static boolean finiteAll(double[] v){for(double x:v)if(!Double.isFinite(x))return false;return true;}
    private record Inputs(double v,double frequency){}
}
