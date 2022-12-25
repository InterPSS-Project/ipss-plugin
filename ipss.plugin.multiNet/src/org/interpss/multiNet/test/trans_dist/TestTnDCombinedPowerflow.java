package org.interpss.multiNet.test.trans_dist;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.multiNet.algo.SubNetworkProcessor;
import org.interpss.multiNet.algo.powerflow.TDMultiNetPowerflowAlgorithm;
import org.interpss.multiNet.algo.powerflow.TposSeqD3PhaseMultiNetPowerflowAlgorithm;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestTnDCombinedPowerflow {
	
	// This test case is for modeling the transmission system in 3-sequence, while representing distribution systems in 3-phase
	@Test
	public void test_TDPowerflow_IEEE9_feeder() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
				"testData/IEEE9Bus/ieee9_dyn.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		
		// The only change to the normal data import is the use of ODM3PhaseDStabParserMapper
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
	    dsNet.getBus("Bus5").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus5").setLoadCode(AclfLoadCode.NON_LOAD);
	    
	    //TODO adding a feeder
	    
	    addADistFeeder(dsNet); 
	    
	    
	    //TODO use subnetworkprocessor to split the system
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		 proc.addSubNetInterfaceBranch("Bus5->Bus11(0)",false);
		 proc.splitFullSystemIntoSubsystems(true);
		 
		 System.out.println("external boundary bus: "+proc.getExternalSubNetBoundaryBusIdList());
		 
		 System.out.println("internal boundary bus: "+proc.getInternalSubNetBoundaryBusIdList());
		 
	    
	    
	    //TODO create TDMultiNetPowerflowAlgo
	    
		 TDMultiNetPowerflowAlgorithm tdAlgo = new TDMultiNetPowerflowAlgorithm((BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch>) dsNet,proc);
		 
	    
		 assertTrue(tdAlgo.powerflow()); 
		 
		 //System.out.println(tdAlgo.getTransmissionNetwork().net2String());
		 
		 
		 System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
		 
	}
	
	// This test case is for modeling the transmission system in positive-sequence (or single phase), while representing distribution systems in 3 phase
	@Test
	public void test_TposSeq_D3Phase_Powerflow_IEEE9_feeder() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
				"testData/IEEE9Bus/ieee9_dyn.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		
		// The only change to the normal data import is the use of ODM3PhaseDStabParserMapper
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
	    dsNet.getBus("Bus5").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus5").setLoadCode(AclfLoadCode.NON_LOAD);
	    
	    //TODO adding a feeder
	    
	    addADistFeeder(dsNet); 
	    
	    
	    //TODO use subnetworkprocessor to split the system
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		 proc.addSubNetInterfaceBranch("Bus5->Bus11(0)",false);
		 proc.splitFullSystemIntoSubsystems(true);
		 
		 System.out.println("external boundary bus: "+proc.getExternalSubNetBoundaryBusIdList());
		 
		 System.out.println("internal boundary bus: "+proc.getInternalSubNetBoundaryBusIdList());
		 
	    
	    
	    //TODO create TDMultiNetPowerflowAlgo
	    
		 TposSeqD3PhaseMultiNetPowerflowAlgorithm tdAlgo = new TposSeqD3PhaseMultiNetPowerflowAlgorithm((BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch>) dsNet,proc);
		 
	    
		 assertTrue(tdAlgo.powerflow()); 
		 
		 //System.out.println(tdAlgo.getTransmissionNetwork().net2String());
		 
		 
		 System.out.println(AclfOutFunc.loadFlowSummary(tdAlgo.getTransmissionNetwork()));
		 System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(tdAlgo.getDistributionNetworkList().get(0)));
		 
	}
	
//	@Test
	public void test_PosSeqPowerflow_IEEE9_feeder() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
				"testData/IEEE9Bus/ieee9_dyn.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		
		// The only change to the normal data import is the use of ODM3PhaseDStabParserMapper
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
	    dsNet.getBus("Bus5").getContributeLoadList().remove(0);
	    dsNet.getBus("Bus5").setLoadCode(AclfLoadCode.NON_LOAD);
	    
	    //TODO adding a feeder
	    
	    addADistFeeder(dsNet); 
	    
	    LoadflowAlgorithm lfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
	    assertTrue(lfAlgo.loadflow());
	    
	    
	    System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
	    
	}
	
	private void addADistFeeder(DStabNetwork3Phase net) throws InterpssException{
		DStab3PBus bus1 = ThreePhaseObjectFactory.create3PAclfBus("Bus11", net);
  		bus1.setAttributes("69 kV feeder source", "");
  		bus1.setBaseVoltage(69000.0);
  		// set the bus to a non-generator bus
  		//bus1.setGenCode(AclfGenCode.SWING);
  		// set the bus to a constant power load bus
  		bus1.setLoadCode(AclfLoadCode.NON_LOAD);
  		bus1.setVoltage(new Complex(1.00,0));

  		
  		DStab3PBus bus2 = ThreePhaseObjectFactory.create3PAclfBus("Bus12", net);
  		bus2.setAttributes("13.8 V feeder bus 12", "");
  		bus2.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus2.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus2.setLoadCode(AclfLoadCode.NON_LOAD);
  		
  		
  		DStab3PBus bus3 = ThreePhaseObjectFactory.create3PAclfBus("Bus13", net);
  		bus3.setAttributes("13.8 V feeder bus 13", "");
  		bus3.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus3.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus3.setLoadCode(AclfLoadCode.CONST_P);
  		
  		DStab3PLoad load1 = new DStab3PLoadImpl();
  		load1.set3PhaseLoad(new Complex3x1(new Complex(0.5,-0.1),new Complex(0.5,-0.1),new Complex(0.5,-0.1)));
  		bus3.getThreePhaseLoadList().add(load1);
  		bus3.setLoadP(0.5);
  		bus3.setLoadQ(-0.1);
  		
  		
  		DStab3PBus bus4 = ThreePhaseObjectFactory.create3PAclfBus("Bus14", net);
  		bus4.setAttributes("13.8 V feeder bus 14", "");
  		bus4.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus4.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus4.setLoadCode(AclfLoadCode.CONST_P);

  		bus4.setLoadP(1);
  		bus4.setLoadQ(0.1);
  		
  		DStab3PLoad load2 = new DStab3PLoadImpl();
  		load2.set3PhaseLoad(new Complex3x1(new Complex(1,0.1),new Complex(1,0.1),new Complex(1,0.1)));
  		bus4.getThreePhaseLoadList().add(load2);
  		
  		
  		DStab3PBranch xfr5_11 = ThreePhaseObjectFactory.create3PBranch("Bus5", "Bus11", "0", net);
  		xfr5_11.setBranchCode(AclfBranchCode.XFORMER);
  		xfr5_11.setToTurnRatio(1.03);
  		//xfr5_11.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.05 )));
  		//xfr1_2.setZ0( new Complex(0.0, 0.4 ));
  		xfr5_11.setZ( new Complex(0.0, 0.08 ));
  		xfr5_11.setZ0( new Complex(0.0, 0.08 ));
  		
  		
  		AcscXformerAdapter xfr1 = acscXfrAptr.apply(xfr5_11);
		xfr1.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		xfr1.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
  		
		
  		
  		DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch("Bus11", "Bus12", "0", net);
  		xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
  		//xfr1_2.setToTurnRatio(1.02);
  		xfr1_2.setZ( new Complex( 0.0, 0.05));
  		xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.05 )));
  		//xfr1_2.setZ0( new Complex(0.0, 0.4 ));
		
		
		AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr1_2);
		xfr0.setFromGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
		xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
		
		// for testing connection and from-to relationship only
//		xfr0.setToConnectGroundZ(XfrConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
//		xfr0.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
		
		DStab3PBranch Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus12", "Bus13", "0", net);
		Line2_3.setBranchCode(AclfBranchCode.LINE);
		Line2_3.setZ( new Complex( 0.004, 0.04 ));
		Line2_3.setZ0( new Complex(0.008, 0.08 ));
  		
		
		DStab3PBranch Line2_4 = ThreePhaseObjectFactory.create3PBranch("Bus14", "Bus12", "0", net);
		Line2_4.setBranchCode(AclfBranchCode.LINE);
		Line2_4.setZ( new Complex( 0.004, 0.04 ));
		Line2_4.setZ0( new Complex(0.008, 0.08 ));
	}
	
	

}
