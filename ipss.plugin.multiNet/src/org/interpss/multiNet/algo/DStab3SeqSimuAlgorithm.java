package org.interpss.multiNet.algo;

import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.algo.sc.ScBusModelType;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;

public class DStab3SeqSimuAlgorithm {
	
	
	private BaseDStabNetwork<?,?> net = null;
	
	private Hashtable<String,Hashtable<Integer,Complex3x1>> threeSeqVoltTable;
	private Hashtable<String,Complex3x1> threeSeqCurInjTable;
	private DynamicSimuAlgorithm posSeqDstabAlgo = null;
	
	private ISparseEqnComplex zeroSeqYMatrix = null; 
	private ISparseEqnComplex negSeqYMatrix  = null;
	private String[] monitoringBusAry =null;
	private List<String> monitoringBusIdList =null;
	
	// for considering faults within the studied network.
	private Complex Ifault = null;
	private String faultBus = "";
	private double seqCurThreshold = 1.0E-3; // pu
	
	private int k = 0;
	
	
	public DStab3SeqSimuAlgorithm(BaseDStabNetwork<?,?> net){
		this.net = net;
		this.posSeqDstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, IpssCorePlugin.getMsgHub());
		
		threeSeqVoltTable = new Hashtable<>();
	}
	
	public DStab3SeqSimuAlgorithm(DynamicSimuAlgorithm dstabAlgo){
		this.posSeqDstabAlgo = dstabAlgo;
		this.net = dstabAlgo.getNetwork();
		
		threeSeqVoltTable = new Hashtable<>();
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
		this.negSeqYMatrix  = this.net.formScYMatrix(SequenceCode.NEGATIVE, ScBusModelType.LOADFLOW_VOLT, true);
		this.zeroSeqYMatrix =  this.net.formScYMatrix(SequenceCode.ZERO, ScBusModelType.LOADFLOW_VOLT, true);
		
		//Step-3 initialize monitoring result table
		if(this.monitoringBusAry!=null){
			for(String busId: this.monitoringBusAry){
				threeSeqVoltTable.put(busId, new Hashtable<Integer,Complex3x1>());
			}
		}
		else
			IpssLogger.getLogger().severe("No monitoring bus is defined");
		
		k = 0;
		
		return flag;
	}
	
	public boolean perform3SeqSimulation(){
		boolean flag = true;
		while(this.posSeqDstabAlgo.getSimuTime()<=this.posSeqDstabAlgo.getTotalSimuTimeSec()){
			flag = performOneStep3SeqSimulation(k++, null);
			
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
	public boolean performOneStep3SeqSimulation(int counter,Hashtable<String,Complex3x1> threeSeqCurInjTable){
		boolean flag = true;
		
		Hashtable<String,Complex> negVoltTable =null;
		Hashtable<String,Complex> zeroVoltTable =null;
		  
		this.threeSeqCurInjTable = threeSeqCurInjTable;
		if(this.threeSeqCurInjTable !=null){
			  
			 // first, solve the  positive sequence network 
		     this.net.setCustomBusCurrInjHashtable(getSeqCurInjTable(SequenceCode.POSITIVE));
		     flag = this.posSeqDstabAlgo.solveDEqnStep(true);
		     
		     
		     //next, for the negative and zero sequence networks, the seq network is solved only when the  
		     // maximum seq current injections is larger than the threshold (1.0E-3)
		     Hashtable<String, Complex> negCurTable = getSeqCurInjTable(SequenceCode.NEGATIVE);
		   
		     if(getMaxCurMag( negCurTable)> this.seqCurThreshold){
		    	 
		    	 negVoltTable = solveSeqNetwork(SequenceCode.NEGATIVE,negCurTable);
		          flag = flag && (negVoltTable!=null);
		     }
		          
		      
	          Hashtable<String, Complex> zeroCurTable = getSeqCurInjTable(SequenceCode.ZERO);
	          
	          if(getMaxCurMag( zeroCurTable)> this.seqCurThreshold){
	        	   zeroVoltTable = solveSeqNetwork(SequenceCode.ZERO,zeroCurTable);
	               flag = flag && (zeroVoltTable!=null);
	          }
		          
		    
		  }
		  else{
			  flag = this.posSeqDstabAlgo.solveDEqnStep(true);
			  
			  //TODO 
			  
			  // check if there is an unsymmetrical fault at this step and solve the
			  // sequence networks involved, with the fault current as the only current injected
			  // into the negative and/or zero sequence networks
			  
			  
			  
		  }
		
		// save the solution result     
        
         for(String busId: this.monitoringBusAry){
     	        Complex3x1 seqVolts = new  Complex3x1();
     	        seqVolts.b_1 = this.net.getBus(busId).getVoltage();
     	        
     	        if(negVoltTable!=null)
     	        	seqVolts.c_2 = negVoltTable.get(busId);
     	        
     	        if(zeroVoltTable!=null)
     	        	seqVolts.a_0 = zeroVoltTable.get(busId);
     	        
				threeSeqVoltTable.get(busId).put(counter, seqVolts);
		   }
		
		
		  // reset it to null
		  this.threeSeqCurInjTable = null;
		  
		  return flag;
			  
	}
	
	/**
	 * solve three-sequence network equation
	 * @return
	 */
    public Hashtable<String,Complex3x1> solve3SeqNetwork( Hashtable<String, Complex3x1> boundary3SeqCurInjTable){
    	
    	Hashtable<String,Complex3x1> threeSeqBusVoltTable = new Hashtable<>();
    	
    	// first,  current injection tables for the three sequences should be extracted 
    	 Hashtable<String, Complex> posCurTable = getSeqCurInjTable(boundary3SeqCurInjTable, SequenceCode.POSITIVE);
    	 Hashtable<String, Complex> negCurTable = getSeqCurInjTable(boundary3SeqCurInjTable, SequenceCode.NEGATIVE);
    	 Hashtable<String, Complex> zeroCurTable = getSeqCurInjTable(boundary3SeqCurInjTable, SequenceCode.ZERO);
    	
    	 // Positive sequence
    	// positive sequence should be solved in the same way as in the solveSubNetWithBoundaryCurrInjection() of  MultiNetDStabSimuHelper
		
    	 ISparseEqnComplex posSeqNetY =this.net.getYMatrix();
    	 
    	 posSeqNetY.setB2Zero();
    	 
    	 for(Entry<String,Complex> e: posCurTable.entrySet()){
    		 posSeqNetY.setBi(e.getValue(),this.net.getBus(e.getKey()).getSortNumber());
		   }
		   try {
			   // solve network to obtain Vext_injection
			    posSeqNetY.solveEqn();
			} catch (IpssNumericException e1) {
				
				e1.printStackTrace();
				return null;
			}
		   
		   for(BaseDStabBus<?,?> b:this.net.getBusList()){
			   //superpostition method
			   //bus voltage V = Vinternal + Vext_injection
			   b.setVoltage(b.getVoltage().add(posSeqNetY.getX(b.getSortNumber())));
			   
			  // also save the result to the threeSeqBusVoltTable
			   Complex3x1 v120 = new Complex3x1( new Complex(0,0),b.getVoltage(),new Complex(0,0));
			   threeSeqBusVoltTable.put(b.getId(),v120);
		   }
    	 
	
        // negative sequence
		   Hashtable<String,Complex> negVoltTable = this.solveSeqNetwork(SequenceCode.NEGATIVE, negCurTable);
		   
		   for(Entry<String,Complex> e: negVoltTable.entrySet()){
			   threeSeqBusVoltTable.get(e.getKey()).c_2 =e.getValue();
		   }
    	
    	// zero sequence
		   
          Hashtable<String,Complex> zeroVoltTable = this.solveSeqNetwork(SequenceCode.ZERO, zeroCurTable);
		   
		   for(Entry<String,Complex> e: zeroVoltTable.entrySet()){
			   threeSeqBusVoltTable.get(e.getKey()).a_0 =e.getValue();
		   }
		   
		   return threeSeqBusVoltTable;
    
    }
	


	public BaseDStabNetwork<?,?> getNet() {
		return net;
	}


	public void setNet(BaseDStabNetwork<?,?> net) {
		this.net = net;
	}


	public Hashtable<String,Hashtable<Integer,Complex3x1>>  getThreeSeqVoltTable() {
		return threeSeqVoltTable;
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
	
	
	public void setMonitoringBusAry(String[] monBusAry){
		this.monitoringBusAry = monBusAry;
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
	
	
	private Hashtable<String, Complex> getSeqCurInjTable(Hashtable<String, Complex3x1> curInjTable, SequenceCode seq){
		
		Hashtable<String, Complex> seqCurInjTable = new Hashtable<>();
		  if(this.threeSeqCurInjTable !=null){
			  switch(seq){
			  case POSITIVE:
				  for(Entry<String, Complex3x1> nvpair :curInjTable.entrySet()){
					  seqCurInjTable.put(nvpair.getKey(), nvpair.getValue().b_1);
				  }
				  break;
			  case NEGATIVE:
				  for(Entry<String, Complex3x1> nvpair :curInjTable.entrySet()){
					  seqCurInjTable.put(nvpair.getKey(), nvpair.getValue().c_2);
				  }
				  break;
			  case ZERO:
				  for(Entry<String, Complex3x1> nvpair :curInjTable.entrySet()){
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
	
	private Hashtable<String, Complex> solveSeqNetwork(SequenceCode seq,Hashtable<String, Complex> seqCurInjTable){
		
		 Hashtable<String, Complex>  busVoltResults = new  Hashtable<>();
		// solve the Ymatrix
		switch (seq){
		
	  	// Positive sequence
		case POSITIVE:
		    this.net.setCustomBusCurrInjHashtable(null);
		   
		    ISparseEqnComplex subNetY= this.net.getYMatrix();
		   
		    subNetY.setB2Zero();
		    
		       for(Entry<String,Complex> e: seqCurInjTable.entrySet()){
				   subNetY.setBi(e.getValue(),this.net.getBus(e.getKey()).getSortNumber());
			   }
			   try {
				   // solve network to obtain Vext_injection
				    subNetY.solveEqn();
				    if(this.monitoringBusAry!=null)
				       for(String busId:this.monitoringBusAry){
				    	  busVoltResults.put(busId, subNetY.getX(this.net.getBus(busId).getSortNumber()));
				      }
				} catch (IpssNumericException e1) {
					
					e1.printStackTrace();
					return null;
				}
		    
		    //TODO extract the current and map them to the buses
		    
		    break;
		case NEGATIVE:
			   //TODO
			   negSeqYMatrix.setB2Zero();
			   
			   for(Entry<String,Complex> e: seqCurInjTable.entrySet()){
				 negSeqYMatrix.setBi(e.getValue(),this.net.getBus(e.getKey()).getSortNumber());
			   }
			   try {
				   // solve network to obtain Vext_injection
				   negSeqYMatrix.solveEqn();
				   
				   if(this.monitoringBusAry!=null)
				     for(String busId:this.monitoringBusAry){
				    	  busVoltResults.put(busId, negSeqYMatrix.getX(this.net.getBus(busId).getSortNumber()));
				      }
			   } catch (IpssNumericException e1) {
					
					e1.printStackTrace();
					return null;
			   }
			
			
			break;
		case ZERO:
			   //TODO
			   zeroSeqYMatrix.setB2Zero();
			   
			   for(Entry<String,Complex> e: seqCurInjTable.entrySet()){
				   zeroSeqYMatrix.setBi(e.getValue(),this.net.getBus(e.getKey()).getSortNumber());
			   }
			   try {
				   // solve network to obtain Vext_injection
				   zeroSeqYMatrix.solveEqn();
				   
				   if(this.monitoringBusAry!=null)
				      for(String busId:this.monitoringBusAry){
				    	  busVoltResults.put(busId, zeroSeqYMatrix.getX(this.net.getBus(busId).getSortNumber()));
				      }
			   } catch (IpssNumericException e1) {
					
					e1.printStackTrace();
					return null;
			   }
			
			break;
	    }
		
		// save the seq bus voltage result;
		
		return busVoltResults;
	}

	

}
