package org.interpss.core.dstab.relay;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.DStabTestSetupBase;
import org.interpss.dstab.relay.impl.GenOverSpeedRelayModel;
import org.interpss.dstab.relay.impl.LoadUFShedRelayModel;
import org.interpss.dstab.relay.impl.LoadUVShedRelayModel;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.datatype.Triplet;
import org.interpss.numeric.util.PerformanceTimer;
import org.junit.Test;

import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.devent.LoadChangeEventType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestRelayOverSpeedGenTrip extends DStabTestSetupBase{
	
	@Test
	public void test_IEEE9Bus_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
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
	    
	   
	    DStabBus bus5 = dsNet.getBus("Bus5");
	    
	    
	    DStabBus bus1 = dsNet.getBus("Bus1");
        
	    GenOverSpeedRelayModel genOS = new GenOverSpeedRelayModel(bus1, "1");
	    
	    //Triplet <speed, time, fraction>
	    Triplet os1 = new Triplet(1.01, 0.05,1);
	 
	    List<Triplet> os_settings= new ArrayList<>();
	    os_settings.add(os1);
	    genOS.setRelaySetPoints(os_settings);
	    
//	    System.out.println(dsNet.net2String());
	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(5);
		
		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
//		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0.0),null,1.0d,0.1),"3phaseFault@Bus5");
//        
//		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0.0),null,1.5d,0.1),"3phaseFault@Bus5");
//        
		
		
		dsNet.addDynamicEvent(DStabObjectFactory.createLoadChangeEvent("Bus5", dsNet,LoadChangeEventType.FIXED_TIME, -0.5, 1.0),"LoadReduce50%@Bus5");
		        
        
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus1"});
		// set the output handler
				dstabAlgo.setSimuOutputHandler(sm);
				dstabAlgo.setOutPutPerSteps(25);
		
		IpssLogger.getLogger().setLevel(Level.INFO);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		//for(int i =1; i<20;i++){
			
		System.out.println("Init Bus 5 load = "+bus5.calStaticLoad().toString());
		if (dstabAlgo.initialization()) {
			System.out.println(dsNet.getMachineInitCondition());
			
			System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();
			
			timer.logStd("total simu time: ");
		}
			//dstabAlgo.performOneStepSimulation();

		//}

		System.out.println("Mach Speed =\n" + sm.toCSVString(sm.getMachSpeedTable()));
		
		System.out.println("Mach status=\n" + sm.toCSVString(sm.getMachStatusTable()));
		
		assertTrue(genOS.getTrippedFraction()==1.0);
		
//		FileUtil.writeText2File("output/ieee9_bus5_machPe_v5_03172015.csv",sm.toCSVString(sm.getMachPeTable()));

	}
	
	

}
