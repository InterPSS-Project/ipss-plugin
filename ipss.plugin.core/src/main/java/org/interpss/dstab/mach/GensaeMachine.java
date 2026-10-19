package org.interpss.dstab.mach;

import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.Vector_dq;
import com.interpss.core.net.Network;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.algo.impl.DynamicSimuAdapterImpl;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.controller.cml.ICMLStateProvider;
import com.interpss.dstab.funcImpl.DStabFunction;
import com.interpss.dstab.mach.MachineIfdBase;
import com.interpss.dstab.mach.impl.SalientPoleMachineImpl;

/**
 * Five-state salient machine with exponential saturation on both axes.
 * Core mechanical integration, rating/base conversion and the fixed Norton
 * admittance are reused. The core d-axis-only electrical adapter cannot express
 * the additional q-axis saturation feedback, so this adapter retains the three
 * published electrical coordinates rather than adding a fictitious fast state.
 */
public final class GensaeMachine extends SalientPoleMachineImpl
        implements IeeeVoltageCompensatedMachine, ICMLStateProvider {
    private IeeeVcData ieeeVcData;
    private final ElectricalAdapter electrical = new ElectricalAdapter();

    public GensaeMachine() { _dEqnSolver = electrical; }

    @Override public boolean initStates(BaseDStabBus<?,?> bus) {
        electrical.initialized=false;electrical.old=null;electrical.first=null;
        return super.initStates(bus);
    }

    @Override protected double cal_dW_dt(double speed,double baseFrequency) {
        return PsseSwingEquation.speedDerivative(this,speed,baseFrequency);
    }
    @Override public IeeeVcData getIeeeVcData() { return ieeeVcData; }
    @Override public void setIeeeVcData(IeeeVcData data) { ieeeVcData=data; }
    @Override public double getSatruationFactor(double flux) {
        double s1=getSe100()*.01,s12=getSe120()*.01;
        if(s1<=0 || s12<=0 || flux<=0)return 0;
        return s1*Math.pow(flux,Math.log(s12/s1)/Math.log(1.2));
    }
    @Override public Map<String,Double> getNamedStates() {
        Map<String,Double> states=new LinkedHashMap<>();
        states.put("E'q",getEq1());states.put("Psi''q",-getPsiq11());states.put("Psi'd",getPsikd());
        states.put("Speed deviation",getSpeed()-1);states.put("Angle",getAngle());
        return Collections.unmodifiableMap(states);
    }
    /** Derivatives in the three native electrical-state coordinates, without stepping. */
    public double[] getElectricalDerivatives() {
        if(!electrical.initialized)throw new IllegalStateException("GENSAE is not initialized");
        electrical.refreshCurrent();return electrical.derivatives();
    }
    @Override public double calculateIfd(MachineIfdBase base) {
        if(electrical==null || !electrical.initialized)throw new IllegalStateException("GENSAE is not initialized");
        electrical.updateFluxes();double field=electrical.fieldDemand();
        return base==MachineIfdBase.EXCITER?field:field/(getXdAdjusted()-getXl());
    }

    private final class ElectricalAdapter extends DynamicSimuAdapterImpl {
        private double psidpp,id,iq,subtransientFlux;
        private double[] old,first;
        private boolean initialized;

        @Override public boolean initStates(BaseDStabBus<?,?> bus) {
            super.initStates(bus);
            // Saturation acts on subtransient flux, not leakage-reactance
            // voltage. Derive its magnitude from solved P/Q/V before rotation.
            Complex voltage=bus.getVoltage().divide(getVMultiFactor());
            Complex current=getParentGen().getGen().divide(bus.getVoltage()).conjugate()
                    .divide(getIMultiFactor());
            double flux=voltage.add(new Complex(getRa(),getXd11()).multiply(current)).abs();
            double sq=getSatruationFactor(flux)*(getXq()-getXl())/(getXd()-getXl());
            double effectiveXq=getXq11()+(getXq()-getXq11())/(1+sq);
            setAngle(voltage.add(new Complex(getRa(),effectiveXq).multiply(current)).getArgument());
            Vector_dq initial=getInitIdq(),v=getVdq();id=initial.d;iq=initial.q;
            psidpp=v.q+getXd11()*id+getRa()*iq;
            setPsiq11(-v.d-getRa()*id+getXq11()*iq);
            setPsikd(psidpp-(getXd11()-getXl())*id);
            setEq1(getPsikd()+(getXd1()-getXl())*id);
            refreshCurrent();setEfd(fieldDemand());initialized=true;updateOutputs();
            return true;
        }
        private void refreshCurrent() {
            updateFluxes();
            Vector_dq v=getVdq();double d=-getPsiq11()-v.d,q=psidpp-v.q;
            double determinant=getRa()*getRa()+getXd11()*getXq11();
            id=(getRa()*d+getXq11()*q)/determinant;
            iq=(-getXd11()*d+getRa()*q)/determinant;
        }
        private void updateFluxes() {
            psidpp=getEq1()*(getXd11()-getXl())/(getXd1()-getXl())
                    +getPsikd()*(getXd1()-getXd11())/(getXd1()-getXl());
            setPsid11(psidpp);
            subtransientFlux=Math.hypot(psidpp,getPsiq11());
        }
        private double dAxisError() { return getEq1()-getPsikd()-(getXd1()-getXl())*id; }
        private double fieldDemand() {
            double correction=(getXd1()-getXd11())/Math.pow(getXd1()-getXl(),2)*dAxisError();
            return getEq1()+(getXd()-getXd1())*(id+correction)+getSatruationFactor(subtransientFlux)*psidpp;
        }
        private double[] derivatives() {
            double sq=getSatruationFactor(subtransientFlux)*(getXq()-getXl())/(getXd()-getXl());
            double nativeQ=-getPsiq11();
            return new double[]{(getEfd()-fieldDemand())/getTd01(),
                    ((getXq()-getXq11())*iq-(1+sq)*nativeQ)/getTq011(),dAxisError()/getTd011()};
        }
        @Override public boolean nextStepElectricalModifiedEuler(double dt,DynamicSimuMethod method,Network<?,?> net,int flag) {
            if(method!=DynamicSimuMethod.MODIFIED_EULER || (flag!=0 && flag!=1))
                throw new UnsupportedOperationException("GENSAE requires modified Euler predictor/corrector stages");
            if(!Double.isFinite(dt) || dt<0)throw new IllegalArgumentException("Invalid GENSAE step");
            if(flag==1 && old==null)throw new IllegalStateException("GENSAE corrector requires a predictor");
            // Like the core salient solver, use the atomic current sample from
            // the network callback, not a rotated sample after rotor prediction.
            updateFluxes();double[] derivative=derivatives();
            if(flag==0){old=new double[]{getEq1(),-getPsiq11(),getPsikd()};first=derivative;}
            double[] next=new double[3];
            for(int i=0;i<3;i++)next[i]=old[i]+dt*(flag==0?first[i]:.5*(first[i]+derivative[i]));
            setEq1(next[0]);setPsiq11(-next[1]);setPsikd(next[2]);updateFluxes();updateOutputs();return true;
        }
        private void updateOutputs() { setPe(psidpp*iq-getPsiq11()*id); }
        @Override public Object getOutputObject() {
            refreshCurrent();updateOutputs();
            Complex current=ComplexFunc.createComplex(DStabFunction.transfer(new Vector_dq(id,iq),getAngle()))
                    .multiply(getIMultiFactor());
            Complex voltage=getDStabBus().getVoltage();
            setQGen(voltage.multiply(current.conjugate()).getImaginary()/getRating());
            return current.add(voltage.multiply(getYgen()));
        }
        @Override public Hashtable<String,Object> getStates(Object ref) {
            Hashtable<String,Object> states=new Hashtable<>();
            states.put(DStabOutSymbol.OUT_SYMBOL_MACH_EQ1,getEq1());
            states.put(DStabOutSymbol.OUT_SYMBOL_MACH_ED11,getPsid11());
            states.put(DStabOutSymbol.OUT_SYMBOL_MACH_EQ11,getPsiq11());return states;
        }
    }
}
