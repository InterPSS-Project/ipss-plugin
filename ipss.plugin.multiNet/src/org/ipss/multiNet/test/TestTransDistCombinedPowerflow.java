package org.ipss.multiNet.test;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.ipss.threePhase.basic.Branch3Phase;
import org.ipss.threePhase.basic.Bus3Phase;
import org.ipss.threePhase.basic.Load3Phase;
import org.ipss.threePhase.basic.impl.Load3PhaseImpl;
import org.ipss.threePhase.dynamic.DStabNetwork3Phase;
import org.ipss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.ipss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.adpter.AcscXformer;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestTransDistCombinedPowerflow {
	
	
	@Test
	public void test_IEEE9_1pac_3phase_Dstab() throws InterpssException{
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
	    
	    //TODO adding a feeder
	    
	    addAFeeder(dsNet); 
	    
	    
	    //TODO use subnetworkprocessor to split the system
	    
	    
	    
	    
	    //TODO create TDMultiNetPowerflowAlgo
	    
	    
	    
	    
	    
	}
	
	private void addAFeeder(DStabNetwork3Phase net) throws InterpssException{
		Bus3Phase bus1 = ThreePhaseObjectFactory.create3PAclfBus("Bus11", net);
  		bus1.setAttributes("69 kV feeder source", "");
  		bus1.setBaseVoltage(69000.0);
  		// set the bus to a non-generator bus
  		bus1.setGenCode(AclfGenCode.SWING);
  		// set the bus to a constant power load bus
  		bus1.setLoadCode(AclfLoadCode.NON_LOAD);
  		bus1.setVoltage(new Complex(1.01,0));

  		
		Bus3Phase bus2 = ThreePhaseObjectFactory.create3PAclfBus("Bus12", net);
  		bus2.setAttributes("13.8 V feeder bus 2", "");
  		bus2.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus2.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus2.setLoadCode(AclfLoadCode.NON_LOAD);
  		
  		
		Bus3Phase bus3 = ThreePhaseObjectFactory.create3PAclfBus("Bus13", net);
  		bus3.setAttributes("13.8 V feeder bus 3", "");
  		bus3.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus3.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus3.setLoadCode(AclfLoadCode.CONST_P);
  		
  		Load3Phase load1 = new Load3PhaseImpl();
  		load1.set3PhaseLoad(new Complex3x1(new Complex(0.5,0.1),new Complex(0.5,0.1),new Complex(0.5,0.1)));
  		bus3.getThreePhaseLoadList().add(load1);
  		//bus3.setLoadPQ(new Complex(0.5,0.1));
  		
  		
		Bus3Phase bus4 = ThreePhaseObjectFactory.create3PAclfBus("Bus14", net);
  		bus4.setAttributes("13.8 V feeder bus 4", "");
  		bus4.setBaseVoltage(13800.0);
  		// set the bus to a non-generator bus
  		bus4.setGenCode(AclfGenCode.NON_GEN);
  		// set the bus to a constant power load bus
  		bus4.setLoadCode(AclfLoadCode.CONST_P);

  		//bus4.setLoadPQ(new Complex(1,0.1));
  		
  		Load3Phase load2 = new Load3PhaseImpl();
  		load2.set3PhaseLoad(new Complex3x1(new Complex(1,0.1),new Complex(1,0.1),new Complex(1,0.1)));
  		bus4.getThreePhaseLoadList().add(load2);
  		
  		
//		Branch3Phase Line1_2 = ThreePhaseObjectFactory.create3PBranch("Bus1", "Bus2", "0", net);
//		Line1_2.setBranchCode(AclfBranchCode.LINE);
//		Line1_2.setZ( new Complex( 0.0, 0.04 ));
//		Line1_2.setZ0( new Complex(0.0, 0.08 ));
  		
  		Branch3Phase xfr1_2 = ThreePhaseObjectFactory.create3PBranch("Bus11", "Bus12", "0", net);
  		xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
  		xfr1_2.setToTurnRatio(1.02);
  		//xfr1_2.setZ( new Complex( 0.0, 0.04 ));
  		xfr1_2.setZabc(Complex3x3.createUnitMatrix().multiply(new Complex( 0.0, 0.04 )));
  		//xfr1_2.setZ0( new Complex(0.0, 0.4 ));
		
		
		AcscXformer xfr0 = acscXfrAptr.apply(xfr1_2);
		xfr0.setFromConnectGroundZ(XfrConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
		xfr0.setToConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
		
		// for testing connection and from-to relationship only
//		xfr0.setToConnectGroundZ(XfrConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
//		xfr0.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
		
		Branch3Phase Line2_3 = ThreePhaseObjectFactory.create3PBranch("Bus12", "Bus13", "0", net);
		Line2_3.setBranchCode(AclfBranchCode.LINE);
		Line2_3.setZ( new Complex( 0.0, 0.04 ));
		Line2_3.setZ0( new Complex(0.0, 0.08 ));
  		
		
		Branch3Phase Line2_4 = ThreePhaseObjectFactory.create3PBranch("Bus14", "Bus12", "0", net);
		Line2_4.setBranchCode(AclfBranchCode.LINE);
		Line2_4.setZ( new Complex( 0.0, 0.04 ));
		Line2_4.setZ0( new Complex(0.0, 0.08 ));
	}

}
