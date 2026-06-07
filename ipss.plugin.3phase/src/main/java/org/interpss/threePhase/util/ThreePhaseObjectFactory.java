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
import org.interpss.threePhase.opf.dist.DistOpfAlgorithm;
import org.interpss.threePhase.opf.dist.impl.DistOpfAlgorithmImpl;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistributionPowerFlowAlgorithmImpl;
import org.interpss.threePhase.qsts.QstsScheduleData;
import org.interpss.threePhase.qsts.QstsStudy;

import com.interpss.common.datatype.Constants;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.netAdj.AclfNetAdjustment;
import com.interpss.core.aclf.netAdj.NetAdjustFactory;
import com.interpss.core.acsc.AcscFactory;
import com.interpss.core.acsc.BusScCode;
import com.interpss.core.acsc.BusScGrounding;
import com.interpss.core.threephase.INetwork3Phase;
import com.interpss.core.threephase.Static3PBus;
import com.interpss.core.threephase.Static3PBranch;
import com.interpss.core.threephase.Static3PGen;
import com.interpss.core.threephase.Static3PLoad;
import com.interpss.core.threephase.Static3PNetwork;
import com.interpss.core.threephase.Static3PhaseFactory;
import com.interpss.core.net.OriginalDataFormat;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.StaticLoadModel;

public class ThreePhaseObjectFactory {

	public static Static3PNetwork createStatic3PhaseNetwork() {
		Static3PNetwork net = Static3PhaseFactory.eINSTANCE.createStatic3PNetwork();
		net.setId("undefined");
		net.setOriginalDataFormat(OriginalDataFormat.IPSS_API);
		return net;
	}

	public static Static3PBus createStatic3PBus(String busId, Static3PNetwork net) {
		Static3PBus bus = Static3PhaseFactory.eINSTANCE.createStatic3PBus();
		bus.setId(busId);
		bus.setStatus(true);
		net.addBus(bus);
		return bus;
	}

	public static Static3PGen createStatic3PGenerator(String genId) {
		Static3PGen gen = Static3PhaseFactory.eINSTANCE.createStatic3PGen();
		gen.setId(genId);
		return gen;
	}

	public static Static3PLoad createStatic3PLoad(String loadId) {
		Static3PLoad load = Static3PhaseFactory.eINSTANCE.createStatic3PLoad();
		load.setId(loadId);
		return load;
	}

	public static Static3PBranch createStatic3PBranch(String fromBusId, String toBusId, String cirId,
			Static3PNetwork net) throws InterpssException {
		Static3PBranch branch = Static3PhaseFactory.eINSTANCE.createStatic3PBranch();
		branch.setStatus(true);
		net.addBranch(branch, fromBusId, toBusId, cirId);
		return branch;
	}

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

	public static DStab3PBus create3PDStabBus(String busId, DStabNetwork3Phase net) {
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
		if (!(net instanceof INetwork3Phase)) {
			throw new IllegalArgumentException("Network must implement INetwork3Phase: "
					+ (net == null ? "null" : net.getClass().getName()));
		}
		return new DistributionPowerFlowAlgorithmImpl((INetwork3Phase) net);
	}

	public static DistOpfAlgorithm createDistOpfAlgorithm(INetwork3Phase net){
		return new DistOpfAlgorithmImpl(net);
	}

	public static QstsStudy createQstsStudy(INetwork3Phase net, QstsScheduleData scheduleData){
		return QstsStudy.from(net, scheduleData);
	}

}
