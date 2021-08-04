package org.interpss.core.dstab;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.util.PerformanceTimer;
import org.junit.Test;

import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class DStab_Kundur_2area_Test  extends DStabTestSetupBase{
	
	
	@Test
	public void test_Kunder_2area_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testdata/adpter/psse/v30/Kundur_2area/Kundur_2area_v30.raw",
				"testData/adpter/psse/v30/Kundur_2area/kundur_2area_full_tgov1.dyr"
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
	    
	    DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		//aclfAlgo.setLfMethod(AclfMethod.PQ);
		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(5);

		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		//Bus fault
		//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0.0),null,1.0d,0.05),"3phaseFault@Bus5");
        
		dsNet.addDynamicEvent(DStabObjectFactory.createBranchSwitchEvent("Bus7->Bus8(1)", 1.0, dsNet),"Line Bus7->Bus8(1) out at 1s");
		dsNet.addDynamicEvent(DStabObjectFactory.createBranchSwitchEvent("Bus7->Bus8(2)", 1.0, dsNet),"Line Bus7->Bus8(2) out at 1s");
        
        
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(25);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
		//for(int i =1; i<20;i++){
			
			

		if (dstabAlgo.initialization()) {
			//System.out.println(dsNet.getMachineInitCondition());
			
			//System.out.println("Running DStab simulation ...");
			timer.start();
			dstabAlgo.performSimulation();
			
			timer.logStd("total simu time: ");
			}
			//dstabAlgo.performOneStepSimulation();

		//}
		System.out.println("Mach Angles (deg):\n"+sm.toCSVString(sm.getMachAngleTable()));
		
//		System.out.println("Mach Pe (pu) :\n"+sm.toCSVString(sm.getMachPeTable()));
		System.out.println("Mach Pm (pu) :\n"+sm.toCSVString(sm.getMachPmTable()));
//		
//		System.out.println("Volages Mag (pu):\n"+sm.toCSVString(sm.getBusVoltTable()));
		
		System.out.println("Volages Angle (Deg):\n"+sm.toCSVString(sm.getBusAngleTable()));
	    
	}
	//@Test
	public void test_Kunder_VSCHVDC_Dstab() throws InterpssException{
		IpssCorePlugin.init();
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testdata/adpter/psse/v30/Kundur_2area/Kundur_2area_v30.raw",
				"testData/adpter/psse/v30/Kundur_2area/Kundur_2area.dyr"
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
	    
	    //addVSCHVDC2Net
	    
	}
	
	private void addVSCHVDC2Net(BaseDStabNetwork dsNet, String fromBusId, String toBusId){
		
	}
	
	

}
