package org.interpss.plugin.optadj.algo.util;

import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.exp.IpssNumericException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.algo.dclf.SenAnalysisType;
import com.interpss.core.common.ReferenceBusException;


/** 
* Helper class for calculating AclfNetwork sensitivities
* 
* @author  Donghao.F 
* @date 2023 Dec 29 11:47:22 
*/
public class AclfNetSensHelper {
    private static final Logger log = LoggerFactory.getLogger(AclfNetSensHelper.class);
    
	// a AclfNetwork object
	private AclfNetwork aclfNet;
	
	/**
	 * Constructor
	 * 
	 * @param aclfNet
	 */
	public AclfNetSensHelper(AclfNetwork aclfNet) {
		this.aclfNet = aclfNet;
	}
	
	/**
	 * calculate AclfNetwork sensitivities Sen[active bus][active branch]
	 * 
	 * @return
	 */
	public float[][] calSen(){
		SenAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(aclfNet);
		setNetBusBranchNumber();
		try {
			dclfAlgo.getDclfSolver().prepareBMatrix(SenAnalysisType.PANGLE);
		} catch (InterpssException | IpssNumericException e) {
			log.error(e.toString());
		}
		
		float[][] senMatrix = new float[aclfNet.getNoActiveBus()][aclfNet.getNoActiveBranch()];
		aclfNet.getBusList().parallelStream().filter(bus -> bus.isActive()).forEach(bus -> {
			try {
				double[] dblAry = dclfAlgo.getDclfSolver().getSenPAngle(bus.getId());
				aclfNet.getBranchList().stream().filter(branch -> branch.isActive())
						.forEach(branch -> {
							double fAng = branch.getFromAclfBus().isRefBus()? 0.0 : dblAry[branch.getFromAclfBus().getSortNumber()];
							double tAng = branch.getToAclfBus().isRefBus()? 0.0 : dblAry[branch.getToAclfBus().getSortNumber()];
							senMatrix[bus.getSortNumber()][branch.getSortNumber()] = (float)(-branch.b1ft() * (fAng - tAng));
						});
			} catch (InterpssException | IpssNumericException | ReferenceBusException e) {
				log.error(e.toString());
			}
		});
		return senMatrix;
	}
	
	/**
	 * set AclfNet bus number and branch number
	 * 
	 * @param aclfNet
	 */
	private void setNetBusBranchNumber() {
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