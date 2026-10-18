package org.interpss.dstab.renewable;

import static org.interpss.dstab.renewable.Reece1Data.Parameter.*;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import com.interpss.dstab.controller.cml.ICMLStateProvider;

/** Eight-state REEC_E controller, using the native 36.7 REECE diagram.
 * Commands use the InterPSS converter convention (negative Iq for positive Q).
 * This typed runtime does not declare REECE1/REECEU1 native record aliases. */
public final class Reece1Model implements RenewableElectricalController, ICMLStateProvider {
    private final Reece1Data data;
    private final double[] vq=new double[10],iq=new double[10],vp=new double[10],ip=new double[10];
    private final double[] x=new double[8];
    private double[] old,first;
    private double initialPref,initialQ,pfRatio,vref,voltageBias,preparedPref,externalPref,externalQref,paux,speed=1;
    private double pmin,pmax,qmin,qmax,vmin,vmax;
    private double heldIp,heldIpmax,heldIq,activeHold,reactiveHold,unblock;
    private boolean initialized,previousDip,blocked;
    private Signals output;
    private Repca1Model plant;

    public Reece1Model(Reece1Data data){
        this.data=Objects.requireNonNull(data);double[] c=data.constants();
        for(int i=0;i<10;i++){vq[i]=c[29+2*i];iq[i]=c[30+2*i];vp[i]=c[49+2*i];ip[i]=c[50+2*i];}
    }
    @Override public void initialize(double p,double q,double v) {
        finite(p,q,v);if(v<=0)throw new IllegalArgumentException("REECE initial voltage must be positive");
        initialPref=data.selectors().powerFlag()==1?p/speed:p;initialQ=q;pfRatio=Math.abs(p)>1e-12?q/p:0;
        pmin=Math.min(value(PMIN),p);pmax=Math.max(value(PMAX),p);
        qmin=Math.min(value(QMIN),q);qmax=Math.max(value(QMAX),q);
        double comp=compensation(p,q,v);vmin=Math.min(value(VMIN),comp);vmax=Math.max(value(VMAX),comp);
        // VFLAG=0/QFLAG=1 selects Vext, not reactive-power Qext. The
        // upstream Qmax/Qmin limits act before the user Vbias summation.
        // Native VAR3 retains Vbias; do not hide it in an equivalent offset
        // after a different limiter coordinate.
        voltageBias=value(VBIAS);
        if(qflag()==1&&data.selectors().voltageFlag()==0){initialQ=comp-voltageBias;qmin=Math.min(qmin,initialQ);qmax=Math.max(qmax,initialQ);}
        vref=value(VREF0);
        if(vref<=0||value(KQV)>0&&!(vref-v>value(DBD1)&&vref-v<value(DBD2)))vref=v;
        x[0]=v;x[1]=powerMeasurementEnabled()?p:0;
        x[2]=qflag()==2?q/v:qflag()==1&&data.selectors().voltageFlag()==1?comp:0;
        x[3]=qflag()==1?q/v:0;x[4]=qflag()==0?q/v:0;x[5]=p;x[6]=qflag()==1?comp:0;x[7]=peflag()?p:0;
        preparedPref=initialPref;externalPref=externalQref=paux=0;
        old=first=null;activeHold=reactiveHold=unblock=0;previousDip=dip();blocked=outside();
        if(plant!=null)plant.initialize(p,q,v);
        initialized=true;output=evaluate(p,q,v);
        heldIp=output.ip;heldIpmax=output.limits.ipmax;heldIq=output.rawIq;
    }

    @Override public void step(double dt,double p,double q,double v,double frequency) {
        step(dt,p,q,v,frequency,0);step(dt,p,q,v,frequency,1);
    }
    @Override public void step(double dt,double p,double q,double v,double frequency,int stage) {
        requireInitialized();finite(dt,p,q,v,frequency);
        if(dt<0||v<0||stage<0||stage>1)throw new IllegalArgumentException("REECE needs dt >= 0, voltage >= 0 and stage 0/1");
        if(dt>0&&stage==1&&old==null)throw new IllegalStateException("REECE corrector without predictor");
        if(dt==0){refreshBypasses(p,q,v);output=evaluate(p,q,v);return;}
        if(stage==0) {
            if(plant!=null)plant.step(dt,p,q,v,frequency);
            refreshBypasses(p,q,v);
            updateTimers(dt);
            double requested=initialPref+externalPref+(plant==null?0:plant.getPref());
            preparedPref+=clamp(requested-preparedPref,value(DPMIN)*dt,value(DPMAX)*dt);
            old=x.clone();output=evaluate(p,q,v);first=rates(p,q,v,output);
            advance(old,first,dt,output);
        } else {
            refreshBypasses(p,q,v);Signals end=evaluate(p,q,v);double[] last=rates(p,q,v,end),average=new double[8];
            for(int i=0;i<8;i++)average[i]=.5*(first[i]+last[i]);
            advance(old,average,dt,end);old=first=null;
        }
        refreshBypasses(p,q,v);output=evaluate(p,q,v);
        if(dip()){heldIp=output.ip;heldIpmax=output.limits.ipmax;heldIq=output.rawIq;}
        for(double state:x)if(!Double.isFinite(state))throw new IllegalStateException("REECE state overflow");
    }

    private double[] rates(double p,double q,double v,Signals s) {
        double[] dx=new double[8];boolean frozen=dip();
        dx[0]=lagRate(x[0],v,value(TRV));
        dx[1]=powerMeasurementEnabled()?lagRate(x[1],p,value(TP)):0;
        dx[6]=qflag()==1?lagRate(x[6],compensation(p,q,v),value(TR1)):0;
        if(qflag()==0)dx[4]=frozen?0:lagRate(x[4],s.qref/divisor(),value(TIQ));
        else {
            if(qflag()==2)dx[2]=Reece1LocalPi.rate(x[2],value(KQI),s.qref-q,s.limits.iqmin,s.limits.iqmax,frozen);
            else {
                if(data.selectors().voltageFlag()==1)dx[2]=Reece1LocalPi.rate(x[2],value(KQI),s.qref-q,vmin,vmax,frozen);
                dx[3]=Reece1LocalPi.rate(x[3],value(KVI),s.vError,s.limits.iqmin,s.limits.iqmax,frozen);
                double pre=x[3],down=value(KVI)*s.vError;
                if((pre>=s.limits.iqmax&&down>0&&dx[2]>0)||(pre<=s.limits.iqmin&&down<0&&dx[2]<0))dx[2]=0;
            }
        }
        // Native 36.7 keeps the active PI integrating during a voltage dip,
        // unlike the freeze arrow in its printed diagram. Only Pord freezes.
        // Cold-paired REGCB terminal-input replays distinguish these behaviors.
        if(peflag())dx[7]=Reece1LocalPi.rate(x[7],value(KPI),s.pref-x[1],pmin,pmax,false);
        dx[5]=frozen?0:lagRate(x[5],s.powerTarget,value(TPORD));
        return dx;
    }
    private void advance(double[] start,double[] rate,double dt,Signals s) {
        for(int i=0;i<8;i++)x[i]=start[i]+dt*rate[i];
        if(qflag()==2&&!dip())x[2]=Reece1LocalPi.project(x[2],s.limits.iqmin,s.limits.iqmax);
        if(qflag()==1&&!dip()) {
            if(data.selectors().voltageFlag()==1)x[2]=Reece1LocalPi.project(x[2],vmin,vmax);
            x[3]=Reece1LocalPi.project(x[3],s.limits.iqmin,s.limits.iqmax);
        }
        if(peflag())x[7]=Reece1LocalPi.project(x[7],pmin,pmax);
        x[5]=clamp(x[5],pmin,pmax);
    }
    private void refreshBypasses(double p,double q,double v) {
        if(value(TRV)==0)x[0]=v;
        if(powerMeasurementEnabled()&&value(TP)==0)x[1]=p;
        if(qflag()==1&&value(TR1)==0)x[6]=compensation(p,q,v);
        // A bypassed lag has no frozen dynamic state (native note 12).
        if(value(TIQ)==0&&qflag()==0&&!dip())x[4]=selectedQ()/divisor();
        if(value(TPORD)==0) {
            double pref=preparedPref*(data.selectors().powerFlag()==1?speed:1);
            x[5]=peflag()?clamp(x[7]+value(KPP)*(pref-x[1]),pmin,pmax):clamp(pref,pmin,pmax);
        }
    }
    private Signals evaluate(double p,double q,double v) {
        boolean dip=dip();double pref=preparedPref*(data.selectors().powerFlag()==1?speed:1);
        double power=peflag()?clamp(x[7]+value(KPP)*(pref-x[1]),pmin,pmax):clamp(pref,pmin,pmax);
        double rawIp=(x[5]+paux)/divisor();
        Limits limits=limits(rawIp,0,dip);
        double qref=selectedQ(),error=0,reactive;
        if(qflag()==0)reactive=x[4];
        else if(qflag()==2)reactive=clamp(x[2]+value(KQP)*(qref-q),limits.iqmin,limits.iqmax);
        else {
            double ref=data.selectors().voltageFlag()==1?clamp(x[2]+value(KQP)*(qref-q),vmin,vmax):clamp(voltageBias+qref,vmin,vmax);
            error=ref-x[6];reactive=clamp(x[3]+value(KVP)*error,limits.iqmin,limits.iqmax);
        }
        double injection=clamp(value(KQV)*Repca1Model.deadband(vref-x[0],value(DBD1),value(DBD2)),value(IQL1),value(IQH1));
        double rawIq=reactive+injection;
        if(!dip&&reactiveHold>0)rawIq=value(THLD)>0?heldIq:value(IQFRZ);
        limits=limits(rawIp,rawIq,dip);
        double ip=clamp(!dip&&activeHold>0?heldIp:rawIp,limits.ipmin,limits.ipmax);
        return new Signals(ip,clamp(rawIq,limits.iqmin,limits.iqmax),rawIq,qref,q,pref,power,error,limits);
    }
    private Limits limits(double ip,double iq,boolean dip) {
        if(blocked||unblock>0)return new Limits(0,0,0,0);
        double ipmax=table(x[0],49),iqmax=table(x[0],29),imax=value(IMAX);
        if(data.selectors().currentPriority(dip)==0) {
            iqmax=Math.min(iqmax,imax);double iqmin=iqmax<0?iqmax:-iqmax;
            ipmax=Math.min(ipmax,remaining(imax,clamp(iq,iqmin,iqmax)));
        } else {
            ipmax=Math.min(ipmax,imax);
            double selected=!dip&&activeHold>0?heldIp:ip;
            iqmax=Math.min(iqmax,remaining(imax,clamp(selected,-value(KE)*ipmax,ipmax)));
        }
        if(!dip&&activeHold>0)ipmax=heldIpmax;
        return new Limits(-value(KE)*ipmax,ipmax,iqmax<0?iqmax:-iqmax,iqmax);
    }
    private double table(double voltage,int offset) {
        return Reecd1Model.tableLimit(voltage,offset==29?vq:vp,offset==29?iq:ip);
    }
    private void updateTimers(double dt) {
        boolean dip=dip();
        if(previousDip&&!dip){activeHold=value(THLD2);reactiveHold=Math.abs(value(THLD));}
        else if(!dip){activeHold=Math.max(0,activeHold-dt);reactiveHold=Math.max(0,reactiveHold-dt);}
        if(outside()){blocked=true;unblock=value(TBLK);}
        else if(blocked&&x[0]>value(VBLKL)&&x[0]<value(VBLKH)){blocked=false;unblock=value(TBLK);}
        else if(!blocked)unblock=Math.max(0,unblock-dt);
        previousDip=dip;
    }
    private double selectedQ(){return clamp(data.selectors().powerFactorFlag()==1?x[1]*pfRatio:initialQ+externalQref+(plant==null?0:plant.getQref()),qmin,qmax);}
    private double compensation(double p,double q,double v) {
        if(data.selectors().voltageCompensationFlag()==0)return v+value(KC)*q;
        double safe=Math.max(.01,v),real=(value(RC)*p+value(XC)*q)/safe,imag=(value(XC)*p-value(RC)*q)/safe;
        return Math.hypot(v-real,imag);
    }
    private boolean dip(){return x[0]<value(VDIP)||x[0]>value(VUP);}
    private boolean outside(){return x[0]<value(VBLKL)||x[0]>value(VBLKH);}
    private int qflag(){return data.selectors().reactiveControlFlag();}
    private boolean peflag(){return data.selectors().activePowerPiFlag()==1;}
    private boolean powerMeasurementEnabled(){return peflag()||data.selectors().powerFactorFlag()==1;}
    private double divisor(){return Math.max(.01,x[0]);}
    private double value(Reece1Data.Parameter p){return data.value(p);}
    private static double lagRate(double state,double target,double t){return t==0?0:(target-state)/t;}
    private static double remaining(double limit,double priority){return Math.sqrt(Math.max(0,limit*limit-priority*priority));}
    private static double clamp(double x,double min,double max){return Math.max(min,Math.min(max,x));}
    private static void finite(double... values){for(double v:values)if(!Double.isFinite(v))throw new IllegalArgumentException("REECE input must be finite");}
    private void requireInitialized(){if(!initialized)throw new IllegalStateException("REECE is not initialized");}
    public void setReferenceSteps(double p,double q){requireInitialized();finite(p,q);externalPref=p;externalQref=q;}
    public void setAuxiliaryPower(double value){finite(value);paux=value;}
    public void setGeneratorSpeed(double value){finite(value);if(value<=0)throw new IllegalArgumentException("REECE speed must be positive");speed=value;}
    public Reece1Data getData(){return data;}
    public boolean isVoltageDip(){requireInitialized();return dip();}
    public boolean isBlocked(){requireInitialized();return blocked||unblock>0;}
    @Override public boolean isConverterBlocked(){return initialized && isBlocked();}
    public double getPreparedPowerReference(){requireInitialized();return preparedPref;}
    @Override public double getIpcmd(){requireInitialized();return output.ip;}
    @Override public double getIqcmd(){requireInitialized();return -output.iq;}
    @Override public Repca1Model getPlantController(){return plant;}
    @Override public void setPlantController(Repca1Model controller){if(initialized)throw new IllegalStateException("Attach REECE plant before initialization");plant=controller;}
    @Override public Map<String,Double> getNamedStates(){
        requireInitialized();Map<String,Double> states=new LinkedHashMap<>();String[] names={"Vmeas","Pmeas","PIQ","PIV","Q_V","Pord","Vcomp","PIP"};
        for(int i=0;i<8;i++)states.put(names[i],x[i]);return Collections.unmodifiableMap(states);
    }
    private record Limits(double ipmin,double ipmax,double iqmin,double iqmax){}
    private record Signals(double ip,double iq,double rawIq,double qref,double q,double pref,double powerTarget,double vError,Limits limits){}
}
