package org.interpss.core.dstab;

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
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.NumericConstant;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.Bus;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabGen;
import com.interpss.dstab.DStabLoad;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.dstab.devent.DynamicEvent;
import com.interpss.dstab.devent.DynamicEventType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class DStab_IEEE300Bus_Test  extends DStabTestSetupBase{
		
		@Test
		public void test_IEEE300_Dstab() throws InterpssException{
			IpssCorePlugin.init();
			IpssLogger.getLogger().setLevel(Level.OFF);
			PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
			assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
					"testData/adpter/psse/v30/IEEE300/IEEE300Bus_modified_noHVDC.raw",
					"testData/adpter/psse/v30/IEEE300/IEEE300_dyn_v2.dyr"
			}));
			DStabModelParser parser =(DStabModelParser) adapter.getModel();
			
			//System.out.println(parser.toXmlDoc());
            
			
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    BaseDStabNetwork<?, ?> dsNet =simuCtx.getDStabilityNet();
		    
		    //dsNet.setBypassDataCheck(true);
			DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			
			aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

			aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
			aclfAlgo.setTolerance(1.0E-6);
			assertTrue(aclfAlgo.loadflow());
			System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.002);
			dstabAlgo.setTotalSimuTimeSec(10.0);
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus10030-mach1"));
			

			StateMonitor sm = new StateMonitor();
			
			sm.addBusStdMonitor(new String[]{"Bus10000","Bus10001","Bus10015","Bus10016","Bus10028"});
			sm.addGeneratorStdMonitor(new String[]{"Bus10003-mach1","Bus10005-mach1","Bus10008-mach1","Bus10009-mach1"});
	
			
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(25);
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus39-mach1"));
			
			IpssLogger.getLogger().setLevel(Level.INFO);
			
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus10003",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0,0),null,1.0d,0.08),"3phaseFault@Bus17");
			

			if (dstabAlgo.initialization()) {
				double t1 = System.currentTimeMillis();
				System.out.println("time1="+t1);
				System.out.println("Running DStab simulation ...");
				System.out.println(dsNet.getMachineInitCondition());
				dstabAlgo.performSimulation();
				double t2 = System.currentTimeMillis();
				System.out.println("used time="+(t2-t1)/1000.0);

			}
			System.out.println(sm.toCSVString(sm.getMachSpeedTable()));
//			System.out.println(sm.toCSVString(sm.getMachAngleTable()));
			System.out.println(sm.toCSVString(sm.getBusVoltTable()));
			
			assertTrue(sm.getMachSpeedTable().get("Bus10003-mach1").get(0).getValue()-sm.getMachSpeedTable().get("Bus10003-mach1").get(10).getValue()<1.0E-4);
			assertTrue(sm.getMachSpeedTable().get("Bus10009-mach1").get(0).getValue()-sm.getMachSpeedTable().get("Bus10009-mach1").get(10).getValue()<1.0E-4);
			
		}
		
		//@Test
		public void test_IEEE300_Dstab_compositeLoadModel() throws InterpssException{
			IpssCorePlugin.init();
			IpssLogger.getLogger().setLevel(Level.WARNING);
			PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
			assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
					"testData/adpter/psse/v30/IEEE300/IEEE300Bus_modified_noHVDC.raw",
					"testData/adpter/psse/v30/IEEE300/IEEE300_dyn_v2_cmld.dyr"
			}));
			DStabModelParser parser =(DStabModelParser) adapter.getModel();
			
			//System.out.println(parser.toXmlDoc());
            
			
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    BaseDStabNetwork<?, ?> dsNet =simuCtx.getDStabilityNet();
		    
		    //dsNet.setBypassDataCheck(true);
			DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			
			aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

			aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
			aclfAlgo.setTolerance(1.0E-6);
			assertTrue(aclfAlgo.loadflow());
			System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.002);
			dstabAlgo.setTotalSimuTimeSec(10.0);
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus10030-mach1"));
			
			List<String> busIdList = new ArrayList<>();
			for(Bus b: dsNet.getBusList()) {
				busIdList.add(b.getId());
			}
			
			

			StateMonitor sm = new StateMonitor();
			
			sm.addBusStdMonitor(busIdList.toArray(new String[] {}));
			
			//sm.addBusStdMonitor(new String[]{"Bus10000","Bus10001","Bus10015","Bus10016","Bus10028"});
			sm.addBusStdMonitor(new String[]{"Bus1_loadBus","Bus33_loadBus","Bus562_loadBus"});
			
			sm.addGeneratorStdMonitor(new String[]{"Bus10003-mach1","Bus10005-mach1","Bus10008-mach1","Bus10009-mach1"});
			
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus1_loadBus");
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus33_loadBus");
			sm.addDynDeviceMonitor(DynDeviceType.InductionMotor, "IndMotor_1_A@Bus1_loadBus");
			
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(10);
			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus39-mach1"));
			
			IpssLogger.getLogger().setLevel(Level.INFO);
			
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus70",dsNet,SimpleFaultCode.GROUND_3P,new Complex(0,0),null,1.0d,0.08),"3phaseFault@Bus17");
			

			if (dstabAlgo.initialization()) {
				double t1 = System.currentTimeMillis();
				System.out.println("time1="+t1);
				System.out.println("Running DStab simulation ...");
				System.out.println(dsNet.getMachineInitCondition());
				dstabAlgo.performSimulation();
				double t2 = System.currentTimeMillis();
				System.out.println("used time="+(t2-t1)/1000.0);

			}
			System.out.println(sm.toCSVString(sm.getMachSpeedTable()));
			System.out.println(sm.toCSVString(sm.getMachAngleTable()));
			//System.out.println(sm.toCSVString(sm.getBusVoltTable()));
			System.out.println(sm.toCSVString(sm.getMotorPTable()));
			System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
			
			assertTrue(sm.getMachSpeedTable().get("Bus10003-mach1").get(0).getValue()-sm.getMachSpeedTable().get("Bus10003-mach1").get(10).getValue()<1.0E-4);
			assertTrue(sm.getMotorPTable().get("IndMotor_1_A@Bus1_loadBus").get(0).getValue()-sm.getMotorPTable().get("IndMotor_1_A@Bus1_loadBus").get(10).getValue()<1.0E-4);
			assertTrue(sm.getAcMotorPTable().get("ACMotor_1@Bus1_loadBus").get(0).getValue()-sm.getAcMotorPTable().get("ACMotor_1@Bus1_loadBus").get(10).getValue()<1.0E-4);
			

		}
		
		
		
		private DynamicEvent create3PhaseFaultEvent(String faultBusId, BaseDStabNetwork net,double startTime, double durationTime){
		       // define an event, set the event id and event type.
				DynamicEvent event1 = DStabObjectFactory.createDEvent("BusFault3P@"+faultBusId, "Bus Fault 3P@"+faultBusId, 
						DynamicEventType.BUS_FAULT, net);
				event1.setStartTimeSec(startTime);
				event1.setDurationSec(durationTime);
				
		      // define a bus fault
				BaseDStabBus faultBus = net.getDStabBus(faultBusId);

				AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus Fault 3P@"+faultBusId, net);
		  		fault.setBus(faultBus);
				fault.setFaultCode(SimpleFaultCode.GROUND_3P);
				fault.setZLGFault(NumericConstant.SmallScZ);

		      // add this fault to the event, must be consist with event type definition before.
				event1.setBusFault(fault); 
				return event1;
		}

}
