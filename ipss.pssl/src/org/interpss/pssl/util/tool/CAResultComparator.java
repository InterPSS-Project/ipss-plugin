package org.interpss.pssl.util.tool;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Hashtable;
import java.util.List;

import org.interpss.pssl.common.PSSLException;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;
import org.interpss.pssl.util.ContingencyAnalysisHelper;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.Contingency;
import com.interpss.core.aclf.contingency.MonitoringBranch;
import com.interpss.core.dclf.common.ReferenceBusException;

public class CAResultComparator {

	private AclfNetwork net;
	private List<Contingency> cntgList=null;
	private Hashtable<String,ContingencyResult> cntgTable=new Hashtable<String,ContingencyResult>();
	
	public CAResultComparator(String ResultFile,AclfNetwork net){
		loadPWDResult(ResultFile);
		this.net= net;
	}
	
	public CAResultComparator(String ResultFile,AclfNetwork net,List<Contingency> contingencyList){
		loadPWDResult(ResultFile);
		this.net= net;
		this.cntgList = contingencyList;
		
	}

	
	public boolean compare(double violationThreshold) 
			throws InterpssException, ReferenceBusException, PSSLException{
		DclfAlgorithmDSL algoCtg = IpssDclf.createDclfAlgorithm(this.net, false)
				.runDclfAnalysis();

		ContingencyAnalysisHelper contHelper = new ContingencyAnalysisHelper(
				algoCtg, true);
		
		contHelper.setViolationThreshold(violationThreshold);
		boolean identical=true;
		
		for (Contingency cont : cntgList) {
			contHelper.contAnalysis(cont);
			ContingencyResult benchMarkCAResult =cntgTable.get(cont.getId());
			if(benchMarkCAResult!=null){
			  for (MonitoringBranch monBra : cont.getMonitoringBranches()) {
				AclfBranch br = monBra.getAclfBranch();
				double preFlow = br.getDclfFlow();
				double shiftedFlow = monBra.getShiftedFlow();
				double postFlow = preFlow + shiftedFlow;
				postFlow = postFlow * this.net.getBaseMva();
				
				//compare with the benchmark ca result
				double errorThreshold = 0.1;//MW
				
				CARecord rec=benchMarkCAResult.getViolateContRecords().get(br.getId()); 
                identical=Math.abs(rec.getValue()-postFlow)<errorThreshold;
                
                if(!identical){
                	System.out.println("Contingency #"+cont.getId()+"\n Ipss CA Result:"+postFlow+
                			"\n Benchmark CA Result:"+rec.getValue());
                }
			  }
			}
			 else{
				 System.out.println("Contingency #"+cont.getId()+" is not defined in the benchmark result file!");
				 identical = false;
			 }
		}
		return identical;
	}
	
	public Hashtable<String,ContingencyResult> getBenchMarkCAResult(){
		return this.cntgTable;
	}

	
	/** load  the contingency result saved in csv format from PWD
	   save each record in a {ElementRecord} object, while the records of the same 
	   contingency will be saved in a List of the same {ContingencyResult} object
    */
	private void loadPWDResult(String pwdResult){
		try {
			BufferedReader in = new BufferedReader( new FileReader(pwdResult));
			String s;
			String label, lastLabel="**NONE**";
			ContingencyResult result=null;
			
			//skim the first two lines, title only
			s=in.readLine();
			s=in.readLine();
			while((s=in.readLine())!=null){
		     String[] records=s.split(",");	
		     label=records[0].trim();
		     
		     //It is assumed here that in the PWD result file, records of the same contingency are listed togethter
		     //while before listing other contingency records
		     
			 if(!label.equals(lastLabel)){ // Start a new contingency
				result = new ContingencyResult();
				result.setLabel(label);
				cntgTable.put(label, result);
				lastLabel=label;
			 }
			 
			  savePWDCntgResult(result,records);
			 
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
    
	private boolean savePWDCntgResult(ContingencyResult result, String[] records){
		
		String elementId  =records[1];
		String category =records[2];
		double value = Double.valueOf(records[3]);
		double limit = Double.valueOf(records[4]);
		double percent = Double.valueOf(records[5]);
		if(category.contains("Branch")){
			
			//Label	    Element	                                  Category	  Value	 Limit	Percent
			//CONT_5	Bus25(25)->Bus52 (52) CKT 1 at Bus25 Change Branch MVA	300	  500	60

		   int firstLeftParenthesis=elementId.indexOf("(");
		   String fromBus = elementId.substring(0,firstLeftParenthesis).trim();
		   
		   String temp =  elementId.substring(firstLeftParenthesis+1);
		   int idx2 = temp.indexOf(">");
		   int idx3 = temp.indexOf("(");
		   String toBus  = temp.substring(idx2+1,idx3).trim();
		   
		   int idx4 = elementId.indexOf("CKT");
		   int idx5 = elementId.indexOf("at");
		   String cirId  = elementId.substring(idx4+3, idx5-1).trim();
		  
		   CARecord elemRecord= new CARecord(fromBus,toBus,cirId,value,limit,percent);
		   String elemId=fromBus+"->"+toBus+"("+cirId+")";
		   result.getViolateContRecords().put(elemId, elemRecord);
		   return true;
		}
		else{
			IpssLogger.getLogger().fine("Input record is not a supported type: "+records);
			return false;
		}
	}
	
	


}
