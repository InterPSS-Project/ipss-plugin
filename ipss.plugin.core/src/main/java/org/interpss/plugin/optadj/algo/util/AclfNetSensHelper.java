package org.interpss.plugin.optadj.algo.util;

import org.interpss.numeric.datatype.Counter;
import org.interpss.numeric.exp.IpssNumericException;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.common.ReferenceBusException;


/** 
* Helper class for calculating AclfNetwork sensitivities
* 
* @author  Donghao.F 
* @date 2023 Dec 29 11:47:22 
*/
public class AclfNetSensHelper {
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
		setNetBusBranchNumber(aclfNet);
		float[][] senMatrix = new float[aclfNet.getNoActiveBus()][aclfNet.getNoActiveBranch()];
		SenAnalysisAlgorithm dclfAlgo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(aclfNet);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);
		aclfNet.getBusList().parallelStream().filter(bus -> bus.isActive()).forEach(bus -> {
			try {
				double[] dblAry = dclfAlgo.getDclfSolver().getSenPAngle(bus.getId());
				aclfNet.getBranchList().stream().filter(branch -> branch.isActive() && branch.getNumber() != 0)
						.forEach(branch -> {
							senMatrix[(int) (bus.getNumber() - 1)][(int) (branch.getNumber() - 1)] = calSen(dblAry, branch);
						});
			} catch (InterpssException | IpssNumericException | ReferenceBusException e) {
				IpssLogger.getLogger().severe(e.toString());
			}
		});
		return senMatrix;
	}

	private float calSen(double[] dblAry, AclfBranch branch) {
		double fAng = branch.getFromAclfBus().isRefBus()? 0.0 : dblAry[branch.getFromAclfBus().getSortNumber()];
		double tAng = branch.getToAclfBus().isRefBus()? 0.0 : dblAry[branch.getToAclfBus().getSortNumber()];
		return  (float)(-branch.b1ft() * (fAng - tAng));
	}
	
	/**
	 * set AclfNet bus number and branch number
	 * 
	 * @param aclfNet
	 */
	private static void setNetBusBranchNumber(AclfNetwork aclfNet) {
		Counter cnt = new Counter();

		aclfNet.getBusList().stream()
			.filter(bus -> bus.isActive())
			.forEach(bus -> {
				cnt.increment();
				bus.setNumber(cnt.getCount());
			});
 
		cnt.reset();
		
		aclfNet.getBranchList().stream().filter(branch -> branch.isActive() && branch.getFromBus() != null
				&& branch.getFromBus().isActive() && branch.getToBus() != null && branch.getToBus().isActive())
				.forEach(branch -> {
					cnt.increment();
					branch.setNumber(cnt.getCount());
				});
	}
}
