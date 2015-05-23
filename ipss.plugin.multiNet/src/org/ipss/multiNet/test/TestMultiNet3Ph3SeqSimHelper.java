package org.ipss.multiNet.test;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.util.PerformanceTimer;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDStabSimuHelper;
import org.ipss.multiNet.algo.SubNetworkProcessor;
import org.ipss.threePhase.dynamic.DStabNetwork3Phase;
import org.ipss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestMultiNet3Ph3SeqSimHelper {
	
	//Note:network equivalent part is tested by the NetworkEquivTest
	
	//This test case serves to test the 1)3ph/3seq subnetwork solution;2) boundary subsystem formation 
	//3) calculation of the  3seq current of boundary tie-lines; 4) 3ph/3-seq co-simulation
	
	/**
	 * Test the three-phase subnetwork is accurately equivalentized. 
	 * @throws InterpssException
	 */
	@Test
	public void test_3phaseSubNet_IEEE9Bus() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
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
		
		
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		    proc.addSubNetInterfaceBranch("Bus4->Bus5(0)");
		    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)");
		
		    
		    proc.splitFullSystemIntoSubsystems(false);
		    
		    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
		    proc.set3PhaseSubNetByBusId("Bus5");
		    
		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		   // subnetwork only bus 5;
		  DStabNetwork3Phase subNet_1 = (DStabNetwork3Phase) proc.getSubNetworkList().get(1);
		  
		  
		   // mNetHelper.set3PhaseSubNetworkId(subNet_1.getId()); 
		  
		    DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(subNet_1, IpssCorePlugin.getMsgHub());
		    
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1.0d);

			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			//subNet_1.addDynamicEvent(create3PhaseFaultEvent("Bus6", subNet_1,1.01d,0.05),"3phaseFault@Bus6");
			//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,1.0d,0.05),"3phaseFault@Bus5");
	        
			
			StateMonitor sm = new StateMonitor();
			sm.addBusStdMonitor(new String[]{"Bus5"});
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			
			IpssLogger.getLogger().setLevel(Level.INFO);
			
			PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
			
			
			if (dstabAlgo.initialization()) {
				
				System.out.println("Running DStab simulation ...");
				timer.start();
				dstabAlgo.performSimulation();
				
				timer.logStd("total simu time: ");
			 }
			
			// System.out.println(sm.toCSVString(sm.getMachPeTable()));
			
		     System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		  
		    
	}
	
	/**
	 * Test the three-phase subnetwork is accurately equivalentized. 
	 * @throws InterpssException
	 */
	@Test
	public void test_3phase3SeqMultiSubNetTS_IEEE9Bus() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
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
		
		
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		    proc.addSubNetInterfaceBranch("Bus4->Bus5(0)");
		    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)");
		
		    
		    proc.splitFullSystemIntoSubsystems(false);
		    
		    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
		    proc.set3PhaseSubNetByBusId("Bus5");
		    
		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		   // subnetwork only bus 5;
		  DStabNetwork3Phase subNet_1 = (DStabNetwork3Phase) proc.getSubNetworkList().get(1);
		  
		  
		   // mNetHelper.set3PhaseSubNetworkId(subNet_1.getId()); 
		  
		    DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(subNet_1, IpssCorePlugin.getMsgHub());
		    
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1.0d);

			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			//subNet_1.addDynamicEvent(create3PhaseFaultEvent("Bus6", subNet_1,1.01d,0.05),"3phaseFault@Bus6");
			//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,1.0d,0.05),"3phaseFault@Bus5");
	        
			
			StateMonitor sm = new StateMonitor();
			sm.addBusStdMonitor(new String[]{"Bus5"});
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			
			IpssLogger.getLogger().setLevel(Level.INFO);
			
			PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
			
			
			if (dstabAlgo.initialization()) {
				
				System.out.println("Running DStab simulation ...");
				timer.start();
				dstabAlgo.performSimulation();
				
				timer.logStd("total simu time: ");
			 }
			
			// System.out.println(sm.toCSVString(sm.getMachPeTable()));
			
		     System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		  
		    
	}

}
