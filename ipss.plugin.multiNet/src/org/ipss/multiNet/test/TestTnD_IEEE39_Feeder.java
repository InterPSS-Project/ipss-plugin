package org.ipss.multiNet.test;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDStabSimuHelper;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDStabSolverImpl;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDynEventProcessor;
import org.ipss.multiNet.algo.SubNetworkProcessor;
import org.ipss.threePhase.dynamic.DStabNetwork3Phase;
import org.ipss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestTnD_IEEE39_Feeder {
	
	@Test
	public void test_3phase3Seq_IEEE39Bus_Feeder() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE39Bus/IEEE39bus_v30.raw",
				"testData/IEEE39Bus/IEEE39bus_v30.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE39Bus/IEEE39bus.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		//
		//
		//TODO select 6 buses in the load center to replace them by detailed feeders

//		
//		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
//		 /*
//		  * 25-26
//		  * 17-18
//		  * 3-4
//		  * 8-9
//		  */
//		    proc.addSubNetInterfaceBranch("Bus3->Bus4(1)");
//		    proc.addSubNetInterfaceBranch("Bus9->Bus39(1)");
//		    proc.addSubNetInterfaceBranch("Bus15->Bus16(1)");
//		    proc.addSubNetInterfaceBranch("Bus16->Bus17(1)");
//		
//		    
//		    proc.splitFullSystemIntoSubsystems(false);
//		    
//		    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
//		    proc.set3PhaseSubNetByBusId("Bus17");
//		    
//		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
//		  
		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		    
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1d);
			
			StateMonitor sm = new StateMonitor();
			sm.addBusStdMonitor(new String[]{"Bus17","Bus18","Bus15","Bus16","Bus28"});
			sm.addGeneratorStdMonitor(new String[]{"Bus30-mach1","Bus31-mach1","Bus34-mach1","Bus39-mach1"});
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			
			IpssLogger.getLogger().setLevel(Level.INFO);
			
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0,0),null,0.5d,0.05),"3phaseFault@Bus5");
			
	        // TODO a special 3-phase 3seq dstab algorithm object, with the following two setting as default
			//dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
			//dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
		  
			 if(dstabAlgo.initialization()){
				 dstabAlgo.performSimulation();
			 }
		   
			 System.out.println(sm.toCSVString(sm.getMachPeTable()));
				
		     System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		 }

}
