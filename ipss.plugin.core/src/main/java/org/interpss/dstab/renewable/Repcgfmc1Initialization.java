package org.interpss.dstab.renewable;

import static org.interpss.dstab.renewable.Repcgfmc1Data.Parameter.*;
import org.apache.commons.math3.complex.Complex;

/** Algebraic initialization for the hybrid plant controller, not a dynamic kernel.
 * Caller supplies the correctly directed branch measurement on system MVA base.
 * Targets, loss estimates and integral biases are returned on plant MVA base. */
public final class Repcgfmc1Initialization {
    private Repcgfmc1Initialization() {}

    public record Measurements(double systemMva,double machineMva,double terminalVoltage,double remoteVoltage,
                               double generatorP,double generatorQ,double branchP,double branchQ,boolean branchFound) {
        public Measurements {
            for(double value:new double[]{systemMva,machineMva,terminalVoltage,remoteVoltage,generatorP,generatorQ,branchP,branchQ})
                if(!Double.isFinite(value))throw new IllegalArgumentException("Finite plant measurements required");
            if(systemMva<=0||machineMva<=0||terminalVoltage<=0||remoteVoltage<=0)
                throw new IllegalArgumentException("Positive plant bases and voltages required");
        }
    }

    public record InitialConditions(double plantMva,double pTarget,double qTarget,double activeReference,
            double reactiveReference,double resistance,double reactance,double activeLoss,double reactiveLoss,
            double compensatedVoltage,double voltageCorrection,double remoteVoltage,double remoteVoltageReference,
            double activeIntegral,double reactiveIntegral,double voltageIntegral) {}

    public static InitialConditions initialize(Repcgfmc1Data data,Measurements m) {
        double base=data.plantMva(m.machineMva()),factor=m.systemMva()/base;
        double pref=m.generatorP()*factor,qref=m.generatorQ()*factor;
        double p=(m.branchFound()?m.branchP():m.generatorP())*factor;
        double q=(m.branchFound()?m.branchQ():m.generatorQ())*factor;
        double remote=m.branchFound()?m.remoteVoltage():m.terminalVoltage();
        double r=m.branchFound()?data.value(RLOSS):0,x=m.branchFound()?data.value(XLOSS):0;
        double squared=p*p+q*q;
        // Native auto-estimation is joint: both entered coefficients must be
        // nonpositive. Do not import the other tool's independent-zero policy.
        if(m.branchFound()&&r<=0&&x<=0){r=squared>0?(pref-p)/squared:0;x=squared>0?(qref-q)/squared:0;}
        double ploss=r*squared,qloss=x*squared;
        // The published compensation assumes unity plant-bus voltage for I;
        // it does not divide the target power by measured remote voltage.
        double compensated=data.voltageReferenceFlag()==1
                ?new Complex(remote,0).add(new Complex(r,x).multiply(new Complex(p,-q))).abs():0;
        double correction=data.voltageReferenceFlag()==1?m.terminalVoltage()-compensated:0;
        double voltageIntegral=data.voltageFlag()==1&&data.value(KIVC)>0?q:0;
        double vref=data.voltageFlag()==1?remote:0;
        if(data.voltageFlag()==1&&data.value(KIVC)==0&&q!=0)
            vref+=q/data.effectiveVoltageGain()+(q>0?data.value(DBVH):data.value(DBVL));
        if(data.voltageFlag()==1&&(q<data.value(QVCMIN)||q>data.value(QVCMAX)
                ||data.value(KIVC)==0&&(vref-remote<data.value(VERRMIN)||vref-remote>data.value(VERRMAX))))
            throw new IllegalArgumentException("REPCGFMC1U initial reactive target is outside voltage-controller capability");
        return new InitialConditions(base,p,q,pref,qref,r,x,ploss,qloss,compensated,correction,remote,vref,
                pref-p-ploss,qref-q-qloss,voltageIntegral);
    }
}
