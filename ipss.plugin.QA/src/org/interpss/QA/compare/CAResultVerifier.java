package org.interpss.QA.compare;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.eclipse.emf.ecore.change.util.ChangeRecorder;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.util.Number2String;
import org.interpss.pssl.common.PSSLException;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;
import org.interpss.pssl.util.ContingencyAnalysisHelper;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.BranchOutageType;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.aclf.contingency.MonitoringBranch;
import com.interpss.core.aclf.contingency.OutageBranch;
import com.interpss.core.dclf.common.ReferenceBusException;
import com.interpss.core.funcImpl.AclfNetHelper;

/**
 * CA result verification
 * 
 * @author mzhou
 *
 */
public class CAResultVerifier {
	// aclfNet object
	private AclfNetwork aclfNet;
	
	// contingency object
	private Contingency cont;
	
	// violation threshold 
	private double violationThreshold = 1.0;
	
	// tolerance
	private double err = 1.0;   // in percent
	
	// true if apply Dclf adjustment
	private boolean applyAdjustment = false;
	
	private List<String> msgList = new ArrayList<>();
	public List<String> getMsgLit() { return this.msgList; }
	
	// record if CA has island
	private boolean hasIsland = false;
	public boolean hasIsland() { return this.hasIsland; }
	
	private boolean excludeIslandCA = false;
	
	/**
	 * constructor
	 * 
	 * @param aclfNet
	 * @param tol
	 */
	public CAResultVerifier(AclfNetwork aclfNet, double tol){
		this.aclfNet = aclfNet;
		this.err = tol;
	}
	
	/**
	 * constructor
	 * 
	 * @param aclfNet
	 * @param tol
	 */
	public CAResultVerifier(AclfNetwork aclfNet, double tol, boolean excludeIslandCA){
		this.aclfNet = aclfNet;
		this.err = tol;
		this.excludeIslandCA = excludeIslandCA;
	}
	
	/**
	 * constructor
	 * 
	 * @param aclfNet
	 * @param cont
	 */
	public CAResultVerifier(AclfNetwork aclfNet, Contingency cont){
		this.aclfNet = aclfNet;
		this.cont = cont;
	}
	
	/**
	 * constructor
	 * 
	 * @param aclfNet
	 * @param cont
	 * @param violationThreshold
	 * @param tol
	 */
	public CAResultVerifier(AclfNetwork aclfNet, Contingency cont,
			double violationThreshold, double tol){
		this.aclfNet = aclfNet;
		this.cont = cont;
		this.violationThreshold = violationThreshold;
		this.err = tol;
	}
	
	/**
	 * set continency
	 * 
	 * @param cont
	 * @return
	 */
	public CAResultVerifier setContingency(Contingency cont) {
		this.cont = cont;
		return this;
	}
	
	/**
	 * set violation threshold
	 * 
	 * @param violationThreshold
	 */
	public void setViolationThreshold(double violationThreshold) {
		this.violationThreshold = violationThreshold;
	}
	
	/**
	 * set error tolerance in %
	 * 
	 * @param tol
	 */
	public void setError (double tol){
		this.err = tol;
	}
	
	/**
	 * verify CA result
	 * 
	 * @param threshold
	 * @return
	 * @throws InterpssException
	 * @throws ReferenceBusException
	 * @throws PSSLException
	 * @throws IpssNumericException 
	 */
	public boolean verify(double threshold) throws InterpssException, ReferenceBusException, PSSLException, IpssNumericException{	
		this.setViolationThreshold(threshold);
		return this.verify();
	}
	
	/**
	 * verify CA result
	 * 
	 * @return
	 * @throws InterpssException
	 * @throws ReferenceBusException
	 * @throws PSSLException
	 * @throws IpssNumericException 
	 */
	public boolean verify() throws InterpssException, ReferenceBusException, PSSLException, IpssNumericException{		
		this.msgList.clear();
		this.hasIsland = false;
		
		//ChangeRecorder recorder = aclfNet.bookmark(false);		
		
		/*
		 * calculate actual contingency DCLF by applying the outages
		 */
		for(OutageBranch br: cont.getOutageBranches()){
			br.getBranch().setStatus(br.getOutageType() == BranchOutageType.CLOSE);				
		}
		
		List<String> list = new ArrayList<String>();
		new AclfNetHelper(aclfNet).findPreContNetworkIslanding(list);
		if (list.size() > 0) {
			this.msgList.add("\nContingency " + cont.getId()
					+ " resulting island bus " + list.toString() + "\n");
			this.hasIsland = true;
		}

		if (this.excludeIslandCA && this.hasIsland)
			return true;
		
		Hashtable<String, Double> lookupTable = new Hashtable<String, Double>();
		
		DclfAlgorithmDSL algoPost = IpssDclf.createDclfAlgorithm(aclfNet, applyAdjustment);
		
		algoPost.runDclfAnalysis();
		
		// cache the actual branch flow by the DCLF 
		for (AclfBranch branch : aclfNet.getBranchList()) {
			double flow = 0.0;
			if (branch.isActive())
				flow = algoPost.algo().getBranchFlow(branch);
			lookupTable.put(branch.getId(), new Double(flow));
		}
		
		//algoPost.destroy();
		
		//aclfNet.rollback(recorder);
		
		/*
		 * 	calculate contingency DCLF by using sensitivity
		 */
		DclfAlgorithmDSL algoCtg = IpssDclf.createDclfAlgorithm(aclfNet, applyAdjustment)
				.runDclfAnalysis();

		ContingencyAnalysisHelper contHelper = new ContingencyAnalysisHelper(algoCtg, true);
		
		contHelper.setViolationThreshold(violationThreshold);
		
		contHelper.contAnalysis(cont);
		
		//contHelper.outResult(cont);
		
		if(cont.getMonitoringBranches().size()>0){
			this.msgList.add("\n\nContingency : " + cont.getId()
					+ ", no of outage branches: "
					+ cont.getOutageBranches().size() + "\n");
			this.msgList.add("Islanding gen : "
					+ Number2String.toStr(cont.getTotalIslandGen()
							* aclfNet.getBaseMva())
					+ "  load: "
					+ Number2String.toStr(cont.getTotalIslandLoad()
							* aclfNet.getBaseMva()) + "\n");
		}
		
		boolean identical = true;

		// compare the branch with largest shifted flow
		AclfBranch maxShiftedFlowBranch = aclfNet.getBranch(cont.getMaxShiftFlowBranchId());
		if (!compare(maxShiftedFlowBranch, algoCtg.algo().getBranchFlow(maxShiftedFlowBranch), cont.getMaxShiftedFlow(), lookupTable))
			identical = false;

		// compare violated branches 
		for (MonitoringBranch mon : cont.getMonitoringBranches()) {
			if (!compare(mon.getBranch(), algoCtg.algo().getBranchFlow(mon.getBranch()), mon.getShiftedFlow(), lookupTable))
				identical = false;
		}			

		System.out.println("Contingency: "+cont.getId()+", CA results " + (identical? "" : "do not") + " match DCLF results." 
				+ (this.hasIsland? "  Island " : "  ") + "  # of violated branches " + cont.getMonitoringBranches().size());
		
		//algoCtg.destroy();
		
		return identical;
	}

	private boolean compare(AclfBranch branch, double preFlow, double shiftedFlow, Hashtable<String, Double> lookupTable) {
		// post flow by using sensitivity
		double post_flow = preFlow + shiftedFlow;
		
		boolean identical = true;
		// actual post flow by DCLF from the lookup table
		if (lookupTable.get(branch.getId()) != null) {
			double actFlow = lookupTable.get(branch.getId());
			double dif = Math.abs(actFlow - post_flow);
			if (Math.abs(preFlow) > 0.01)
				dif = dif / Math.abs(preFlow); 
			if (dif * 100 > err	){
				identical = false;
				
				this.msgList.add("Monitoring branch: " + branch.getId() + 
						"   Shifted power flow (Mw): " + Number2String.toStr(shiftedFlow*aclfNet.getBaseMva()) + "\n");

				this.msgList.add("branch flow (mw) [CA, DCLF]: " 
				        + branch.getBranchCode() + "  "
				        //+ "   " + mon.getAclfBranch().getZ().getReal() + "   "
				        + Number2String.toStr(post_flow*aclfNet.getBaseMva())+
						", "+ Number2String.toStr(actFlow*aclfNet.getBaseMva()) + "  dif: " + dif*100.0 + "%\n");	
			}					
		}
		return identical;
	}
}

