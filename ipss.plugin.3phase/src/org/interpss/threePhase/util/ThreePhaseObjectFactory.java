package org.interpss.threePhase.util;

import org.interpss.numeric.NumericConstant;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.DStab3W3PBranch;
import org.interpss.threePhase.basic.dstab.impl.DStab3PBusImpl;
import org.interpss.threePhase.basic.dstab.impl.DStab3PGenImpl;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.basic.dstab.impl.DStab3W3PBranchImpl;
import org.interpss.threePhase.basic.dstab.impl.Dstab3PBranchImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.impl.DStabNetwork3phaseImpl;
import org.interpss.threePhase.dynamic.model.DStabGen3PhaseAdapter;
import org.interpss.threePhase.dynamic.model.impl.DStabGen3PhaseAdapterImpl;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistributionPowerFlowAlgorithmImpl;

import com.interpss.DStabObjectFactory;
import com.interpss.common.datatype.Constants;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.abc.Static3PXformer;
import com.interpss.core.abc.impl.Static3PXformerImpl;
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

	/*
	public static Static3PXformer create3PXformer(){
	   Static3PXformer ph3Xfr = new Static3PXformerImpl();
	   return ph3Xfr;
	}
	*/
	
	public static DStab3PBus create3PAclfBus(String busId, BaseAclfNetwork net) throws InterpssException{
		DStab3PBus bus = new DStab3PBusImpl();
	  
		//The following is copied from the DStabObjectFactory
		bus.setId(busId);
		net.addBus(bus);
		
		return bus;
	}
	
	public static DStab3PBus create3PDStabBus(String busId, DStabNetwork3Phase net) throws InterpssException{
		DStab3PBus bus = new DStab3PBusImpl();
	  
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

	public static DStab3W3PBranch createBranch3W3Phase(){
		DStab3W3PBranch branch = new DStab3W3PBranchImpl();
		return branch;
	}
	
	public static DStab3W3PBranch createBranch3W3Phase(String fromBusId, String toBusId, String cirId,BaseAclfNetwork net) throws InterpssException{
		DStab3W3PBranch branch = new DStab3W3PBranchImpl();
		net.addBranch(branch, fromBusId, toBusId, cirId);
		return branch;
	}
	

	
	public static DStab3PBranch create3PBranch(String fromBusId, String toBusId, String cirId,BaseAclfNetwork net) throws InterpssException{
		DStab3PBranch branch = new Dstab3PBranchImpl();
		net.addBranch(branch, fromBusId, toBusId, cirId);
		return branch;
	}
	
	public static DStab3PGen  create3PGenerator(String genId){
		DStab3PGen gen = new DStab3PGenImpl();
		gen.setId(genId);
		return gen;
	}
	
	public static DStabGen3PhaseAdapter  create3PDynGenerator(String genId){
		DStabGen3PhaseAdapter gen = new DStabGen3PhaseAdapterImpl();
		gen.setId(genId);
		return gen;
	}
	
	public static DStab3PLoad create3PLoad(String loadId){
		DStab3PLoad load = new DStab3PLoadImpl();
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
