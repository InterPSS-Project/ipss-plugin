package org.interpss.dstab.control.exc.psse.exac2;

import java.lang.reflect.Field;

import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.util.IntegrationStepAware;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnFunctionField;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.controller.cml.field.ICMLFunction;
import com.interpss.dstab.controller.cml.field.ICMLControlBlock;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock;
import com.interpss.dstab.controller.cml.field.adapt.CMLFunctionAdapter;
import com.interpss.dstab.controller.cml.field.adapt.CMLStaticBlockAdapter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.controller.cml.field.block.IntegrationControlBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.controller.cml.wrapper.BaseFieldAnWrapper;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** IEEE/PSS/E Type AC2 rotating exciter with field-current low-value gate. */
@AnController(input="mach.vt", output="this.rectifier.y",
        refPoint="this.leadLag.u0+this.transducer.y+this.washout.y-pss.vs", display={})
public class Exac2Exciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS=1.0e-12;
    private final Exac2Data data;
    public double one=1, tr, tb, tc, ka, ta, vamax, vamin, kb, vrmax, vrmin;
    public double integratorGain, kl, kh, kf, tf, washoutGain, kc, kd, ke, vlrEffective;
    public double e1, se1, e2, se2, spdmlt, va0;
    private double integrationStep,minimumTimeConstantMultiplier=1.0;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,input="mach.vt",
            parameter={"type.NoLimit","this.one","this.tr"},y0="mach.vt",initOrderNumber=-1)
    public DelayControlBlock transducer;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.refPoint+pss.vs-this.transducer.y-this.washout.y",
            parameter={"type.NoLimit","this.one","this.tc","this.tb"},y0="this.regulator.u0")
    public FilterControlBlock leadLag;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,input="this.leadLag.y",
            parameter={"type.NonWindup","this.ka","this.ta","this.vamax","this.vamin"},y0="this.va0")
    public DelayControlBlock regulator;

    @AnControllerField(type=CMLFieldEnum.StaticBlock,
            input="this.regulator.y-this.kh*this.vfe.y",y0="this.vrLimiter.u0")
    public ICMLStaticBlock lowValueGate=new CMLStaticBlockAdapter() {
        @Override public boolean initStateY0(double y0){this.u=y0;return true;}
        @Override public double getU0(){return this.u;}
        @Override public void eulerStep1(double u,double dt){this.u=u;}
        @Override public void eulerStep2(double u,double dt){this.u=u;}
        @Override public double getY(){
            try {
                double vfe=Exac2Exciter.this.getFieldVaule("this.vfe.y");
                return Math.min(this.u,kl*(vlrEffective-vfe));
            } catch(Exception e) {
                return this.u;
            }
        }
    };

    @AnControllerField(type=CMLFieldEnum.StaticBlock,input="this.lowValueGate.y",
            parameter={"type.Limit","this.kb","this.vrmax","this.vrmin"},
            y0="this.fieldIntegrator.u0+this.vfe.y")
    public GainBlock vrLimiter;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,input="this.vrLimiter.y-this.vfe.y",
            parameter={"type.NoLimit","this.integratorGain"},y0="this.rectifier.u0")
    public IntegrationControlBlock fieldIntegrator;

    @AnFunctionField(input={"this.fieldIntegrator.y"})
    public ICMLFunction saturation=new CMLFunctionAdapter() {
        @Override public double eval(double[] value) {
            return Exac1Exciter.saturation(value[0],e1,se1,e2,se2);
        }
    };

    @AnFunctionField(input={"this.fieldIntegrator.y","mach.ifd"})
    public ICMLFunction vfe=new CMLFunctionAdapter() {
        @Override public double eval(double[] value) {
            double ifd=Double.isFinite(value[1]) ? value[1] : 0;
            return value[0]*(ke+saturation.eval(new double[]{value[0]}))+kd*ifd;
        }
    };

    @AnControllerField(type=CMLFieldEnum.ControlBlock,input="this.vfe.y",
            parameter={"type.NoLimit","this.washoutGain","this.tf"},feedback=true)
    public WashoutControlBlock washout;

    @AnControllerField(type=CMLFieldEnum.StaticBlock,input="this.fieldIntegrator.y",y0="mach.efd")
    public ICMLStaticBlock rectifier=new CMLStaticBlockAdapter() {
        @Override public boolean initStateY0(double y0) {
            this.u=Exac1Exciter.solveInternalVoltage(y0,kc*exciterIfd());
            return Double.isFinite(this.u);
        }
        @Override public double getU0(){return this.u;}
        @Override public void eulerStep1(double u,double dt){this.u=u;}
        @Override public void eulerStep2(double u,double dt){this.u=u;}
        @Override public double getY(){
            double efd=this.u*Exac1Exciter.rectifierFactor(kc*exciterIfd()/Math.max(this.u,1e-12));
            return spdmlt!=0 ? efd*getMachine().getSpeed() : efd;
        }
    };

    public Exac2Exciter(String id,Exac2Data data,Machine machine) {
        super(id,"EXAC2","PSS/E"); this.data=data; this._data=data; setMachine(machine);
    }
    public Exac2Data getData(){return data;}

    @Override public void configureIntegrationStep(double seconds){configureIntegrationStep(seconds,1.0);}
    public void configureIntegrationStep(double seconds,double multiplier){
        integrationStep=seconds;minimumTimeConstantMultiplier=multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine) {
        tr=correctedOptionalTime(data.getTr());tb=correctedOptionalTime(data.getTb());tc=data.getTc();
        ka=data.getKa();if(Math.abs(ka)<=EPS&&minimumTime()>0)ka=minimumTime();
        ta=correctedRequiredTime(data.getTa());
        kb=data.getKb(); kl=data.getKl(); kh=data.getKh(); kf=data.getKf(); tf=data.getTf();
        kc=data.getKc(); kd=data.getKd(); ke=data.getKe(); e1=data.getE1(); se1=data.getSe1();
        e2=data.getE2();se2=data.getSe2();spdmlt=0.0;
        tf=correctedRequiredTime(data.getTf());double te=correctedRequiredTime(data.getTe());
        if(te<=EPS||tf<=EPS||tr<0||tb<0||ta<0||tc<0||kb<=0||kl<=0||kc<0
                ||!finiteParameters(te))return false;
        integratorGain=1/te;
        // CML's washout is K*T*s/(1+s*T); EXAC2 requires Kf*s/(1+s*Tf).
        washoutGain=kf/tf;
        double ifd=exciterIfd();
        double ve0=Exac1Exciter.solveInternalVoltage(machine.getEfd(),kc*ifd);
        double vfe0=ve0*(ke+Exac1Exciter.saturation(ve0,e1,se1,e2,se2))+kd*ifd;
        vlrEffective=effectiveVlr(data.getVlr(),vfe0,kl,kb);
        va0=kh*vfe0+vfe0/kb;
        vamax=Math.max(Math.max(data.getVamax(),data.getVamin()),va0);
        vamin=Math.min(Math.min(data.getVamax(),data.getVamin()),va0);
        vrmax=Math.max(Math.max(data.getVrmax(),data.getVrmin()),vfe0);
        vrmin=Math.min(Math.min(data.getVrmax(),data.getVrmin()),vfe0);
        return super.initStates(bus,machine);
    }

    private double minimumTime(){return minimumTimeConstantMultiplier*integrationStep;}
    private double correctedOptionalTime(double value){
        double minimum=minimumTime();
        if(value>0&&value<.5*minimum)return 0;
        if(value>.5*minimum&&value<minimum)return minimum;
        return value;
    }
    private double correctedRequiredTime(double value){
        double minimum=minimumTime();return value>0&&value<minimum?minimum:value;
    }
    private boolean finiteParameters(double te){
        double[] values={tr,tb,tc,ka,ta,data.getVamax(),data.getVamin(),kb,
                data.getVrmax(),data.getVrmin(),te,kl,kh,kf,tf,kc,kd,ke,
                data.getVlr(),e1,se1,e2,se2};
        for(double value:values)if(!Double.isFinite(value))return false;
        return true;
    }

    private double exciterIfd(){
        double ifd=getMachine().calculateIfd(MachineIfdBase.EXCITER);
        return Double.isFinite(ifd)?ifd:0;
    }
    public static double lowValueGate(double va,double vfe,double kh,double kl,double vlr) {
        return Math.min(va-kh*vfe,kl*(vlr-vfe));
    }
    public static double effectiveVlr(double vlr,double vfe,double kl,double kb) {
        return Math.max(vlr,vfe+vfe/(kl*kb));
    }

    /** Five PSS/E states: sensed ET, lead-lag, VA, VE, and feedback low-pass state. */
    public double[] getStateSnapshot(){
        return new double[]{runtimeBlock("transducer").getStateX(),getLeadLagLowPassState(),
                runtimeBlock("regulator").getStateX(),runtimeBlock("fieldIntegrator").getStateX(),
                getWashoutLowPassState()};
    }
    public double[] getStateInputSnapshot(){
        return new double[]{runtimeBlock("transducer").getU(),runtimeBlock("leadLag").getU(),
                runtimeBlock("regulator").getU(),runtimeBlock("fieldIntegrator").getU(),
                runtimeBlock("washout").getU()};
    }
    public double getRegulatorOutput(){return runtimeBlock("regulator").getY();}
    public double getGatedRegulatorOutput(){return runtimeStaticBlock("vrLimiter").getY();}
    /** PowerWorld/IEEE diagram state VE, before the loaded-rectifier output. */
    public double getInternalFieldVoltage(){return runtimeBlock("fieldIntegrator").getY();}
    /** PowerWorld/IEEE diagram state for the filtered terminal voltage. */
    public double getSensedVoltage(){return runtimeBlock("transducer").getY();}
    /** PowerWorld/IEEE diagram VLL signal at the lead-lag output. */
    public double getLeadLagOutput(){return runtimeBlock("leadLag").getY();}
    /** PowerWorld/IEEE diagram VF rate-feedback signal. */
    public double getRateFeedback(){return runtimeBlock("washout").getY();}
    private double getLeadLagLowPassState(){
        if(Math.abs(tb)<=EPS)return 0;double dynamicGain=1-tc/tb;
        return Math.abs(dynamicGain)>EPS?runtimeBlock("leadLag").getStateX()/dynamicGain:0;
    }
    private double getWashoutLowPassState(){
        return Math.abs(washoutGain)>EPS?runtimeBlock("washout").getStateX()/washoutGain:0;
    }
    private ICMLControlBlock runtimeBlock(String name){
        for(BaseFieldAnWrapper<?> wrapper:getFieldWrapperList())
            if(wrapper.getFieldName().equals(name)&&wrapper.getField() instanceof ICMLControlBlock block)
                return block;
        throw new IllegalStateException("EXAC2 CML block is not initialized: "+name);
    }
    private ICMLStaticBlock runtimeStaticBlock(String name){
        for(BaseFieldAnWrapper<?> wrapper:getFieldWrapperList())
            if(wrapper.getFieldName().equals(name)&&wrapper.getField() instanceof ICMLStaticBlock block)
                return block;
        throw new IllegalStateException("EXAC2 CML block is not initialized: "+name);
    }
    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
