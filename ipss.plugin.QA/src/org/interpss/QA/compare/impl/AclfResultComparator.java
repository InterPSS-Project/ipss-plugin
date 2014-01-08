package org.interpss.QA.compare.impl;

import org.apache.commons.math3.complex.Complex;
import org.interpss.datamodel.bean.aclf.AclfBranchResultBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.aclf.AclfNetResultBean;
import org.interpss.datamodel.bean.datatype.ComplexBean;
import org.interpss.numeric.datatype.Unit;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

/**
 * Utility class to compare Aclf results
 * 
 * @author mzhou
 *
 */
public class AclfResultComparator extends NetModelComparator<AclfBusBean, AclfBranchResultBean> {
	public AclfResultComparator() { super(); }
	
	public AclfResultComparator(AclfNetwork net, AclfNetResultBean qaResultSet) {
		super(net, qaResultSet);
	}
	
	/**
	 * compare aclf result, assuming the AclfNetwork object has converge Aclf results
	 */
	public void compAclfResult() {
		if (!net.isLfConverged()) {
			addErrMsg("AclfNet Lf not converge");
			return;
		}
		
		double baseKva = this.qaResultSet.base_kva;
		if (!NumericUtil.equals(net.getBaseKva(), baseKva, 0.1)) {
			addErrMsg("AclfNet baseKva not the same, " + baseKva + ", " + net.getBaseKva()*0.001);
			return;			
		}
		
		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus)b;
			if (bus.isActive()) {
				AclfBusBean rec = this.qaResultSet.getBus(b.getId());
				compareBusAclfResult(bus, rec);
			}
		}
		
		for (Branch b : net.getBranchList()) {
			AclfBranch branch = (AclfBranch)b;
			if (branch.isActive()) {
				AclfBranchResultBean rec = this.qaResultSet.getBranch(branch.getId());
				compareBranchAclfResult(branch, rec);
			}
		}		
	}
	
	private void compareBusAclfResult(AclfBus bus, AclfBusBean rec) {
		/*
		 * Compare voltage
		 */
		double volt = bus.getVoltageMag();
		if (!NumericUtil.equals(rec.v_mag, volt, VMagErr)) {
			String msg = "Bus voltage mag mismatch: Bus-" + rec.id + ", " + 
					 String.format("%5.4f, %5.4f, %4.3f", rec.v_mag, volt,  
					     Math.abs(100.0*(rec.v_mag - volt)/rec.v_mag)) + "%";
			//IpssLogger.getLogger().warning(msg);
			addErrMsg(msg);
		}

		double ang = bus.getVoltageAng(Unit.UnitType.Deg);
		//double rec_ang_deg = rec.v_ang;
		if (!NumericUtil.equals(rec.v_ang, ang, VAngErr)) {
			String msg = "Bus voltage ang mismatch: Bus-" + rec.id + ", " + 
						 String.format("%5.2f, %5.2f, %4.3f", rec.v_ang, ang,
							Math.abs(100.0*(rec.v_ang - ang)/rec.v_ang)) + "%";
			//IpssLogger.getLogger().warning(msg);
			addErrMsg(msg);
		}	
		
		/*
		 * Compare gen P, Q
		 */
		if (bus.isGen()) {
			Complex busPQ = bus.getNetGenResults();
			double p = busPQ.getReal();
			double q = busPQ.getImaginary();
			if (!NumericUtil.equals(rec.gen.re, p, PQErr)) {
				String msg = "Bus GenP mismatch:        Bus-" + rec.id + ", " + 
							String.format("%5.1f, %5.1f, %4.3f", rec.gen.re, p,  
							 Math.abs(100.0*(rec.gen.re - p)/rec.gen.re)) + "%";
				//IpssLogger.getLogger().warning(msg);
				addErrMsg(msg);
			}
			
			if (!NumericUtil.equals(rec.gen.im, q, PQErr)) {
				String msg = "Bus GenQ mismatch:        Bus-" + rec.id + ", " + 
							String.format("%5.1f, %5.1f, %4.3f", rec.gen.im, q,  
							 Math.abs(100.0*(rec.gen.im - q)/rec.gen.im)) + "%";
				//IpssLogger.getLogger().warning(msg);
				addErrMsg(msg);
			}	
		}
		
		/*
		 * Compare load P, Q
		 */
		if (bus.isLoad()) {
			Complex busPQ = bus.getNetLoadResults();
			double p = busPQ.getReal();
			double q = busPQ.getImaginary();
			if (!NumericUtil.equals(rec.load.re, p, PQErr)) {
				String msg = "Bus LoadP mismatch:       Bus-" + rec.id + ", " + 
							String.format("%5.1f, %5.1f, %4.3f", rec.load.re, p,  
							 Math.abs(100.0*(rec.load.re - p)/rec.load.re)) + "%";
				//IpssLogger.getLogger().warning(msg);
				addErrMsg(msg);
			}
			if (!NumericUtil.equals(rec.load.im, q, PQErr)) {
				String msg = "Bus LoadQ mismatch:       Bus-" + rec.id + ", " + 
							String.format("%5.1f, %5.1f, %4.3f", rec.load.im, q,  
							 Math.abs(100.0*(rec.load.im - q)/rec.load.im)) + "%";
				//IpssLogger.getLogger().warning(msg);
				addErrMsg(msg);
			}
		}
		
		/*
		 * Compare shuntQ
		 */
		if (bus.getShuntY() != null) {
			if (rec.shunt == null) 
				rec.shunt = new ComplexBean();
			double q = -bus.getShuntY().getImaginary();
			q *= bus.getVoltageMag() * bus.getVoltageMag();				
			if (!NumericUtil.equals(rec.shunt.im, q, PQErr)) {
				String msg = "Bus ShuntQ mismatch:     Bus-" + rec.id + ", " + 
							String.format("%5.1f, %5.1f, %4.3f", rec.shunt.im, q,  
							 Math.abs(100.0*(rec.shunt.im - q)/rec.shunt.im)) + "%";
				//IpssLogger.getLogger().warning(msg);
				addErrMsg(msg);
			}		
		}
	}

	private void compareBranchAclfResult(AclfBranch branch, AclfBranchResultBean rec) {
		// TODO
	}
}
