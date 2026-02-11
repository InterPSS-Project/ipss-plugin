package org.interpss.plugin.optadj.algo.util;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.contingency.ContingencyBranchOutageType;
import com.interpss.core.aclf.contingency.dclf.DclfOutageBranch;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.net.Branch;


/** 
* Helper class for calculating AclfNetwork LODF sensitivities. The sensitivity matrices
* are indexed by bus sort number and branch sort number set in this class.
* 
* @author  Donghao.F 
* @date 2023 Dec 29 11:47:22 
*/
public class AclfNetLODFsHelper extends BaseAclfNetSensHelper {
    private static final Logger log = LoggerFactory.getLogger(AclfNetLODFsHelper.class);
	
	/**
	 * Constructor
	 * 
	 * @param aclfNet the AclfNetwork object
	 */
	public AclfNetLODFsHelper(AclfNetwork aclfNet) {
		super(aclfNet);
	}
	
	/**
	 * calculate AclfNetwork sensitivities LODF[active branch][active branch]
	 * 
	 * @return the LODF matrix
	 */
	public Sen2DMatrix calLODF(){
		BaseAclfNetwork<?,?> aclfNet = dclfAlgo.getNetwork();
		
		Sen2DMatrix lodfMatrix = new Sen2DMatrix(aclfNet.getNoActiveBranch(), aclfNet.getNoActiveBranch());
		log.info("Calculating LODF for {} outage branches and {} monitor branches", 
					aclfNet.getNoActiveBranch(), aclfNet.getNoActiveBranch());
		 
		aclfNet.getBranchList().parallelStream()
			.filter(outBranch -> outBranch.isActive())
			.forEach(outBranch -> calLODFImpl(aclfNet, outBranch, lodfMatrix, 
											  monBranch -> monBranch.isActive()));
		
		return lodfMatrix;
	}
	
	/**
	 * calculate AclfNetwork sensitivities LODF[ out branch ][ active branch ]
	 * 
	 * @param outBranchIdSet the set of branch IDs for which LODF is to be calculated as outaged branch
	 * @return the LODF matrix
	 */
	public Sen2DMatrix calLODF(Set<String> outBranchIdSet){
		BaseAclfNetwork<?,?> aclfNet = dclfAlgo.getNetwork();
		
		// create the sensitivity matrix [no of out branch][no of active branch]
		Sen2DMatrix lodfMatrix = new Sen2DMatrix(outBranchIdSet.size(), aclfNet.getNoActiveBranch());
		log.info("Calculating LODF for {} outage branches and {} monitor branches", 
					outBranchIdSet.size(), aclfNet.getNoActiveBranch());
		
		// set the row index for the outaged branch
		setLodfRowIndex(aclfNet, lodfMatrix, outBranchIdSet);
		
		// calculate LODF only for the specified out branch set
		aclfNet.getBranchList().parallelStream()
			.filter(outBranch -> outBranch.isActive() && outBranchIdSet.contains(outBranch.getId()))
			.forEach(outBranch -> calLODFImpl(aclfNet, outBranch, lodfMatrix, 
					       					  monBranch -> monBranch.isActive()));
		
		return lodfMatrix;
	}
	
	/**
	 * calculate AclfNetwork sensitivities LODF[ out branch ][ mon branch ]
	 * 
	 * @param outBranchIdSet the set of branch IDs for which LODF is to be calculated as outaged branch
	 * @param monBranchIdSet the set of branch IDs to be monitored
	 * @return the LODF matrix
	 */
	public Sen2DMatrix calLODF(Set<String> outBranchIdSet, Set<String> monBranchIdSet){
		BaseAclfNetwork<?,?> aclfNet = dclfAlgo.getNetwork();
		
		// create the sensitivity matrix [no of out branch][no of mon branch]
		Sen2DMatrix lodfMatrix = new Sen2DMatrix(outBranchIdSet.size(), monBranchIdSet.size());
		log.info("Calculating LODF for {} outage branches and {} monitor branches", 
					outBranchIdSet.size(), monBranchIdSet.size());
		
		// set the row index for the outaged branch
		setLodfRowIndex(aclfNet, lodfMatrix, outBranchIdSet);
		
		// set the col index for the monitored branch
		setLodfColIndex(aclfNet, lodfMatrix, monBranchIdSet);
		
		// calculate LODF only for the specified out branch set and monitored branch set
		aclfNet.getBranchList().parallelStream()
			.filter(outBranch -> outBranch.isActive() && outBranchIdSet.contains(outBranch.getId()))
			.forEach(outBranch -> calLODFImpl(aclfNet, outBranch, lodfMatrix, 
					       					  monBranch -> monBranch.isActive() && monBranchIdSet.contains(monBranch.getId())));
		
		return lodfMatrix;
	}	
	
	private void setLodfRowIndex(BaseAclfNetwork<?,?> aclfNet, Sen2DMatrix lodfMatrix, Set<String> outBranchIdSet) {
		// calculate the row index mapping from branch sort number to lodf index
		Map<Integer, String> outBranchIdMap = arrangeIndex(outBranchIdSet);
		int[] rowIndex = new int[aclfNet.getNoActiveBranch()];
		for (int i = 0; i < rowIndex.length; i++)
			rowIndex[i] = -1;
		
		outBranchIdMap.forEach((no, braId) -> {
			int braNo = aclfNet.getBranch(braId).getSortNumber();
			rowIndex[braNo] = no; 
		});
		
		// set the row index to the sensitivity matrix
		lodfMatrix.setRowIndex(rowIndex);
	}
	
	private void setLodfColIndex(BaseAclfNetwork<?,?> aclfNet, Sen2DMatrix lodfMatrix, Set<String> monBranchIdSet) {
		// calculate the col index mapping from branch sort number to lodf index
		Map<Integer, String> monBranchIdMap = arrangeIndex(monBranchIdSet);
		int[] colIndex = new int[aclfNet.getNoActiveBranch()];
		for (int i = 0; i < colIndex.length; i++)
			colIndex[i] = -1;
		
		monBranchIdMap.forEach((no, braId) -> {
			int braNo = aclfNet.getBranch(braId).getSortNumber();
			colIndex[braNo] = no; 
		});
		
		// set the col index to the sensitivity matrix
		lodfMatrix.setColIndex(colIndex);
	}
	
	private void calLODFImpl(BaseAclfNetwork<?,?> aclfNet, AclfBranch outBranch, Sen2DMatrix lodfMatrix,
							 Predicate<Branch> branchFilter) {
		try {
			DclfAlgoBranch outDclfBranch = dclfAlgo.getDclfAlgoBranch(outBranch.getId());
			DclfOutageBranch caOutBranch = DclfAlgoObjectFactory.createCaOutageBranch(outDclfBranch, ContingencyBranchOutageType.OPEN);
			double[] lodfAry = dclfAlgo.lineOutageDFactors(caOutBranch);
			int outBranchNo = outBranch.getSortNumber();
			/*
			for (int i = 0; i < lodfAry.length; i++) {
				lodfMatrix.set(outBranchNo, i, lodfAry[i]);
			}
			*/
			aclfNet.getBranchList().parallelStream()
					.filter(branch -> branchFilter.test(branch))
					.forEach(branch -> {
						int i = branch.getSortNumber();
						lodfMatrix.set(outBranchNo, i, lodfAry[i]);
					});
		} catch (InterpssException e) {
			log.error(e.toString());
		}
	}
}