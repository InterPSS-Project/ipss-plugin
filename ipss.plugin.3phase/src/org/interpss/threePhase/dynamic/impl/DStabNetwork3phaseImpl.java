package org.interpss.threePhase.dynamic.impl;

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static org.interpss.threePhase.util.ThreePhaseUtilFunction.threePhaseGenAptr;
import static org.interpss.threePhase.util.ThreePhaseUtilFunction.threePhaseInductionMotorAptr;

import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplexMatrix3x3;
import org.interpss.threePhase.basic.DStab3PBranch;
import org.interpss.threePhase.basic.DStab3PBus;
import org.interpss.threePhase.basic.Gen3Phase;
import org.interpss.threePhase.basic.Load3Phase;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.model.DStabGen3PhaseAdapter;
import org.interpss.threePhase.dynamic.model.DynLoadModel1Phase;
import org.interpss.threePhase.dynamic.model.DynLoadModel3Phase;
import org.interpss.threePhase.util.ThreeSeqLoadProcessor;

import com.interpss.common.datatype.Constants;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.BaseAcscBus;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.net.NetworkType;
import com.interpss.core.sparse.impl.SparseEqnComplexMatrix3x3Impl;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.device.DynamicBusDevice;
import com.interpss.dstab.dynLoad.DynLoadModel;
import com.interpss.dstab.impl.BaseDStabNetworkImpl;

public class DStabNetwork3phaseImpl extends BaseDStabNetworkImpl<DStab3PBus, DStab3PBranch> implements DStabNetwork3Phase {
    
	protected ISparseEqnComplexMatrix3x3 yMatrixAbc = null;
	protected boolean is3PhaseNetworkInitialized = false;
	protected Hashtable<String, Complex3x1> threePhaseCurInjTable = null;
	private boolean isLoadModelConverted = false;

	
	@Override
	public boolean initThreePhaseFromLfResult() {

		/*  
		 * initialize the bus phase voltages. 
		 *   Special attentions need to be paid to the buses within the subtransmission and the distribution system, if any, 
		 *   connected to the LV side of Delta/Yg connected step-down transformer 
		 *   
		 *   steps:
		 *   
		 *   (1) initialization by setting the visited status false
		 *   (2) search the step-down delta/Y connected transformers
		 *   (3) starting from the low voltage side of the transformers, set the phase voltage with 30 deg lagging w.r.t the positive sequenc voltage, 
		 *      by assuming the connection meeting the  U.S. Delta connection standard, with high voltage side leading 30 degree, always 
	     *      Finally, set the visited attribute to be true.
	     *   (4) iterate over all step-down transformers and the connected subtransmissions 
	     *   (5) for the rest of the buses, set the phase votlages directly based on the positive sequence voltage
	     *
	     */
		
		//step (1)
		for(AcscBranch bra: this.getBranchList()){
			bra.setBooleanFlag(false);
		}
		for(Bus b: this.getBusList()){
			b.setBooleanFlag(false);
			b.setIntFlag(0);
		}
		
		double phaseShiftDeg = 0;
		
		for(AcscBranch bra: this.getBranchList()){
			if(bra.isActive() && bra.isXfr()){
				if((isDeltaConnected(bra.getXfrFromConnectCode()) && 
						!isDeltaConnected(bra.getXfrToConnectCode()))
						
						||
						(!isDeltaConnected(bra.getXfrFromConnectCode()) &&
						    isDeltaConnected(bra.getXfrToConnectCode()))){
					
					 bra.setBooleanFlag(true);
					 
                     //NOTE When Delta connection is on the low voltage side (step up), such as the case of Generation connection
					 // all buses on the low side should be shifted -30 deg. On the hand, if the the Delta Connection is on the high
					 // voltage side, the low voltage side should be shifted - 30 deg.
					 // 
					 phaseShiftDeg = -30;
					 DStab3PBus  StartingBus =null;
					 
					 //high voltage side leads 30 deg, always starts from the low voltage side
					 if(bra.getFromAclfBus().getBaseVoltage()>bra.getToAclfBus().getBaseVoltage()){
						 
						 StartingBus = (DStab3PBus) bra.getToAclfBus();
						 
						 if(isDeltaConnected(bra.getXfrFromConnectCode()))  { // if low voltage side is delta connected, then it is delta11
							//TODO fix the input xfr connect code issue
							 bra.setXfrFromConnectCode(XfrConnectCode.DELTA11);
						 }
					 }		
					 else {
					
						 StartingBus = (DStab3PBus) bra.getFromAclfBus();
						 if(isDeltaConnected(bra.getXfrToConnectCode())) { // if low voltage side is delta connected, then it is delta11
						
							 //TODO fix the input xfr connect code issue
							 bra.setXfrToConnectCode(XfrConnectCode.DELTA11);
						 }
					 }
					 
					    Complex vpos = StartingBus.getVoltage();
						Complex va = vpos.multiply(phaseShiftCplxFactor(phaseShiftDeg));
						Complex vb = va.multiply(phaseShiftCplxFactor(-120));
						Complex vc = va.multiply(phaseShiftCplxFactor(120));
						StartingBus.set3PhaseVoltages(new Complex3x1(va,vb,vc));
						StartingBus.setVoltage(StartingBus.getThreeSeqVoltage().b_1);
					 
					 Queue<DStab3PBus> q = new  LinkedList<DStab3PBus>();
				     q.add(StartingBus);
				     
				     BFSSubTransmission(phaseShiftDeg,q);
				}
			}
		}
		
		
		
		// initialize the phase voltages of those which are not set before, three-phase generation power output and load
		for(BaseAcscBus<?,?> b: this.getBusList()){
			
			if(b.isActive() && !b.isBooleanFlag()){
				   Complex vpos = b.getVoltage();
					Complex va = vpos;
					Complex vb = va.multiply(phaseShiftCplxFactor(-120));
					Complex vc = va.multiply(phaseShiftCplxFactor(120));
					((DStab3PBus) b).set3PhaseVoltages(new Complex3x1(va,vb,vc));
			}
				
			//initialize the 3p power output of generation;
			if(b.isGen()){
				for(AclfGen gen: b.getContributeGenList()){
					if(gen instanceof Gen3Phase){
						Gen3Phase ph3Gen = (Gen3Phase) gen;
						Complex phaseGen = gen.getGen();// phase gen and 3-phase gen are of the same value in PU
						ph3Gen.setPower3Phase(new Complex3x1(phaseGen,phaseGen,phaseGen), UnitType.PU);
					}
				}
			}
			
			// initialize the load 3-phase power
			if(b.isLoad()){
				for(AclfLoad load: b.getContributeLoadList()){
					if(load instanceof Load3Phase){
						Load3Phase ph3Load = (Load3Phase) load; 
						Complex phaseLoad = load.getLoad(b.getVoltageMag()); // phase load and 3-phase load are of the same value in PU
						
						ph3Load.set3PhaseLoad(new Complex3x1(phaseLoad,phaseLoad,phaseLoad));
					}
				}
			}
			
		}
		
		
		
		return is3PhaseNetworkInitialized= true;
	}
	
	private boolean isDeltaConnected(XfrConnectCode code){
		return code ==XfrConnectCode.DELTA ||
				code== XfrConnectCode.DELTA11;
	}
	
	private void BFSSubTransmission (double phaseShiftDeg, Queue<DStab3PBus> onceVisitedBuses){
		
		//Retrieves and removes the head of this queue, or returns null if this queue is empty.
	    while(!onceVisitedBuses.isEmpty()){
			DStab3PBus  startingBus = onceVisitedBuses.poll();
			startingBus.setBooleanFlag(true);
			startingBus.setIntFlag(2);
			
			if(startingBus!=null){
				  for(Branch connectedBra: startingBus.getBranchList()){
						if(connectedBra.isActive() && !connectedBra.isBooleanFlag()){
							try {
								Bus findBus = connectedBra.getOppositeBus(startingBus);
								
								//update status
								connectedBra.setBooleanFlag(true);
								
								//for first time visited buses
								
								if(findBus.getIntFlag()==0){
									findBus.setIntFlag(1);
									onceVisitedBuses.add((DStab3PBus) findBus);
									
									// update the phase voltage
									Complex vpos = ((BaseAclfBus)findBus).getVoltage();
									Complex va = vpos.multiply(phaseShiftCplxFactor(phaseShiftDeg));
									Complex vb = va.multiply(phaseShiftCplxFactor(-120.0d));
									Complex vc = va.multiply(phaseShiftCplxFactor(120.0d));
									
									((DStab3PBus) findBus).set3PhaseVoltages(new Complex3x1(va,vb,vc));
									 ((BaseAclfBus)findBus).setVoltage(((DStab3PBus) findBus).getThreeSeqVoltage().b_1);
								}
							} catch (InterpssException e) {
								
								e.printStackTrace();
							}
							
						}
				 }
			 
			}
			
	      }
	}
	
	private Complex phaseShiftCplxFactor(double shiftDeg){
		return new Complex(Math.cos(shiftDeg/180.0d*Math.PI),Math.sin(shiftDeg/180.0d*Math.PI));
	}

	@Override
	public ISparseEqnComplexMatrix3x3 formYMatrixABC() throws Exception {
		
		double yiiMinTolerance = 1.0E-8;
		
		// check if load model is converted
		if(!this.isLoadModelConverted )
			    convertLoadModel();
		
		yMatrixAbc = new SparseEqnComplexMatrix3x3Impl(getNoBus());
		
		for(BaseDStabBus b:this.getBusList()){
			if(b.isActive()){
				if(b instanceof DStab3PBus){
					int i = b.getSortNumber();
					DStab3PBus ph3Bus = (DStab3PBus) b;
					Complex3x3 yii = ph3Bus.getYiiAbc();
					// check if there is any Yii = 0.0 (abs(Yii) <1.0E-8)
					
				    if(yii.aa.abs()<yiiMinTolerance){
				    	yii.aa = new Complex(1.0,0);
				    	IpssLogger.getLogger().info("Bus : "+b.getId()+": abs of Yii.aa of is less than 1.0E-8, changed to 1.0 ");
				    }
				    
				    if(yii.bb.abs()<yiiMinTolerance){
				    	yii.bb = new Complex(1.0,0);
				    	IpssLogger.getLogger().info("Bus : "+b.getId()+": abs of Yii.bb of is less than 1.0E-8, changed to 1.0 ");
				    }
				    
				    if(yii.cc.abs()<yiiMinTolerance){
				    	yii.cc = new Complex(1.0,0);
				    	IpssLogger.getLogger().info("Bus : "+b.getId()+": abs of Yii.cc of is less than 1.0E-8, changed to 1.0 ");
				    }
										
					yMatrixAbc.setA( yii,i, i);
				}
				else
					throw new Exception("The processing bus # "+b.getId()+"  is not a threePhaseBus");
			}
		}
		
		for (AcscBranch bra : this.getBranchList()) {
			if (bra.isActive()) {
				if(bra instanceof DStab3PBranch){
					DStab3PBranch ph3Branch = (DStab3PBranch) bra;
					int i = bra.getFromBus().getSortNumber(),
						j = bra.getToBus().getSortNumber();
					yMatrixAbc.addToA( ph3Branch.getYftabc(), i, j );
					yMatrixAbc.addToA( ph3Branch.getYtfabc(), j, i );
				}
				else
					throw new Exception("The processing branch #"+bra.getId()+"  is not a threePhaseBranch");
			}
			
		}
		
		
		//TODO append the equivalent admittance of dynamic loads to YMatrixABC
		for (DStab3PBus bus3p : getBusList() ) {
			if(bus3p.isActive() && bus3p.isLoad()){

				Complex3x3 threePhasedynLoadEquivY = new Complex3x3();
				
				//TODO process three-phase dynamic loads
//                 if(bus3p.getThreePhaseDynLoadList().size()>0){
//					
//					for(DynLoadModel3Phase load3p:bus3p.getThreePhaseDynLoadList()){
//						if(load3p.isActive()){
//							threePhasedynLoadEquivY = threePhasedynLoadEquivY.add(load3p.getEquivYabc());
//						}
//					}
//                 }
				
				for(DynamicBusDevice dynDevice: bus3p.getDynamicBusDeviceList()){
                	if(dynDevice instanceof InductionMotor ){
                		DynLoadModel3Phase dynLoad3P = threePhaseInductionMotorAptr.apply((InductionMotor) dynDevice);
                		if(dynLoad3P.isActive()){
                    		//dynLoad3P.initStates();
                    		threePhasedynLoadEquivY = threePhasedynLoadEquivY.add(dynLoad3P.getEquivYabc());
                    	}
                	}
                	else if (dynDevice instanceof DynLoadModel3Phase){
                		DynLoadModel3Phase dynLoad3P = (DynLoadModel3Phase) dynDevice;
                		if(dynLoad3P.isActive()){
                    		//dynLoad3P.initStates();
                			threePhasedynLoadEquivY = threePhasedynLoadEquivY.add(dynLoad3P.getEquivYabc());
                    	}
                	}
				}
				
				//TODO process 1-phase dynamic loads on each phase
				Complex phaseAdynLoadEquivY = new Complex(0,0);
				Complex phaseBdynLoadEquivY = new Complex(0,0);
				Complex phaseCdynLoadEquivY = new Complex(0,0);
				
				if(bus3p.getPhaseADynLoadList().size()>0){
					
					for(DynLoadModel1Phase load:bus3p.getPhaseADynLoadList()){
						if(load.isActive()){
							phaseAdynLoadEquivY = phaseAdynLoadEquivY.add(load.getEquivY());

						}
					}
										
				}
				
                 if(bus3p.getPhaseBDynLoadList().size()>0){
					
					for(DynLoadModel1Phase load:bus3p.getPhaseBDynLoadList()){
						if(load.isActive()){
							phaseBdynLoadEquivY = phaseBdynLoadEquivY.add(load.getEquivY());
					
						}
					}
					
				}
                 
                 if(bus3p.getPhaseCDynLoadList().size()>0){
 					
					for(DynLoadModel1Phase load:bus3p.getPhaseCDynLoadList()){
						if(load.isActive()){
							phaseCdynLoadEquivY = phaseCdynLoadEquivY.add(load.getEquivY());
					
						}
					}
					
					
				}
				
                if(phaseAdynLoadEquivY.abs()>0 || phaseBdynLoadEquivY.abs()>0 || phaseCdynLoadEquivY.abs()>0 ){
                	Complex3x3 y = new Complex3x3(phaseAdynLoadEquivY,phaseBdynLoadEquivY,phaseCdynLoadEquivY) ;
                	
                	
                	yMatrixAbc.addToA(y, bus3p.getSortNumber(), bus3p.getSortNumber());
                }
                
                
               // Consider the equivalent Y of three-phase dynamic loads
                if(threePhasedynLoadEquivY.abs()>0){
                	yMatrixAbc.addToA(threePhasedynLoadEquivY, bus3p.getSortNumber(), bus3p.getSortNumber());
                }
                
                	
			}
		}
		
		
		
		
		setYMatrixDirty(true);
	
		return yMatrixAbc;
	}
	
	private void convertLoadModel() {
		for ( BaseDStabBus<?,?> busi : getBusList() ) {
			   //only the active buses will be initialized
				if(busi.isActive()){
					//init three sequence load
					ThreeSeqLoadProcessor.initEquivLoadY120(busi);
				}
		}
		this.isLoadModelConverted = true;
		
	}

	@Override
	public ISparseEqnComplexMatrix3x3 getYMatrixABC(){
		return this.yMatrixAbc;
	}

	@Override
	public boolean run3PhaseLoadflow() {
		throw new UnsupportedOperationException();
	}

	
	@Override
	public boolean solveNetEqn() {
  		try {
  			
  			if(isYMatrixDirty()){
	  			getYMatrixABC().factorization(Constants.Matrix_LU_Tolerance);
				setYMatrixDirty(false);
  			}
  			
		  	// Calculate and set generator injection current
			for( Bus b : getBusList()) {
				BaseDStabBus<?,?> bus = (BaseDStabBus<?,?>)b;

				if(bus.isActive()){
					DStab3PBus bus3p = (DStab3PBus) bus;
					Complex3x1 iInject = new Complex3x1();

					if(bus.getContributeGenList().size()>0){
						 for(AclfGen gen: bus.getContributeGenList()){
						      if(gen.isActive() && gen instanceof DStabGen){
						    	  DStabGen dynGen = (DStabGen)gen;
						    	  if( dynGen.getDynamicGenDevice()!=null){
						    		  DStabGen3PhaseAdapter gen3P = threePhaseGenAptr.apply(dynGen);
						    		  iInject = iInject.add(gen3P.getISource3Phase());
						    		 
						    		  //System.out.println("Iinj@Gen-"+dynGen.getId()+", "+iInject.toString());
						    	  }
						    	 
						       }
						  }
				    }
					//TODO 3-phase dynamic load list
					//if(bus3p.getp)
					if(bus3p.isLoad()){
						
						//// Phase A
						if(bus3p.getPhaseADynLoadList().size()>0){
							Complex iPhAInj = new Complex(0,0);
							
							for(DynLoadModel1Phase load1p:bus3p.getPhaseADynLoadList()){
								if(load1p.isActive()){
							        iPhAInj = iPhAInj.add(load1p.getNortonCurInj());
							       // System.out.println("Iinj@Load-"+bus3p.getId()+", "+ load1p.getId()+","+load1p.getCompensateCurInj().toString());
								}
							}
							
							if(iPhAInj.abs()>0.0)
								iInject.a_0 = iInject.a_0.add(iPhAInj);
						}
						
						// Phase B
						if(bus3p.getPhaseBDynLoadList().size()>0){
							Complex iPhBInj = new Complex(0,0);
							
							for(DynLoadModel1Phase load1p:bus3p.getPhaseBDynLoadList()){
								if(load1p.isActive()){
							        iPhBInj = iPhBInj.add(load1p.getNortonCurInj());
							       // System.out.println("Iinj@Load-"+bus3p.getId()+", "+ load1p.getId()+","+load1p.getCompensateCurInj().toString());
								}
							}
							
							if(iPhBInj.abs()>0.0)
								iInject.b_1 = iInject.b_1.add(iPhBInj);
						}
						
						// Phase C
						if(bus3p.getPhaseCDynLoadList().size()>0){
							Complex iPhCInj = new Complex(0,0);
							
							for(DynLoadModel1Phase load1p:bus3p.getPhaseCDynLoadList()){
								if(load1p.isActive()){
							        iPhCInj = iPhCInj.add(load1p.getNortonCurInj());
							       // System.out.println("Iinj@Load-"+bus3p.getId()+", "+ load1p.getId()+","+load1p.getCompensateCurInj().toString());
								}
							}
							
							if(iPhCInj.abs()>0.0)
								iInject.c_2 = iInject.c_2.add(iPhCInj);
						}
						
						//TODO three-phase dynamic loads
						
//						if(bus3p.getThreePhaseDynLoadList().size()>0){
//							for(DynLoadModel3Phase load3p:bus3p.getThreePhaseDynLoadList()){
//								if(load3p.isActive()){
//									iInject = iInject.add(load3p.getISource3Phase());
//								}
//							}
//						}
						
						 for(DynamicBusDevice dynDevice: bus.getDynamicBusDeviceList()){
							    if(dynDevice.isActive()){
		                        	if(dynDevice instanceof InductionMotor ){
		                        		DynLoadModel3Phase dynLoad3P = threePhaseInductionMotorAptr.apply((InductionMotor) dynDevice);
		                        		iInject = iInject.add(dynLoad3P.getISource3Phase());

		                        	}
		                        	else if (dynDevice instanceof DynLoadModel3Phase){
		                        		DynLoadModel3Phase dynLoad3P = (DynLoadModel3Phase) dynDevice;

		                        		iInject = iInject.add(dynLoad3P.getISource3Phase());

		                        	}
							    }

	                         }

					}
				  
				  if(iInject == null){
					  throw new Error (bus.getId()+" current injection is null");
				  }
				  
				  // add external/customized bus current injection
				  if(this.get3phaseCustomCurrInjTable()!=null){
					  if(this.get3phaseCustomCurrInjTable().get(bus.getId())!=null)
					    iInject = iInject.add(this.get3phaseCustomCurrInjTable().get(bus.getId()));
				  }

				  getYMatrixABC().setBi(iInject, bus.getSortNumber());
				}
			}
			
//			 ISparseEqnComplexMatrix3x3  Yabc = getYMatrixABC();
//			 System.out.println(Yabc.getSparseEqnComplex().toString());
		   
			getYMatrixABC().solveEqn();

			// update bus voltage and machine Pe
			for( Bus b : getBusList()) {
				BaseDStabBus bus = (BaseDStabBus)b;
				if(bus.isActive()){
					Complex3x1 vabc = getYMatrixABC().getX(bus.getSortNumber());
					//if(bus.getId().equals("Bus12"))
					//System.out.println("Bus, Vabc:"+b.getId()+","+vabc.toString());
					
					if(!vabc.a_0.isNaN() && !vabc.b_1.isNaN() && !vabc.c_2.isNaN()){
                    
						//if(bus instanceof Bus3Phase){
							DStab3PBus bus3P = (DStab3PBus) bus;
							 bus3P.set3PhaseVoltages(vabc);
							 
							 // update the positive sequence voltage
							 Complex v = bus3P.getThreeSeqVoltage().b_1;
							 bus.setVoltage(v);
							// System.out.println("posV @ bus :"+v.toString()+","+bus.getId());
							
                      //   }

					}
					else
						 throw new Error (bus.getId()+" solution voltage is NaN");
				}
			}
  			
  		} 
  		catch (IpssNumericException e) {
  			ipssLogger.severe(e.toString());
  			return false;
  		}
  	
		return true;
	}

	@Override
	public boolean initDStabNet() {
		boolean initFlag = true;
		IpssLogger.getLogger().info("Start three-phase DStabNetwork initialization...");
		
		
	  	//TODO this is a must step, otherwise the system cannot be initialized properly
		if(this.getNetworkType()==NetworkType.TRANSMISSION && !is3PhaseNetworkInitialized)
	  	     initThreePhaseFromLfResult();
		
		for ( BaseDStabBus<?,?> b : getBusList() ) {

			if( b instanceof DStab3PBus){
			    DStab3PBus bus =(DStab3PBus) b;

			   //only the active buses will be initialized
				if(b.isActive()){
					// set bus initial vaule 
					bus.setInitLoad(bus.getLoadPQ());
					bus.setInitVoltMag(bus.getVoltageMag());
					bus.setVoltage(bus.getThreeSeqVoltage().b_1); // make sure the positive sequence voltage is set up;
					
					//save the three-phase voltage
					bus.setThreePhaseInitVoltage(bus.get3PhaseVotlages());
					
					//1) init bus dynamic signal calculation, 
					// for example, bus Frequency measurement
					bus.initStates();
					
					
					//2) initialize the bus generator
					//TODO add the three-phase dynamic generation list to the bus
					bus.getContributeGenList().addAll(bus.getThreePhaseGenList());
					
					for(Object obj: bus.getContributeGenList()){
						AclfGen gen = (AclfGen)obj;
						if(gen.isActive() && gen instanceof DStabGen){
							DStabGen dynGen = (DStabGen) gen;
							//TODO 11/19/2015 consider generic generation models
							/*
							if(dynGen.getMach()!=null){
								dynGen.getMach().calMultiFactors();
							    if(!dynGen.getMach().initStates(bus))
								   initFlag = false;
							}
							*/
							
							if(dynGen.getDynamicGenDevice()!=null){
							    if(!dynGen.getDynamicGenDevice().initStates(bus))
								   initFlag = false;
							}
							
						}
					}
					
					
					//3) process the dynamic loads, for each load, subtract the portion of dynamic loads, including 
					// 3-phase dynamic loads and 1-phase dynamic loads from the total loads

						
					if(b.isLoad()){
							
						// first process the 3phase dynamic loads
						double totalDynLoadPercent = 0;
						Complex totalPosSeqDynLoadPQ = new Complex(0,0);
						
						if( b.getDynLoadModelList().size()>0){
							for(Object obj:bus.getDynLoadModelList()){
								DynLoadModel load = (DynLoadModel)obj;
								if(load.isActive()){
									totalDynLoadPercent += load.getLoadPercent(); 
							
								}
							}
							
							// check the value of totalDynLoadPercent, it must be <=100.0; otherwise rescale it down to 100.
							if(totalDynLoadPercent>100.0){
								ipssLogger.severe("The total dynamic loads accout for more than 100% of the bus load. Rescaled down to 100%");
								for(Object obj :bus.getDynLoadModelList()){
									DynLoadModel load = (DynLoadModel)obj;
									if(load.isActive()){
								       load.setLoadPercent(load.getLoadPercent()*100.0/totalDynLoadPercent);
									}
								}
								totalDynLoadPercent = 100.0;
							}
							
							// the init load is only available after initialization
							for(Object obj :bus.getDynLoadModelList()){
								DynLoadModel load = (DynLoadModel)obj;
								if(load.isActive()){
							       load.initStates();
							       totalPosSeqDynLoadPQ = totalPosSeqDynLoadPQ.add(load.getInitLoadPQ());
								}
							}

							
						}
						
						Complex orginalLoadPQ  = bus.getNetLoadResults();
						bus.setNetLoadResults(orginalLoadPQ.subtract(totalPosSeqDynLoadPQ));
						
						
						//TODO init the single-phase, three-phase dynamic loads
						
						
						// second, process the 1-phase dynamic loads
			
						
						Complex totalPhaseADynLoadPQ = new Complex(0,0);
						Complex totalPhaseBDynLoadPQ = new Complex(0,0);
						Complex totalPhaseCDynLoadPQ = new Complex(0,0);
						Complex3x1 total3PhaseDynLoadPQ = new Complex3x1();
                      
                        
                        //TODO check the total dynamic LOAD percentage
                        
                        
                        for(DynLoadModel1Phase dynLoadPA : bus.getPhaseADynLoadList()){
                        	if(dynLoadPA.isActive()){
                        		dynLoadPA.initStates();
                        		
                        		totalPhaseADynLoadPQ = totalPhaseADynLoadPQ.add(dynLoadPA.getInitLoadPQ()); 
                        	}
						}
                        
                        for(DynLoadModel1Phase dynLoadPB : bus.getPhaseBDynLoadList()){
                        	if(dynLoadPB.isActive()){
                        		dynLoadPB.initStates();
                        		
                        		totalPhaseBDynLoadPQ = totalPhaseBDynLoadPQ.add(dynLoadPB.getInitLoadPQ()); 
                        	}
						}
                        
                        
                        for(DynLoadModel1Phase dynLoadPC : bus.getPhaseCDynLoadList()){
                        	if(dynLoadPC.isActive()){
                        		dynLoadPC.initStates();
                        	
                        		totalPhaseCDynLoadPQ = totalPhaseCDynLoadPQ.add(dynLoadPC.getInitLoadPQ()); 
                        	}
						}
                        
                        
//                        for(DynLoadModel3Phase dynLoad3P : bus.getThreePhaseDynLoadList()){
//                        	if(dynLoad3P.isActive()){
//                        		dynLoad3P.initStates();
//                        		total3PhaseDynLoadPQ = total3PhaseDynLoadPQ.add(dynLoad3P.getInitLoadPQ3Phase());
//                        	}
//                        }
                        
                        //TODO comment out the threePhaesDynLoadList above to avoid double counting, as all the dynamic load models are already in the DynamicBusDeviceList()
                        for(DynamicBusDevice dynDevice: bus.getDynamicBusDeviceList()){
                        	if(dynDevice instanceof InductionMotor ){
                        		DynLoadModel3Phase dynLoad3P = threePhaseInductionMotorAptr.apply((InductionMotor) dynDevice);
                        		if(dynLoad3P.isActive()){
                            		dynLoad3P.initStates();
                            		total3PhaseDynLoadPQ = total3PhaseDynLoadPQ.add(dynLoad3P.getInitLoadPQ3Phase());
                            	}
                        	}	                        	
                            else if (dynDevice instanceof DynLoadModel3Phase){
                        		DynLoadModel3Phase dynLoad3P = (DynLoadModel3Phase) dynDevice;
                        		if(dynLoad3P.isActive()){
                            		dynLoad3P.initStates();
                            		total3PhaseDynLoadPQ = total3PhaseDynLoadPQ.add(dynLoad3P.getInitLoadPQ3Phase());
                            	}
                        	}

                         }                     
                        
                        // sum up the 1-phase and 3-phase dynamic loads
                        total3PhaseDynLoadPQ = total3PhaseDynLoadPQ.add(
                        		new Complex3x1(totalPhaseADynLoadPQ,totalPhaseBDynLoadPQ,totalPhaseCDynLoadPQ));

                        
                        if(bus.get3PhaseTotalLoad().abs()>0){
                        
                        	  bus.set3PhaseNetLoadResults(bus.get3PhaseTotalLoad().subtract(total3PhaseDynLoadPQ));
                        }
						

							
						// add the dynamic loads to dynamicBusDeviceList()
                        
                     // TODO this need to be fixed, as it causes duplication issues for adapter implementation of existing dynamic load models, such as inductionMotor3PhaseLoad
                     // because DynLoadModel extends from DynamicBusDevice, as the DynLoadModel.setDStabBus() method will create the bus-loadmodel containing relationship. 
						
                        bus.getDynamicBusDeviceList().addAll(bus.getDynLoadModelList());  //three-sequence based
						bus.getDynamicBusDeviceList().addAll(bus.getPhaseADynLoadList()); // single-phase based
						bus.getDynamicBusDeviceList().addAll(bus.getPhaseBDynLoadList()); // single-phase based
						bus.getDynamicBusDeviceList().addAll(bus.getPhaseCDynLoadList()); // single-phase based
						bus.getDynamicBusDeviceList().addAll(bus.getThreePhaseDynLoadList()); // three-phase based
				  
					}//end if-isLoad
				   
					
				}
				
				
				
			}
			//end of if- check if the processing bus is of Bus3Phase type
			else{
				throw new Error("The processing bus is not a Bus3Phase type # "+b.getId());
			}
		}
		
		//form the Ymatrix
		try {
			formYMatrixABC();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			initFlag = false;
		}
 
		if(initFlag) this.isInitialized = true;
		return initFlag;
	}

	@Override
	public boolean solvePosSeqNetEqn() {
//		if(this.getYMatrix()== null){
//			this.yMatrix = this.formYMatrix(SequenceCode.POSITIVE, false);
//			this.setYMatrixDirty(true);
//		}
		return  super.solveNetEqn();
	}
	
	@Override
	public boolean initPosSeqDStabNet() {
		
		return super.initDStabNet();
	}

	@Override
	public Hashtable<String, Complex3x1> get3phaseCustomCurrInjTable() {
		if(this.threePhaseCurInjTable ==null)
			this.threePhaseCurInjTable = new Hashtable<String, Complex3x1>();
		return this.threePhaseCurInjTable;
	}

	@Override
	public void set3phaseCustomCurrInjTable(
			Hashtable<String, Complex3x1> new3PhaseCurInjTable) {
		this.threePhaseCurInjTable = new3PhaseCurInjTable;
		
	}





}
