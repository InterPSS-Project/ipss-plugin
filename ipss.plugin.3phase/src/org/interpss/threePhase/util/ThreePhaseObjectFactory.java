package org.interpss.threePhase.util;

import org.interpss.numeric.NumericConstant;
import org.interpss.threePhase.basic.Branch3W3Phase;
import org.interpss.threePhase.basic.DStab3PBranch;
import org.interpss.threePhase.basic.DStab3PBus;
import org.interpss.threePhase.basic.Gen3Phase;
import org.interpss.threePhase.basic.Load3Phase;
import org.interpss.threePhase.basic.Transformer3Phase;
import org.interpss.threePhase.basic.impl.Branch3W3PhaseImpl;
import org.interpss.threePhase.basic.impl.Bus3PhaseImpl;
import org.interpss.threePhase.basic.impl.Dstab3PBranchImpl;
import org.interpss.threePhase.basic.impl.Gen3PhaseImpl;
import org.interpss.threePhase.basic.impl.Load3PhaseImpl;
import org.interpss.threePhase.basic.impl.Transformer3PhaseImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.impl.DStabNetwork3phaseImpl;
import org.interpss.threePhase.dynamic.model.DStabGen3PhaseAdapter;
import org.interpss.threePhase.dynamic.model.impl.DStabGen3PhaseAdapterImpl;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistributionPowerFlowAlgorithmImpl;

import com.interpss.DStabObjectFactory;
import com.interpss.common.datatype.Constants;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.netAdj.AclfNetAdjustment;
import com.interpss.core.aclf.netAdj.NetAdjustFactory;
import com.interpss.core.acsc.AcscFactory;
import com.interpss.core.acsc.BusScCode;
import com.interpss.core.acsc.BusScGrounding;
import com.interpss.core.net.OriginalDataFormat;
import com.interpss.dstab.StaticLoadModel;

public class ThreePhaseObjectFactory {
	
	public static  DStabNetwork3Phase create3PhaseDStabNetwork() {
	       DStabNetwork3Phase net = new DStabNetwork3phaseImpl();
	        
	       //The following is copied from the DStabObjectFactory
	        AclfNetAdjustment netAdj = NetAdjustFactory.eINSTANCE.createAclfNetAdjustment();
			net.setAclfNetAdjust(netAdj);
			netAdj.setAclfNet(net);
			net.setId("undefined");
			net.setOriginalDataFormat(OriginalDataFormat.IPSS_API);
			net.setStaticLoadModel(StaticLoadModel.CONST_Z);
			net.setStaticLoadSwitchVolt(Constants.DStabStaticLoadSwithVolt);
			net.setStaticLoadSwitchDeadZone(Constants.DStabStaticLoadSwithDeadband);
	      return net;
	}
	public static Transformer3Phase create3PXformer(){
	   Transformer3Phase ph3Xfr = new Transformer3PhaseImpl();
	   return ph3Xfr;
	}
	
	public static DStab3PBus create3PAclfBus(String busId, BaseAclfNetwork net) throws InterpssException{
		DStab3PBus bus = new Bus3PhaseImpl();
	  
		//The following is copied from the DStabObjectFactory
		bus.setId(busId);
		net.addBus(bus);
		
		return bus;
	}
	
	public static DStab3PBus create3PDStabBus(String busId, DStabNetwork3Phase net) throws InterpssException{
		DStab3PBus bus = new Bus3PhaseImpl();
	  
		//The following is copied from the DStabObjectFactory
		bus.setId(busId);
		BusScGrounding g = AcscFactory.eINSTANCE.createBusScGrounding();
  		bus.setId(busId);
  		bus.setScCode(BusScCode.NON_CONTRI);
  		bus.setScGenZ1(NumericConstant.LargeBusZ);
  		bus.setScGenZ0(NumericConstant.LargeBusZ);
  		bus.setScGenZ2(NumericConstant.LargeBusZ);
  		bus.setGrounding(g);
		bus.setBusFreqMeasureBlock(DStabObjectFactory.createBusFreqMeasurement());
		
		net.addBus(bus);
		
		return bus;
	}

	public static Branch3W3Phase createBranch3W3Phase(){
		Branch3W3Phase branch = new Branch3W3PhaseImpl();
		return branch;
	}
	
	public static Branch3W3Phase createBranch3W3Phase(String fromBusId, String toBusId, String cirId,BaseAclfNetwork net) throws InterpssException{
		Branch3W3Phase branch = new Branch3W3PhaseImpl();
		net.addBranch(branch, fromBusId, toBusId, cirId);
		return branch;
	}
	

	
	public static DStab3PBranch create3PBranch(String fromBusId, String toBusId, String cirId,BaseAclfNetwork net) throws InterpssException{
		DStab3PBranch branch = new Dstab3PBranchImpl();
		net.addBranch(branch, fromBusId, toBusId, cirId);
		return branch;
	}
	
	public static Gen3Phase  create3PGenerator(String genId){
		Gen3Phase gen = new Gen3PhaseImpl();
		gen.setId(genId);
		return gen;
	}
	
	public static DStabGen3PhaseAdapter  create3PDynGenerator(String genId){
		DStabGen3PhaseAdapter gen = new DStabGen3PhaseAdapterImpl();
		gen.setId(genId);
		return gen;
	}
	
	public static Load3Phase create3PLoad(String loadId){
		Load3Phase load = new Load3PhaseImpl();
		load.setId(loadId);
		return load;
	}
	public static DStab3PBranch create3PBranch() {
		DStab3PBranch branch = new Dstab3PBranchImpl();
		return branch;
	}
	
	public static DistributionPowerFlowAlgorithm createDistPowerFlowAlgorithm(BaseAclfNetwork net){
		DistributionPowerFlowAlgorithmImpl algo = new DistributionPowerFlowAlgorithmImpl(net);
		
		return algo;
	}

}
