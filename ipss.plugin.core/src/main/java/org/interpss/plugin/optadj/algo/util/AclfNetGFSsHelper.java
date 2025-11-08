package org.interpss.plugin.optadj.algo.util;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.interpss.numeric.exp.IpssNumericException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.common.ReferenceBusException;
import com.interpss.core.net.Branch;


/** 
* Helper class for calculating AclfNetwork GFS sensitivities. The sensitivity matrices
* are indexed by bus sort number and branch sort number set in this class.
* 
* @author  Donghao.F 
* @date 2023 Dec 29 11:47:22 
*/
public class AclfNetGFSsHelper extends BaseAclfNetSensHelper {
    private static final Logger log = LoggerFactory.getLogger(AclfNetGFSsHelper.class);
	
	/**
	 * Constructor
	 * 
	 * @param aclfNet the AclfNetwork object
	 */
	public AclfNetGFSsHelper(AclfNetwork aclfNet) {
		super(aclfNet);
	}
	
	/**
	 * calculate AclfNetwork sensitivities GFS[active bus][active branch]
	 * 
	 * @return the GSF matrix
	 */
	public Sen2DMatrix calGFS(){
		BaseAclfNetwork<?,?> aclfNet = dclfAlgo.getNetwork();
		
		// create the sensitivity matrix [no of active bus][no of active branch]
		Sen2DMatrix senMatrix = new Sen2DMatrix(aclfNet.getNoActiveBus(), aclfNet.getNoActiveBranch());
		log.info("Calculating GFS for {} buses and {} branches",
					aclfNet.getNoActiveBus(), aclfNet.getNoActiveBranch());
		
		// calculate GFS for all the active buses
		aclfNet.getBusList().parallelStream()
			.filter(bus -> bus.isActive())
			.forEach(bus -> calGFSImpl(aclfNet, bus, senMatrix, branch -> branch.isActive()));
		return senMatrix;
	}
	
	/**
	 * calculate AclfNetwork sensitivities GFS[gsf bus][active branch]
	 * 
	 * @param gfsBusIdSet the set of bus IDs for which GFS is to be calculated
	 * @return the GSF matrix
	 */
	public Sen2DMatrix calGFS(Set<String> gfsBusIdSet){
		BaseAclfNetwork<?,?> aclfNet = dclfAlgo.getNetwork();
		
		// create the sensitivity matrix [no of gsf][no of active branch]
		Sen2DMatrix senMatrix = new Sen2DMatrix(gfsBusIdSet.size(), aclfNet.getNoActiveBranch());
		log.info("Calculating GFS for {} buses and {} branches",
					gfsBusIdSet.size(), aclfNet.getNoActiveBranch());
	
		setGfsRowIndex(aclfNet, senMatrix, gfsBusIdSet);
		
		// calculate GFS only for the specified bus set
		aclfNet.getBusList().parallelStream()
			.filter(bus -> bus.isActive() && gfsBusIdSet.contains(bus.getId()))
			.forEach(bus -> calGFSImpl(aclfNet, bus, senMatrix, branch -> branch.isActive()));
		
		return senMatrix;
	}
	
	/**
	 * calculate AclfNetwork sensitivities GFS[gsf bus][monitor branch]
	 * 
	 * @param gfsBusIdSet the set of bus IDs for which GFS is to be calculated
	 * @param monBranchIdSet the set of branch IDs to be monitored
	 * @return the GSF matrix
	 */
	public Sen2DMatrix calGFS(Set<String> gfsBusIdSet, Set<String> monBranchIdSet){
		BaseAclfNetwork<?,?> aclfNet = dclfAlgo.getNetwork();
		
		// create the sensitivity matrix [no of gsf][no of branch]
		Sen2DMatrix senMatrix = new Sen2DMatrix(gfsBusIdSet.size(), monBranchIdSet.size());
		log.info("Calculating GFS for {} buses and {} branches",
					gfsBusIdSet.size(), monBranchIdSet.size());
	
		setGfsRowIndex(aclfNet, senMatrix, gfsBusIdSet);
		
		setGfsColIndex(aclfNet, senMatrix, monBranchIdSet);
		
		// calculate GFS only for the specified bus set
		aclfNet.getBusList().parallelStream()
			.filter(bus -> bus.isActive() && gfsBusIdSet.contains(bus.getId()))
			.forEach(bus -> calGFSImpl(aclfNet, bus, senMatrix, 
					 branch -> branch.isActive() && monBranchIdSet.contains(branch.getId())));
		
		return senMatrix;
		
	}
	
	private void setGfsRowIndex(BaseAclfNetwork<?,?> aclfNet, Sen2DMatrix senMatrix, Set<String> gfsBusIdSet) {
		// calculate the row index mapping from bus sort number to gfs index
		Map<Integer, String> busIdMap = arrangeIndex(gfsBusIdSet);
		int[] rowIndex = new int[aclfNet.getNoActiveBus()];
		for (int i = 0; i < rowIndex.length; i++)
			rowIndex[i] = -1;
		
		busIdMap.forEach((no, busId) -> {
			int busNo = aclfNet.getBus(busId).getSortNumber();
			rowIndex[busNo] = no; 
		});
		
		// set the row index to the sensitivity matrix
		senMatrix.setRowIndex(rowIndex);
	}
	
	private void setGfsColIndex(BaseAclfNetwork<?,?> aclfNet, Sen2DMatrix senMatrix, Set<String> branchIdSet) {
		// calculate the col index mapping from bus sort number to gfs index
		Map<Integer, String> branchIdMap = arrangeIndex(branchIdSet);
		int[] colIndex = new int[aclfNet.getNoActiveBranch()];
		for (int i = 0; i < colIndex.length; i++)
			colIndex[i] = -1;
		
		branchIdMap.forEach((no, braId) -> {
			int busNo = aclfNet.getBranch(braId).getSortNumber();
			colIndex[busNo] = no; 
		});
		
		// set the col index to the sensitivity matrix
		senMatrix.setColIndex(colIndex);
	}
	
	private void calGFSImpl(BaseAclfNetwork<?,?> aclfNet, BaseAclfBus<?,?> bus, Sen2DMatrix senMatrix,
						Predicate<Branch> branchFilter) {					
		try {
			double[] dblAry = dclfAlgo.getDclfSolver().getSenPAngle(bus.getId());
			aclfNet.getBranchList().parallelStream()
				.filter(branch -> branchFilter.test(branch))
				.forEach(branch -> {
					double fAng = branch.getFromAclfBus().isRefBus()? 0.0 : dblAry[branch.getFromAclfBus().getSortNumber()];
					double tAng = branch.getToAclfBus().isRefBus()? 0.0 : dblAry[branch.getToAclfBus().getSortNumber()];
					senMatrix.set(bus.getSortNumber(), branch.getSortNumber(),
								  -branch.b1ft() * (fAng - tAng));
				});
		} catch (InterpssException | IpssNumericException | ReferenceBusException e) {
			log.error(e.toString());
		}
	}
}