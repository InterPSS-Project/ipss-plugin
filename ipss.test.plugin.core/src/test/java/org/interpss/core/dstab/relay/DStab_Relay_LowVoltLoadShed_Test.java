package org.interpss.core.dstab.relay;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.DStabTestSetupBase;
import org.interpss.dstab.relay.LVSHLoadRelayModel;
import org.interpss.numeric.datatype.Triplet;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.odm.mapper.ODMDStabParserMapper;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class DStab_Relay_LowVoltLoadShed_Test extends DStabTestSetupBase{
	
	@Test
	public void test_IEEE9Bus_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				//"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabilityNetwork dsNet =(DStabilityNetwork) simuCtx.getDStabilityNet();
	    
	    //add the LVSH load shedding model
	    DStabBus bus5 = dsNet.getBus("Bus5");
	    
	    LVSHLoadRelayModel lvsh = new LVSHLoadRelayModel(bus5, "1");
	    
	    //Triplet <voltage, time, fraction>
	    Triplet vtf1 = new Triplet(0.6, 0.05,0.2);
	    Triplet vtf2 = new Triplet(0.4, 0.06,0.3);
	    List<Triplet> settings= new ArrayList<>();
	    settings.add(vtf1);
	    settings.add(vtf2);
	  
	    lvsh.setRelaySetPoints(settings);
	    
	    dsNet.getRelayModelList().add(lvsh);


//	    System.out.println(dsNet.net2String());
	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(10);
		
		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0.0),null,1.0d,0.08),"3phaseFault@Bus5");
        
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus1"});
		// set the output handler
				dstabAlgo.setSimuOutputHandler(sm);
				dstabAlgo.setOutPutPerSteps(5);
		
		IpssLogger.getLogger().setLevel(Level.INFO);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		//for(int i =1; i<20;i++){
			
			

		if (dstabAlgo.initialization()) {
			System.out.println(dsNet.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();
			
			timer.logStd("total simu time: ");
			}
			//dstabAlgo.performOneStepSimulation();

		//}
		//System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		
//		System.out.println(sm.toCSVString(sm.getMachPeTable()));
		
//		FileUtil.writeText2File("output/ieee9_bus5_machPe_v5_03172015.csv",sm.toCSVString(sm.getMachPeTable()));

	}
	
	

}
