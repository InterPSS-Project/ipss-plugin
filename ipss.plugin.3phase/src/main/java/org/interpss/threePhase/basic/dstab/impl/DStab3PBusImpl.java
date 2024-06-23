package org.interpss.threePhase.basic.dstab.impl;

import static org.interpss.threePhase.util.ThreePhaseUtilFunction.threePhaseGenAptr;
import static org.interpss.threePhase.util.ThreePhaseUtilFunction.threePhaseInductionMotorAptr;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.dstab.dynLoad.InductionMotor;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.dstab.DStab1PLoad;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.dynamic.model.DStabGen3PhaseAdapter;
import org.interpss.threePhase.dynamic.model.DynLoadModel1Phase;
import org.interpss.threePhase.dynamic.model.DynLoadModel3Phase;
import org.interpss.threePhase.util.ThreeSeqLoadProcessor;

import com.interpss.core.abc.LoadConnectionType;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.net.Branch;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.device.DynamicBusDevice;
import com.interpss.dstab.impl.BaseDStabBusImpl;



public class DStab3PBusImpl extends BaseDStabBusImpl<DStab3PGen,DStab3PLoad> implements DStab3PBus {
	
	private Complex3x1 Vabc = null;
	private Complex3x1 initVabc = null;
	private Complex3x3 shuntYabc = null;
	Complex3x3 yiiAbc = new Complex3x3();
	
	private List<DynLoadModel1Phase> phaseADynLoadList;
	
	private List<DynLoadModel1Phase> phaseBDynLoadList;
	
	private List<DynLoadModel1Phase> phaseCDynLoadList;
	
	private List<DynLoadModel3Phase> threePhaseDynLoadList;
	
	private List<DStab1PLoad> singlePhaseLoadList = null;
	
	private List<DStab3PLoad> threePhaseLoadList = null;
	private List<DStab3PGen> threePhaseGenList = null;
	
	private Complex3x1 load3PhEquivCurInj = null;
	private Complex3x1 equivCurInj3Phase = null;
	
	private Complex3x1 netTotalLoad3Phase = null;
	
	private Complex3x1 totalLoad3Phase = null;
	
	private Complex3x1 staticTotalLoad3Phase = null;

	@Override
	public Complex3x1 get3PhaseVotlages() {
		
		return  this.Vabc;
	}

	@Override
	public void set3PhaseVotlages(Complex3x1 vabc) {
		this.Vabc = vabc;
		super.setThreeSeqVoltage(Complex3x1.abc_to_z12(Vabc));
		
	}
	
	@Override
	public Complex3x1 getThreeSeqVoltage() {
		  if(this.threeSeqVoltage.abs() ==0.0){
		    if(this.Vabc!=null)
			  this.threeSeqVoltage = Complex3x1.abc_to_z12(this.Vabc);
		    else
		    	this.threeSeqVoltage.b_1 = this.getVoltage();
		  }
		   return this.threeSeqVoltage;
	}

	@Override
	public void setThreeSeqVoltage(Complex3x1 v120) {
		super.setThreeSeqVoltage(v120);
		this.Vabc =Complex3x1.z12_to_abc(v120);  // all voltages are saved in three-phase, in order to make sure data consistency
	}

	@Override
	public Complex3x3 getYiiAbc() {
		//always start from zero
		yiiAbc = new Complex3x3();
		// contributions from the connected branches
		 for(Branch bra:this.getBranchList()) {
			 if(bra.isActive()){
				 if(bra instanceof DStab3PBranch){
					 DStab3PBranch thrPhBranch = (DStab3PBranch) bra; 
					 if(this.getId().equals(bra.getFromBus().getId()))
					    yiiAbc = yiiAbc.add(thrPhBranch.getYffabc());
					 else
						 yiiAbc = yiiAbc.add(thrPhBranch.getYttabc());
				 }
			 }
			 
		 }
   
        // the  shuntYabc
		 if(shuntYabc != null){
			 yiiAbc= yiiAbc.add(shuntYabc);
		 }
		
		//Switch shunt
		 
		 
		 
		 //the conventional three-sequence load definition, they need to be pre-processed using ThreeSeqLoadProcessor
		 
		 //TODO 06/16/2015
		 //EquivLoadYabc does not limit to load buses, otherwise buses with sequence shuntY cannot be correctly modeled
		 //ALSO NOTE: The contribution from the generators has been considered in the  <EquivLoadYabc> above   		    
		  
		 Complex3x3 equivLoadYabc = ThreeSeqLoadProcessor.getEquivLoadYabc(this);
		// System.out.println(this.getId()+"equivLoadYabc = \n"+equivLoadYabc.toString());
		 
		 yiiAbc= yiiAbc.add(equivLoadYabc);
		 
		
		     
		//TODO 11/19/2015 work on three-phase and single-phase loads
		//ONly consider the net Load after excluding the effects of dynamic loads
		 if(((BaseDStabNetwork)this.getNetwork()).isStaticLoadIncludedInYMatrix()) {
			 if(this.get3PhaseNetLoadResults() != null && this.get3PhaseNetLoadResults().abs() >0.0){
				 Complex3x1 initVoltABC = this.get3PhaseInitVoltage();
				 double va = initVoltABC.a_0.abs();
				 double vb = initVoltABC.b_1.abs();
				 double vc = initVoltABC.c_2.abs();
				 Complex ya = this.get3PhaseNetLoadResults().a_0.conjugate().divide(va*va);
				 Complex yb = this.get3PhaseNetLoadResults().b_1.conjugate().divide(vb*vb);
				 Complex yc = this.get3PhaseNetLoadResults().c_2.conjugate().divide(vc*vc);
				 
				 yiiAbc = yiiAbc.add(new Complex3x3(ya,yb,yc));
			 }
		 }


        
		

		return yiiAbc;
	}

	@Override
	public List<DynLoadModel1Phase> getPhaseADynLoadList() {
		
		if(this.phaseADynLoadList == null)
			this.phaseADynLoadList = new ArrayList<DynLoadModel1Phase>();
		return this.phaseADynLoadList;
	}

	@Override
	public List<DynLoadModel1Phase> getPhaseBDynLoadList() {
		if(this.phaseBDynLoadList == null)
			this.phaseBDynLoadList = new ArrayList<DynLoadModel1Phase>();
		
		return this.phaseBDynLoadList;
	}

	@Override
	public List<DynLoadModel1Phase> getPhaseCDynLoadList() {
		if(this.phaseCDynLoadList == null)
			this.phaseCDynLoadList = new ArrayList<DynLoadModel1Phase>();
		return this.phaseCDynLoadList;
	}

	@Override
	public List<DStab3PGen> getThreePhaseGenList() {
		if(threePhaseGenList ==null)
			threePhaseGenList = new ArrayList<>();
		return threePhaseGenList;
	}

	@Override
	public List<DStab3PLoad> getThreePhaseLoadList() {
		if(threePhaseLoadList ==null)
			threePhaseLoadList = new ArrayList<>();
		return threePhaseLoadList;
	}
	
	@Override
	public List<DStab1PLoad> getSinglePhaseLoadList() {
		if(singlePhaseLoadList ==null)
			singlePhaseLoadList = new ArrayList<>();
		return singlePhaseLoadList;
		
	}


	
	public Complex3x1 calcLoad3PhEquivCurInj() {
		this.load3PhEquivCurInj = new Complex3x1();
		if (this.Vabc == null) 
			this.Vabc = new Complex3x1(new Complex(1,0),new Complex(-Math.sin(Math.PI/6),-Math.cos(Math.PI/6)),new Complex(-Math.sin(Math.PI/6),Math.cos(Math.PI/6)));
		
		//single-phase loads
		if(this.getSinglePhaseLoadList().size()>0){
			for(DStab1PLoad load1P: this.getSinglePhaseLoadList()){
				if(load1P.isActive()){
					this.load3PhEquivCurInj=this.load3PhEquivCurInj.add(load1P.getEquivCurrInj(Vabc));
				}
				else{
					throw new Error("Load instance is not single-phase load! Bus, load = "+this.getId()+","+load1P.getId());
				}
			}
		}
		
		//three phase loads
		if(this.getThreePhaseLoadList().size()>0){
			
			for(DStab3PLoad load:this.getThreePhaseLoadList()){
				if(load.isActive())
				  this.load3PhEquivCurInj=this.load3PhEquivCurInj.add(load.getEquivCurrInj(Vabc));
			}
			
		}
		return this.load3PhEquivCurInj;
		
	}
	
	
   // for power flow purpose
	@Override
	public Complex3x1 calc3PhEquivCurInj() {
		
		this.equivCurInj3Phase = calcLoad3PhEquivCurInj();
		
		for(DStab3PGen gen:this.getThreePhaseGenList()){
			this.equivCurInj3Phase = this.equivCurInj3Phase.add(gen.getPowerflowEquivCurrInj());
		}
		
		return this.equivCurInj3Phase;
	}
  
	
	// for dynamic purpose
	@Override
	public List<DynLoadModel3Phase> getThreePhaseDynLoadList() {
		if(threePhaseDynLoadList ==null)
			threePhaseDynLoadList = new ArrayList<>();
		return threePhaseDynLoadList;

	}

	@Override
	public Complex3x1 get3PhaseNetLoadResults() {
		
		
//		// 1, process the 1-phase dynamic loads
//
//		
//		Complex totalPhaseADynLoadPQ = new Complex(0,0);
//		Complex totalPhaseBDynLoadPQ = new Complex(0,0);
//		Complex totalPhaseCDynLoadPQ = new Complex(0,0);
//		
//        
//        for(DynLoadModel1Phase dynLoadPA : getPhaseADynLoadList()){
//        	if(dynLoadPA.isActive()){
//        		dynLoadPA.initStates();
//        		
//        		totalPhaseADynLoadPQ = totalPhaseADynLoadPQ.add(dynLoadPA.getInitLoadPQ()); 
//        	}
//		}
//        
//        for(DynLoadModel1Phase dynLoadPB : getPhaseBDynLoadList()){
//        	if(dynLoadPB.isActive()){
//        		dynLoadPB.initStates();
//        		
//        		totalPhaseBDynLoadPQ = totalPhaseBDynLoadPQ.add(dynLoadPB.getInitLoadPQ()); 
//        	}
//		}
//        
//        
//        for(DynLoadModel1Phase dynLoadPC : getPhaseCDynLoadList()){
//        	if(dynLoadPC.isActive()){
//        		dynLoadPC.initStates();
//        	
//        		totalPhaseCDynLoadPQ = totalPhaseCDynLoadPQ.add(dynLoadPC.getInitLoadPQ()); 
//        	}
//		}
       
		
		return this.netTotalLoad3Phase ;
	}
	
	
	@Override
	public void set3PhaseNetLoadResults(Complex3x1 netLoad3Phase) {
		  this.netTotalLoad3Phase = netLoad3Phase;
		
	}
	

	@Override
	public Complex3x1 get3PhaseTotalLoad() {
		this.totalLoad3Phase = new Complex3x1();
		for(DStab3PLoad load: this.getThreePhaseLoadList()){
			if(load.isActive())
			     this.totalLoad3Phase = this.totalLoad3Phase.add(load.get3PhaseLoad(this.get3PhaseVotlages()));  
		}
		//consider single-phase Wye connected load included in the contributeLoadList()
		// TODO how about delta connected load??
		for(DStab1PLoad load1P: this.getSinglePhaseLoadList()){
			if(load1P.isActive()){
				if(load1P.getLoadConnectionType()==LoadConnectionType.SINGLE_PHASE_DELTA){
					throw new Error (" get3PhaseTotalLoad() does not support LoadConnectionType.Single_Phase_Delta yet! bus, load = "+this.getId()+","+load1P.getId());
				}
				else{
					switch(load1P.getPhaseCode()){
					   case A:
						   double vmag = this.get3PhaseVotlages().a_0.abs();
						   this.totalLoad3Phase.a_0 = this.totalLoad3Phase.a_0.add(load1P.getLoad(vmag));
					     break;
					   case B:
						   vmag = this.get3PhaseVotlages().b_1.abs();
						   this.totalLoad3Phase.b_1 = this.totalLoad3Phase.b_1.add(load1P.getLoad(vmag));
						 break;
					  
					   case C:
						   vmag = this.get3PhaseVotlages().c_2.abs();
						   this.totalLoad3Phase.c_2 = this.totalLoad3Phase.c_2.add(load1P.getLoad(vmag));
						  break;
					   default:
						   throw new Error (" get3PhaseTotalLoad() does not support the phase code of this single load yet! bus, load, phase code = "+this.getId()+","+load1P.getId()+","+load1P.getPhaseCode());
					
						
					}
				}
			}
		}
		return this.totalLoad3Phase;
	}

	@Override
	public void setThreePhaseInitVoltage(Complex3x1 initVoltAbc) {
		this.initVabc = initVoltAbc;
		
	}

	@Override
	public Complex3x1 get3PhaseInitVoltage() {
		
		
		return this.initVabc;
	}

	
   @Override
   public AclfLoad getContributeLoad(String id) {
	  
	   if(this.getContributeLoadList()!=null) {
		   for(AclfLoad load: this.getContributeLoadList()) {
			   if(load.getId().equals(id)) {
				   return load;
			   }
		   }
	   }
	   
	   if(this.getSinglePhaseLoadList()!=null) {
		   for(AclfLoad load: this.getSinglePhaseLoadList()) {
			   if(load.getId().equals(id)) {
				   return load;
			   }
		   }
	   }
	   
	   if(this.getThreePhaseLoadList()!=null) {
		   for(AclfLoad load: this.getThreePhaseLoadList()) {
			   if(load.getId().equals(id)) {
				   return load;
			   }
		   }
	   }
	   
	   return null;
	   
	   
   }
   
   public Complex3x1 injCurDynamic3Phase() {
	    Complex3x1 iInject = new Complex3x1();
	    
	    // generations
		if(this.getContributeGenList().size()>0){
			 for(AclfGen gen: this.getContributeGenList()){
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
		
		//loads
		if(this.isLoad()){
			
			//// Phase A
			if(this.getPhaseADynLoadList().size()>0){
				Complex iPhAInj = new Complex(0,0);
				
				for(DynLoadModel1Phase load1p:this.getPhaseADynLoadList()){
					if(load1p.isActive()){
				        iPhAInj = iPhAInj.add(load1p.getNortonCurInj());
				       // System.out.println("Iinj@Load-"+bus3p.getId()+", "+ load1p.getId()+","+load1p.getCompensateCurInj().toString());
					}
				}
				
				if(iPhAInj.abs()>0.0)
					iInject.a_0 = iInject.a_0.add(iPhAInj);
			}
			
			// Phase B
			if(this.getPhaseBDynLoadList().size()>0){
				Complex iPhBInj = new Complex(0,0);
				
				for(DynLoadModel1Phase load1p:this.getPhaseBDynLoadList()){
					if(load1p.isActive()){
				        iPhBInj = iPhBInj.add(load1p.getNortonCurInj());
				       // System.out.println("Iinj@Load-"+bus3p.getId()+", "+ load1p.getId()+","+load1p.getCompensateCurInj().toString());
					}
				}
				
				if(iPhBInj.abs()>0.0)
					iInject.b_1 = iInject.b_1.add(iPhBInj);
			}
			
			// Phase C
			if(this.getPhaseCDynLoadList().size()>0){
				Complex iPhCInj = new Complex(0,0);
				
				for(DynLoadModel1Phase load1p:this.getPhaseCDynLoadList()){
					if(load1p.isActive()){
				        iPhCInj = iPhCInj.add(load1p.getNortonCurInj());
				       // System.out.println("Iinj@Load-"+bus3p.getId()+", "+ load1p.getId()+","+load1p.getCompensateCurInj().toString());
					}
				}
				
				if(iPhCInj.abs()>0.0)
					iInject.c_2 = iInject.c_2.add(iPhCInj);
			}
			
			// three-phase dynamic loads

			 for(DynamicBusDevice dynDevice: this.getDynamicBusDeviceList()){
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
	
			 // add static load equivalent current injection 
			 if(!((BaseDStabNetwork)this.getNetwork()).isStaticLoadIncludedInYMatrix()) {
				 iInject = iInject.add(calc3PhaseStaticLoadInjCur());
			 }
		} // end of isLoad()
		
	
	 
	 if(iInject == null){
		  throw new Error (this.getId()+" current injection is null");
	 }
	 
	 return iInject;
  }

   private Complex3x1 calc3PhaseStaticLoadInjCur() {
		
		Complex3x1 staticLoadCurInj= new Complex3x1();
		
		Complex3x1 vabc = this.get3PhaseVotlages();
		
		Complex3x1 staticLoad = cal3PhaseStaticLoad();
		
		if(vabc.abs()>1.0E-4) {
			if(vabc.a_0.abs()>1.0E-4 && vabc.b_1.abs()>1.0E-4 && vabc.c_2.abs()>1.0E-4) {
				 staticLoadCurInj = staticLoad.divide(vabc).conjugate().multiply(-1); // multiplying -1  because current injection into the network is positive
			}
			else {
				if(vabc.a_0.abs()>1.0E-4){
					 staticLoadCurInj.a_0 = staticLoad.a_0.divide(vabc.a_0).conjugate().multiply(-1);
				}
				if(vabc.b_1.abs()>1.0E-4){
					 staticLoadCurInj.b_1 = staticLoad.b_1.divide(vabc.b_1).conjugate().multiply(-1);
				}
				if(vabc.c_2.abs()>1.0E-4){
					 staticLoadCurInj.c_2 = staticLoad.c_2.divide(vabc.c_2).conjugate().multiply(-1);
				}
				//staticLoadCurInj = staticLoadCurInj.conjugate().multiply(-1);
			}
		}
	
//		System.out.println("bus volt ="+vabc.toString());
//	    System.out.println("calc staticLoadCurInj ="+staticLoadCurInj.toString());
//	    System.out.println("calc staticLoad ="+vabc.multiply(staticLoadCurInj.conjugate()));
		
		return staticLoadCurInj;
		
	}
   
   public Complex3x1 cal3PhaseStaticLoad() {
   	
   	
   	this.staticTotalLoad3Phase = new Complex3x1();
   	
   	if(this.get3PhaseNetLoadResults() != null && this.get3PhaseNetLoadResults().abs()>0) {
   		
   		Complex3x1 vabc = this.get3PhaseVotlages();
       	
       	Complex3x1 vabc_init = this.get3PhaseInitVoltage();
       	

   		double va = vabc.a_0.abs();
   		double vb = vabc.b_1.abs();
   		double vc = vabc.c_2.abs();
   		 
   		double va0 = vabc_init.a_0.abs();
   		double vb0 = vabc_init.b_1.abs();
   		double vc0 = vabc_init.c_2.abs();
       	
   		//calculate the three phase power voltage dependent coefficients
   		//assuming three-phase are Y connected and they are independent in terms of voltage impact
   		double cza = va*va/va0/va0;
   		double czb = vb*vb/vb0/vb0;
   		double czc = vc*vc/vc0/vc0;
   		
   		//TODO
   		//Tentatively, we assume the static loads are modeled as constant impedances 
   		this.staticTotalLoad3Phase = this.get3PhaseNetLoadResults().multiply(new Complex3x1 (new Complex(cza),new Complex(czb),new Complex(czc)));
   	}
   	
   	// considering load shedding or load changes
   	this.staticTotalLoad3Phase = this.staticTotalLoad3Phase.multiply(1+this.getAccumulatedLoadChangeFactor());
   	
//	System.out.println("\n\n"+this.getId()+", total load ="+	this.get3PhaseTotalLoad().toString());
//	System.out.println(this.getId()+", net load ="+	this.get3PhaseNetLoadResults().toString());
//   	System.out.println(this.getId()+", calc staticLoad ="+	this.staticTotalLoad3Phase.toString());
   	
   	return this.staticTotalLoad3Phase;
   }
   
	@Override
	public Complex3x1 calcNetPowerIntoNetwork() {
		Complex3x1 netPower = new Complex3x1();
		
		for(Branch bra:this.getFromBranchList()) {
			netPower = netPower.add(((DStab3PBranch)bra).calc3PhaseCurrentFrom2To());
		}
		
	    for(Branch bra:this.getToBranchList()) {
	    	netPower = netPower.add(((DStab3PBranch)bra).calc3PhaseCurrentTo2From());
		}
	    
	    netPower = this.get3PhaseVotlages().multiply(netPower.conjugate());
		
		return netPower;
	}



}
