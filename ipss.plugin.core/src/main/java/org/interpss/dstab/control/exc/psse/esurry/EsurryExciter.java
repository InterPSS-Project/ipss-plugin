package org.interpss.dstab.control.exc.psse.esurry;

import java.lang.reflect.Field;

import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnFunctionField;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.controller.cml.field.ICMLFunction;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock;
import com.interpss.dstab.controller.cml.field.adapt.CMLFunctionAdapter;
import com.interpss.dstab.controller.cml.field.adapt.CMLStaticBlockAdapter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.controller.cml.field.block.IntegrationControlBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/**
 * PSS/E ESURRY, named EXAC1M in the WECC approved-model list.
 *
 * <p>This is the corrected published topology: the rotating-exciter internal
 * voltage {@code VE} drives both the rectifier and the {@code VFE}
 * calculation. The latter includes demagnetizing field current and is then
 * filtered before the K16 algebraic feedback and washout paths.</p>
 */
@AnController(input="mach.vt", output="this.rectifier.y",
        refPoint="this.leadLag1.u0+this.transducer.y-pss.vs", display={})
public class EsurryExciter extends AnnotateExciter {
    private final EsurryData data;
    public double one=1.0, tr, t1, ta, tb, tc, td, k10, k16, kf, tf, washoutGain;
    public double vrmax, vrmin, integratorGain, e1, se1, e2, se2, kc, kd, ke;
    public double iref, veMax=1.0e10, veMin=0.0;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="mach.vt",
            parameter={"type.NoLimit", "this.one", "this.tr"},
            y0="mach.vt", initOrderNumber=-1)
    public DelayControlBlock transducer;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.refPoint+pss.vs-this.transducer.y",
            parameter={"type.NoLimit", "this.one", "this.ta", "this.tb"},
            y0="this.leadLag2.u0")
    public FilterControlBlock leadLag1;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.leadLag1.y",
            parameter={"type.NoLimit", "this.one", "this.tc", "this.td"},
            y0="this.regulator.u0/this.k10")
    public FilterControlBlock leadLag2;

    @AnFunctionField(input={"this.vfeFilter.y"})
    public ICMLFunction feedbackBias = new CMLFunctionAdapter() {
        @Override public double eval(double[] values) { return iref-k16*values[0]; }
    };

    @AnControllerField(type=CMLFieldEnum.StaticBlock,
            input="this.k10*this.leadLag2.y+this.feedbackBias.y-this.washout.y",
            parameter={"type.Limit", "this.one", "this.vrmax", "this.vrmin"},
            y0="this.fieldIntegrator.u0+this.vfe.y")
    public GainBlock regulator;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.regulator.y-this.vfe.y",
            parameter={"type.NonWindup", "this.integratorGain", "this.veMax", "this.veMin"},
            y0="this.rectifier.u0")
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
            double ifd=Double.isFinite(values[1]) ? values[1] : 0.0;
            return feedbackVoltage(values[0],ifd,ke,kd,e1,se1,e2,se2);
        }
    };

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.vfe.y",
            parameter={"type.NoLimit", "this.one", "this.t1"},
            y0="this.vfe.y", feedback=true)
    public DelayControlBlock vfeFilter;

    // PSS/E defines this path as KF*s/(1 + TF*s).  The shared CML washout
    // block is K*TF*s/(1 + TF*s), so its gain must be KF/TF here.
    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.vfeFilter.y",
            parameter={"type.NoLimit", "this.washoutGain", "this.tf"}, feedback=true)
    public WashoutControlBlock washout;

    @AnControllerField(type=CMLFieldEnum.StaticBlock,
            input="this.fieldIntegrator.y", y0="mach.efd")
    public ICMLStaticBlock rectifier = new CMLStaticBlockAdapter() {
        @Override public boolean initStateY0(double y0) {
            this.u=Exac1Exciter.solveInternalVoltage(y0,kc*exciterIfd());
            return Double.isFinite(this.u);
        }
        @Override public double getU0() { return this.u; }
        @Override public void eulerStep1(double u,double dt) { this.u=u; }
        @Override public void eulerStep2(double u,double dt) { this.u=u; }
        @Override public double getY() {
            return this.u*Exac1Exciter.rectifierFactor(
                    kc*exciterIfd()/Math.max(this.u,1.0e-12));
        }
    };

    public EsurryExciter(String id,EsurryData data,Machine machine) {
        super(id,"ESURRY","PSS/E");
        this.data=data;
        this._data=data;
        setMachine(machine);
    }

    public EsurryData getData() { return data; }

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine) {
        tr=data.getTr(); t1=data.getT1(); ta=data.getTa(); tb=data.getTb();
        tc=data.getTc(); td=data.getTd(); k10=data.getK10(); k16=data.getK16();
        kf=data.getKf(); tf=data.getTf(); e1=data.getE1(); se1=data.getSe1();
        e2=data.getE2(); se2=data.getSe2(); kc=data.getKc(); kd=data.getKd(); ke=data.getKe();
        if (k10<=0.0 || data.getTe()<=0.0 || tf<=0.0) return false;
        washoutGain=kf/tf;
        integratorGain=1.0/data.getTe();
        double ifd=exciterIfd();
        double ve0=Exac1Exciter.solveInternalVoltage(machine.getEfd(),kc*ifd);
        double vfe0=feedbackVoltage(ve0,ifd,ke,kd,e1,se1,e2,se2);
        iref=k16*vfe0;
        vrmax=Math.max(Math.max(data.getVrmax(),data.getVrmin()),vfe0);
        vrmin=Math.min(Math.min(data.getVrmax(),data.getVrmin()),vfe0);
        return super.initStates(bus,machine);
    }

    private double exciterIfd() {
        double ifd=getMachine().calculateIfd(MachineIfdBase.EXCITER);
        return Double.isFinite(ifd) ? ifd : 0.0;
    }

    public static double feedbackVoltage(double ve,double ifd,double ke,double kd,
            double e1,double se1,double e2,double se2) {
        return ve*(ke+Exac1Exciter.saturation(ve,e1,se1,e2,se2))+kd*ifd;
    }

    /** Rotating-exciter internal voltage VE. */
    public double getInternalFieldVoltage() { return diagnosticFieldValue("this.fieldIntegrator.y"); }
    /** Terminal-voltage transducer output. */
    public double getSensedVoltage() { return diagnosticFieldValue("this.transducer.y"); }
    /** T1-filtered VFE signal, exported by PowerWorld as VT1. */
    public double getFilteredFeedbackVoltage() { return diagnosticFieldValue("this.vfeFilter.y"); }
    /** Rate-feedback washout output VF. */
    public double getRateFeedback() { return diagnosticFieldValue("this.washout.y"); }
    /** Tc/Td lead-lag output, exported by PowerWorld as VLLcd. */
    public double getSecondLeadLagOutput() { return diagnosticFieldValue("this.leadLag2.y"); }
    /** Ta/Tb lead-lag output, exported by PowerWorld as VLLab. */
    public double getFirstLeadLagOutput() { return diagnosticFieldValue("this.leadLag1.y"); }

    private double diagnosticFieldValue(String fieldName) {
        try {
            return getFieldVaule(fieldName);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read ESURRY field " + fieldName, exception);
        }
    }

    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
