package org.ipss.multiNet.algo;

import java.util.Hashtable;
import java.util.Map.Entry;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;

import com.interpss.DStabObjectFactory;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;

public class DStab3SeqSimuAlgorithm {
	
	
	private DStabilityNetwork net = null;
	
	private Hashtable<String,Complex3x1> threeSeqVoltTable;
	private Hashtable<String,Complex3x1> threeSeqCurInjTable;
	private DynamicSimuAlgorithm posSeqDstabAlgo = null;
	
	private ISparseEqnComplex zeroSeqYMatrix = null; 
	private ISparseEqnComplex negSeqYMatrix  = null;
	
	
	// for considering faults within the studied network.
	private Complex Ifault = null;
	private String faultBus = "";
	private double seqCurThreshold = 1.0E-3; // pu
	
	
	public DStab3SeqSimuAlgorithm(DStabilityNetwork net){
		this.net = net;
		this.posSeqDstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, IpssCorePlugin.getMsgHub());
	}
	
	public DStab3SeqSimuAlgorithm(DynamicSimuAlgorithm dstabAlgo){
		this.posSeqDstabAlgo = dstabAlgo;
		this.net = dstabAlgo.getNetwork();
	}
	
	/**
	 * Make sure all three-sequence data are available in the input DStab network object
	 * @return true if all three-sequence data are avaiable
	 */
	public boolean has3SeqNetworkData(){
		return !net.isPositiveSeqDataOnly();
	}
	
	/**
	 *  The initialization process mainly includes two parts:
	 *  1) initialize the positive sequence dstab algorithm
	 *	2) form the negative and zero sequence Y matrices of the network
	 * @return true if no error during the initialization process
	 */
	public boolean initialize(){
		if(!has3SeqNetworkData())
			throw new Error("Only positive-sequence data is available in the input network object");
		
		boolean flag = true;
		//Step-1 initialize the positive sequence dstab algorithm
		flag = posSeqDstabAlgo.initialization();
		
		//Step-2 form the negative and zero sequence Y matrices of the network
		this.negSeqYMatrix  = this.net.formYMatrix(SequenceCode.NEGATIVE, true);
		this.zeroSeqYMatrix =  this.net.formYMatrix(SequenceCode.ZERO, true);
		
		return flag;
	}
	
	public boolean perform3SeqSimulation(){
		boolean flag = true;
		while(this.posSeqDstabAlgo.getSimuTime()<=this.posSeqDstabAlgo.getTotalSimuTimeSec()){
			flag = performOneStep3SeqSimulation(null);
			if(!flag){
				IpssLogger.getLogger().severe("Error during performing 3-Seq TS Simulation");
				break;
			}
		}
		return flag;
	}
	
	
	/**
	 * perform One time step 3-Seq TS Simulation, with t =  t+ dt
	 * if there is no three Seq Currunt Injection, set the <threeSeqCurInjTable> to null
	 * @param threeSeqCurInjTable
	 * @return
	 */
	public boolean performOneStep3SeqSimulation(Hashtable<String,Complex3x1> threeSeqCurInjTable){
		boolean flag = true;
		this.threeSeqCurInjTable = threeSeqCurInjTable;
		  if(this.threeSeqCurInjTable !=null){
			  
			 // first, solve the  positive sequence network 
		     this.net.setCustomBusCurrInjHashtable(getSeqCurInjTable(SequenceCode.POSITIVE));
		     flag = this.posSeqDstabAlgo.solveDEqnStep(true);
		     
		     //next, for the negative and zero sequence networks, the seq network is solved only when the  
		     // maximum seq current injections is larger than the threshold (1.0E-3)
		     Hashtable<String, Complex> negCurTable = getSeqCurInjTable(SequenceCode.NEGATIVE);
		     if(getMaxCurMag( negCurTable)> this.seqCurThreshold);
		         flag = flag && solveSeqNetwork(SequenceCode.NEGATIVE,negCurTable);
		          
		      
	          Hashtable<String, Complex> zeroCurTable = getSeqCurInjTable(SequenceCode.ZERO);
	         if(getMaxCurMag( zeroCurTable)> this.seqCurThreshold);
	             flag = flag && solveSeqNetwork(SequenceCode.ZERO,zeroCurTable);
		          
		     //TODO save the solution result     
		  }
		  else{
			  flag = this.posSeqDstabAlgo.solveDEqnStep(true);
			  
			  // check if there is an unsymmetrical fault at this step and solve the
			  // sequence networks involved, with the fault current as the only current injected
			  // into the negative and/or zero sequence networks
			  
		  }
		  // reset it to null
		  this.threeSeqCurInjTable = null;
		  
		  return flag;
			  
	}
	
	/**
	 * solve three-sequence network equation
	 * @return
	 */
    public boolean solve3SeqNetwork(){
    	
    	// Positive sequence
    	    this.net.setCustomBusCurrInjHashtable(null);
		   
		   ISparseEqnComplex subNetY= this.net.getYMatrix();
		   
		   subNetY.setB2Zero();
		   
		   for(Entry<String,Complex> e: this.subNetCurrInjTable.get(subNet.getId()).entrySet()){
			   subNetY.setBi(e.getValue(),subNet.getBus(e.getKey()).getSortNumber());
		   }
		   try {
			   // solve network to obtain Vext_injection
			    subNetY.solveEqn();
			} catch (IpssNumericException e1) {
				
				e1.printStackTrace();
				return false;
			}
		
	}
	


	public DStabilityNetwork getNet() {
		return net;
	}


	public void setNet(DStabilityNetwork net) {
		this.net = net;
	}


	public Hashtable<String, Complex3x1> getThreeSeqVoltTable() {
		return threeSeqVoltTable;
	}


	public void setThreeSeqVoltTable(Hashtable<String, Complex3x1> threeSeqVoltTable) {
		this.threeSeqVoltTable = threeSeqVoltTable;
	}


	public Hashtable<String, Complex3x1> getThreeSeqCurInjTable() {
		return threeSeqCurInjTable;
	}


	public void setThreeSeqCurInjTable(
			Hashtable<String, Complex3x1> threeSeqCurInjTable) {
		this.threeSeqCurInjTable = threeSeqCurInjTable;
	}


	public DynamicSimuAlgorithm getPosSeqDstabAlgo() {
		return posSeqDstabAlgo;
	}


	public void setPosSeqDstabAlgo(DynamicSimuAlgorithm posSeqDstabAlgo) {
		this.posSeqDstabAlgo = posSeqDstabAlgo;
	}
	
	private Hashtable<String, Complex> getSeqCurInjTable(SequenceCode seq){
		
		Hashtable<String, Complex> seqCurInjTable = new Hashtable<>();
		  if(this.threeSeqCurInjTable !=null){
			  switch(seq){
			  case POSITIVE:
				  for(Entry<String, Complex3x1> nvpair :this.threeSeqCurInjTable.entrySet()){
					  seqCurInjTable.put(nvpair.getKey(), nvpair.getValue().b_1);
				  }
				  break;
			  case NEGATIVE:
				  for(Entry<String, Complex3x1> nvpair :this.threeSeqCurInjTable.entrySet()){
					  seqCurInjTable.put(nvpair.getKey(), nvpair.getValue().c_2);
				  }
				  break;
			  case ZERO:
				  for(Entry<String, Complex3x1> nvpair :this.threeSeqCurInjTable.entrySet()){
					  seqCurInjTable.put(nvpair.getKey(), nvpair.getValue().a_0);
				  }
				  break;
			  
			  }
			  
			  return seqCurInjTable;
		  }
		  else 
			  return null;
	}
	
	private double getMaxCurMag(Hashtable<String, Complex> seqCurInjTable){
		double imax = 0;
		for(Complex i :seqCurInjTable.values()){
			  if(imax <i.abs())
				  imax = i.abs();
		  }
	    return imax;
	}
	
	private boolean solveSeqNetwork(SequenceCode seq,Hashtable<String, Complex> seqCurInjTable){
		// solve the Ymatrix
		switch (seq){
		
	  	// Positive sequence
		case POSITIVE:
		    this.net.setCustomBusCurrInjHashtable(null);
		   
		    ISparseEqnComplex subNetY= this.net.getYMatrix();
		   
		    subNetY.setB2Zero();
		    
		    //TODO extract the current and map them to the buses
		    
		    break;
		case NEGATIVE:
			  //TODO
			
			break;
		case ZERO:
			 //TODO
			
			break;
	    }
		
		// save the seq bus voltage result;
	}

	

}
