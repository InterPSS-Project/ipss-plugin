package org.interpss.pssl.util.tool;

import java.util.ArrayList;
import java.util.List;

import org.interpss.pssl.common.PSSLException;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;
import org.interpss.pssl.util.ContingencyAnalysisHelper;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.dclf.common.ReferenceBusException;

public class ContingencyClassfier {
	private AclfNetwork aclfNet;
	private List<Contingency> contList;
	private double violationThreshold = 1.0;
	
	private List<String> singleLineOutNoIslandingContList = new ArrayList<String>();
	private List<String> singleLineOutWithIslandingNoGenNoLoadContList= new ArrayList<String>();
	private List<String> singleLineOutWithGenIslandingContList= new ArrayList<String>();
	private List<String> singleLineOutWithLoadIslandingContList= new ArrayList<String>();
	private List<String> multiLineOutNoIslandingContList= new ArrayList<String>();
	private List<String> multiLineOutWithIslandingNoGenNoLoadContList= new ArrayList<String>();
	private List<String> multiLineOutWithGenIslandingContList= new ArrayList<String>();
	private List<String> multiLineOutWithLoadIslandingContList= new ArrayList<String>();
		
	
	public ContingencyClassfier(AclfNetwork aclfNet, List<Contingency> contList){
		this.aclfNet = aclfNet;
		this.contList = contList;
	}
	
	public ContingencyClassfier(AclfNetwork aclfNet, List<Contingency> contList,
			double violationThreshold){
		this.aclfNet = aclfNet;
		this.contList = contList;
		this.violationThreshold = violationThreshold;		
	}
	
	public void setViolationThreshold(double violationThreshold) {
		this.violationThreshold = violationThreshold;
	}
	
	
		
	public void classify() throws InterpssException, ReferenceBusException, PSSLException {		
		
		for (Contingency cont : contList) {
			DclfAlgorithmDSL algoCtg = IpssDclf.createDclfAlgorithm(aclfNet, false)
					.runDclfAnalysis();

			ContingencyAnalysisHelper contHelper = new ContingencyAnalysisHelper(
					algoCtg, true);
			
			contHelper.setViolationThreshold(violationThreshold);
			
			contHelper.contAnalysis(cont);
			
			int numOfoutage = cont.getOutageBranches().size();
			int numOfIslandingBus = cont.getIslandBuses().size();
			double islandingGen = cont.getTotalIslandGen()
					* aclfNet.getBaseMva();
			double islandingLoad = cont.getTotalIslandLoad()
					* aclfNet.getBaseMva();
			
			if(numOfoutage ==1){ // categories 1-4
				if(numOfIslandingBus ==0){ // 1
					this.singleLineOutNoIslandingContList.add(cont.getId());
				}else {
					if(islandingGen == 0 && islandingLoad == 0){
						this.singleLineOutWithIslandingNoGenNoLoadContList.add(cont.getId());
					}else if (islandingGen != 0){
						this.singleLineOutWithGenIslandingContList.add(cont.getId());
					}else if (islandingLoad != 0){
						this.singleLineOutWithLoadIslandingContList.add(cont.getId());
					}						
				}
			}else{ // categories 5 -8
				if(numOfIslandingBus ==0){ // 1
					this.multiLineOutNoIslandingContList.add(cont.getId());
				}else {
					if(islandingGen == 0 && islandingLoad == 0){
						this.multiLineOutWithIslandingNoGenNoLoadContList.add(cont.getId());
					}else if (islandingGen != 0){
						this.multiLineOutWithGenIslandingContList.add(cont.getId());
					}else if (islandingLoad != 0){
						this.multiLineOutWithLoadIslandingContList.add(cont.getId());
					}						
				}
			}			
			algoCtg.destroy();			
		}				
	}
	
	public List<String> getSingleLineOutNoIslandingContList(){
		return this.singleLineOutNoIslandingContList;
	}
	public List<String> getSingleLineOutWithIslandingNoGenNoLoadContList(){
		return this.singleLineOutWithIslandingNoGenNoLoadContList;
	}
	public List<String> getSingleLineOutWithGenIslandingContList(){
		return this.singleLineOutWithGenIslandingContList;
	}
	public List<String> getSingleLineOutWithLoadIslandingContList(){
		return this.singleLineOutWithLoadIslandingContList;
	}
	public List<String> getMultiLineOutNoIslandingContList(){
		return this.multiLineOutNoIslandingContList;
	}
	public List<String> getMultiLineOutWithIslandingNoGenNoLoadContList(){
		return this.multiLineOutWithIslandingNoGenNoLoadContList;
	}
	public List<String> getMultiLineOutWithGenIslandingContList(){
		return this.multiLineOutWithGenIslandingContList;
	}
	public List<String> getMultiLineOutWithLoadIslandingContList(){
		return this.multiLineOutWithLoadIslandingContList;
	}

}

