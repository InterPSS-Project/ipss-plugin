/**
 * Copyright www.interpss.org 2005-2007
 *
 * $Id: DclfAlgorithmImpl.java,v 1.5 2007/08/09 23:41:33 mzhou Exp $
 */
package org.interpss.QA.core;

import org.interpss.QA.compare.dep.DepPWDResultFileProcessor;
import org.interpss.QA.result.QAAclfBranchRec;
import org.interpss.QA.result.QAAclfBusRec;
import org.interpss.QA.result.QAResultContainer;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.dclf.SenAnalysisAlgorithm;
import com.interpss.core.dclf.IDclfSolver;
import com.interpss.core.dclf.LODFSenAnalysisType;
import com.interpss.core.dclf.impl.SenAnalysisAlgorithmImpl;
import com.interpss.core.dclf.solver.DclfSolver;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

public class DclfAlgoDebug extends SenAnalysisAlgorithmImpl {
	public DclfAlgoDebug() {
		super();
	}

	public IDclfSolver getDclfSolver() {
		return this.dclfSolver;
	}

	public static DclfAlgoDebug createDclfAlgoDebug(AclfNetwork aclfNet) {
		DclfAlgoDebug algo = new DclfAlgoDebug();
		algo.setLodfAnalysisType(LODFSenAnalysisType.SINGLE_BRANCH);
		algo.setNetwork(aclfNet);
		return algo;
	}

	public void initDclfResult(double[] dclfResult) {
		((DclfSolver)this.dclfSolver).initDclfResultForDebug(dclfResult);
	}

	public void setPSAngleForDebug(DepPWDResultFileProcessor proc, AclfNetwork net) {

		QAResultContainer<QAAclfBusRec, QAAclfBranchRec> qaResultSet = proc
				.getQAResultSet();
		for (Branch b : net.getBranchList()) {
			if (b.isActive() && ((AclfBranch) b).isPSXfrPControl()) {
				AclfBranch bra = (AclfBranch) b;
				QAAclfBranchRec rec = qaResultSet.getBranchResult(bra.getId());
				double angRad = rec.fromShiftAng;
				bra.setFromPSXfrAngle(angRad);
			}
		}

	}
	//preProcessingNet is the cached network before network topo processing
	public void setPSAngleForDebug(AclfNetwork preProcessingNet, AclfNetwork net) {
		
		for (Branch b : net.getBranchList()) {
			if (b.isActive() && ((AclfBranch) b).isPSXfrPControl()) {
				AclfBranch bra = (AclfBranch) b;				
				double angRad = ((AclfBranch)preProcessingNet.getBranch(bra.getId())).getFromPSXfrAngle();
				bra.setFromPSXfrAngle(angRad);
			}
		}

	}

	public void setBusAngleForDebug(DepPWDResultFileProcessor proc, AclfNetwork net) {

		QAResultContainer<QAAclfBusRec, QAAclfBranchRec> qaResultSet = proc
				.getQAResultSet();
		// this.dclfSolver.
		for (Bus b : net.getBusList()) {
			if (b.isActive()) {
				AclfBus bus = (AclfBus) b;
				QAAclfBusRec rec = qaResultSet.getBusResult(bus.getId());
				// double rangDeg = Math.toDegrees(rec.vang);
				double rangRad = rec.vang;
				this.getDclfSolver().setBusAngle(bus.getId(), rangRad);

				if (b.isParent()) {
					for (Bus bus1 : b.getBusSecList()) {
						bus = (AclfBus) bus1;
						rec = qaResultSet.getBusResult(bus.getId());
						// double rangDeg = Math.toDegrees(rec.vang);
						rangRad = rec.vang;
						this.getDclfSolver().setBusAngle(bus.getId(), rangRad);
					}
				}

			}

		}

	}
	
	public void setBusAngleForDebug(SenAnalysisAlgorithm algoPre, AclfNetwork net) {			
		for (Bus b : net.getBusList()) {
			if (b.isActive()) {				
				AclfBus bus = (AclfBus) b;				
				double rangRad = algoPre.getBusAngle(b.getId());
				this.getDclfSolver().setBusAngle(bus.getId(), rangRad);

				if (b.isParent()) {
					for (Bus bus1 : b.getBusSecList()) {
						bus = (AclfBus) bus1;						
						rangRad =algoPre.getBusAngle(bus.getId());
						this.getDclfSolver().setBusAngle(bus.getId(), rangRad);
					}
				}
			}
		}

	}

	public void getMaxMismatch(AclfNetwork net) {
		double max = 0;
		String id = "";
		for (Bus b : net.getBusList()) {
			if (b.getId().equals("Bus12383")) {
				System.out.println("12383");
			}
			if (b.isActive()) {
				double mis = this.getMismatch((AclfBus) b);
				if (Math.abs(mis) > max) {
					max = Math.abs(mis);
					id = b.getId();
				}
			}
		}
		System.out.println("Max bus mismatch: " + max + "@" + id);
	}

}
