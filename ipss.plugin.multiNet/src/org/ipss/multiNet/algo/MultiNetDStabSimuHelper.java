package org.ipss.multiNet.algo;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.algo.SubNetworkProcessor;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;

public class MultiNetDStabSimuHelper {
	
	private DStabilityNetwork net = null;
	private SubNetworkProcessor subNetProcessor = null;
	
	
	public  MultiNetDStabSimuHelper(DStabilityNetwork net, SubNetworkProcessor subNetProc){
		this.net = net;
		this.subNetProcessor = subNetProc;
	}
	
	
	
	/**
	 *   This Multi SubNetwork processing is for three-sequence only;
	 *   This processing is mainly based on the paper  Chengshan Wang,Jiaan Zhang,"PARALLEL ALGORITHM FOR TRANSIENT STABILITY SIMULATION BASED ON  
	 *   BRANCH CUTTING AND SUBSYSTEM ITERATING " 
	 * 
	 * 
	 *   (1) add the Yff of the tieLine branch to the boundary bus yii
	 *   (2) equivalent current injection into the boundary buses which are to represent the contribution
	 *    from the buses on the other end of the tie-line
	 *   (3) set SubNetwork load flow status as converged
	 */
	public  void preProcess3SeqMultiSubNetwork(){
		
		/*
		 *  (1) add the Yff of the tieLine branch to the boundary bus shuntY, if there is no fixed shuntY 
		 *  in the boundary bus, create one first.
		 *  
		 *  step-1: obtain the boundary information from SubNetworkProcessor, iterate over the interface branches
		 *  
		 *  step-2: with the iteration, obtain the two terminal buses, add the Yff or Ytt of the tie-line depending the side of the bus on 
		 *  the branch
		 *  
		 *  (2) equivalent current injection into the boundary buses which are to represent the contribution from the buses on the other end of the tie-line
		 *  
		 *  step-3:  calculate the equivalent current injection and add to the subNetwork as custom current injection
		 *  
		 *  
		 */
		
		//Check the full network power flow convergence status
		
		if(!this.net.isLfConverged()){
			try {
				throw new Exception("The full network is not converged, cannot proceed the pre-processing of Multi-SubNetworks");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		for(String branchId:this.subNetProcessor.getInterfaceBranchIdList()){
			DStabBranch branch= net.getBranch(branchId);
		
			
					DStabBus fBus = (DStabBus) branch.getFromBus();
					DStabBus tBus = (DStabBus) branch.getToBus();
					
					/*
					 * Step-1, add the tie-line equivalent shuntY1/2/0 to the terminal buses
					 */
					//From bus
					if(fBus.getShuntY()!=null){
						
						fBus.setShuntY(fBus.getShuntY().add(branch.yff()));
						
					}
			        if(fBus.getScFixedShuntY0()==null){
						
						fBus.setScFixedShuntY0(branch.yff0());
						
					}
			        else
			        	fBus.setScFixedShuntY0(fBus.getScFixedShuntY0().add(branch.yff0()));
			        
			        
			        
					//To bus
					if(tBus.getShuntY()!=null){
						
						tBus.setShuntY(tBus.getShuntY().add(branch.ytt()));
						
					}
			        if(tBus.getScFixedShuntY0()==null){
						
						tBus.setScFixedShuntY0(branch.ytt0());
						
					}
			        else
			        	tBus.setScFixedShuntY0(tBus.getScFixedShuntY0().add(branch.ytt0()));
			        
	        
			        /*
					 * Step-2, add the tie-line equivalent curret injection to the terminal buses
					 */
			        //From bus side
			        int fChildNetIdx =  this.subNetProcessor.getBusId2SubNetworkTable().get(fBus.getId());
			        DStabilityNetwork fChileNet = this.subNetProcessor.getSubNetworkList().get(fChildNetIdx);
			        //HasCurrentInejctionTable 
			        if(fChileNet.getCustomBusCurrInjHashtable()==null){
			           Hashtable<String, Complex> customBusCurTable = new Hashtable<>();
			           fChileNet.setCustomBusCurrInjHashtable(customBusCurTable);
			           customBusCurTable.put(fBus.getId(), tBus.getVoltage().multiply(branch.yft()).multiply(-1.0d));
			        }
			        else{
			        	//fChileNet.getCustomBusCurrInjHashtable().put(fBus.getId(), tBus.getVoltage().multiply(branch.yft()).multiply(-1.0d));
			        	
			        	Complex currentInj = fChileNet.getCustomBusCurrInjHashtable().get(fBus.getId());
			        	if(currentInj==null) currentInj = new Complex(0,0) ;
			        	currentInj = currentInj.add(tBus.getVoltage().multiply(branch.yft()).multiply(-1.0d));
			        	fChileNet.getCustomBusCurrInjHashtable().put(fBus.getId(), currentInj);
			        }
			        	
			        
			        
			        
			        //To Bus side
			        int tChildNetIdx =  this.subNetProcessor.getBusId2SubNetworkTable().get(tBus.getId());
			        DStabilityNetwork tChileNet = this.subNetProcessor.getSubNetworkList().get(tChildNetIdx);
			        //HasCurrentInejctionTable 
			        if(tChileNet.getCustomBusCurrInjHashtable()==null){
			           Hashtable<String, Complex> customBusCurTable = new Hashtable<>();
			           tChileNet.setCustomBusCurrInjHashtable(customBusCurTable);
			           customBusCurTable.put(tBus.getId(), fBus.getVoltage().multiply(branch.ytf()).multiply(-1.0d));
			        }
			        else{
			        	Complex currentInj = tChileNet.getCustomBusCurrInjHashtable().get(tBus.getId());
			        	if(currentInj==null) currentInj = new Complex(0,0) ;
			        	currentInj = currentInj.add(fBus.getVoltage().multiply(branch.ytf()).multiply(-1.0d));
			        	tChileNet.getCustomBusCurrInjHashtable().put(tBus.getId(), currentInj);
			        	
			        }
			   // after mapping the effect of the tie-line branch to both terminal buses, set it to out-of-service     
			  branch.setStatus(false);      	
			}
		
		  // set the power flow convergence status 
		  for( DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){

				  subNet.setLfConverged(true);
			  
		  }
	   
	  }
	
	
public  void processInterfaceBranchEquiv(){
		
		/*
		 *  (1) add the Yff of the tieLine branch to the boundary bus shuntY, if there is no fixed shuntY 
		 *  in the boundary bus, create one first.
		 *  
		 *  step-1: obtain the boundary information from SubNetworkProcessor, iterate over the interface branches
		 *  
		 *  step-2: with the iteration, obtain the two terminal buses, add the Yff or Ytt of the tie-line depending the side of the bus on 
		 *  the branch
		 *  
		 *  (2) equivalent current injection into the boundary buses which are to represent the contribution from the buses on the other end of the tie-line
		 *  
		 *  step-3:  calculate the equivalent current injection and add to the subNetwork as custom current injection
		 *  
		 *  
		 */
		
		//Check the full network power flow convergence status
		
		if(!this.net.isLfConverged()){
			try {
				throw new Exception("The full network is not converged, cannot proceed the pre-processing of Multi-SubNetworks");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		for(String branchId:this.subNetProcessor.getInterfaceBranchIdList()){
			DStabBranch branch= net.getBranch(branchId);
		
			
					DStabBus fBus = (DStabBus) branch.getFromBus();
					DStabBus tBus = (DStabBus) branch.getToBus();
					
					/*
					 * Step-1, add the tie-line equivalent shuntY1/2/0 to the terminal buses
					 */
					//From bus
					if(fBus.getShuntY()!=null){
						
						fBus.setShuntY(fBus.getShuntY().add(branch.getFromShuntY()).add(branch.getHShuntY()));
						
					}
			        if(fBus.getScFixedShuntY0()==null){
						
						fBus.setScFixedShuntY0(new Complex(0,branch.getHB0()));
						
					}
			        else
			        	fBus.setScFixedShuntY0(fBus.getScFixedShuntY0().add(new Complex(0,branch.getHB0())));
			        
			        
			        
					//To bus
					if(tBus.getShuntY()!=null){
						
						tBus.setShuntY(tBus.getShuntY().add(branch.getToShuntY()).add(branch.getHShuntY()));
						
					}
			        if(tBus.getScFixedShuntY0()==null){
						
						tBus.setScFixedShuntY0(new Complex(0,branch.getHB0()));
						
					}
			        else
			        	tBus.setScFixedShuntY0(tBus.getScFixedShuntY0().add(new Complex(0,branch.getHB0())));
			        
	        
			        /*
					 * Step-2, add the tie-line equivalent curret injection to the terminal buses
					 */
			        //From bus side
			        int fChildNetIdx =  this.subNetProcessor.getBusId2SubNetworkTable().get(fBus.getId());
			        DStabilityNetwork fChileNet = this.subNetProcessor.getSubNetworkList().get(fChildNetIdx);
			        //HasCurrentInejctionTable 
			        if(fChileNet.getCustomBusCurrInjHashtable()==null){
			           Hashtable<String, Complex> customBusCurTable = new Hashtable<>();
			           fChileNet.setCustomBusCurrInjHashtable(customBusCurTable);
			           customBusCurTable.put(fBus.getId(),  branch.getY().multiply((tBus.getVoltage().subtract(fBus.getVoltage()))));
			        }
			        else{
			        	//fChileNet.getCustomBusCurrInjHashtable().put(fBus.getId(), tBus.getVoltage().multiply(branch.yft()).multiply(-1.0d));
			        	
			        	Complex currentInj = fChileNet.getCustomBusCurrInjHashtable().get(fBus.getId());
			        	if(currentInj==null) currentInj = new Complex(0,0) ;
			        	currentInj = currentInj.add(branch.getY().multiply((tBus.getVoltage().subtract(fBus.getVoltage()))));
			        	fChileNet.getCustomBusCurrInjHashtable().put(fBus.getId(), currentInj);
			        }
			        	
			        
			        
			        
			        //To Bus side
			        int tChildNetIdx =  this.subNetProcessor.getBusId2SubNetworkTable().get(tBus.getId());
			        DStabilityNetwork tChileNet = this.subNetProcessor.getSubNetworkList().get(tChildNetIdx);
			        //HasCurrentInejctionTable 
			        if(tChileNet.getCustomBusCurrInjHashtable()==null){
			           Hashtable<String, Complex> customBusCurTable = new Hashtable<>();
			           tChileNet.setCustomBusCurrInjHashtable(customBusCurTable);
			           customBusCurTable.put(tBus.getId(), branch.getY().multiply((fBus.getVoltage().subtract(tBus.getVoltage()))));
			        }
			        else{
			        	Complex currentInj = tChileNet.getCustomBusCurrInjHashtable().get(tBus.getId());
			        	if(currentInj==null) currentInj = new Complex(0,0) ;
			        	currentInj = currentInj.add(branch.getY().multiply((fBus.getVoltage().subtract(tBus.getVoltage()))));
			        	tChileNet.getCustomBusCurrInjHashtable().put(tBus.getId(), currentInj);
			        	
			        }
			   // after mapping the effect of the tie-line branch to both terminal buses, set it to out-of-service     
			  branch.setStatus(false);      	
			}
		
		  // set the power flow convergence status 
		  for( DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){

				  subNet.setLfConverged(true);
			  
		  }
	   
	  }
	
	
	   public boolean updateBoundaryBusEquivCurrentInjection(){
		   
		   // need to first reset all customized current injection to be zero
		   for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
			   subNet.setCustomBusCurrInjHashtable(null);
		   }
		  
		   for(String branchId: subNetProcessor.getInterfaceBranchIdList()){
				DStabBranch branch= net.getBranch(branchId);
				
				// at this stage, the statuses of boundary buses  are inactive, so it needs to be turned to active first;
				// as the Ymatrix is already built, this won't affect the V=INV(Y)*I network solution
				branch.setStatus(true);
				
				
				DStabBus fBus = (DStabBus) branch.getFromBus();
				DStabBus tBus = (DStabBus) branch.getToBus();

				 /*
				 * Step-2, add the tie-line equivalent curret injection to the terminal buses
				 */
		        //From bus side
		        int fChildNetIdx =  this.subNetProcessor.getBusId2SubNetworkTable().get(fBus.getId());
		        DStabilityNetwork fChileNet = this.subNetProcessor.getSubNetworkList().get(fChildNetIdx);
		        //HasCurrentInejctionTable 
		        if(fChileNet.getCustomBusCurrInjHashtable()==null){
		           Hashtable<String, Complex> customBusCurTable = new Hashtable<>();
		           fChileNet.setCustomBusCurrInjHashtable(customBusCurTable);
		           customBusCurTable.put(fBus.getId(), tBus.getVoltage().multiply(branch.yft()).multiply(-1.0d));
		        }
		        else{
		        	//fChileNet.getCustomBusCurrInjHashtable().put(fBus.getId(), tBus.getVoltage().multiply(branch.yft()).multiply(-1.0d));
		        	
		        	Complex currentInj = fChileNet.getCustomBusCurrInjHashtable().get(fBus.getId());
		        	if(currentInj==null) currentInj = new Complex(0,0) ;
		        	currentInj = currentInj.add(tBus.getVoltage().multiply(branch.yft()).multiply(-1.0d));
		        	fChileNet.getCustomBusCurrInjHashtable().put(fBus.getId(), currentInj);
		        }
		        	
		        
		        
		        
		        //To Bus side
		        int tChildNetIdx =  this.subNetProcessor.getBusId2SubNetworkTable().get(tBus.getId());
		        DStabilityNetwork tChileNet = this.subNetProcessor.getSubNetworkList().get(tChildNetIdx);
		        //HasCurrentInejctionTable 
		        if(tChileNet.getCustomBusCurrInjHashtable()==null){
		           Hashtable<String, Complex> customBusCurTable = new Hashtable<>();
		           tChileNet.setCustomBusCurrInjHashtable(customBusCurTable);
		           customBusCurTable.put(tBus.getId(), fBus.getVoltage().multiply(branch.ytf()).multiply(-1.0d));
		        }
		        else{
		        	Complex currentInj = tChileNet.getCustomBusCurrInjHashtable().get(tBus.getId());
		        	if(currentInj==null) currentInj = new Complex(0,0) ;
		        	currentInj = currentInj.add(fBus.getVoltage().multiply(branch.ytf()).multiply(-1.0d));
		        	tChileNet.getCustomBusCurrInjHashtable().put(tBus.getId(), currentInj);
		        	
		        }
		        
		        
		        branch.setStatus(false);
		   }
		   
		   for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
			   System.out.println(subNet.getId()+"   customCurrentTable:\n"+subNet.getCustomBusCurrInjHashtable());
		   }
		   
		   return true;
		   
	   }
	   
	   public void predictNextStepBoundaryVoltage(DStabilityNetwork net, SubNetworkProcessor subNetProc){
		   
	   }
	    
	   
	   public SubNetworkProcessor getSubNetworkProcessor(){
		   return this.subNetProcessor;
	   }
		
	 

}
