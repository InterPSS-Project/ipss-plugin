package org.interpss.threePhase.powerflow;

import java.util.List;

import com.interpss.core.threephase.INetwork3Phase;
import org.interpss.threePhase.powerflow.control.CapacitorControlData;
import org.interpss.threePhase.powerflow.control.RegulatorControlData;


public interface DistributionPowerFlowAlgorithm {


	INetwork3Phase getNetwork();

	void setNetwork(INetwork3Phase net);

	/**
	 * Order the distribution system buses. If <radialOnly> is set to be true,
	 * then the search algorithm will determine whether the input network structure
	 * has any loop or is radial.
	 *
	 * Breadth-first search (BFS) is used for the ordering. The source bus is ordered with
	 * the sortNumber as 0, and the farest one from the source is ordered with the highest sortNumber.
	 *
	 * @param radialOnly
	 * @return
	 */
	public boolean orderDistributionBuses(boolean radialOnly);

	/**
	 * initialize the bus voltages as follows: set phase A voltages of PQ bus as 1.0/_0, PV bus as Vset/_0 , while the voltages on phases B and C
	 * are set to have -120 and 120 degrees,respectively ; source bus is the swing bus
	 *
	 * @return
	 */
	public boolean initBusVoltages();

	/**
	 * run distribution system power flow simulation
	 * @return  true if power flow converges, false if not converge.
	 */
	public boolean powerflow();

	public void setRegulatorControls(List<RegulatorControlData> controls);

	public List<RegulatorControlData> getRegulatorControls();

	public void setRegulatorControlEnabled(boolean enabled);

	public boolean isRegulatorControlEnabled();

	public void setCapacitorControls(List<CapacitorControlData> controls);

	public List<CapacitorControlData> getCapacitorControls();

	public void setCapacitorControlEnabled(boolean enabled);

	public boolean isCapacitorControlEnabled();

	public DistributionPFMethod getPFMethod();

	public void setPFMethod(DistributionPFMethod method);

	public void setTolerance(double tolerance);

	public double getTolerance();

	public void setMaxIteration(int maxIterNum);

	public int getMaxIteration();

	public int getIterationCount();

	public boolean isFixedPointFallbackUsed();

	public void setFixedPointYMatrixCacheEnabled(boolean enabled);

	public boolean isFixedPointYMatrixCacheEnabled();

	public void clearFixedPointYMatrixCache();

	public void  setInitBusVoltageEnabled(boolean enableInitBus3PhaseVolts);

	public boolean isInitBusVoltageEnabled();



}
