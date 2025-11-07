package org.interpss.multiNet.algo.powerflow;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.math3.complex.Complex;
import org.interpss.multiNet.algo.SubNetworkProcessor;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.Branch;
import com.interpss.core.net.NetworkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TposSeqD3PhaseMultiNetPowerflowAlgorithm {
    private static final Logger log = LoggerFactory.getLogger(TposSeqD3PhaseMultiNetPowerflowAlgorithm.class);
	
	protected BaseAclfNetwork<? extends BaseAclfBus, ?extends AclfBranch> net = null;
	protected BaseAclfNetwork<? extends BaseAclfBus, ?extends AclfBranch> transmissionNet = null;
	protected List<BaseAclfNetwork> distNetList = null;
	protected SubNetworkProcessor subNetProcessor = null;
	protected Hashtable<String,String> distNetId2BoundaryBusTable = null;
	protected Hashtable<String,Complex3x1> distBoundary3SeqCurInjTable = null;
	protected Hashtable<String,Complex3x1> transBoundary3SeqCurInjTable = null;
	protected Hashtable<String,Complex3x1> distBoundaryBus3SeqVoltages = null;
	protected Hashtable<String,Complex3x1> transBoundaryBus3SeqVoltages = null;
	
	protected Hashtable<String,Complex> distBoundaryTotalPowerTable = null;
	protected List<String> transNetworkBoundaryBusIdList = null;
	
	
	
	protected boolean pfFlag = true;
	protected int iterationMax = 30;
	protected double tolerance = 1.0E-4;
	protected LoadflowAlgorithm transLfAlgo = null;
	
	protected double distTolerance = 1.0E-4;
	
	private Hashtable<String,Complex3x1> lastStepTransBoundaryBus3SeqVoltages = null;
	
	
	public TposSeqD3PhaseMultiNetPowerflowAlgorithm(BaseAclfNetwork<? extends BaseAclfBus, ?extends AclfBranch> tdNet, 
			SubNetworkProcessor subNetProc) {
		this.net = tdNet;
		subNetProcessor = subNetProc;
		distNetId2BoundaryBusTable = new Hashtable<>();
		
		distBoundary3SeqCurInjTable = new Hashtable<>();
		distBoundaryTotalPowerTable = new Hashtable<>();
		transBoundary3SeqCurInjTable = new Hashtable<>();
		
		distBoundaryBus3SeqVoltages = new Hashtable<>();
		transBoundaryBus3SeqVoltages = new Hashtable<>();
		
		transNetworkBoundaryBusIdList  = new ArrayList<>();
		
		lastStepTransBoundaryBus3SeqVoltages  = new Hashtable<>();
		
		this.transmissionNet = (BaseAclfNetwork<? extends BaseAclfBus, ? extends AclfBranch>) subNetProc.getExternalSubNetwork();
		
		this.distNetList = new  ArrayList<>();
		for(String id:subNetProc.getInternalSubNetBoundaryBusIdList()){
			BaseAclfNetwork distNet = subNetProc.getSubNetworkByBusId(id);
			if(!this.distNetList.contains(distNet))
			  this.distNetList.add(distNet);
			  log.info("Subsystem #"+distNet.getId()+" is set to be Distribution network before performing T&D Loadflow");
			  distNet.setNetworkType(NetworkType.DISTRIBUTION);
		}
		
		transLfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(transmissionNet);
		
	}
	
	public boolean powerflow() throws InterpssException{
		
		// set the interconnection tie-line created during the network splitting to be out-of-service
		
		for(String tieLineId :this.subNetProcessor.getInterfaceBranchIdList()){
			AclfBranch connectBranch = this.net.getBranch(tieLineId);
			connectBranch.setStatus(false);
		}
		
		/*
		 * 1. network splitting should be performed before running power flow
		 */
		if(transmissionNet == null || this.distNetList == null){
			throw new Error(" The network is not splitted yet!");
		}
		
		//TODO need to obtain the transmission network and distribution networks from the subNetworkProcessor 
		
		/*
		 *  2.  set the boundary buses of distribution system as swing bus and their bus voltages to be unit voltages
		 *
		 *  3. run distribution power flow with unit swing bus voltages 
		 *  
		 *  4. obtain the "approximate" power consumption of each distribution system 
		 */
		double transMVABase = this.transmissionNet.getBaseMva();
		
		for(BaseAclfNetwork distNet:this.distNetList){
			List<String> boundaryList = subNetProcessor.getSubNet2BoundaryBusListTable().get(distNet.getId());
			
			if(boundaryList.size()!=1){
				throw new Error(" Only one source bus for a distribution system is supported!");
			}
			else{
				BaseAclfBus sourceBus =(BaseAclfBus) distNet.getBus(boundaryList.get(0));
				
				distNetId2BoundaryBusTable.put(distNet.getId(), sourceBus.getId());
				
				//initialize the distribution system source (substation) bus
				sourceBus.setGenCode(AclfGenCode.SWING);
				sourceBus.setVoltage(new Complex(1.0,0));
				
				DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
				distPFAlgo.setTolerance(this.distTolerance);
			
				if(!distPFAlgo.powerflow()){
					throw new Error("Distribution system power flow is NOT converged! # "+distNet.getId());
				}
				
				Complex3x1 currInj3Phase = new Complex3x1();
				
				for(Branch bra: sourceBus.getBranchList()){
					if(bra.isActive()){
						DStab3PBranch acLine = (DStab3PBranch) bra;
						
						//NOTE the positive sign of branch current flow is fromBus->ToBus  
						if(bra.getFromBus().getId().equals(sourceBus.getId())){
							currInj3Phase = currInj3Phase.add(acLine.getCurrentAbcAtFromSide().multiply(-1));
						}
						else{
							currInj3Phase = currInj3Phase.add(acLine.getCurrentAbcAtToSide());
						}
					}
				}
				
				//TODO this needs to be updated if actual values are used in the distribution system
				double distMVABase = distNet.getBaseMva();
				
//				// save the three-sequence current injection to the table
//				Complex3x1 currInj3Seq = currInj3Phase.to012().multiply(distMVABase/transMVABase);
//				
//				System.out.println("3seq current injection: "+currInj3Seq.toString());
//				distBoundary3SeqCurInjTable.put(sourceBus.getId(), currInj3Phase.to012());
//				
				DStab3PBus sourceBus3Ph = (DStab3PBus) sourceBus; 
				
				Complex totalPower = sourceBus3Ph.get3PhaseVotlages().dotProduct(currInj3Phase.conjugate()).divide(3.0).multiply(distMVABase/transMVABase);
				
				System.out.println("Total power (on Transmission Network MVA Base) = "+totalPower.toString());
				log.debug("Total power (on Transmission Network MVA Base) = {}", totalPower);
				
				distBoundaryTotalPowerTable.put(sourceBus.getId(), totalPower);
			}
		}
			
		
		/*
		 * 5. represent them as constant loads at the boundary buses of the transmission system   
		 * and run positive sequence power flow
		 * 
		 * NOTE: representing the distribution system as power is better than current injections because
		 *       the phase angles of the current injections at this initial stage are not accurate.
		 *       While power form avoid such phase angle shift issue.
		 *       
		 */
		
		   
		
		   for(Entry<String,Complex> e: distBoundaryTotalPowerTable.entrySet()){
			   String distBoundaryBusId = e.getKey();
			   
			   String transBoundaryBusId = "";
			   if(distBoundaryBusId.contains("Dummy")){
				   transBoundaryBusId = distBoundaryBusId.replace("Dummy", "");
			   }
			   else
				   transBoundaryBusId = distBoundaryBusId+"Dummy";
			   
			   BaseAclfBus transBoundaryBus = this.transmissionNet.getBus(transBoundaryBusId);
			   
			   if(transBoundaryBus == null){
				   throw new Error("The tranmission network boundary bus is not found, ID: "+transBoundaryBusId);
			   }
			   else{
				   
				   
				   //TODO assuming there is no loads at the boundary bus
				   if(new Complex(transBoundaryBus.getLoadP(),transBoundaryBus.getLoadQ()).abs()>0.0){
					   throw new Error("The  boundary bus in the tranmission network cannot be a load bus: "+transBoundaryBusId);
				   }
				   
				   // represent the power flow into the boundary bus as "negative" load
				   transBoundaryBus.setLoadP(-e.getValue().getReal());
				   transBoundaryBus.setLoadQ(-e.getValue().getImaginary());
				   transBoundaryBus.setLoadCode(AclfLoadCode.CONST_P);
				   
				   transNetworkBoundaryBusIdList.add(transBoundaryBusId);
				   
				   transBoundaryBus3SeqVoltages.put(transBoundaryBusId, new Complex3x1());
			   }
			   
		   }
	
		   /*
		    * 6. Run positive sequence power flow 
		    */
		
		      
		      if(!transLfAlgo.loadflow()){
		    	  throw new Error(" positive sequence power flow for the transmission system is not converged");
		      }
		   
		   /*
		    * 7. save the initial transmission boundary bus voltage results
		    */
		      
		      for(Entry<String,Complex3x1> e: transBoundaryBus3SeqVoltages .entrySet()){
				   String transBoundaryBusId = e.getKey();
				   BaseAclfBus transBoundaryBus = this.transmissionNet.getBus(transBoundaryBusId);
				   e.getValue().b_1 = transBoundaryBus.getVoltage();
				   
		      }
		      
		      
		      
		      
		      //-----------------------------------------------------------------------------------------
		      //   Start the iterative solution of both transmission and distribution systems
		      //-----------------------------------------------------------------------------------------
		   
		     
		      for(int i = 0;i<this.iterationMax;i++){
		      
		  
			   /*
			    * 
			    * 8. update the three-phase voltages of source buses of the distribution systems 
			    *    and run the distribution system. Calculate the three sequence current injections at the boundary
			    * 
			    */
		    	  updateDistBoundaryBus3SeqVoltTable();
		    	  
		    	  for(BaseAclfNetwork distNet:this.distNetList){
		    		  DStab3PBus sourceBus3Ph = (DStab3PBus) distNet.getBus(distNetId2BoundaryBusTable.get(distNet.getId()));
		    		  
		    		  Complex3x1 vabc = this.distBoundaryBus3SeqVoltages.get(sourceBus3Ph.getId()).toABC();
		    		  
		    		  System.out.println("updated dist source bus vabc = "+vabc);
		    		  log.debug("updated dist source bus vabc = {}", vabc);
		    		  sourceBus3Ph.set3PhaseVotlages(vabc);
		    		  //manually update the positive sequence, since internally it won't be automatically updated.
		    		  sourceBus3Ph.setVoltage(sourceBus3Ph.getThreeSeqVoltage().b_1);
		    		  
		    		  DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
                      
		    		  // use the voltages obtained from the last step and transmission system power flow results
		    		  distPFAlgo.setInitBusVoltageEnabled(false);
		    		  
						if(!distPFAlgo.powerflow()){
							throw new Error("Distribution system power flow is NOT converged! # "+distNet.getId()+", "+distNet.getBusList().get(0));
						}
						
					
						Complex3x1 currInj3Phase = new Complex3x1();
						
						for(Branch bra: sourceBus3Ph.getBranchList()){
							if(bra.isActive()){
								DStab3PBranch acLine = (DStab3PBranch) bra;
								if(bra.getFromBus().getId().equals(sourceBus3Ph.getId())){
									currInj3Phase = currInj3Phase.add(acLine.getCurrentAbcAtFromSide().multiply(-1));
								}
								else{
									currInj3Phase = currInj3Phase.add(acLine.getCurrentAbcAtToSide());
								}
							}
						}
						
						Complex totalPower = sourceBus3Ph.get3PhaseVotlages().dotProduct(currInj3Phase.conjugate()).divide(3.0).multiply(distNet.getBaseMva()/transMVABase);
						
						System.out.println("Total power (on Transmission Network MVA Base) = "+totalPower.toString());
						log.debug("Total power (on Transmission Network MVA Base) = {}", totalPower);
						
						distBoundaryTotalPowerTable.put(sourceBus3Ph.getId(), totalPower);
						
				
		    		  
		    	  }
			   
			       /*
			        *  9.   update the positive sequence equivalent load
			        */
		    	  

		    	  for(Entry<String,Complex> e: distBoundaryTotalPowerTable.entrySet()){
					   String distBoundaryBusId = e.getKey();
					   
					   String transBoundaryBusId = "";
					   if(distBoundaryBusId.contains("Dummy")){
						   transBoundaryBusId = distBoundaryBusId.replace("Dummy", "");
					   }
					   else
						   transBoundaryBusId = distBoundaryBusId+"Dummy";
					   
					   BaseAclfBus transBoundaryBus = this.transmissionNet.getBus(transBoundaryBusId);
					   
					   if(transBoundaryBus == null){
						   throw new Error("The tranmission network boundary bus is not found, ID: "+transBoundaryBusId);
					   }
					   else{
						   
						   // UPDATE THE EQUIVALENT LOAD VALUE
						   transBoundaryBus.setLoadP(-e.getValue().getReal());
						   transBoundaryBus.setLoadQ(-e.getValue().getImaginary());
						   
						   //System.out.println(" trans bounary bus equiv load = "+transBoundaryBus.getLoadPQ().toString());
						  
					   }
					   
				   }
			
		    	     
		    	 
		    	  
		    	  /*
		    	   *  10. solve the positive sequence power flow and the negative and zero sequence networks
		    	   */
		    	   
				      
				      // run positive sequence power flow
				      if(!transLfAlgo.loadflow()){
				    	  throw new Error(" positive sequence power flow for the transmission system is not converged");
				      }
				      
				      //update the positive sequence voltage of the boundary buses for checking the convergence later
				      for(Entry<String, Complex3x1> e : transBoundaryBus3SeqVoltages.entrySet()){
				    	  e.getValue().b_1= transmissionNet.getBus(e.getKey()).getVoltage();
				      }
				      
				    
				    /*
				     *  11. check convergence of the iteration by monitoring all the boundary buses
				     */
				      
				      this.pfFlag = true;
				      
				      for( Entry<String, Complex3x1> e : transBoundaryBus3SeqVoltages.entrySet()){
				    	  if(i>0){
				    		  if(this.lastStepTransBoundaryBus3SeqVoltages.get(e.getKey()).subtract(e.getValue()).absMax() > this.tolerance){
				    			  this.pfFlag = false;
				    			  System.out.println("i = "+i+" TDPF not converge!");
				    			  System.out.println("Last step transmission system boundary bus 3 seq voltage: \n"+e.getKey() +","+this.lastStepTransBoundaryBus3SeqVoltages.get(e.getKey()));
				    			  System.out.println("Current step transmission system boundary bus 3 seq voltage: \n"+e.getKey() +","+e.getValue());
				    			  log.warn("i = {} TDPF not converge!", i);
				    			  log.debug("Last step transmission system boundary bus 3 seq voltage: {} {}", e.getKey(), this.lastStepTransBoundaryBus3SeqVoltages.get(e.getKey()));
				    			  log.debug("Current step transmission system boundary bus 3 seq voltage: {} {}", e.getKey(), e.getValue());
				    		      
				    		  }
				    	  }
				    	  this.lastStepTransBoundaryBus3SeqVoltages.put(e.getKey(), e.getValue().clone());
				      }
		    	     
				      if (i>0 && this.pfFlag) {
				    	  // taking into account the 1 iteration at the initialization stage
				    	  log.info(" Transmision&Distribution combined power flow converges after {} iterations.", (i+2));
				    	  // update the load flow convergence status
				    	  this.transmissionNet.setLfConverged(true);
				    	  
				    	  // remove the equivalent load from the boundary buses by setting bus as NON_LOAD bus
				    	  for(String boundaryId: transNetworkBoundaryBusIdList){
				    		  this.transmissionNet.getBus(boundaryId).setLoadCode(AclfLoadCode.NON_LOAD);
				    		  this.transmissionNet.getBus(boundaryId).setLoadP(0.0);
				    		  this.transmissionNet.getBus(boundaryId).setLoadQ(0.0);
				    	  }
				    	  
				    	  for(BaseAclfNetwork distNet: this.distNetList){
				    		  distNet.setLfConverged(true);
				    	  }
				    	  this.net.setLfConverged(true);
				    	  
				    	  break;
				      }
		    	  
		      }
		      
		// set the tie-line status back to be true after power flow      
//		if(pfFlag){
//			for(String tieLineId :this.subNetProcessor.getInterfaceBranchIdList()){
//				AclfBranch connectBranch = this.net.getBranch(tieLineId);
//				connectBranch.setStatus(true);
//			}
//		}
		
		return pfFlag;
	}
	
	private Hashtable<String, Complex3x1> updateDistBoundaryBus3SeqVoltTable(){
		
		for(Entry<String,Complex3x1> e: transBoundaryBus3SeqVoltages .entrySet()){
			   String transBoundaryBusId = e.getKey();
			   
			   String distBoundaryBusId = "";
			   if(transBoundaryBusId.contains("Dummy")){
				   distBoundaryBusId = transBoundaryBusId.replace("Dummy", "");
			   }
			   else
				   distBoundaryBusId = transBoundaryBusId+"Dummy";
			   
			   distBoundaryBus3SeqVoltages.put(distBoundaryBusId, e.getValue());
		}
		
		return distBoundaryBus3SeqVoltages;
		
		
	}
	
//	private boolean updateTransBoundary3SeqCurInjTable(){
//		for(Entry<String,Complex3x1> e: distBoundary3SeqCurInjTable.entrySet()){
//			   String distBoundaryBusId = e.getKey();
//			   
//			   String transBoundaryBusId = "";
//			   if(distBoundaryBusId.contains("Dummy")){
//				   transBoundaryBusId = distBoundaryBusId.replace("Dummy", "");
//			   }
//			   else
//				   transBoundaryBusId = distBoundaryBusId+"Dummy";
//			   
//			   if(transmissionNet.getBus(transBoundaryBusId) !=null){
//				   transBoundary3SeqCurInjTable.put(transBoundaryBusId, e.getValue());
//			   }
//			   else{
//				   System.out.println("The tranmission network boundary bus is not found, ID: "+transBoundaryBusId);
//				   return false;
//			   }
//			   
//	     }
//		return true;
//	}
//	
	public void setTransmissionNetwork(BaseAclfNetwork<? extends BaseAclfBus, ?extends AclfBranch> net){
		this.transmissionNet = net;
	}
	public void setDistributionNetworkList(List<BaseAclfNetwork> distributionNetList){
		this.distNetList = distributionNetList;
	}
	
	public BaseAclfNetwork getTransmissionNetwork(){
		return this.transmissionNet;
	}
	
	public List<BaseAclfNetwork> getDistributionNetworkList(){
		return this.distNetList;
	}
	
    public LoadflowAlgorithm getTransLfAlgorithm(){
    	return this.transLfAlgo;
    }
    
    public void setDistLfTolerance(double distTol){
    	this.distTolerance = distTol;
    }
}
