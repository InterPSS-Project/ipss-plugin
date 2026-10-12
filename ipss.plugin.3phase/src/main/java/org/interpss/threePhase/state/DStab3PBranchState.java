package org.interpss.threePhase.state;

import com.interpss.common.datatype.Complex3x1ValueBean;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.state.dstab.DStabBranchState;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.impl.Dstab3PBranchImpl;

/** Phase branch state extending the core dynamic/sequence branch companion. */
public final class DStab3PBranchState extends DStabBranchState {
    public String phaseCode;
    public double ratedKva;
    public double[] fromRatios,toRatios;
    public Complex3x3State impedance,fromShunt,toShunt,yff,yft,ytf,ytt;
    public Complex3x1ValueBean fromCurrent,toCurrent;

    public DStab3PBranchState(DStab3PBranch source) {
        super(source);
        if(source.getClass()!=Dstab3PBranchImpl.class)
            throw new IllegalArgumentException("Unsupported phase branch implementation: "+source.getClass().getName());
        phaseCode=source.getPhaseCode().name(); ratedKva=source.getXfrRatedKVA();
        fromRatios=source.getFromTurnRatioABC(); toRatios=source.getToTurnRatioABC();
        impedance=Complex3x3State.capture(source.getZabc());
        fromShunt=Complex3x3State.capture(source.getFromShuntYabc());
        toShunt=Complex3x3State.capture(source.getToShuntYabc());
        if(source.hasExplicitYabc()) {
            yff=Complex3x3State.capture(source.getYffabc()); yft=Complex3x3State.capture(source.getYftabc());
            ytf=Complex3x3State.capture(source.getYtfabc()); ytt=Complex3x3State.capture(source.getYttabc());
        }
        if(source.getCurrentAbcAtFromSide()!=null)fromCurrent=new Complex3x1ValueBean(source.getCurrentAbcAtFromSide());
        if(source.getCurrentAbcAtToSide()!=null)toCurrent=new Complex3x1ValueBean(source.getCurrentAbcAtToSide());
    }
    public static DStab3PBranch create(DStab3PBranchState state) {
        var target=new Dstab3PBranchImpl(); state.map2EObject(target); state.restorePhaseFields(target); return target;
    }
    public void updateEObject(DStab3PBranch target) {
        super.updateEObject(target); restorePhaseFields(target);
    }
    /** Apply only phase fields after scalar replay has translated network identifiers. */
    public void restorePhaseFields(DStab3PBranch target) {
        target.setPhaseCode(PhaseCode.valueOf(phaseCode)); target.setXfrRatedKVA(ratedKva);
        target.setZabc(Complex3x3State.restore(impedance));
        target.setZ(z.toComplex());
        target.setFromShuntYabc(Complex3x3State.restore(fromShunt)); target.setToShuntYabc(Complex3x3State.restore(toShunt));
        target.clearPhaseTurnRatios();
        if(fromRatios!=null)target.setFromTurnRatioABC(fromRatios[0],fromRatios[1],fromRatios[2]);
        if(toRatios!=null)target.setToTurnRatioABC(toRatios[0],toRatios[1],toRatios[2]);
        target.setExplicitYabc(Complex3x3State.restore(yff),Complex3x3State.restore(yft),Complex3x3State.restore(ytf),Complex3x3State.restore(ytt));
        target.setCurrentAbcAtFromSide(fromCurrent==null?null:fromCurrent.toComplex3x1());
        target.setCurrentAbcAtToSide(toCurrent==null?null:toCurrent.toComplex3x1());
    }
}
