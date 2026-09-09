package org.interpss.dstab.control.exc.psse.esac1a;

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

/** IEEE 421.5/PSS/E ESAC1A rotating AC exciter. */
@AnController(input="mach.vt", output="this.rectifier.y",
        refPoint="this.leadLag.u0+this.transducer.y+this.washout.y-pss.vs", display={})
public class Esac1aExciter extends AnnotateExciter implements IntegrationStepAware {
    private static final double EPS=1.0e-12;
    private final Esac1aData data;
    public double one=1.0, tr, tb, tc, ka, ta, vamax, vamin, vrmax, vrmin;
    public double integratorGain, kf, tf, washoutGain, kc, kd, ke, e1, se1, e2, se2, spdmlt;
    private double integrationStep, minimumTimeConstantMultiplier=1.0;
    private double vuel, voel;
    private boolean hasVuel, hasVoel;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="mach.vt",
            parameter={"type.NoLimit", "this.one", "this.tr"}, y0="mach.vt", initOrderNumber=-1)
    public DelayControlBlock transducer;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.refPoint+pss.vs-this.transducer.y-this.washout.y",
            parameter={"type.NoLimit", "this.one", "this.tc", "this.tb"},
            y0="this.regulator.u0")
    public FilterControlBlock leadLag;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.leadLag.y",
            parameter={"type.NonWindup", "this.ka", "this.ta", "this.vamax", "this.vamin"},
            y0="this.vrLimiter.u0")
    public DelayControlBlock regulator;

    @AnControllerField(type=CMLFieldEnum.StaticBlock, input="this.regulator.y",
            y0="this.vrLimiter.u0")
    public ICMLStaticBlock limiterGate = new CMLStaticBlockAdapter() {
        @Override public boolean initStateY0(double y0){this.u=y0;return true;}
        @Override public double getU0(){return this.u;}
        @Override public void eulerStep1(double u,double dt){this.u=u;}
        @Override public void eulerStep2(double u,double dt){this.u=u;}
        @Override public double getY() {
            double value=this.u;
            if(hasVuel) value=Math.max(value,vuel);
            if(hasVoel) value=Math.min(value,voel);
            return value;
        }
    };

    @AnControllerField(type=CMLFieldEnum.StaticBlock, input="this.limiterGate.y",
            parameter={"type.Limit", "this.one", "this.vrmax", "this.vrmin"},
            y0="this.fieldIntegrator.u0+this.vfe.y")
    public GainBlock vrLimiter;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.vrLimiter.y-this.vfe.y",
            parameter={"type.NoLimit", "this.integratorGain"}, y0="this.rectifier.u0")
    public IntegrationControlBlock fieldIntegrator;

    @AnFunctionField(input={"this.fieldIntegrator.y"})
    public ICMLFunction saturation = new CMLFunctionAdapter() {
        @Override public double eval(double[] values) {
            return Exac1Exciter.saturation(values[0], e1, se1, e2, se2);
        }
    };

    @AnFunctionField(input={"this.fieldIntegrator.y", "mach.ifd"})
    public ICMLFunction vfe = new CMLFunctionAdapter() {
        @Override public double eval(double[] values) {
            double ifd = Double.isFinite(values[1]) ? values[1] : 0.0;
            return values[0]*(ke+saturation.eval(new double[] {values[0]}))+kd*ifd;
        }
    };

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.vfe.y",
            parameter={"type.NoLimit", "this.washoutGain", "this.tf"}, feedback=true)
    public WashoutControlBlock washout;

    @AnControllerField(type=CMLFieldEnum.StaticBlock, input="this.fieldIntegrator.y", y0="mach.efd")
    public ICMLStaticBlock rectifier = new CMLStaticBlockAdapter() {
        @Override public boolean initStateY0(double y0) {
            this.u=Exac1Exciter.solveInternalVoltage(y0,kc*exciterIfd());
            return Double.isFinite(this.u);
        }
        @Override public double getU0() { return this.u; }
        @Override public void eulerStep1(double u,double dt) { this.u=u; }
        @Override public void eulerStep2(double u,double dt) { this.u=u; }
        @Override public double getY() {
            double efd=this.u*Exac1Exciter.rectifierFactor(kc*exciterIfd()/Math.max(this.u,1e-12));
            return spdmlt!=0.0 ? efd*getMachine().getSpeed() : efd;
        }
    };

    public Esac1aExciter(String id,Esac1aData data,Machine machine) {
        super(id,"ESAC1A","PSS/E");
        this.data=data; this._data=data; setMachine(machine);
    }

    public Esac1aData getData() { return data; }

    @Override public void configureIntegrationStep(double seconds) {
        configureIntegrationStep(seconds,1.0);
    }

    public void configureIntegrationStep(double seconds,double multiplier) {
        integrationStep=seconds;
        minimumTimeConstantMultiplier=multiplier;
    }

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine) {
        tr=correctedTransducerTime(data.getTr()); tb=correctedOptionalTime(data.getTb());
        tc=data.getTc(); ka=data.getKa()==0.0 ? minimumTime() : data.getKa();
        ta=correctedOptionalTime(data.getTa());
        kc=data.getKc(); kd=data.getKd(); ke=data.getKe(); kf=data.getKf(); tf=data.getTf();
        e1=data.getE1(); se1=data.getSe1(); e2=data.getE2(); se2=data.getSe2();
        spdmlt=data.getSpdmlt();
        tf=correctedRequiredTime(tf);
        double te=correctedRequiredTime(data.getTe());
        if(te<=EPS||tf<=EPS||tr<0||tb<0||ta<0||tc<0||kc<0||ka<=EPS||!finiteParameters(te))return false;
        integratorGain=1.0/te;
        // CML's washout is K*T*s/(1+s*T); ESAC1A requires Kf*s/(1+s*Tf).
        washoutGain=kf/tf;
        double ifd=exciterIfd();
        double ve0=Exac1Exciter.solveInternalVoltage(machine.getEfd(),kc*ifd);
        double vr0=ve0*(ke+Exac1Exciter.saturation(ve0,e1,se1,e2,se2))+kd*ifd;
        vamax=Math.max(Math.max(data.getVamax(),data.getVamin()),vr0);
        vamin=Math.min(Math.min(data.getVamax(),data.getVamin()),vr0);
        vrmax=Math.max(Math.max(data.getVrmax(),data.getVrmin()),vr0);
        vrmin=Math.min(Math.min(data.getVrmax(),data.getVrmin()),vr0);
        return super.initStates(bus,machine);
    }

    private double minimumTime(){return minimumTimeConstantMultiplier*integrationStep;}

    private double correctedTransducerTime(double value){
        double minimum=minimumTime();
        if(value>0&&value<.25*minimum)return 0;
        if(value>.25*minimum&&value<.5*minimum)return .5*minimum;
        return value;
    }

    private double correctedOptionalTime(double value){
        double minimum=minimumTime();
        if(value>0&&value<.5*minimum)return 0;
        if(value>.5*minimum&&value<minimum)return minimum;
        return value;
    }

    private double correctedRequiredTime(double value){
        double minimum=minimumTime();
        return value>0&&value<minimum?minimum:value;
    }

    private boolean finiteParameters(double te){
        double[] values={tr,tb,tc,ka,ta,data.getVamax(),data.getVamin(),te,kf,tf,kc,kd,ke,
                e1,se1,e2,se2,data.getVrmax(),data.getVrmin(),spdmlt};
        for(double value:values)if(!Double.isFinite(value))return false;
        return true;
    }

    private double exciterIfd() {
        double ifd=getMachine().calculateIfd(MachineIfdBase.EXCITER);
        return Double.isFinite(ifd) ? ifd : 0.0;
    }

    public void setVuel(double value){vuel=value;hasVuel=true;}
    public void clearVuel(){hasVuel=false;}
    public void setVoel(double value){voel=value;hasVoel=true;}
    public void clearVoel(){hasVoel=false;}
    public double getVuel(){return vuel;}
    public double getVoel(){return voel;}

    /** Five PSS/E states: sensed ET, lead-lag, VA, VE, and washout low-pass state. */
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
    public double getGatedRegulatorOutput(){return limiterGate.getY();}
    public double getRateFeedback(){return runtimeBlock("washout").getY();}
    /** PowerWorld/IEEE diagram state VE, before the loaded-rectifier output. */
    public double getInternalFieldVoltage(){return runtimeBlock("fieldIntegrator").getY();}
    /** PowerWorld/IEEE diagram state for the filtered terminal voltage. */
    public double getSensedVoltage(){return runtimeBlock("transducer").getY();}
    /** PowerWorld/IEEE diagram VLL signal at the lead-lag output. */
    public double getLeadLagOutput(){return runtimeBlock("leadLag").getY();}

    private double getLeadLagLowPassState(){
        if(Math.abs(tb)<=EPS)return 0;
        double dynamicGain=1.0-tc/tb;
        return Math.abs(dynamicGain)>EPS?runtimeBlock("leadLag").getStateX()/dynamicGain:0;
    }

    private double getWashoutLowPassState(){
        return Math.abs(washoutGain)>EPS?runtimeBlock("washout").getStateX()/washoutGain:0;
    }

    private ICMLControlBlock runtimeBlock(String name){
        for(BaseFieldAnWrapper<?> wrapper:getFieldWrapperList()){
            if(wrapper.getFieldName().equals(name)&&wrapper.getField() instanceof ICMLControlBlock block)return block;
        }
        throw new IllegalStateException("ESAC1A CML block is not initialized: "+name);
    }

    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
