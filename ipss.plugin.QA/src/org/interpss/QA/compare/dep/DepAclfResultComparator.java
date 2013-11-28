package org.interpss.QA.compare.dep;

import org.apache.commons.math3.complex.Complex;
import org.interpss.QA.result.QAAclfBranchRec;
import org.interpss.QA.result.QAAclfBusRec;
import org.interpss.QA.result.QAResultContainer;
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
@Deprecated
public class DepAclfResultComparator	extends DepDclfResultComparator {
	public DepAclfResultComparator() { super(); }
	
	public DepAclfResultComparator(AclfNetwork net, QAResultContainer<QAAclfBusRec, QAAclfBranchRec> qaResultSet) {
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
		
		double baseMva = this.qaResultSet.getBaseMva();
		if (!NumericUtil.equals(net.getBaseKva()*0.001, baseMva, 0.1)) {
			addErrMsg("AclfNet baseKva not the same, " + baseMva + ", " + net.getBaseKva()*0.001);
			return;			
		}
		
		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus)b;
			if (bus.isActive()) {
				QAAclfBusRec rec = this.qaResultSet.getBusResult(b.getId());
				compareBusAclfResult(bus, rec);
			}
		}
		
		for (Branch b : net.getBranchList()) {
			AclfBranch branch = (AclfBranch)b;
			if (branch.isActive()) {
				QAAclfBranchRec rec = this.qaResultSet.getBranchResult(branch.getId());
				compareBranchAclfResult(branch, rec);
			}
		}		
	}
	
	private void compareBusAclfResult(AclfBus bus, QAAclfBusRec rec) {
		/*
		 * Compare voltage
		 */
		double volt = bus.getVoltageMag();
		if (!NumericUtil.equals(rec.vmag, volt, VMagErr)) {
			String msg = "Bus voltage mag mismatch: Bus-" + rec.id + ", " + 
					 String.format("%5.4f, %5.4f, %4.3f", rec.vmag, volt,  
					     Math.abs(100.0*(rec.vmag - volt)/rec.vmag)) + "%";
			//IpssLogger.getLogger().warning(msg);
			addErrMsg(msg);
		}

		double ang = bus.getVoltageAng(Unit.UnitType.Deg);
		double rec_ang_deg = Math.toDegrees(rec.vang);
		if (!NumericUtil.equals(rec_ang_deg, ang, VAngErr)) {
			String msg = "Bus voltage ang mismatch: Bus-" + rec.id + ", " + 
						 String.format("%5.2f, %5.2f, %4.3f", rec_ang_deg, ang,
							Math.abs(100.0*(rec_ang_deg - ang)/rec_ang_deg)) + "%";
			//IpssLogger.getLogger().warning(msg);
			addErrMsg(msg);
		}	
		
		/*
		 * Compare gen P, Q
		 */
		Complex busPQ = bus.getGenResults();
		double p = busPQ.getReal();
		double q = busPQ.getImaginary();
		if (!NumericUtil.equals(rec.genp, p, PQErr)) {
			String msg = "Bus GenP mismatch:        Bus-" + rec.id + ", " + 
						String.format("%5.1f, %5.1f, %4.3f", rec.genp, p,  
						 Math.abs(100.0*(rec.genp - p)/rec.genp)) + "%";
			//IpssLogger.getLogger().warning(msg);
			addErrMsg(msg);
		}
		
		if (!NumericUtil.equals(rec.genq, q, PQErr)) {
			String msg = "Bus GenQ mismatch:        Bus-" + rec.id + ", " + 
						String.format("%5.1f, %5.1f, %4.3f", rec.genq, q,  
						 Math.abs(100.0*(rec.genq - q)/rec.genq)) + "%";
			//IpssLogger.getLogger().warning(msg);
			addErrMsg(msg);
		}	
		
		/*
		 * Compare load P, Q
		 */
		busPQ = bus.getLoadResults();
		p = busPQ.getReal();
		q = busPQ.getImaginary();
		if (!NumericUtil.equals(rec.loadp, p, PQErr)) {
			String msg = "Bus LoadP mismatch:       Bus-" + rec.id + ", " + 
						String.format("%5.1f, %5.1f, %4.3f", rec.loadp, p,  
						 Math.abs(100.0*(rec.loadp - p)/rec.loadp)) + "%";
			//IpssLogger.getLogger().warning(msg);
			addErrMsg(msg);
		}
		if (!NumericUtil.equals(rec.loadq, q, PQErr)) {
			String msg = "Bus LoadQ mismatch:       Bus-" + rec.id + ", " + 
						String.format("%5.1f, %5.1f, %4.3f", rec.loadq, q,  
						 Math.abs(100.0*(rec.loadq - q)/rec.loadq)) + "%";
			//IpssLogger.getLogger().warning(msg);
			addErrMsg(msg);
		}
		
		/*
		 * Compare shuntQ
		 */
		q = -bus.getShuntY().getImaginary();
		q *= bus.getVoltageMag() * bus.getVoltageMag();				
		if (!NumericUtil.equals(rec.shuntq, q, PQErr)) {
			String msg = "Bus ShuntQ mismatch:     Bus-" + rec.id + ", " + 
						String.format("%5.1f, %5.1f, %4.3f", rec.shuntq, q,  
						 Math.abs(100.0*(rec.shuntq - q)/rec.shuntq)) + "%";
			//IpssLogger.getLogger().warning(msg);
			addErrMsg(msg);
		}		
	}

	private void compareBranchAclfResult(AclfBranch branch, QAAclfBranchRec rec) {
		// TODO
	}
}
