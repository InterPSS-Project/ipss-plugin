package org.interpss.threePhase.basic.dstab;

import java.util.List;

import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.dynamic.model.DynLoadModel1Phase;
import org.interpss.threePhase.dynamic.model.DynLoadModel3Phase;

import com.interpss.core.abc.IBus3Phase;
import com.interpss.dstab.BaseDStabBus;

public interface DStab3PBus extends IBus3Phase, BaseDStabBus<DStab3PGen,DStab3PLoad> {

	//public Complex3x1 get3PhaseVotlages();

    //public void set3PhaseVoltages(Complex3x1 vabc);

    //public Complex3x3 getYiiAbc();

    // TODO in the next phase, put all dynamic models in one array;
    // or we still need to separate the generators and loads;
    public List<DynLoadModel1Phase> getPhaseADynLoadList();

    public List<DynLoadModel1Phase> getPhaseBDynLoadList();

    public List<DynLoadModel1Phase> getPhaseCDynLoadList();

    public List<DynLoadModel3Phase> getThreePhaseDynLoadList();

    public List<DStab1PLoad> getSinglePhaseLoadList();

    public List<DStab3PLoad> getThreePhaseLoadList();

    public List<DStab3PGen> getThreePhaseGenList();

    public Complex3x1  calc3PhEquivCurInj();

    public Complex3x1 get3PhaseTotalLoad();

    public Complex3x1 calcLoad3PhEquivCurInj();

	public Complex3x1 cal3PhaseStaticLoad();

	public Complex3x1 injCurDynamic3Phase();

	/**
	 * Get the 3-phase net loads (in power) by subtracting the dynamic phase-oriented (including single-phase and three-phase) loads
	 * from the bus total phase-oriented loads
	 * @return
	 */
	public Complex3x1 get3PhaseNetLoadResults();

	public void set3PhaseNetLoadResults(Complex3x1 netLoad3Phase);

	public void setThreePhaseInitVoltage(Complex3x1 initVoltAbc);

	public Complex3x1 get3PhaseInitVoltage();

	public Complex3x1 calcNetPowerIntoNetwork();
}
