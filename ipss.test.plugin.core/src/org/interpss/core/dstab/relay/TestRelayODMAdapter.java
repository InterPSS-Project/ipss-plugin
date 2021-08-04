package org.interpss.core.dstab.relay;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.DStabTestSetupBase;
import org.interpss.dstab.relay.impl.GenUnderOverFreqTripRelayModel;
import org.interpss.dstab.relay.impl.GenUnderOverVoltTripRelayModel;
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
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabLoad;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestRelayODMAdapter extends DStabTestSetupBase{
	
	@Test
	public void test_IEEE9Bus_Dstab_UVLS_UFLS() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_fullModel_relay.dyr"
				//"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
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
	    
	    // build sequence network
//	    SequenceNetworkBuilder seqNetHelper = new SequenceNetworkBuilder(dsNet,true);
//	    seqNetHelper.buildSequenceNetwork(SequenceCode.NEGATIVE);
//	    seqNetHelper.buildSequenceNetwork(SequenceCode.ZERO);
//	    
        BaseDStabBus bus5 = (BaseDStabBus) dsNet.getBus("Bus5");
        //DStabLoad load1 = (DStabLoad) bus5.getContributeLoad("1");
        
        LoadUFShedRelayModel lds3 = (LoadUFShedRelayModel) bus5.getDynamicBusDeviceList().get(0);
        
        assertTrue(lds3.getRelaySetPoints().size()==1);
        Triplet setting1 = lds3.getRelaySetPoints().get(0);
        assertTrue(setting1.getValue1()==59.300);
        assertTrue(setting1.getValue2()==15.000);
        assertTrue(setting1.getValue3()==1.0);
        
        LoadUVShedRelayModel lvs3 = (LoadUVShedRelayModel) bus5.getDynamicBusDeviceList().get(1);
        
        assertTrue(lvs3.getRelaySetPoints().size()==2);
        
        Triplet setting2 = lvs3.getRelaySetPoints().get(0);
        assertTrue(setting2.getValue1()==0.91300);
        assertTrue(setting2.getValue2()==6.000);
        assertTrue(setting2.getValue3()==0.24500);
        
        Triplet setting3 = lvs3.getRelaySetPoints().get(1);
        assertTrue(setting3.getValue1()==0.91300);
        assertTrue(setting3.getValue2()==10.000);
        assertTrue(setting3.getValue3()==0.54500);
        
        
        BaseDStabBus bus1 = (BaseDStabBus) dsNet.getBus("Bus1");
        
        assertTrue(bus1.getDynamicBusDeviceList().size()==2);
        
        GenUnderOverFreqTripRelayModel freqRelay = (GenUnderOverFreqTripRelayModel) bus1.getDynamicBusDeviceList().get(0);
       
        
        Triplet setting4 =  freqRelay.getRelaySetPoints().get(0);
        assertTrue(setting4.getValue1()==0.95);
        assertTrue(setting4.getValue2()==10.000);
        assertTrue(setting4.getValue3()==1.0);
        
        Triplet setting5 =  freqRelay.getRelaySetPoints().get(1);
        assertTrue(setting5.getValue1()==5.0);
        assertTrue(setting5.getValue2()==10.000);
        assertTrue(setting5.getValue3()==1.0);
        
        assertTrue(freqRelay.getUnderOverFlagList().get(0)==0.0);
        assertTrue(freqRelay.getUnderOverFlagList().get(1)==1.0);
        
        GenUnderOverVoltTripRelayModel voltRelay = (GenUnderOverVoltTripRelayModel) bus1.getDynamicBusDeviceList().get(1);
        
        Triplet setting6 =  voltRelay.getRelaySetPoints().get(0);
        assertTrue(setting6.getValue1()==0.75);
        assertTrue(setting6.getValue2()==10.000);
        assertTrue(setting6.getValue3()==1.0);
        
        Triplet setting7 =  voltRelay.getRelaySetPoints().get(1);
        assertTrue(setting7.getValue1()==5.0);
        assertTrue(setting7.getValue2()==10.000);
        assertTrue(setting7.getValue3()==1.0);
        
        assertTrue(voltRelay.getUnderOverFlagList().get(0)==0.0);
        assertTrue(voltRelay.getUnderOverFlagList().get(1)==1.0);
       
	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(15);

		dstabAlgo.setRefMachine(dsNet.getMachine("Bus2-mach1"));
		
		//Bus fault
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0.0),null,1.0d,0.05),"3phaseFault@Bus5");
        
		//generator tripping event 
		//dsNet.addDynamicEvent(DStabObjectFactory.createGeneratorTripEvent("Bus1", "1", dsNet, 1),"Bus1_Mach1_trip_1sec");
        
        
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
		
		System.out.println("Mach Pe (pu) :\n"+sm.toCSVString(sm.getMachPeTable()));
		
		System.out.println("Volages (pu):\n"+sm.toCSVString(sm.getBusVoltTable()));
		
		System.out.println("Bus freq (pu):\n"+sm.toCSVString(sm.getBusFreqTable()));
	}

}
