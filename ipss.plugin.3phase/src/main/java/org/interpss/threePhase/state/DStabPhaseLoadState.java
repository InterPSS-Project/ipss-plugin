package org.interpss.threePhase.state;

import com.interpss.common.datatype.Complex3x1ValueBean;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.threephase.LoadConnectionType;
import com.interpss.state.acsc.AcscLoadState;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab1PLoadImpl;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;

/** Frozen phase-load parameters; derived admittances are rebuilt from the copied bus. */
public final class DStabPhaseLoadState extends AcscLoadState {
    public boolean threePhase;
    public String phaseCode, connection;
    public double nominalKv, minVoltage, maxVoltage, cvrWatts, cvrVars;
    public int openDssModel;
    public double[] zipv;
    public Complex3x1ValueBean phasePower;

    public DStabPhaseLoadState(DStab1PLoad source) {
        super(source);
        if(source.getClass()!=DStab1PLoadImpl.class && source.getClass()!=DStab3PLoadImpl.class)
            throw new IllegalArgumentException("Unsupported phase load implementation: "+source.getClass().getName());
        threePhase=source instanceof DStab3PLoad;
        phaseCode=source.getPhaseCode().name();
        connection=source.getLoadConnectionType().name();
        nominalKv=source.getNominalKV();
        minVoltage=source.getVminpu(); maxVoltage=source.getVmaxpu();
        openDssModel=source.getOpenDssLoadModel();
        cvrWatts=source.getCvrWatts(); cvrVars=source.getCvrVars();
        zipv=source.getOpenDssZipv()==null?null:source.getOpenDssZipv().clone();
        phasePower=new Complex3x1ValueBean(source.getInit3PhaseLoad());
    }

    public static DStab1PLoad create(DStabPhaseLoadState state) {
        DStab1PLoad target=state.threePhase?new DStab3PLoadImpl():new DStab1PLoadImpl();
        state.map2EObject(target);
        state.restorePhaseFields(target);
        return target;
    }

    /** Replay into an already attached private load without changing its parent reference. */
    public void updateEObject(DStab1PLoad target) {
        if(threePhase!=(target instanceof DStab3PLoad))throw new IllegalArgumentException("Phase load representation changed: "+id);
        super.updateEObject(target);
        restorePhaseFields(target);
    }

    private void restorePhaseFields(DStab1PLoad target) {
        target.setPhaseCode(PhaseCode.valueOf(phaseCode));
        target.setLoadConnectionType(LoadConnectionType.valueOf(connection));
        target.setNominalKV(nominalKv);
        target.set3PhaseLoad(phasePower.toComplex3x1());
        // Imported positive-sequence loads may have an independent scalar power.
        target.setLoadCP(loadCP.toComplex());
        target.setVminpu(minVoltage); target.setVmaxpu(maxVoltage);
        target.setOpenDssLoadModel(openDssModel,cvrWatts,cvrVars,zipv==null?null:zipv.clone());
    }
}
