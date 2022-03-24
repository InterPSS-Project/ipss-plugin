package org.interpss.threePhase.powerflow.impl;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.powerflow.DistributionPFMethod;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.abc.Static3PXformer;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.funcImpl.AclfNetHelper;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.net.OriginalDataFormat;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabilityNetwork;
import com.sun.org.apache.xerces.internal.dom.ParentNode;

public class DistributionPowerFlowAlgorithmImpl implements DistributionPowerFlowAlgorithm{

	private BaseAclfNetwork<? extends BaseAclfBus<? extends AclfGen, ? extends AclfLoad>,? extends AclfBranch> distNet = null;
	
	private DistributionPFMethod pfMethod = DistributionPFMethod.Forward_Backword_Sweep;
	
	private double tol = 1.0E-6;
	private int    maxIteration = 20;
	private boolean radialNetworkOnly = true;
	private boolean pfFlag =false;
	private Hashtable<String,Complex3x1> busVoltTable =null;
	private boolean initBusVoltagesEnabled = true;
	private boolean isAllPowerFlowConverged = false; 
	
	
	public DistributionPowerFlowAlgorithmImpl(){
		busVoltTable = new Hashtable<>();
		
	}
	   
    public DistributionPowerFlowAlgorithmImpl(BaseAclfNetwork net){
		this.distNet = net;
		busVoltTable = new Hashtable<>();
	}
	
	
	@Override
	public boolean orderDistributionBuses(boolean radialOnly) {
		Queue<DStab3PBus> onceVisitedBuses = new  LinkedList<>();
		
		// find the source bus, which is the swing bus for radial feeders;
		for(BaseAclfBus<?,?> b: distNet.getBusList()){
			    b.setIntFlag(0); // reset it as this will be used below.
				if(b.isActive() && b.isSwing()){
					onceVisitedBuses.add((DStab3PBus) b);
				}
		}
		
		//make sure all internal branches are unvisited
		for(AclfBranch bra:distNet.getBranchList()){
			bra.setBooleanFlag(false);
		}

		// perform BFS and set the bus sortNumber 
		BFS(onceVisitedBuses);
		
		
		distNet.setBusNumberArranged(true);

	   
		return true;
	}
	
	
	
    private void BFS (Queue<DStab3PBus> onceVisitedBuses){
    	int orderNumber = 0;
		//Retrieves and removes the head of this queue, or returns null if this queue is empty.
	    while(!onceVisitedBuses.isEmpty()){
			DStab3PBus  startingBus = onceVisitedBuses.poll();
			startingBus.setSortNumber(orderNumber++);
			startingBus.setBooleanFlag(true);
			startingBus.setIntFlag(2);
			
			if(startingBus!=null){
				  for(Branch connectedBra: startingBus.getBranchList()){
						if(connectedBra.isActive() && !connectedBra.isBooleanFlag()){
								Bus findBus = connectedBra.getOppositeBus(startingBus).get();
								
								//update status
								connectedBra.setBooleanFlag(true);
								
								//for first time visited buses
								if(findBus.getIntFlag()==0){
									findBus.setIntFlag(1);
									onceVisitedBuses.add((DStab3PBus) findBus);
									
								}
						}
				 }
			 
			}
			
	      }
	}

	@Override
	public boolean initBusVoltages() {
	
			
			for(BaseAclfBus b: distNet.getBusList()){
					DStab3PBus bus = (DStab3PBus) b;
					
					if(b.isSwing())
						bus.set3PhaseVotlages(getSwingBusThreePhaseVoltages(b.getVoltageMag(), b.getVoltageAng(UnitType.Deg)));
					else if(b.isGenPV()) 
						bus.set3PhaseVotlages(getPVBusThreePhaseVoltages(b.getVoltageMag()));
					else
					    bus.set3PhaseVotlages(getUnitThreePhaseVoltages());
					
			}

		return true;
	}
		
	private Complex phaseShiftCplxFactor(double shiftDeg){
			return new Complex(Math.cos(shiftDeg/180.0d*Math.PI),Math.sin(shiftDeg/180.0d*Math.PI));
	}
	
	private Complex3x1 getUnitThreePhaseVoltages(){
		return new Complex3x1(new Complex(1,0),new Complex(-Math.sin(Math.PI/6),-Math.cos(Math.PI/6)),new Complex(-Math.sin(Math.PI/6),Math.cos(Math.PI/6)));
	}
	
	private Complex3x1 getPVBusThreePhaseVoltages(double Vset){
		return new Complex3x1(new Complex(Vset,0),new Complex(-1*Vset*Math.sin(Math.PI/6),-1*Vset*Math.cos(Math.PI/6)),new Complex(-1*Vset*Math.sin(Math.PI/6),Vset*Math.cos(Math.PI/6)));
	}
	
	private Complex3x1 getSwingBusThreePhaseVoltages(double Vset, double angleDeg){
		return new Complex3x1(new Complex(Vset,0).multiply(phaseShiftCplxFactor(angleDeg)),
				new Complex(-1*Vset*Math.sin(Math.PI/6),-1*Vset*Math.cos(Math.PI/6)).multiply(phaseShiftCplxFactor(angleDeg)),
				new Complex(-1*Vset*Math.sin(Math.PI/6),Vset*Math.cos(Math.PI/6)).multiply(phaseShiftCplxFactor(angleDeg)));
	
	}

	@Override
	public boolean powerflow() {
         
		 this.isAllPowerFlowConverged = true;

		//step-1. check if there is any island in the system
		 AclfNetHelper helper = new AclfNetHelper(getNetwork());
		 List<String> islandBusList = new ArrayList<String>();
		 helper.calIslandBuses(islandBusList);
		 
		 // turn off single islanded bus
		 for(String busId: islandBusList) {
			 BaseAclfBus bus = (BaseAclfBus) this.distNet.getBus(busId);
			 if(bus.isActive() && bus.nActiveBranchConnected()==0)
				 bus.setStatus(false);
		 }
		 
		//step-2a. if the system is one single island, run it as a single Network 
		if(islandBusList.isEmpty()) {
			this.isAllPowerFlowConverged = powerflow_singleNet(this.distNet);
		}
		// step-2b. otherwise, create subnetworks for the islands and run power flow for each island
		else {
			 final BaseAclfNetwork originNetwork = this.distNet;
			 List<BaseAclfNetwork>  subNetworkList = createSubNetworkList(this.distNet, islandBusList);
			 
			 
			  
			 for(BaseAclfNetwork subnet: subNetworkList) {
				 
				    // check swing generator, if not assign one
				    helper = new AclfNetHelper(subnet);
					if (!helper.checkSwingRefBus())
						helper.assignSwingBusTurnOffIslandBus();
				 
				    // run power flow for each single island (as a subnetwork)
					if(!powerflow_singleNet(subnet)) {
						
						IpssLogger.getLogger().severe("Power flow does not converge in subnetwork #"+subnet.getId());
						return this.isAllPowerFlowConverged = false;
					}
		     }
			 //set the parent network load flow status as converged
			 this.setNetwork(originNetwork);
			 this.getNetwork().setLfConverged(true);
		}
		
		return this.isAllPowerFlowConverged;
	}
	
	private List<BaseAclfNetwork> createSubNetworkList(BaseAclfNetwork parentNetwork, List<String> list){
		List<BaseAclfNetwork> subNetList = new ArrayList<BaseAclfNetwork>();
		
		for (Object bus : parentNetwork.getBusList()) {
				// mark swing bus with intFlag = 0
			   BaseAclfBus aclfBus = (BaseAclfBus) bus;
			   if(aclfBus.isActive()) {
				   if(!list.contains(aclfBus.getId())) {
					   list.add(aclfBus.getId());
				   }
			   }
			   
		}
		
		int subNetIdx =0;
		for( String busId:list){
		    BaseAclfBus source = (BaseAclfBus) parentNetwork.getBus(busId);
			if(source.isActive() && !source.isBooleanFlag()){
				
				// for each iteration back to this layer, it means one subnetwork search is finished; subsequently, it is going to start
				// searching a new subnetwork. Thus, a new subnetwork object needs to be created first.
				BaseAclfNetwork subNet = null;
				
				if(parentNetwork instanceof DStabNetwork3Phase)
					subNet = ThreePhaseObjectFactory.create3PhaseDStabNetwork();
				
				else
					throw new UnsupportedOperationException("The network should be either  DStabNetwork3Phase or DStabilityNetwork type!");
				subNet.setId("SubNet-"+(subNetIdx+1));
				
				subNetList.add(subNet);
				
				subNet.addBus((BaseDStabBus<?,?>) source);
				
				DFS(parentNetwork, subNet,busId);
				subNetIdx++;
			}
		}
		
		return subNetList;
	}
	
	private boolean DFS(BaseAclfNetwork _net, BaseAclfNetwork _subNet, String busId) {
		boolean isToBus = true;
		
		Bus source = _net.getBus(busId);
		
		source.setBooleanFlag(true);
		
		
		//System.out.println("BusId, Name, kV: "+busId+","+source.getName()+","+source.getBaseVoltage()*0.001);
		
		for (Branch bra : source.getBranchList()) {
		
		  if (bra.isActive() && !bra.isGroundBranch() && bra instanceof AclfBranch) {
			isToBus = bra.getFromBus().getId().equals(busId);
			String nextBusId = isToBus ? bra.getToBus().getId() : bra.getFromBus().getId();
			
			if(_subNet.getBus(nextBusId)==null){
				BaseAclfBus bus = (BaseAclfBus) _net.getBus(nextBusId);
				_subNet.addBus(bus);
			}
		
			if (!bra.isBooleanFlag() ) { // fromBusId-->buId
				_subNet.addBranch((DStabBranch)bra, bra.getFromBus().getId(), bra.getToBus().getId() , bra.getCircuitNumber());
				
				bra.setBooleanFlag(true);
				
			    //DFS searching
			    DFS(_net,_subNet,nextBusId);
				
				}
			}
		}
	
	    return true;
	}
	
	private boolean powerflow_singleNet(BaseAclfNetwork distNet) {
		this.setNetwork(distNet);
		//step-1 order the network
		 pfFlag = orderDistributionBuses(radialNetworkOnly);
		
		
		//step-2 initialize bus voltage
		if(!pfFlag)
			try {
				throw new Exception("Error in odering the distribution buses");
			} catch (Exception e) {
				e.printStackTrace();
			}
		else{
			if(this.initBusVoltagesEnabled)
			pfFlag = this.initBusVoltages();
		}
			
		if(!pfFlag)
			try {
				throw new Exception("Error in iniitalizing the three-phase voltages of distribution buses");
			} catch (Exception e) {
				e.printStackTrace();
			}
		//step-3 applied a power flow solver forward/backward sweep algorithm. 
		if(this.pfMethod==DistributionPFMethod.Forward_Backword_Sweep){
			 
		        pfFlag =  FBSPowerflow(); 
		        if(pfFlag) {
		        	IpssLogger.getLogger().fine("The distribution network power flow is converged.");
		        }
			 }
		else{
			throw new UnsupportedOperationException("The power flow method is not supported yet:"+this.pfMethod);
		}
		
		this.distNet.setLfConverged(pfFlag);
       

		return pfFlag;
	}
	
	private boolean FBSPowerflow(){
		/*
		 * 1. Backward sweep:  calculate the current injections of buses starting the most remote bus and the current flows in
		 *  all active lines and transformers, all the way up to the source bus.
		 *    
		 * 2. convergence checking: ||deltaV|| < tolerance. 
		 * 
		 * 3. Forward  sweep:  update the voltages of the buses in the downstream  based on the voltages of the upstream bus and the current of the branch or transformer
		 * 
		 */
		
		
		
		for (int i=0;i<this.maxIteration;i++){
		
			for (Branch bra: this.distNet.getBranchList()){
				bra.setIntFlag(0);
			}
			
			for (Bus b: this.distNet.getBusList()){
				 b.setIntFlag(0);
			}
			
			//-----------------------------------------------------------------------
			//Step-1 backward sweep step
			//-----------------------------------------------------------------------
			
			for(int sortNum =this.distNet.getNoBus()-1;sortNum>0;sortNum--){
				BaseAclfBus bus = this.distNet.getBus(sortNum);
				if(bus==null){
					throw new Error(" The bus sort num # "+sortNum +" returns null bus object in distribution #"+this.distNet.getId());
					
				}
				if(bus.isActive()){
					DStab3PBus bus3P = null;
					if(bus instanceof DStab3PBus){
						bus3P = (DStab3PBus) bus;
					}
					else{
						throw new UnsupportedOperationException("The bus oject is not a 3phase type:"+bus.getId());
					}
						
					
					// update the non-visited branch current based on the bus current injection
					// and all the currents of all other connected down-stream branches of this bus
					
					/*
					 * The line modeling
					 * 
					 *   Vabc,m                                       Vabc,n
					 *   --|->Iabc,m--------|Zline|----------->Iabc,n---|----
					 *                |                 |
					 *             1/2ShuntY         1/2ShuntY
					 *                |                 |
					 *                _                 _
					 * 
					 */
					
					Complex3x1 sumOfBranchCurrents = new Complex3x1();
					String upStreamBranchId = "";
					String upStreamBusId="";
					int unvisitedBranchNum = 0;
					for (Branch bra: bus.getBranchList()){
						DStab3PBranch bra3P = (DStab3PBranch) bra;
						// all visited branches are on the downstream side, and there should be only one upstream branch
						if(bra.isActive() && bra.getIntFlag() ==1){
							
							if(bra.getFromBus().getId().equals(bus.getId())){
							 
								
							   sumOfBranchCurrents= sumOfBranchCurrents.add(bra3P.getCurrentAbcAtFromSide());
							}
							else{ 
								
								
								sumOfBranchCurrents= sumOfBranchCurrents.add(bra3P.getCurrentAbcAtToSide().multiply(-1.0));
							}
						}
						else if(bra.isActive() && bra.getIntFlag() ==0){
		
							 upStreamBranchId = bra.getId();
							 unvisitedBranchNum +=1;
							 bra.setIntFlag(1);	
							
						}
					    
					}
					
					
					//Error in the searching
					if(bus.getBranchList().size()==1 && unvisitedBranchNum !=1 && !bus.isSwing()){
						throw new Error(" There must be only one 'upstream' unvisited branch for an active, non-swing bus:"+bus.getId());
					}
					
//					if(bus.getId().equals("Bus2")){
//						System.out.println("processing bus 2");
//					}
					
					//else {
						
						// consider the existing bus current injection into the network from generators, loads, shunt capacitors, etc.
						Complex3x1 busSelfEquivCurInj3Ph =bus3P.calc3PhEquivCurInj();
						
						// add the branch current flows to obtain the current injections
						DStab3PBranch upStreamBranch = (DStab3PBranch) this.distNet.getBranch(upStreamBranchId);
						
						DStab3PBus upStreamBus3P = null;
						
						/*
						 * The line modeling
						 * 
						 *   Vabc,m                                       Vabc,n
						 *   --|->Iabc,m--------|Zline|----------->Iabc,n---|----
						 *                |                 |
						 *             1/2ShuntY         1/2ShuntY
						 *                |                 |
						 *                _                 _
						 * 
						 */
						
						// update the upstream branch current and the upstream bus voltage
						if(upStreamBranch != null && upStreamBranch.getFromBus().getId().equals(bus.getId())){
							
							//calculate and set the upstream branch current
							upStreamBranch.setCurrentAbcAtFromSide(busSelfEquivCurInj3Ph.subtract( sumOfBranchCurrents));
							
							upStreamBus3P = (DStab3PBus) upStreamBranch.getToBus();
							
							//calculate the voltages at the upstream end
							//NOTE: For, current flowing through the branch, the direction from bus -> to bus  is regarded as positive;
							Complex3x1 vabc = null;
							Complex3x1 iabc = null;
							
							
							// line 
							if(upStreamBranch.isLine()){
								vabc = upStreamBranch.getToBusVabc2FromBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										upStreamBranch.getToBusIabc2FromBusVabcMatrix().multiply(upStreamBranch.getCurrentAbcAtFromSide().multiply(-1)));
								
								//calculate the current injection at the upstream end
								
								iabc= upStreamBranch.getToBusVabc2FromBusIabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										upStreamBranch.getToBusIabc2FromBusIabcMatrix().multiply(upStreamBranch.getCurrentAbcAtFromSide().multiply(-1)));
								
							}
							
							// transformer
							else if (upStreamBranch.isXfr()){
								Static3PXformer xfr3p = upStreamBranch.to3PXformer();
								vabc = xfr3p.getLVBusVabc2HVBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										xfr3p.getLVBusIabc2HVBusVabcMatrix().multiply(upStreamBranch.getCurrentAbcAtFromSide().multiply(-1)));
								
								iabc= xfr3p.getLVBusVabc2HVBusIabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										xfr3p.getLVBusIabc2HVBusIabcMatrix().multiply(upStreamBranch.getCurrentAbcAtFromSide().multiply(-1)));
								
							}
							
							
							upStreamBranch.setCurrentAbcAtToSide(iabc.multiply(-1.0));
							
							if(upStreamBus3P.getIntFlag()==0 && !upStreamBus3P.isSwing()){
							   upStreamBus3P.set3PhaseVotlages(vabc);
							   upStreamBus3P.setIntFlag(1);
							}
						}
						else{
							upStreamBranch.setCurrentAbcAtToSide(sumOfBranchCurrents.subtract(busSelfEquivCurInj3Ph));
							
	                        upStreamBus3P = (DStab3PBus) upStreamBranch.getFromBus();
							
	                        //calculate the bus voltage at the upstream end
							Complex3x1 vabc = null;
							Complex3x1 iabc = null;
							
							// line 
							if(upStreamBranch.isLine()){
								vabc =	upStreamBranch.getToBusVabc2FromBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										upStreamBranch.getToBusIabc2FromBusVabcMatrix().multiply(upStreamBranch.getCurrentAbcAtToSide()));
								
	                            //calculate the current injection at the upstream end
								
								
								iabc = upStreamBranch.getToBusVabc2FromBusIabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										upStreamBranch.getToBusIabc2FromBusIabcMatrix().multiply(upStreamBranch.getCurrentAbcAtToSide()));
							}
							
							// transformer
							else if (upStreamBranch.isXfr()){
								Static3PXformer xfr3p = upStreamBranch.to3PXformer();
								
								vabc =	xfr3p.getLVBusVabc2HVBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										xfr3p.getLVBusIabc2HVBusVabcMatrix().multiply(upStreamBranch.getCurrentAbcAtToSide()));
								
	                            //calculate the current injection at the upstream end
								
								
								iabc =  xfr3p.getLVBusVabc2HVBusIabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
										xfr3p.getLVBusIabc2HVBusIabcMatrix().multiply(upStreamBranch.getCurrentAbcAtToSide()));
								
								
							}
							
							
							upStreamBranch.setCurrentAbcAtFromSide(iabc.multiply(1.0));
							
							
							if(upStreamBus3P.getIntFlag()==0 && !upStreamBus3P.isSwing()){
								   upStreamBus3P.set3PhaseVotlages(vabc);
								   upStreamBus3P.setIntFlag(1);
							}
							
						}
						
					//}
						
				}
			}
			
			
			//-----------------------------------------------------------------------
			//Step-2 check convergence.
			//-----------------------------------------------------------------------
			
			// compare the voltage results of the last two steps
			
				
			double mis = 0;
			this.pfFlag =true;
			for(BaseAclfBus bus: this.distNet.getBusList()){ 
				if(bus.isActive()){
					DStab3PBus bus3P = (DStab3PBus) bus;
					if(i>=1){
						mis=bus3P.get3PhaseVotlages().subtract(busVoltTable.get(bus3P.getId())).absMax();
						if(mis>this.getTolerance()){
							this.pfFlag = false;
						}
					}
					busVoltTable.put(bus3P.getId(), bus3P.get3PhaseVotlages());
				 }
			}
			
			// power flow is converged, break the outer iteration and return
			if(i>0 && this.pfFlag) {
				System.out.println("\n\nDistribution power flow converged, iterations = "+i+"\n");
				
				
				calcSwingBusGenPower();
				
				break;
			}
			
			
			//-----------------------------------------------------------------------
			//Step-3 :forward sweep step
			//-----------------------------------------------------------------------
			
			for(int sortNum2 = 0;sortNum2<this.distNet.getNoBus();sortNum2++){
				
				BaseAclfBus bus = this.distNet.getBus(sortNum2);
				
				if(bus.isActive()){
					// update the bus state, with intFlag =2 meaning this bus voltage has been updated 
					bus.setIntFlag(2);
					DStab3PBus bus3P = (DStab3PBus) bus;
					for(Branch bra:bus.getBranchList()){
						
						if(bra.isActive()){
							DStab3PBranch bra3Phase = (DStab3PBranch) bra;
							
							DStab3PBus downStreamBus = null;
							downStreamBus = (DStab3PBus) bra.getOppositeBus(bus).get();
							
							if(downStreamBus.getIntFlag()<2){
								Complex3x1 vabc = null;
								if(bra.isFromBus(bus)){
									
									 //calculate the bus voltage at the downstream end
									//  the current flow definition is align with the up/downstream definition
									//  which is the same as the definition in Dr.Kersting's book
									if(bra3Phase.isLine()){
									
									   vabc =  bra3Phase.getFromBusVabc2ToBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).subtract(
											bra3Phase.getToBusIabc2ToBusVabcMatrix().multiply(bra3Phase.getCurrentAbcAtToSide())); 
									}
									else if (bra3Phase.isXfr()){
										Static3PXformer xfr3p = bra3Phase.to3PXformer();
										vabc =  xfr3p.getHVBusVabc2LVBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).subtract(
												xfr3p.getLVBusIabc2LVBusVabcMatrix().multiply(bra3Phase.getCurrentAbcAtToSide()));
									}
							
								}
								else{
									
									 //calculate the bus voltage at the downstream end
									
									
									//TODO   Positive current direction definition:
									//     upstream  |--<-Iabc,To-------Zline-----<--Iabc,from---| downstream
									//   because the current flow definition is opposite to the up/downstream definition
									//   the subtract() operation has been changed to add() in the following calculation
									//   also the current is measured at the downstream side, which is the fromside
									
									if(bra3Phase.isLine()){
										vabc =  bra3Phase.getFromBusVabc2ToBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
												bra3Phase.getToBusIabc2ToBusVabcMatrix().multiply(bra3Phase.getCurrentAbcAtFromSide())); 
									}
									else if (bra3Phase.isXfr()){
										Static3PXformer xfr3p = bra3Phase.to3PXformer();
										
										vabc =  xfr3p.getHVBusVabc2LVBusVabcMatrix().multiply(bus3P.get3PhaseVotlages()).add(
												xfr3p.getLVBusIabc2LVBusVabcMatrix().multiply(bra3Phase.getCurrentAbcAtFromSide())); 
									}
								}
								
								downStreamBus.set3PhaseVotlages(vabc);
								downStreamBus.setIntFlag(2);
							}
						}
					}
					
					
				}
				
			}
			
		
		}
		
		
		
		return this.pfFlag;
		
		
	}

	public void calcSwingBusGenPower() {
		// update the swing bus generation output based on the converged power flow result
		
		for(BaseAclfBus<? extends AclfGen, ? extends AclfLoad> bus: this.distNet.getBusList()){ 
			if(bus.isActive() && bus.isSwing()){
				DStab3PBus bus3p = (DStab3PBus) bus;
				Complex3x1 sumOfBranchCurrents = new Complex3x1();
				
				sumOfBranchCurrents = bus3p.calcLoad3PhEquivCurInj().multiply(-1);
				
				for (Branch bra: bus.getBranchList()){
					if(bra.isActive()){
					    if(bra instanceof DStab3PBranch){
					
						DStab3PBranch bra3P = (DStab3PBranch) bra;
						// all visited branches are on the downstream side, and there should be only one upstream branch
						
							
							if(bra.getFromBus().getId().equals(bus.getId())){
							 
								
							   sumOfBranchCurrents= sumOfBranchCurrents.add(bra3P.getCurrentAbcAtFromSide());
							}
							else{ 
								
								
								sumOfBranchCurrents= sumOfBranchCurrents.add(bra3P.getCurrentAbcAtToSide().multiply(-1.0));
							}
						}
					    else{
							throw new Error("The branch is not a three-phase type #"+bra.getId() );
						}
					}
					
			    }
				
				Complex posGenPQ = bus3p.getThreeSeqVoltage().b_1.multiply(sumOfBranchCurrents.to012().b_1.conjugate());
				if(bus.getContributeGenList().size()>0){
				   bus.getContributeGenList().get(0).setGen(posGenPQ);
				
				}
			}
		}
	}
	
	

	@Override
	public DistributionPFMethod getPFMethod() {

		return this.pfMethod;
	}

	@Override
	public void setTolerance(double tolerance) {
		this.tol = tolerance;
		
	}

	@Override
	public double getTolerance() {
		
		return this.tol;
	}

	@Override
	public void setMaxIteration(int maxIterNum) {
		this.maxIteration = maxIterNum;
		
	}

	@Override
	public int getMaxIteration() {
		
		return this.maxIteration;
	}

	@Override
	public BaseAclfNetwork getNetwork() {
		
		return this.distNet;
	}

	@Override
	public void setNetwork(BaseAclfNetwork net) {
		this.distNet = net;
		
	}

	@Override
	public void setInitBusVoltageEnabled(boolean enableInitBus3PhaseVolts) {
		this.initBusVoltagesEnabled = enableInitBus3PhaseVolts;
		
	}

	@Override
	public boolean isInitBusVoltageEnabled() {
		
		return this.initBusVoltagesEnabled;
	}
    
	
}
