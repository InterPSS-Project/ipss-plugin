package org.interpss.dstab.control.exc.psse.esac2a;

import java.lang.reflect.Field;

import org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter;
import org.interpss.dstab.control.util.DynamicLimitIntegrationBlock;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.controller.cml.annotate.AnControllerField;
import com.interpss.dstab.controller.cml.annotate.AnFunctionField;
import com.interpss.dstab.controller.cml.annotate.AnnotateExciter;
import com.interpss.dstab.controller.cml.annotate.util.AnControllerInitializer;
import com.interpss.dstab.controller.cml.field.ICMLFunction;
import com.interpss.dstab.controller.cml.field.ICMLStaticBlock;
import com.interpss.dstab.controller.cml.field.adapt.CMLFunctionAdapter;
import com.interpss.dstab.controller.cml.field.adapt.CMLStaticBlockAdapter;
import com.interpss.dstab.controller.cml.field.block.DelayControlBlock;
import com.interpss.dstab.controller.cml.field.block.FilterControlBlock;
import com.interpss.dstab.controller.cml.field.block.GainBlock;
import com.interpss.dstab.controller.cml.field.block.WashoutControlBlock;
import com.interpss.dstab.datatype.CMLFieldEnum;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineIfdBase;

/** IEEE/PSS/E Type AC2A rotating exciter. */
@AnController(input="mach.vt", output="this.rectifier.y",
        refPoint="this.leadLag.u0+this.transducer.y+this.washout.y-pss.vs", display={})
public class Esac2aExciter extends AnnotateExciter {
    private final Esac2aData data;
    public double one=1.0, zero=0.0, tr, tb, tc, ka, ta, vamax, vamin, kb, vrmax, vrmin;
    public double integratorGain, initialVeMax, vfemaxEffective, kh, kf, tf, kc, kd, ke;
    public double e1, se1, e2, se2, spdmlt, va0;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="mach.vt",
            parameter={"type.NoLimit", "this.one", "this.tr"}, y0="mach.vt", initOrderNumber=-1)
    public DelayControlBlock transducer;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.refPoint+pss.vs-this.transducer.y-this.washout.y",
            parameter={"type.NoLimit", "this.one", "this.tc", "this.tb"}, y0="this.regulator.u0")
    public FilterControlBlock leadLag;

    @AnControllerField(type=CMLFieldEnum.ControlBlock, input="this.leadLag.y",
            parameter={"type.NonWindup", "this.ka", "this.ta", "this.vamax", "this.vamin"},
            y0="this.va0")
    public DelayControlBlock regulator;

    @AnControllerField(type=CMLFieldEnum.StaticBlock,
            input="this.regulator.y-this.kh*this.vfe.y",
            parameter={"type.Limit", "this.kb", "this.vrmax", "this.vrmin"},
            y0="this.fieldIntegrator.u0+this.vfe.y")
    public GainBlock vrLimiter;

    @AnControllerField(type=CMLFieldEnum.ControlBlock,
            input="this.vrLimiter.y-this.vfe.y",
            parameter={"this.integratorGain", "this.initialVeMax", "this.zero"},
            y0="this.rectifier.u0")
    public DynamicLimitIntegrationBlock fieldIntegrator;

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
            parameter={"type.NoLimit", "this.kf", "this.tf"}, feedback=true)
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

    public Esac2aExciter(String id, Esac2aData data, Machine machine) {
        super(id,"ESAC2A","PSS/E");
        this.data=data; this._data=data; setMachine(machine);
    }

    public Esac2aData getData() { return data; }

    @Override public boolean initStates(BaseDStabBus<?,?> bus, Machine machine) {
        tr=data.getTr(); tb=data.getTb(); tc=data.getTc(); ka=data.getKa(); ta=data.getTa();
        kb=data.getKb(); kh=data.getKh(); kf=data.getKf(); tf=data.getTf(); kc=data.getKc();
        kd=data.getKd(); ke=data.getKe(); e1=data.getE1(); se1=data.getSe1();
        e2=data.getE2(); se2=data.getSe2(); spdmlt=data.getSpdmlt();
        if (data.getTe()<=0.0 || tf<=0.0 || kb<=0.0) return false;
        integratorGain=1.0/data.getTe();
        double ifd=exciterIfd();
        double ve0=Exac1Exciter.solveInternalVoltage(machine.getEfd(),kc*ifd);
        double vfe0=fieldFeedback(ve0,ifd);
        vfemaxEffective=Math.max(data.getVfemax(),vfe0);
        initialVeMax=fieldVoltageUpperLimit(ve0,ifd);
        va0=kh*vfe0+vfe0/kb;
        vamax=Math.max(Math.max(data.getVamax(),data.getVamin()),va0);
        vamin=Math.min(Math.min(data.getVamax(),data.getVamin()),va0);
        vrmax=Math.max(Math.max(data.getVrmax(),data.getVrmin()),vfe0);
        vrmin=Math.min(Math.min(data.getVrmax(),data.getVrmin()),vfe0);
        boolean initialized=super.initStates(bus,machine);
        if (initialized) {
            fieldIntegrator=(DynamicLimitIntegrationBlock)AnControllerInitializer.getBlock(
                    "fieldIntegrator",getFieldWrapperList());
        }
        return initialized;
    }

    @Override public boolean nextStep(double dt, DynamicSimuMethod method, Machine machine, int flag) {
        if (fieldIntegrator != null) {
            fieldIntegrator.setLimits(fieldVoltageUpperLimit(fieldIntegrator.getY(),exciterIfd()),0.0);
        }
        return super.nextStep(dt,method,machine,flag);
    }

    double fieldFeedback(double ve, double ifd) {
        return ve*(ke+Exac1Exciter.saturation(ve,e1,se1,e2,se2))+kd*ifd;
    }

    double fieldVoltageUpperLimit(double ve, double ifd) {
        double denominator=ke+Exac1Exciter.saturation(Math.max(ve,0.0),e1,se1,e2,se2);
        if (denominator<=0.0) return Math.max(ve,0.0);
        return Math.max(0.0,(vfemaxEffective-kd*ifd)/denominator);
    }

    private double exciterIfd() {
        double ifd=getMachine().calculateIfd(MachineIfdBase.EXCITER);
        return Double.isFinite(ifd) ? ifd : 0.0;
    }

    @Override public AnController getAnController(){return getClass().getAnnotation(AnController.class);}
    @Override public Field getField(String name)throws Exception{return getClass().getField(name);}
    @Override public Object getFieldObject(Field field)throws Exception{return field.get(this);}
}
