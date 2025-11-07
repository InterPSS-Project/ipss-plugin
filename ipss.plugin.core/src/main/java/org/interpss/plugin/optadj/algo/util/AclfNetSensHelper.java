package org.interpss.plugin.optadj.algo.util;

import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.exp.IpssNumericException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.algo.dclf.CaBranchOutageType;
import com.interpss.core.algo.dclf.CaOutageBranch;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.algo.dclf.SenAnalysisType;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.common.ReferenceBusException;


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
	 * @param aclfNet
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
	 * @return
	 */
	public double[][] calGFS(){
		BaseAclfNetwork<?,?> aclfNet = dclfAlgo.getNetwork();
		
		double[][] senMatrix = new double[aclfNet.getNoActiveBus()][aclfNet.getNoActiveBranch()];
		aclfNet.getBusList().parallelStream().filter(bus -> bus.isActive()).forEach(bus -> {
			try {
				double[] dblAry = dclfAlgo.getDclfSolver().getSenPAngle(bus.getId());
				aclfNet.getBranchList().stream().filter(branch -> branch.isActive())
						.forEach(branch -> {
							double fAng = branch.getFromAclfBus().isRefBus()? 0.0 : dblAry[branch.getFromAclfBus().getSortNumber()];
							double tAng = branch.getToAclfBus().isRefBus()? 0.0 : dblAry[branch.getToAclfBus().getSortNumber()];
							senMatrix[bus.getSortNumber()][branch.getSortNumber()] = (-branch.b1ft() * (fAng - tAng));
						});
			} catch (InterpssException | IpssNumericException | ReferenceBusException e) {
				log.error(e.toString());
			}
		});
		return senMatrix;
	}
	
	/**
	 * calculate AclfNetwork sensitivities LODF[active branch][active branch]
	 * 
	 * @return
	 */
	public double[][] calLODF(){
		BaseAclfNetwork<?,?> aclfNet = dclfAlgo.getNetwork();
		
		double[][] lodfMatrix = new double[aclfNet.getNoActiveBranch()][aclfNet.getNoActiveBranch()];
		 
		aclfNet.getBranchList().parallelStream()
			.filter(outBranch -> outBranch.isActive())
			.forEach(outBranch -> {
				DclfAlgoBranch outDclfBranch = dclfAlgo.getDclfAlgoBranch(outBranch.getId());
				CaOutageBranch caOutBranch = DclfAlgoObjectFactory.createCaOutageBranch(outDclfBranch, CaBranchOutageType.OPEN);
				try {
					int outBranchNo = outBranch.getSortNumber();
					lodfMatrix[outBranchNo] = dclfAlgo.lineOutageDFactors(caOutBranch);
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
}