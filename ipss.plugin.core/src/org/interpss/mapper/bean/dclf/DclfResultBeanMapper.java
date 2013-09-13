
 
package org.interpss.mapper.bean.dclf;

import org.interpss.datamodel.bean.dclf.DclfBranchResultBean;
import org.interpss.datamodel.bean.dclf.DclfBusResultBean;
import org.interpss.datamodel.bean.dclf.DclfNetResultBean;
import org.interpss.mapper.bean.aclf.AclfNet2ResultBeanMapper;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.dclf.DclfAlgorithm;


public class DclfResultBeanMapper extends AclfNet2ResultBeanMapper {
	    
	/**
	 * constructor
	 */
	public DclfResultBeanMapper() {		

	}

	/**
	 * map into store in the AclfNetBean object into simuCtx object
	 * 
	 * @param netBean
	 *            AclfNetBean object
	 * @return DclfNetResultBean object
	 */
	public DclfNetResultBean map2Model(DclfAlgorithm algo) throws InterpssException {		
		DclfNetResultBean dclfResult = new DclfNetResultBean();

		map2Model(algo, dclfResult);

		return dclfResult;
	}

	/**
	 * map the DclfAlgorithm object into simuCtx object
	 * 
	 * @param algo
	 *            an DclfAlgorithm object, representing a dclf algorithm
	 * @param dclfResult
	 */
	public boolean map2Model(DclfAlgorithm algo, DclfNetResultBean dclfResult) {

		AclfNetwork aclfNet = algo.getAclfNetwork();

		boolean noError = true;

		dclfResult.base_kva = aclfNet.getBaseKva();		

		for (AclfBus bus : aclfNet.getBusList()) {
			DclfBusResultBean bean = new DclfBusResultBean();
			dclfResult.bus_list.add(bean);
			mapBaseBus(algo, bus, bean);
		}
		
		for (AclfBranch branch : aclfNet.getBranchList()) {
			DclfBranchResultBean bean = new DclfBranchResultBean();
			dclfResult.branch_list.add(bean);
			mapBaseBranch(algo,branch, bean);
		}
		

		return noError;
	}	
	
	private void mapBaseBus(DclfAlgorithm algo,AclfBus bus, DclfBusResultBean bean) {
		super.mapBaseBus(bus, bean);
	}

	private void mapBaseBranch(DclfAlgorithm algo,AclfBranch branch, DclfBranchResultBean bean) {
		super.mapBaseBranch(branch, bean);
	}	
}