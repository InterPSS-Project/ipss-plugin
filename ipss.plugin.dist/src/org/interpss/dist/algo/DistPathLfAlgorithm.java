package org.interpss.dist.algo;

import java.util.ArrayList;
import java.util.List;

import org.interpss.dist.algo.path.DistPathBranchLfSolver;
import org.interpss.dist.algo.path.DistPathBusLfSolver;
import org.interpss.dist.algo.path.DistPathBusWalker;
import org.interpss.dist.algo.path.DistPathNetInitinizer;
import org.interpss.dist.algo.path.DistPathNetLfSolver;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.path.NetPathWalkDirectionEnum;
import com.interpss.core.algo.path.impl.NetPathWalkAlgorithmImpl;
import com.interpss.core.datatype.Mismatch;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.net.Network;
import com.interpss.dist.DistNetwork;

/**
/*
 * - Calculate bus and branch weight
 *   In the following example, there are three branches connected to the bus. 
 *   p1, ... p3 being the branch weight, branch weight follows power flow positive direction from->to. 
 *   
 *   Bus weight is defined as 
 *      
 *            sum of all out-going branch weight 
 *        or 
 *            sum of all in-coming branch weight - bus load 
 *   
 *                 p1 -> |
 *                       |  -> p2     bus weight = p2 = p1 + p3 + gen - load
 *                 p3 -> |
 *                 
 *                 p1 -> |  -> p2
 *                       |            bus weight = p2 + p3 = p1 + gen - load
 *                       |  -> p3  
 *                       
 *  - visit branches connected to a bus 
 *     
 *       if for a bus, there is only one un-visited branch, then weight of the branch 
 *       can be determined. This algorithm only applies to radial network. 
 *                        
 *  - visit bus
 *  
 *       if all branches connected to a bus are visited, the bus is said to have visited.                    
 * 
 * @author mzhou
 *
 */

public class DistPathLfAlgorithm extends NetPathWalkAlgorithmImpl {
	private DistNetwork net = null;
	private List<String> statusInfo = null;
	
	private double tolerance = 0.001;
	private int    maxItr = 20;
	
	public DistPathLfAlgorithm(double tolerance, int maxItr) {
		this.tolerance = tolerance;
		this.maxItr = maxItr;
	}
	
	public DistPathLfAlgorithm(DistNetwork net) {
		this.net = net;
	}
	
	public DistPathLfAlgorithm(DistNetwork net, boolean debug) {
		this.net = net;
		if (debug)
			statusInfo = new ArrayList<String>();
	}

	public List<String> getStatusInfo() {
		return statusInfo;
	}

	public boolean loadflow() throws InterpssException {
		// first calculate branch power flow direction, without considering branch loss. Branch
		// loss will not change power flow direction in a radial network.
		calculateBranchFlowDirection();
		
		DistPathNetLfSolver netWalker = new DistPathNetLfSolver();
		DistPathBusLfSolver busWalker = new DistPathBusLfSolver(this.statusInfo);
		DistPathBranchLfSolver braWalker = new DistPathBranchLfSolver(this.statusInfo);

		for (int cnt = 0; cnt < this.maxItr; cnt++) {
			if (this.statusInfo != null)
				statusInfo.add("\nLf Itr " + cnt);
			
			// backward walking to calculate branch power flow, stored 
			// in branch.weight
			if (this.statusInfo != null)
				statusInfo.add("\nLf backward walking - calculate branch flow");
			netWalker.setDirection(NetPathWalkDirectionEnum.OPPOSITE_PATH);
			this.busWalkThough(this.net, netWalker, busWalker);

			// forward walking to calculate bus voltage
			if (this.statusInfo != null)
				statusInfo.add("\nLf forward walking - calculate bus voltage");
			netWalker.setDirection(NetPathWalkDirectionEnum.ALONG_PATH);
			this.branchWalkThough(this.net, netWalker, braWalker);
			
			// check convergence
			Mismatch mis = this.net.getAclfNet().maxMismatch(AclfMethod.NR);
			ipssLogger.info("Dist Path Lf, Itr " + cnt + "\nMismatch - " + mis.toString());
			if (this.statusInfo != null)
				statusInfo.add("\nMismatch - " + mis.toString());

			if (mis.maxMis.abs() < this.tolerance) {
				ipssLogger.info("Dist Path Lf converged");
				if (this.statusInfo != null)
					statusInfo.add("\nLf Converged");
				this.net.getAclfNet().setLfConverged(true);
				return true;
			}
		}

		this.net.getAclfNet().setLfConverged(false);
		ipssLogger.info("Dist Path Lf not converge");
		if (this.statusInfo != null)
			statusInfo.add("\nLf not Converge");
		
		return false;
	}

	@Override public boolean visit(Network net) {
		this.net = (DistNetwork)net;
		try {
			return loadflow();
		} catch (InterpssException e) {
			ipssLogger.severe(e.toString());
			return false;
		}
	}

	/**
	 * calculate branch power flow direction, without considering branch loss. Branch
		// loss will not change power flow direction in a radial network.
	 * 
	 *     branch.intFlag = 1, if flow direction fromBus->toBus, branch connection direction = branch flow direction
	 *     branch.intFlag = 0, if flow direction fromBus<-toBus
	 * 
	 * @throws InterpssException if there is loop in the network
	 */
	public void calculateBranchFlowDirection() throws InterpssException {
		//NetPathWalkAlgorithm walkAlgo = CoreObjectFactory.createNetPathWalkAlgorithm();

		// backward walking to determine branch power flow direction
		DistPathNetInitinizer netWalker = new DistPathNetInitinizer(NetPathWalkDirectionEnum.OPPOSITE_PATH);
		DistPathBusWalker busWalker = new DistPathBusWalker();
		busWalkThough(this.net, netWalker, busWalker);		
		
		checkNetVisitedStatus();
	}

	private void checkNetVisitedStatus() throws InterpssException {
		// all buses and branches should be visited
		boolean err = false;
		for (Bus b : this.net.getBusList()) 
		 	if (!b.isVisited()) {                                                                                                                                                                                                                                                                                                                                                                                                                            
		 		ipssLogger.warning("Dist bus not visited: " + b.getId());
		 		err = true;
		 	}
		for (Branch b : this.net.getBranchList())
		 	if (!b.isVisited()) {
		 		ipssLogger.warning("Dist branch not visited: " + b.getId());
		 		err = true;
		 	}

		if (err) {
	 		ipssLogger.warning("Error in setting Dist net branch flow direction");
	 		throw new InterpssException("Error in setting Dist net branch flow direction");
		}
	}
}
