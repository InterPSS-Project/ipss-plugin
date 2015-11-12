package org.ipss.multiNet.algo.powerflow;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.ipss.multiNet.algo.SubNetworkProcessor;
import org.ipss.threePhase.basic.Branch3Phase;
import org.ipss.threePhase.basic.Bus3Phase;
import org.ipss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.ipss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.CoreObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.BaseAcscNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.Branch;

public class TDMultiNetPowerflowAlgorithm {
	
	protected BaseAclfNetwork<? extends AclfBus, ?extends AclfBranch> net = null;
	protected BaseAclfNetwork<? extends AclfBus, ?extends AclfBranch> transmissionNet = null;
	protected List<BaseAclfNetwork> distNetList = null;
	protected SubNetworkProcessor subNetProcessor = null;
	protected Hashtable<String,String> distNetId2BoundaryBusTable = null;
	protected Hashtable<String,Complex3x1> distBoundary3SeqCurInjTable = null;
	protected Hashtable<String,Complex3x1> transBoundary3SeqCurInjTable = null;
	protected Hashtable<String,Complex3x1> distBoundaryBus3SeqVoltages = null;
	protected Hashtable<String,Complex3x1> transBoundaryBus3SeqVoltages = null;
	
	protected Hashtable<String,Complex> distBoundaryPosSeqPowerTable = null;
	protected List<String> transNetworkBoundaryBusIdList = null;
	
	
	
	protected boolean pfFlag = true;
	protected int iterationMax = 20;
	protected double tolerance = 1.0E-7;
	
	private Hashtable<String,Complex3x1> lastStepTransBoundaryBus3SeqVoltages = null;
	
	
	public TDMultiNetPowerflowAlgorithm(BaseAclfNetwork<? extends AclfBus, ?extends AclfBranch> tdNet, 
			SubNetworkProcessor subNetProc) {
		this.net = tdNet;
		subNetProcessor = subNetProc;
		distNetId2BoundaryBusTable = new Hashtable<>();
		
		distBoundary3SeqCurInjTable = new Hashtable<>();
		distBoundaryPosSeqPowerTable = new Hashtable<>();
		transBoundary3SeqCurInjTable = new Hashtable<>();
		
		distBoundaryBus3SeqVoltages = new Hashtable<>();
		transBoundaryBus3SeqVoltages = new Hashtable<>();
		
		transNetworkBoundaryBusIdList  = new ArrayList<>();
		
		lastStepTransBoundaryBus3SeqVoltages  = new Hashtable<>();
		
		this.transmissionNet = subNetProc.getExternalSubNetwork();
		
		this.distNetList = new  ArrayList<>();
		for(String id:subNetProc.getInternalSubNetBoundaryBusIdList()){
			BaseAclfNetwork distNet = subNetProc.getSubNetworkByBusId(id);
			if(!this.distNetList.contains(distNet))
			  this.distNetList.add(distNet);
		}
		
		
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
		for(BaseAclfNetwork distNet:this.distNetList){
			List<String> boundaryList = subNetProcessor.getSubNet2BoundaryBusListTable().get(distNet.getId());
			
			if(boundaryList.size()!=1){
				throw new Error(" Only one source bus for a distribution system is supported!");
			}
			else{
				AclfBus sourceBus = (AclfBus) distNet.getBus(boundaryList.get(0));
				
				distNetId2BoundaryBusTable.put(distNet.getId(), sourceBus.getId());
				
				sourceBus.setGenCode(AclfGenCode.SWING);
				sourceBus.setVoltage(new Complex(1.0,0));
				
				DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);

			
				if(!distPFAlgo.powerflow()){
					throw new Error("Distribution system power flow is NOT converged! # "+distNet.getId());
				}
				
				Complex3x1 currInj3Phase = new Complex3x1();
				
				for(Branch bra: sourceBus.getConnectedPhysicalBranchList()){
					if(bra.isActive()){
						Branch3Phase acLine = (Branch3Phase) bra;
						
						//NOTE the positive sign of branch current flow is fromBus->ToBus  
						if(bra.getFromBus().getId().equals(sourceBus.getId())){
							currInj3Phase = currInj3Phase.add(acLine.getCurrentAbcAtFromSide().multiply(-1));
						}
						else{
							currInj3Phase = currInj3Phase.add(acLine.getCurrentAbcAtToSide());
						}
					}
				}
				
				// save the three-sequence current injection to the table
				Complex3x1 currInj3Seq = currInj3Phase.To012();
				
				System.out.println("3seq current injection"+currInj3Seq.toString());
				distBoundary3SeqCurInjTable.put(sourceBus.getId(), currInj3Phase.To012());
				
				Bus3Phase sourceBus3Ph = (Bus3Phase) sourceBus; 
				
				Complex posSeqPower = sourceBus3Ph.getThreeSeqVoltage().b_1.multiply(currInj3Seq.b_1.conjugate());
				
				System.out.println("pos seq power = "+posSeqPower.toString());
				
				distBoundaryPosSeqPowerTable.put(sourceBus.getId(), posSeqPower);
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
		
		   
		
		   for(Entry<String,Complex> e: distBoundaryPosSeqPowerTable.entrySet()){
			   String distBoundaryBusId = e.getKey();
			   
			   String transBoundaryBusId = "";
			   if(distBoundaryBusId.contains("Dummy")){
				   transBoundaryBusId = distBoundaryBusId.replace("Dummy", "");
			   }
			   else
				   transBoundaryBusId = distBoundaryBusId+"Dummy";
			   
			   AclfBus transBoundaryBus = this.transmissionNet.getBus(transBoundaryBusId);
			   
			   if(transBoundaryBus == null){
				   throw new Error("The tranmission network boundary bus is not found, ID: "+transBoundaryBusId);
			   }
			   else{
				   //TODO assuming there is no loads at the boundary bus
				   if(transBoundaryBus.getLoadPQ().abs()>0.0){
					   throw new Error("The  boundary bus in the tranmission network cannot be a load bus: "+transBoundaryBusId);
				   }
				   
				   // represent the power flow into the boundary bus as "negative" load
				   transBoundaryBus.setLoadPQ(e.getValue().multiply(-1.0));
				   transBoundaryBus.setLoadCode(AclfLoadCode.CONST_P);
				   transNetworkBoundaryBusIdList.add(transBoundaryBusId);
			   }
			   
		   }
	
		   /*
		    * 6. Run positive sequence power flow 
		    */
		
		      LoadflowAlgorithm transLfAlgo = CoreObjectFactory.createLoadflowAlgorithm(transmissionNet);
		      if(!transLfAlgo.loadflow()){
		    	  throw new Error(" positive sequence power flow for the transmission system is not converged");
		      }
		   
		   /*
		    * 7. initialize the negative and zero sequence of the transmission system
		    *    and create a sequence network solver, and solve these two sequence networks
		    */
		      
		      
		      SequenceNetworkSolver seqNetSolver = new SequenceNetworkSolver(
		    		  (BaseAcscNetwork<? extends AcscBus, ? extends AcscBranch>) transmissionNet,
		    		  transNetworkBoundaryBusIdList.toArray(new String[]{""}));
		      
		      
		      updateTransBoundary3SeqCurInjTable();
		      
		     
		      transBoundaryBus3SeqVoltages = seqNetSolver.solveNegZeroSeqNetwork(transBoundary3SeqCurInjTable);
		      
		      
		      
		      
		      
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
		    		  Bus3Phase sourceBus3Ph = (Bus3Phase) distNet.getBus(distNetId2BoundaryBusTable.get(distNet.getId()));
		    		  
		    		  Complex3x1 vabc = this.distBoundaryBus3SeqVoltages.get(sourceBus3Ph.getId()).ToAbc();
		    		  
		    		  System.out.println("updated dist source bus vabc = "+vabc);
		    		  sourceBus3Ph.set3PhaseVoltages(vabc);
		    		  
		    		  DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
                      
		    		  // use the voltages obtained from the last step and transmission system power flow results
		    		  distPFAlgo.setInitBusVoltageEnabled(false);
		    		  
						if(!distPFAlgo.powerflow()){
							throw new Error("Distribution system power flow is NOT converged! # "+distNet.getId());
						}
						
					
						Complex3x1 currInj3Phase = new Complex3x1();
						
						for(Branch bra: sourceBus3Ph.getConnectedPhysicalBranchList()){
							if(bra.isActive()){
								Branch3Phase acLine = (Branch3Phase) bra;
								if(bra.getFromBus().getId().equals(sourceBus3Ph.getId())){
									currInj3Phase = currInj3Phase.add(acLine.getCurrentAbcAtFromSide().multiply(-1));
								}
								else{
									currInj3Phase = currInj3Phase.add(acLine.getCurrentAbcAtToSide());
								}
							}
						}
						
						// save the three-sequence current injection to the table
						Complex3x1 currInj3Seq = currInj3Phase.To012();
						distBoundary3SeqCurInjTable.put(sourceBus3Ph.getId(), currInj3Phase.To012());
						
						
						
						Complex posSeqPower = sourceBus3Ph.getThreeSeqVoltage().b_1.multiply(currInj3Seq.b_1.conjugate());
						
						distBoundaryPosSeqPowerTable.put(sourceBus3Ph.getId(), posSeqPower);
		    		  
		    	  }
			   
			       /*
			        *  9.  represent the positive sequence current injections as constant power load
			        *      in positive sequence power flow, while the negative and zero sequence current components 
			        *      are still represented as current source in the network solution
			        */
		    	  
		    	  // update the three-sequence current injection on the transmission side
		    	  updateTransBoundary3SeqCurInjTable();
		    	  
		    	  
		    	  // update the positive sequence equivalent load
		    	  for(Entry<String,Complex> e: distBoundaryPosSeqPowerTable.entrySet()){
					   String distBoundaryBusId = e.getKey();
					   
					   String transBoundaryBusId = "";
					   if(distBoundaryBusId.contains("Dummy")){
						   transBoundaryBusId = distBoundaryBusId.replace("Dummy", "");
					   }
					   else
						   transBoundaryBusId = distBoundaryBusId+"Dummy";
					   
					   AclfBus transBoundaryBus = this.transmissionNet.getBus(transBoundaryBusId);
					   
					   if(transBoundaryBus == null){
						   throw new Error("The tranmission network boundary bus is not found, ID: "+transBoundaryBusId);
					   }
					   else{
						   
						   // UPDATE THE EQUIVALENT LOAD VALUE
						   transBoundaryBus.setLoadPQ(e.getValue().multiply(-1));
						   
						   System.out.println(" trans bounary bus equiv load = "+transBoundaryBus.getLoadPQ().toString());
						  
					   }
					   
				   }
			
		    	     
		    	 
		    	  
		    	  /*
		    	   *  10. solve the positive sequence power flow and the negative and zero sequence networks
		    	   */
		    	      transLfAlgo.setTolerance(1.0E-7);
				      if(!transLfAlgo.loadflow()){
				    	  throw new Error(" positive sequence power flow for the transmission system is not converged");
				      }
				   
				      transBoundaryBus3SeqVoltages = seqNetSolver.solveNegZeroSeqNetwork(transBoundary3SeqCurInjTable);
				      
				      
				    /*
				     *  11. check convergence of the iteration by monitoring all the boundary buses
				     */
				      
				      this.pfFlag = true;
				      
				      for( Entry<String, Complex3x1> e : transBoundaryBus3SeqVoltages.entrySet()){
				    	  if(i>0){
				    		  if(this.lastStepTransBoundaryBus3SeqVoltages.get(e.getKey()).subtract(e.getValue()).
				    				  absMax() >this.tolerance)
				    			  this.pfFlag = false;
				    	  }
				    	  this.lastStepTransBoundaryBus3SeqVoltages.put(e.getKey(), e.getValue());
				      }
		    	     
				      if (i>0 && this.pfFlag) {
				    	  IpssLogger.getLogger().info(" Transmision&Distribution combined power flow converges after " + i +" iterations.");
				    	  System.out.println(" Transmision&Distribution combined power flow converges after " + (i+1) +" iterations.");
				    	  // update the load flow convergence status
				    	  this.transmissionNet.setLfConverged(true);
				    	  
				    	  for(BaseAclfNetwork distNet: this.distNetList){
				    		  distNet.setLfConverged(true);
				    	  }
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
	
	private boolean updateTransBoundary3SeqCurInjTable(){
		for(Entry<String,Complex3x1> e: distBoundary3SeqCurInjTable.entrySet()){
			   String distBoundaryBusId = e.getKey();
			   
			   String transBoundaryBusId = "";
			   if(distBoundaryBusId.contains("Dummy")){
				   transBoundaryBusId = distBoundaryBusId.replace("Dummy", "");
			   }
			   else
				   transBoundaryBusId = distBoundaryBusId+"Dummy";
			   
			   if(transmissionNet.getBus(transBoundaryBusId) !=null){
				   transBoundary3SeqCurInjTable.put(transBoundaryBusId, e.getValue());
			   }
			   else{
				   System.out.println("The tranmission network boundary bus is not found, ID: "+transBoundaryBusId);
				   return false;
			   }
			   
	     }
		return true;
	}
	
	public void setTransmissionNetwork(BaseAclfNetwork<? extends AclfBus, ?extends AclfBranch> net){
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
	

}
