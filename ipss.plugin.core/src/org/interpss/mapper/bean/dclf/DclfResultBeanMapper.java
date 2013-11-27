
 
package org.interpss.mapper.bean.dclf;

import org.apache.commons.math3.complex.Complex;
import org.interpss.datamodel.bean.datatype.ComplexBean;
import org.interpss.datamodel.bean.dclf.DclfBranchResultBean;
import org.interpss.datamodel.bean.dclf.DclfBusResultBean;
import org.interpss.datamodel.bean.dclf.DclfNetResultBean;
import org.interpss.mapper.bean.aclf.BaseAclfNet2BeanMapper;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.mapper.AbstractMapper;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.dclf.DclfAlgorithm;


public class DclfResultBeanMapper extends AbstractMapper<DclfAlgorithm, DclfNetResultBean> {
	    
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
	@Override
	public DclfNetResultBean map2Model(DclfAlgorithm algo) throws InterpssException {		
		DclfNetResultBean dclfResult = new DclfNetResultBean();

		if (map2Model(algo, dclfResult))
			return dclfResult;
		else
			throw new InterpssException("Error during mapping DclfAlgorithm object to DclfNetResultBean");
	}

	/**
	 * map the DclfAlgorithm object into simuCtx object
	 * 
	 * @param algo
	 *            an DclfAlgorithm object, representing a dclf algorithm
	 * @param dclfResult
	 */
	@Override
	public boolean map2Model(DclfAlgorithm algo, DclfNetResultBean dclfResult) {

		AclfNetwork aclfNet = algo.getNetwork();

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
	
	private void mapBaseBus(DclfAlgorithm algo, AclfBus bus, DclfBusResultBean bean) {
		// map bus parameters
		BaseAclfNet2BeanMapper.mapBaseBus(bus, bean);
		
		// map Dclf result
		bean.v_mag = 1.0;
		int n = bus.getSortNumber();
		bean.v_ang = format(algo.getNetwork().isRefBus(bus) ? 0.0 : Math
				.toDegrees(algo.getBusAngle(n)));
		
		double pgen = (bus.isRefBus() ? algo.getBusPower(bus) : bus
				.getGenP());
		Complex gen = new Complex(pgen,0);
		bean.gen = new ComplexBean(format(gen));

		double pload = bus.getLoadP();
		Complex load = new Complex(pload,0);
		bean.load = new ComplexBean(format(load));
	}
	
	private void mapBaseBranch(DclfAlgorithm algo,AclfBranch branch, DclfBranchResultBean bean) {
		// map branch parameters
		BaseAclfNet2BeanMapper.mapBaseBranch(branch, bean);
		
		// map Dclf result
		double mwFlow = algo.getBranchFlow(branch, UnitType.PU);		
		Complex flow = new Complex(mwFlow, 0);		
		bean.flow_f2t = new ComplexBean(format(flow));

		//assuming lossless network
		bean.flow_t2f = new ComplexBean(format(flow));
		
		Complex loss =new Complex(0,0);
		bean.loss = new ComplexBean(format(loss));
		
		bean.cur = 0;
	}	
	

	private Complex format(Complex x) {
		return new Complex(new Double(Number2String.toStr(x.getReal())).doubleValue(), 
				           new Double(Number2String.toStr(x.getImaginary())).doubleValue());
	}
	
	private static double format(double x) {
		return new Double(Number2String.toStr(x)).doubleValue();
	}	
}