package org.interpss.core.dstab;

import static com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType.MachineState;
import static org.junit.Assert.assertTrue;

import java.util.Hashtable;
import java.util.List;
import java.util.logging.Level;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.algo.SequenceNetworkBuilder;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.util.Number2String;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.Bus;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateVariableRecorder;
import com.interpss.dstab.cache.StateVariableRecorder.StateRecord;
import com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.devent.DynamicEvent;
import com.interpss.dstab.devent.DynamicEventType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class DStab_IEEE39Bus_Test  extends DStabTestSetupBase{
		
		@Test
		public void test_IEEE39Bus_Dstab() throws InterpssException{
			IpssCorePlugin.init();
			PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
			assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
					"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus_v30.raw",
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
			
			
		    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
		    
		    // build sequence network
		    SequenceNetworkBuilder seqNetHelper = new SequenceNetworkBuilder(dsNet,true);
		    seqNetHelper.buildSequenceNetwork(SequenceCode.NEGATIVE);
		    seqNetHelper.buildSequenceNetwork(SequenceCode.ZERO);
		    
		    System.out.println(dsNet.net2String());

		    
			DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			assertTrue(aclfAlgo.loadflow());
			System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.004167);
			dstabAlgo.setTotalSimuTimeSec(0);
			dstabAlgo.setRefMachine(dsNet.getMachine("Bus31-mach1"));
			dsNet.setNetEqnIterationNoEvent(6);
			dsNet.addDynamicEvent(create3PhaseFaultEvent("Bus2",dsNet,1,0.0833),"3phaseFault@Bus2");
			

			StateMonitor sm = new StateMonitor();
			sm.addGeneratorStdMonitor(new String[]{"Bus30-mach1","Bus31-mach1","Bus32-mach1","Bus33-mach1","Bus34-mach1"
					,"Bus35-mach1","Bus36-mach1","Bus37-mach1","Bus38-mach1",});

			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(2);
			
			IpssLogger.getLogger().setLevel(Level.INFO);
			if (dstabAlgo.initialization()) {
				System.out.println("Running DStab simulation ...");
				System.out.println(dsNet.getMachineInitCondition());
				dstabAlgo.performSimulation();

			}
			//System.out.println(sm.toCSVString(sm.getMachAngleTable()));
			//System.out.println(sm.toCSVString(sm.getMachEfdTable()));
			FileUtil.writeText2File("E:/ieee39_angle.csv", sm.toCSVString(sm.getMachAngleTable()));
			FileUtil.writeText2File("E:/ieee39_efd.csv", sm.toCSVString(sm.getMachEfdTable()));
	
		}
		
		//@Test
		public void IEEE39_Dstab_benchMark() throws InterpssException{
			IpssCorePlugin.init();
			PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
			assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
					"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus_v30.raw",
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
			
			
		    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
		    //System.out.println(dsNet.net2String());

		    
			DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			assertTrue(aclfAlgo.loadflow());
			System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.001);
			dstabAlgo.setTotalSimuTimeSec(1);
			dstabAlgo.setRefMachine(dsNet.getMachine("Bus31-mach1"));
			//dsNet.setNetEqnIterationNoEvent(1);
			double[] timePoints   = {0.0,    0.04,    0.3,    0.5},
		      			 machPePoints = {0.83333, 0.83333,   0.83333,   0.83333},
		      			 machAngPoints  = {-8.58308, -8.58308,  -8.58308,   -8.58308},
		      			 machEfdPoints  = {3.06708, 3.06708,   3.06708,   3.06708};
			
			StateVariableRecorder stateTestRecorder = new StateVariableRecorder(0.0001);
			stateTestRecorder.addTestRecords("Bus30-mach1", MachineState, 
						DStabOutSymbol.OUT_SYMBOL_MACH_PE, timePoints, machPePoints);
			stateTestRecorder.addTestRecords("Bus30-mach1", MachineState, 
						DStabOutSymbol.OUT_SYMBOL_MACH_ANG, timePoints, machAngPoints);
			stateTestRecorder.addTestRecords("Bus30-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_Efd, timePoints, machEfdPoints);
			dstabAlgo.setSimuOutputHandler(stateTestRecorder);
				

			if (dstabAlgo.initialization()) {
				//System.out.println(simuCtx.getDStabilityNet().net2String());

				System.out.println("Running DStab simulation ...");
				assertTrue(dstabAlgo.performSimulation());
			}
				
			assertTrue(stateTestRecorder.diffTotal("Bus30-mach1", MachineState, 
						DStabOutSymbol.OUT_SYMBOL_MACH_PE) < 0.002);
			assertTrue(stateTestRecorder.diffTotal("Bus30-mach1", MachineState, 
						DStabOutSymbol.OUT_SYMBOL_MACH_ANG) < 0.01);
			assertTrue(stateTestRecorder.diffTotal("Bus30-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_Efd) < 0.001);
		}
		
		private DynamicEvent create3PhaseFaultEvent(String faultBusId, DStabilityNetwork net,double startTime, double durationTime){
		       // define an event, set the event id and event type.
				DynamicEvent event1 = DStabObjectFactory.createDEvent("BusFault3P@"+faultBusId, "Bus Fault 3P@"+faultBusId, 
						DynamicEventType.BUS_FAULT, net);
				event1.setStartTimeSec(startTime);
				event1.setDurationSec(durationTime);
				
		      // define a bus fault
				DStabBus faultBus = net.getDStabBus(faultBusId);
				AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus Fault 3P@"+faultBusId, net);
		  		fault.setBus(faultBus);
				fault.setFaultCode(SimpleFaultCode.GROUND_3P);
				fault.setZLGFault(NumericConstant.SmallScZ);

		      // add this fault to the event, must be consist with event type definition before.
				event1.setBusFault(fault); 
				return event1;
		}

}
