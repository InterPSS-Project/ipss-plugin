package org.interpss.threePhase.powerflow;

import com.interpss.core.aclf.BaseAclfNetwork;


public interface DistributionPowerFlowAlgorithm {
	
	
	BaseAclfNetwork getNetwork();
	
	void setNetwork(BaseAclfNetwork net);
	
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
	
	public DistributionPFMethod getPFMethod();
	
	public void setTolerance(double tolerance);
	
	public double getTolerance();
	
	public void setMaxIteration(int maxIterNum);
	
	public int getMaxIteration();
	
	public void  setInitBusVoltageEnabled(boolean enableInitBus3PhaseVolts);
	
	public boolean isInitBusVoltageEnabled();
	
	

}
