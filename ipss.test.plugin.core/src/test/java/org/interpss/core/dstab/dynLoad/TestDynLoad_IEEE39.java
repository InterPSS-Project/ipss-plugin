package org.interpss.core.dstab.dynLoad;

import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.dstab.dynLoad.LD1PAC;
import org.interpss.odm.mapper.ODMDStabParserMapper;
import org.junit.Test;

import com.interpss.common.CoreCommonFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class TestDynLoad_IEEE39 {
	
	@Test
	public void test_IEEE39Bus_DynLoad_ACMotor() throws InterpssException{
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonFactory.getIpssMsgHub();
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus_multiloads_xfmr4_smallX_v30.raw",
				"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus_v30.seq",
				"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus_3AC.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());
        
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    BaseDStabNetwork<DStabBus,DStabBranch> dsNet =(BaseDStabNetwork<DStabBus, DStabBranch>) simuCtx.getDStabilityNet();
	    
	    // build sequence network
//	    SequenceNetworkBuilder seqNetHelper = new SequenceNetworkBuilder(dsNet,true);
//	    seqNetHelper.buildSequenceNetwork(SequenceCode.NEGATIVE);
//	    seqNetHelper.buildSequenceNetwork(SequenceCode.ZERO);
//	    
//	    System.out.println(dsNet.net2String());

	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		aclfAlgo.setTolerance(1.0E-6);
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005);
		dstabAlgo.setTotalSimuTimeSec(0.050);
		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus39-mach1"));
		

		StateMonitor sm = new StateMonitor();
		sm.addBusStdMonitor(new String[]{"Bus4","Bus7","Bus8","Bus504","Bus507","Bus508"});
		sm.addGeneratorStdMonitor(new String[]{"Bus30-mach1","Bus37-mach1","Bus34-mach1","Bus38-mach1","Bus39-mach1"});
		sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_2@Bus504");
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(5);
		
		//output AC motor parameters
		LD1PAC ac_504 = (LD1PAC) dsNet.getBus("Bus504").getDynLoadModelList().get(0);
		System.out.println ("ac motor at 504"+ac_504.toString());
		
		//IpssLogger.getLogger().setLevel(Level.INFO);
		
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus4",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0,0),null,1.0d,0.07),"3phaseFault@Bus17");
		

		if (dstabAlgo.initialization()) {
			double t1 = System.currentTimeMillis();
			System.out.println("time1="+t1);
			System.out.println("Running DStab simulation ...");
			//System.out.println(dsNet.getMachineInitCondition());
			dstabAlgo.performSimulation();
			double t2 = System.currentTimeMillis();
			System.out.println("used time="+(t2-t1)/1000.0);

		}
//		System.out.println(sm.toCSVString(sm.getMachPeTable()));
		System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
		System.out.println(sm.toCSVString(sm.getAcMotorQTable()));
		System.out.println(sm.toCSVString(sm.getAcMotorStateTable()));
		System.out.println(sm.toCSVString(sm.getAcMotorVtTable()));
//		FileUtil.writeText2File("D://ieee39_pos_3P@Bus28_GenAngle.csv", sm.toCSVString(sm.getMachAngleTable()));
//		FileUtil.writeText2File("D://ieee39_pos_3P@Bus28_GenSpd.csv", sm.toCSVString(sm.getMachSpeedTable()));

        //voltage
		System.out.println("Bus voltage at Bus 507 at index 50 = "+sm.getBusVoltTable().get("Bus507").get(50).value);
		assertTrue(Math.abs(sm.getBusVoltTable().get("Bus507").get(20).value-0.97123)<1.0E-4);
		assertTrue(Math.abs(sm.getBusVoltTable().get("Bus507").get(50).value-0.73405)<1.0E-4);
	}

}
