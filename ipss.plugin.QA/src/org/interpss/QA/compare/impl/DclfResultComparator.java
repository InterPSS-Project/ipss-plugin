package org.interpss.QA.compare.impl;

import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.aclf.ext.AclfBranchResultBean;
import org.interpss.datamodel.bean.base.DefaultExtBean;
import org.interpss.datamodel.bean.dclf.DclfBranchResultBean;
import org.interpss.datamodel.bean.dclf.DclfBusResultBean;
import org.interpss.datamodel.bean.dclf.DclfNetResultBean;
import org.interpss.datamodel.mapper.dclf.DclfResultBeanMapper;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.util.Number2String;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;
import com.interpss.core.common.ReferenceBusException;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

/**
 * Utility class for compare Dclf results. There are two main steps:
 *  
 *  1) Load some network parameter and solved Dclf results into the qaResultSet object
 *  2) Compare network parameter stored in the this.net object
 *  3) Compare dclf result by running dclf or stored in the dclfAlgo object
 * 
 * @author mzhou
 *
 */
public class DclfResultComparator extends NetModelComparator<DclfBusResultBean<DefaultExtBean>, DclfBranchResultBean<DefaultExtBean>, DefaultExtBean, DefaultExtBean> {
	private SenAnalysisAlgorithm algo = null;
	
	public DclfResultComparator() {
		this.qaResultSet = new  DclfNetResultBean<DefaultExtBean, DefaultExtBean>();
		this.qaResultSet.base_kva = 100000.0;
	}
	
	public DclfResultComparator(AclfNetwork net, DclfNetResultBean<DefaultExtBean, DefaultExtBean> qaResultSet) {
		super(net, qaResultSet);
	}
	
	/**
	 * set Dclf Algo with solved Dclf results
	 * 
	 * @param algo
	 */
	public void setDclfAlgo( SenAnalysisAlgorithm algo) {
		this.algo = algo;
		this.net = algo.getNetwork();
	}
	
	/**
	 * set Dclf result to the resultSet object
	 * 
	 * @param algo
	 */
	public DclfResultComparator setBaseResult(SenAnalysisAlgorithm algo) {
		new DclfResultBeanMapper().map2Model(algo, (DclfNetResultBean<DefaultExtBean,DefaultExtBean>)this.qaResultSet);
		
		return this;
	}	
	
	/**
	 * Compare PsXfr shifting angle
	 * 
	 * @return
	 */
	public boolean comparePsXfrShiftAngle() {
		this.errMsgList.clear();
		int cnt = 0;
		for (Branch bra : net.getBranchList()) {
			if (bra.isActive()) {
				AclfBranch branch = (AclfBranch)bra;
				if (branch.isPSXfr()) {
					cnt++;
					AclfBranchResultBean<DefaultExtBean> rec = this.qaResultSet.getBranch(bra.getId());
					double ang = branch.toPSXfr().getFromAngle();
					if (!NumericUtil.equals(rec.ang.f, ang, VAngErr)) {
						String msg = "PsXfr shift ang mismatch: Branch -" + bra.getId() + ", " + 
								 String.format("%5.4f(r), %5.4f(m), %4.3f", rec.ang.f, ang,  
								     Math.toDegrees(Math.abs(rec.ang.f-ang))) + " deg";
						//IpssLogger.getLogger().warning(msg);
						addErrMsg(msg);
					}
				}
			}
		}
		System.out.println("Total PsXfr: " + cnt);
		return true;
	}
	
	/**
	 * Compare network parameters and data before Dclf, including gen/load 
	 * at each bus and total gen/load in the network.
	 * 
	 * @return
	 */
	public boolean compareNetParamData() {
		this.errMsgList.clear();

		// before running, bus genP and loadP should be the same 
		
		double totalGenModel = 0.0, totalLoadModel = 0.0,
				totalGenResult = 0.0, totalLoadResult = 0.0;
		for (Bus bus : this.net.getBusList()) {
			if (bus.isActive()) {
				AclfBus aclfBus = (AclfBus)bus; 
				AclfBusBean<DefaultExtBean> rec = this.qaResultSet.getBus(bus.getId());

				// compare base voltage against the NomVolt in the result file
				double v = aclfBus.getBaseVoltage();
				double rv = rec.v_mag;
				if ((rec.gen.re != 0.0 || v != 0.0) && !NumericUtil.equals(rv, v, VMagErr)) {
					double base = rv!=0?rv:v;
					String msg = "VMag mismatch: Bus-" + rec.id + ", " + 
							 String.format("%5.4f(r), %5.4f(m), %4.3f", rv, v,  
							     Math.abs(100.0*(rv - v)/base)) + "%";
					//IpssLogger.getLogger().warning(msg);
					addErrMsg(msg);
				}

				// compare genp
				if (!this.net.isRefBus(aclfBus)) {
					double genp = aclfBus.getGenP();
					if ((rec.gen.re != 0.0 || genp != 0.0) && !NumericUtil.equals(rec.gen.re, genp, PQErr)) {
						double base = rec.gen.re!=0?rec.gen.re : genp;
						String msg = "GenP mismatch: Bus-" + rec.id + ", " + 
								 String.format("%5.4f(r), %5.4f(m), %4.3f", rec.gen.re, genp,  
								     Math.abs(100.0*(rec.gen.re - genp)/base)) + "%";
						//IpssLogger.getLogger().warning(msg);
						addErrMsg(msg);
					}
					totalGenModel += genp;
					totalGenResult += rec.gen.re;
				}

				// compare loadp
				double loadp = aclfBus.getLoadP();
				if ((rec.load.re != 0.0 || loadp != 0.0) && !NumericUtil.equals(rec.load.re, loadp, PQErr)) {
					double base = rec.load.re!=0?rec.load.re : loadp;
					String msg = "LoadP mismatch: Bus-" + rec.id + ", " + 
							 String.format("%5.4f(r), %5.4f(m), %4.3f", rec.load.re, loadp,  
							     Math.abs(100.0*(rec.load.re - loadp)/base)) + "%";
					//IpssLogger.getLogger().warning(msg);
					addErrMsg(msg);
				}
				totalLoadModel += loadp;
				totalLoadResult += rec.load.re;
			}
		}
		
		//System.out.println("Total model gen, load " + totalGenModel + "  " + totalLoadModel);
		//System.out.println("Total result gen, load " + totalGenResult + "  " + totalLoadResult);
		
		// compare total gen
		if (!NumericUtil.equals(totalGenResult, totalGenModel, PQErr)) {
			String msg = "Total system Gen mismatch: " + 
					 String.format("%5.4f(r), %5.4f(m), %4.3f", totalGenResult, totalGenModel,  
					     Math.abs(100.0*(totalGenResult - totalGenModel)/totalGenResult)) + "%";
			//IpssLogger.getLogger().warning(msg);
			addErrMsg(msg);
		}

		// compare total load
		if (!NumericUtil.equals(totalLoadResult, totalLoadModel, PQErr)) {
			String msg = "Total system Load mismatch: " + 
					 String.format("%5.4f(r), %5.4f(m), %4.3f", totalLoadResult, totalLoadModel,  
					     Math.abs(100.0*(totalLoadResult - totalLoadModel)/totalLoadResult)) + "%";
			//IpssLogger.getLogger().warning(msg);
			addErrMsg(msg);
		}

		return true;
	}

	/**
	 * It first run Dclf on the AclfNetwork object and then compare the result
	 * store in the resultSet object, including bus angle, branch angle and branch
	 * flow
	 * 
	 * @param refBusAng reference bus angle offset
	 * @param angErrDeg angle error for comparison
	 * @param pErr power error for comparison
	 * @param busResult bus result for output to a file
	 * @param branchResult branch result for output to a file
	 * @return
	 */
	public boolean compareDclfResult(double refBusAng,	double angErrDeg, double pErr) {
		return compareDclfResult(refBusAng, angErrDeg, pErr, false);	}
	public boolean compareDclfResult(double refBusAng,	double angErrDeg, double pErr, boolean applyAdjust) {
		StringBuffer busResult = new StringBuffer();
		busResult.append("BusId,BusAng(deg),Gen(mw),Load(mv)\n");
		StringBuffer branchResult = new StringBuffer();
		branchResult.append("FromBusId,ToBusId,CirId,PFlow(mv)\n");
		return compareDclfResult(refBusAng, angErrDeg, pErr, busResult, branchResult, applyAdjust);	}
	public boolean compareDclfResult(double refBusAng, 
			double angErrDeg, double pErr, 
			StringBuffer busResult, StringBuffer branchResult,
			boolean applyAdjust) {
		this.errMsgList.clear();

		if (this.algo == null) {
			this.algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net, CacheType.SenNotCached, applyAdjust);
			//DclfAlgorithm algo = DclfObjectFactory.createDclfAlgorithm(net, false);
			try {
				algo.calculateDclf();
			} catch (InterpssException e) {
				e.printStackTrace();
			}	
		}
		
		// compare bus angle
		
		double baseMva = net.getBaseKva() * 0.001;
		double max = 0.0;
		for (Bus b : algo.getNetwork().getBusList()) {
			if (b.isActive()) {
				AclfBus bus = (AclfBus)b;
				int n = bus.getSortNumber();
				double angDeg = Math.toDegrees(algo.getBusAngle(n)) + refBusAng;
				AclfBusBean<DefaultExtBean> rec = this.qaResultSet.getBus(bus.getId());
				double rangDeg = rec.v_ang;
				if (Math.abs(rangDeg-angDeg) > max )
					max = Math.abs(rangDeg-angDeg);
				if ((rangDeg != 0.0 || angDeg != 0.0) && !NumericUtil.equals(rangDeg, angDeg, angErrDeg)) {
					String msg = "VAng mismatch: Bus-" + rec.id + ", " + 
							 String.format("%5.4f(r), %5.4f(m), %4.3f", rangDeg, angDeg,  
							     Math.abs(rangDeg-angDeg)) + " deg";
					//IpssLogger.getLogger().warning(msg);
					addErrMsg(msg);
				}
				busResult.append(String.format("%s, %5.4f, %5.4f, %4.2f\n", bus.getId(), 
						angDeg, bus.getGenP()*baseMva, bus.getLoadP()*baseMva));
			}
		}
		addErrMsg("Max bus angle diff: " + max + " deg");

		// compare branch angle difference
		
		max = 0.0;
		for (Branch bra : algo.getNetwork().getBranchList()) {
			if (bra.isActive()) {
				int fn = bra.getFromBus().getSortNumber();
				int tn = bra.getToBus().getSortNumber();
					
				// compare angle
				double angDeg = Math.toDegrees(algo.getBusAngle(fn) - algo.getBusAngle(tn));
				AclfBusBean<DefaultExtBean> frec = this.qaResultSet.getBus(bra.getFromBusId());
				AclfBusBean<DefaultExtBean> trec = this.qaResultSet.getBus(bra.getToBusId());
				double rangDeg = frec.v_ang - trec.v_ang;
				if (Math.abs(rangDeg-angDeg) > max )
					max = Math.abs(rangDeg-angDeg);
				if ((rangDeg != 0.0 || angDeg != 0.0) && !NumericUtil.equals(rangDeg, angDeg, angErrDeg)) {
					String msg = "VAng mismatch: Branch -" + bra.getId() + ", " + 
							 String.format("%5.4f(r), %5.4f(m), %4.3f", rangDeg, angDeg,  
							     Math.abs(rangDeg-angDeg)) + " deg";
					//IpssLogger.getLogger().warning(msg);
					addErrMsg(msg);
				}
			}
		}
		addErrMsg("Max branch angle diff: " + max + " deg");

		// algo.getBranchFlow(algo.getAclfNetwork().getAclfBranch("Bus3771->Bus3807(1)"));

		// compare branch flow 
		
		max = 0.0;
		for (Branch bra : algo.getNetwork().getBranchList()) {
			if (bra.isActive()) {
				AclfBranch aclfBra = (AclfBranch)bra;				
				double pflow = algo.getBranchFlow(aclfBra);
				
				
				// compare pflow
				AclfBranchResultBean<DefaultExtBean> rec = this.qaResultSet.getBranch(bra.getId());

				if (Math.abs(rec.flow_f2t.re-pflow) > max )
					max = Math.abs(rec.flow_f2t.re-pflow);
				
				if ((pflow != 0.0 || rec.flow_f2t.re != 0.0) && !NumericUtil.equals(rec.flow_f2t.re, pflow, pErr)) {
					String msg = "Pflwo mismatch: Branch -" + bra.getId() + ", " + 
							 String.format("%5.4f(r), %5.4f(m), %4.3f", rec.flow_f2t.re, pflow,  
							     Math.abs(rec.flow_f2t.re-pflow)) + " pu";
					//IpssLogger.getLogger().warning(msg);
					addErrMsg(msg);
				}
				branchResult.append(String.format("%s, %s, %s, %5.4f\n", bra.getFromBusId(), 
						bra.getToBusId(), bra.getCircuitNumber(), pflow * baseMva));
			}
		}
		addErrMsg("Max branch power diff: " + max + " pu");
		
		//algo.destroy();
		
		return this.errMsgList.size() == 3;  // there are three status msg
	}

	public String outDclfResult(double angOffset) throws InterpssException, ReferenceBusException, IpssNumericException {
		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		algo.calculateDclf();
		
		StringBuffer buf = new StringBuffer();
		buf.append("                -------    Dclf  --------     -------   Result --------\n");
		buf.append("   Bud Id       VoltAng(deg)     Gen/Load     VoltAng(deg)     Gen/Load\n");
		buf.append("========================================================================\n");
		
		for (Bus bus : algo.getNetwork().getBusList()) {
			if (bus.isActive()) {
				AclfBus aclfBus = (AclfBus)bus; 
				int n = bus.getSortNumber();
				double angDeg = Math.toDegrees(algo.getBusAngle(n)) - angOffset;
				double pPu = (aclfBus.getGenP() - aclfBus.getLoadP()); 
				
				AclfBusBean<DefaultExtBean> busRec = this.qaResultSet.getBus(bus.getId());
				
				buf.append(Number2String.toFixLengthStr(8, bus.getId()) + "        "
						+ String.format("%8.2f", angDeg) + "         "
						+ ((pPu != 0.0)? String.format("%8.2f",pPu) : "        ") 
						+ String.format("     %8.2f", Math.toDegrees(busRec.v_ang)) + "         "
						+ ((pPu != 0.0)? String.format("%8.2f", (busRec.gen.re - busRec.load.re)) : "") + "\n"); 
			}
		}

		//algo.destroy();
		
		return buf.toString();
	}
}
