
 
package org.interpss.mapper.bean.dclf;

import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.datamodel.bean.BaseBranchBean;
import org.interpss.datamodel.bean.BaseBusBean;
import org.interpss.datamodel.bean.aclf.AclfBranchResultBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.datatype.BranchValueBean;
import org.interpss.datamodel.bean.datatype.ComplexBean;
import org.interpss.datamodel.bean.dclf.DclfBranchResultBean;
import org.interpss.datamodel.bean.dclf.DclfBusResultBean;
import org.interpss.datamodel.bean.dclf.DclfNetResultBean;
import org.interpss.datamodel.bean.dclf.GSFResultBean;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.mapper.AbstractMapper;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfXformer;
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
	 * @return SimuContext object
	 */
	@Override
	public DclfNetResultBean map2Model(DclfAlgorithm algo)
			throws InterpssException {		
		DclfNetResultBean dclfResult = new DclfNetResultBean();

		map2Model(algo, dclfResult);

		return dclfResult;
	}

	/**
	 * map the DclfAlgorithm object into simuCtx object
	 * 
	 * @param algo
	 *            an DclfAlgorithm object, representing a dclf algorithm
	 * @param simuCtx
	 */
	@Override
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
		bean.number = bus.getNumber();
		bean.id = bus.getId();
		bean.base_v = bus.getBaseVoltage()/1000;
		bean.status = 1;
		boolean status = bus.isActive();
		if(!status)
			bean.status = 0;
		bean.v_mag = 1.0;
		int n = bus.getSortNumber();
		bean.v_ang = format(algo.getAclfNetwork().isRefBus(bus) ? 0.0 : Math
				.toDegrees(algo.getBusAngle(n)));
		
		bean.vmax = format(bus.getVLimit().getMax()) == 0? bean.vmax : format(bus.getVLimit().getMax());
		bean.vmin = format(bus.getVLimit().getMin()) == 0? bean.vmin : format(bus.getVLimit().getMin());

		bean.gen_code = bus.isGenPQ() || !bus.isGen() ? AclfBusBean.GenCode.PQ :
			(bus.isGenPV() ? AclfBusBean.GenCode.PV : AclfBusBean.GenCode.Swing);
		
		double pgen = (bus.isRefBus() ? algo.getBusPower(bus) : bus
				.getGenP());
		
		Complex gen = new Complex(pgen,0);
		bean.gen = new ComplexBean(format(gen));

		bean.load_code = bus.isConstPLoad() ? AclfBusBean.LoadCode.ConstP :
			(bus.isConstZLoad() ? AclfBusBean.LoadCode.ConstZ : AclfBusBean.LoadCode.ConstI);

		double pload = bus.getLoadP();
		Complex load = new Complex(pload,0);
		bean.load = new ComplexBean(format(load));
		
		Complex sh = bus.getShuntY();
		bean.shunt = new ComplexBean(format(sh));
		
		bean.area = (long) 1;
		bean.zone = (long) 1;
		if(bus.getArea() !=null)
		bean.area = bus.getArea().getNumber();
		if(bus.getZone() != null)
		bean.zone = bus.getZone().getNumber();
		
	}
	
	private void mapBaseBranch(DclfAlgorithm algo,AclfBranch branch, DclfBranchResultBean bean) {
		bean.id = branch.getId();
		bean.f_id = branch.getFromBus().getId();
		bean.f_num = branch.getFromBus().getNumber();
		bean.t_id = branch.getToBus().getId();
		bean.t_num = branch.getToBus().getNumber();		
		bean.cir_id = branch.getCircuitNumber();
		
		bean.status = branch.isActive()? 1 : 0; 		
		
		bean.bra_code = branch.isLine() ? BaseBranchBean.BranchCode.Line :
			(branch.isXfr() ? BaseBranchBean.BranchCode.Xfr : 
			(branch.isPSXfr() ? BaseBranchBean.BranchCode.PsXfr:BaseBranchBean.BranchCode.ZBR ));
		
		Complex z = branch.getZ();
		bean.z = new ComplexBean(z);
		bean.shunt_y = new ComplexBean(format(new Complex(0, 0)));	
		bean.ratio = new BranchValueBean(1.0,1.0);		
		if (branch.getBranchCode() == AclfBranchCode.LINE) {
			if (branch.getHShuntY() != null)				
				bean.shunt_y = new ComplexBean(format(new Complex(0, branch.getHShuntY().getImaginary()*2)));				
				
		}
		else if (branch.getBranchCode() == AclfBranchCode.XFORMER ||
				branch.getBranchCode() == AclfBranchCode.PS_XFORMER){
			AclfXformer xfr = branch.toXfr();			
			bean.ratio.f = xfr.getFromTurnRatio();
			bean.ratio.t = xfr.getToTurnRatio();			
		}
		
		bean.mvaRatingA = branch.getRatingMva1();
		bean.mvaRatingB = branch.getRatingMva2();
		bean.mvaRatingC = branch.getRatingMva3();
		
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

	private double format(double x) {
		return new Double(Number2String.toStr(x)).doubleValue();
	}

	private double format2(double x) {
		return new Double(Number2String.toStr(x, "#0.0#")).doubleValue();
	}
	
}