package org.interpss.threePhase.basic.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.Branch3Phase;
import org.interpss.threePhase.basic.Bus3Phase;
import org.interpss.threePhase.basic.Gen3Phase;
import org.interpss.threePhase.basic.Load1Phase;
import org.interpss.threePhase.basic.Load3Phase;
import org.interpss.threePhase.basic.LoadConnectionType;
import org.interpss.threePhase.dynamic.model.DynLoadModel1Phase;
import org.interpss.threePhase.dynamic.model.DynLoadModel3Phase;
import org.interpss.threePhase.util.ThreeSeqLoadProcessor;

import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.net.Branch;
import com.interpss.dstab.impl.BaseDStabBusImpl;



public class Bus3PhaseImpl extends BaseDStabBusImpl<Gen3Phase,Load3Phase> implements Bus3Phase{
	
	private Complex3x1 Vabc = null;
	private Complex3x1 initVabc = null;
	private Complex3x3 shuntYabc = null;
	Complex3x3 yiiAbc = new Complex3x3();
	
	private List<DynLoadModel1Phase> phaseADynLoadList;
	
	private List<DynLoadModel1Phase> phaseBDynLoadList;
	
	private List<DynLoadModel1Phase> phaseCDynLoadList;
	
	private List<DynLoadModel3Phase> threePhaseDynLoadList;
	
	private List<Load1Phase> singlePhaseLoadList = null;
	
	private List<Load3Phase> threePhaseLoadList = null;
	private List<Gen3Phase> threePhaseGenList = null;
	
	private Complex3x1 load3PhEquivCurInj = null;
	private Complex3x1 equivCurInj3Phase = null;
	
	private Complex3x1 netTotalLoad3Phase = null;
	
	private Complex3x1 totalLoad3Phase = null;

	@Override
	public Complex3x1 get3PhaseVotlages() {
		
		return  this.Vabc;
	}

	@Override
	public void set3PhaseVoltages(Complex3x1 vabc) {
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
				 if(bra instanceof Branch3Phase){
					 Branch3Phase thrPhBranch = (Branch3Phase) bra; 
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
		  
		 yiiAbc= yiiAbc.add(ThreeSeqLoadProcessor.getEquivLoadYabc(this));
		 
		
		     
		//TODO 11/19/2015 work on three-phase and single-phase loads
		//ONly consider the net Load after excluding the effects of dynamic loads
		  
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
	public List<Gen3Phase> getThreePhaseGenList() {
		if(threePhaseGenList ==null)
			threePhaseGenList = new ArrayList<>();
		return threePhaseGenList;
	}

	@Override
	public List<Load3Phase> getThreePhaseLoadList() {
		if(threePhaseLoadList ==null)
			threePhaseLoadList = new ArrayList<>();
		return threePhaseLoadList;
	}
	
	@Override
	public List<Load1Phase> getSinglePhaseLoadList() {
		if(singlePhaseLoadList ==null)
			singlePhaseLoadList = new ArrayList<>();
		return singlePhaseLoadList;
		
	}


	
	private Complex3x1 calcLoad3PhEquivCurInj() {
		this.load3PhEquivCurInj = new Complex3x1();
		if (this.Vabc == null) 
			this.Vabc = new Complex3x1(new Complex(1,0),new Complex(-Math.sin(Math.PI/6),-Math.cos(Math.PI/6)),new Complex(-Math.sin(Math.PI/6),Math.cos(Math.PI/6)));
		
		//single-phase loads
		if(this.getSinglePhaseLoadList().size()>0){
			for(Load1Phase load1P: this.getSinglePhaseLoadList()){
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
			
			for(Load3Phase load:this.getThreePhaseLoadList()){
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
		
		for(Gen3Phase gen:this.getThreePhaseGenList()){
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
		for(Load3Phase load: this.getThreePhaseLoadList()){
			if(load.isActive())
			     this.totalLoad3Phase = this.totalLoad3Phase.add(load.get3PhaseLoad(this.get3PhaseVotlages()));  
		}
		//consider single-phase Wye connected load included in the contributeLoadList()
		// TODO how about delta connected load??
		for(Load1Phase load1P: this.getSinglePhaseLoadList()){
			if(load1P.isActive()){
				if(load1P.getLoadConnectionType()==LoadConnectionType.Single_Phase_Delta){
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



}
