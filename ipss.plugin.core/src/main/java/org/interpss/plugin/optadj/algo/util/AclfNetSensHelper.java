package org.interpss.plugin.optadj.algo.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.exp.IpssNumericException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.algo.dclf.CaBranchOutageType;
import com.interpss.core.algo.dclf.CaOutageBranch;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.algo.dclf.SenAnalysisType;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.common.ReferenceBusException;
import com.interpss.core.net.Branch;


/** 
* Helper class for calculating AclfNetwork sensitivities
* 
* @author  Donghao.F 
* @date 2023 Dec 29 11:47:22 
*/
public class AclfNetSensHelper {
    private static final Logger log = LoggerFactory.getLogger(AclfNetSensHelper.class);
    
	// a SenAnalysisAlgorithm object
	private SenAnalysisAlgorithm dclfAlgo;
	
	/**
	 * Constructor
	 * 
	 * @param aclfNet the AclfNetwork object
	 */
	public AclfNetSensHelper(AclfNetwork aclfNet) {
		//this.aclfNet = aclfNet;
		this.dclfAlgo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(aclfNet);
		setNetBusBranchNumber();
		try {
			dclfAlgo.getDclfSolver().prepareBMatrix(SenAnalysisType.PANGLE);
		} catch (InterpssException | IpssNumericException e) {
			log.error(e.toString());
		}
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
	
		setRowIndex(aclfNet, senMatrix, gfsBusIdSet);
		
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
	 * @param branchIdSet the set of branch IDs to be monitored
	 * @return the GSF matrix
	 */
	public Sen2DMatrix calGFS(Set<String> gfsBusIdSet, Set<String> branchIdSet){
		BaseAclfNetwork<?,?> aclfNet = dclfAlgo.getNetwork();
		
		// create the sensitivity matrix [no of gsf][no of branch]
		Sen2DMatrix senMatrix = new Sen2DMatrix(gfsBusIdSet.size(), branchIdSet.size());
	
		setRowIndex(aclfNet, senMatrix, gfsBusIdSet);
		
		setColIndex(aclfNet, senMatrix, branchIdSet);
		
		// calculate GFS only for the specified bus set
		aclfNet.getBusList().parallelStream()
			.filter(bus -> bus.isActive() && gfsBusIdSet.contains(bus.getId()))
			.forEach(bus -> calGFSImpl(aclfNet, bus, senMatrix, 
					 branch -> branch.isActive() && branchIdSet.contains(branch.getId())));
		
		return senMatrix;
		
	}
	
	private void setRowIndex(BaseAclfNetwork<?,?> aclfNet, Sen2DMatrix senMatrix, Set<String> gfsBusIdSet) {
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
	
	private void setColIndex(BaseAclfNetwork<?,?> aclfNet, Sen2DMatrix senMatrix, Set<String> branchIdSet) {
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
	
	/**
	 * calculate AclfNetwork sensitivities LODF[active branch][active branch]
	 * 
	 * @return
	 */
	public Sen2DMatrix calLODF(){
		BaseAclfNetwork<?,?> aclfNet = dclfAlgo.getNetwork();
		
		Sen2DMatrix lodfMatrix = new Sen2DMatrix(aclfNet.getNoActiveBranch(), aclfNet.getNoActiveBranch());
		 
		aclfNet.getBranchList().parallelStream()
			.filter(outBranch -> outBranch.isActive())
			.forEach(outBranch -> {
				DclfAlgoBranch outDclfBranch = dclfAlgo.getDclfAlgoBranch(outBranch.getId());
				CaOutageBranch caOutBranch = DclfAlgoObjectFactory.createCaOutageBranch(outDclfBranch, CaBranchOutageType.OPEN);
				try {
					int outBranchNo = outBranch.getSortNumber();
					double[] lodfAry = dclfAlgo.lineOutageDFactors(caOutBranch);
					for (int i = 0; i < lodfAry.length; i++) {
						lodfMatrix.set(outBranchNo, i, lodfAry[i]);
					}
				} catch (InterpssException e) {
					log.error(e.toString());
				}
			});
		
		return lodfMatrix;
	}
	
	/**
	 * set AclfNet bus number and branch number
	 * 
	 * @param aclfNet
	 */
	private void setNetBusBranchNumber() {
		BaseAclfNetwork<?,?> aclfNet = dclfAlgo.getNetwork();
		
		Counter cnt = new Counter();

		aclfNet.getBusList().stream()
			.filter(bus -> bus.isActive())
			.forEach(bus -> {
				//cnt.increment();
				bus.setSortNumber(cnt.getCountThenIncrement());
			});
 
		cnt.reset();
		
		aclfNet.getBranchList().stream().filter(branch -> branch.isActive() && branch.getFromBus() != null
				&& branch.getFromBus().isActive() && branch.getToBus() != null && branch.getToBus().isActive())
				.forEach(branch -> {
					//cnt.increment();
					branch.setSortNumber(cnt.getCountThenIncrement());
				});
	}
	
	public static <T> Map<Integer, T> arrangeIndex(Set<T> controlElemSet) {
		Map<Integer, T> genMap = new HashMap<>();
		int index = 0;
		for (T gen : controlElemSet) {
			genMap.put(index++, gen);
//			System.out.print(gen.getName()+",");
		}
		return genMap;
	}
}