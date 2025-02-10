package org.interpss.core.dstab;

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfNetHelper;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class DStab_MultiIslands_IEEE39 extends DStabTestSetupBase{
	
	@Test
	public void test_IEEE39Bus_Dstab_multi_islands() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus_v30.raw",
				"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus_v30.seq",
				"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());
        
		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    BaseDStabNetwork dsNet =simuCtx.getDStabilityNet();
	
	    
	    dsNet.getBranch("Bus1", "Bus39", "1",false).setStatus(false);
	    dsNet.getBranch("Bus3", "Bus4", "1",false).setStatus(false);
	    dsNet.getBranch("Bus16", "Bus17", "1",false).setStatus(false);
	    
	    
	    List<String> list = new AclfNetHelper(dsNet).calIslandBuses(); 
		if (list.size() > 0) 
			 System.out.println("There are island buses: " + list.toString());
			
		
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		aclfAlgo.setTolerance(1.0E-6);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005);
		dstabAlgo.setTotalSimuTimeSec(10.0);
		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus39-mach1"));
		

		StateMonitor sm = new StateMonitor();
		sm.addBusStdMonitor(new String[]{"Bus17","Bus18","Bus15","Bus16","Bus28"});
		sm.addGeneratorStdMonitor(new String[]{"Bus30-mach1","Bus37-mach1","Bus34-mach1","Bus38-mach1","Bus39-mach1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(10);
		//dstabAlgo.setRefMachine(dsNet.getMachine("Bus39-mach1"));
		
		//IpssLogger.getLogger().setLevel(Level.INFO);
		
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus28",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0,0),null,1.0d,0.07),"3phaseFault@Bus17");
	

		if (dstabAlgo.initialization()) {
			double t1 = System.currentTimeMillis();
			System.out.println("time1="+t1);
			System.out.println("Running DStab simulation ...");
			//System.out.println(dsNet.getMachineInitCondition());
			dstabAlgo.performSimulation();
			double t2 = System.currentTimeMillis();
			System.out.println("used time="+(t2-t1)/1000.0);

		}
		System.out.println(sm.toCSVString(sm.getMachPeTable()));
		System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		
	}
	

}
