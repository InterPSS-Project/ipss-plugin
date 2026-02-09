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
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.contingency.dclf.CaBranchOutageType;
import com.interpss.core.aclf.contingency.dclf.CaOutageBranch;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.algo.dclf.SenAnalysisType;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBranch;
import com.interpss.core.common.ReferenceBusException;
import com.interpss.core.net.Branch;


/** 
* Base Helper class for calculating AclfNetwork sensitivities (GFS and LODF). The sensitivity matrices
* are indexed by bus sort number and branch sort number set in this class.
* 
* @author  Donghao.F 
* @date 2023 Dec 29 11:47:22 
*/
public abstract class BaseAclfNetSensHelper {
    private static final Logger log = LoggerFactory.getLogger(BaseAclfNetSensHelper.class);
    
	// a SenAnalysisAlgorithm object
	protected SenAnalysisAlgorithm dclfAlgo;
	
	/**
	 * Constructor
	 * 
	 * @param aclfNet the AclfNetwork object
	 */
	public BaseAclfNetSensHelper(AclfNetwork aclfNet) {
		//this.aclfNet = aclfNet;
		this.dclfAlgo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(aclfNet);
		setBusBranchSortNumber();
		try {
			dclfAlgo.getDclfSolver().prepareBMatrix(SenAnalysisType.PANGLE);
		} catch (InterpssException | IpssNumericException e) {
			log.error(e.toString());
		}
	}
	
	/**
	 * set AclfNet bus number and branch sort number, [0, n-1] convention for active buses/branches
	 * 
	 * @param aclfNet
	 */
	private void setBusBranchSortNumber() {
		BaseAclfNetwork<?,?> aclfNet = dclfAlgo.getNetwork();
		
		Counter cnt = new Counter();

		aclfNet.getBusList().stream()
			.filter(bus -> bus.isActive())
			.forEach(bus -> {
				//cnt.increment();
				bus.setSortNumber(cnt.getCountThenIncrement());
			});
 
		cnt.reset();
		
		aclfNet.getBranchList().stream()
				.filter(branch -> branch.isActive() && 
								  branch.getFromBus() != null && branch.getFromBus().isActive() && 
								  branch.getToBus() != null && branch.getToBus().isActive())
				.forEach(branch -> {
					//cnt.increment();
					branch.setSortNumber(cnt.getCountThenIncrement());
				});
	}
	
	/**
	 * arrange the index for the given set of control elements
	 * 
	 * @param controlElemSet the set of control elements
	 * @return the map of index to control element
	 */
	public static <T> Map<Integer, T> arrangeIndex(Set<T> controlElemSet) {
		return arrangeIndex(controlElemSet, 0);
	}
	
	/**
	 * arrange the index for the given set of control elements
	 * 
	 * @param controlElemSet the set of control elements
	 * @return the map of index to control element
	 */
	public static <T> Map<Integer, T> arrangeIndex(Set<T> controlElemSet,int point) {
		Map<Integer, T> genMap = new HashMap<>();
		int index = point;
		for (T gen : controlElemSet) {
			genMap.put(index++, gen);
//			System.out.print(gen.getName()+",");
		}
		return genMap;
	}
}