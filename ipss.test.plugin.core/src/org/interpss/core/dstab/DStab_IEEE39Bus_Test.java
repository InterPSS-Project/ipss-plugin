package org.interpss.core.dstab;

import static com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType.MachineState;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.logging.Level;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.util.Number2String;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.Bus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateVariableRecorder;
import com.interpss.dstab.cache.StateVariableRecorder.StateRecord;
import com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class DStab_IEEE39Bus_Test  extends DStabTestSetupBase{
		
		//@Test
		public void test_IEEE39Bus_Dstab(){
			IpssCorePlugin.init();
			PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
			assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
					"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus_v30.raw",
					//"testData/adpter/psse/v30/IEEE39Bus/ieee9.seq",
					"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus.dyr"
			}));
			DStabModelParser parser =(DStabModelParser) adapter.getModel();
			
			System.out.println(parser.toXmlDoc());

			
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
		    System.out.println(dsNet.net2String());
//		    long num=0;
//		    for(Bus b: dsNet.getBusList()){
//		    	b.setNumber(num++);
//		    }
//		    dsNet.setBusNumberArranged(true);
		    
			DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			assertTrue(aclfAlgo.loadflow());
			System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.001);
			dstabAlgo.setTotalSimuTimeSec(0.1);
			dstabAlgo.setRefMachine(dsNet.getMachine("Bus31-mach1"));
			
			StateVariableRecorder ssRecorder = new StateVariableRecorder(0.0001);
			ssRecorder.addCacheRecords("Bus30-mach1",      // mach id 
					MachineState,    // record type
					DStabOutSymbol.OUT_SYMBOL_MACH_ANG,       // state variable name
					0.01,                                      // time steps for recording 
					10);                                      // total points to record 
			
			ssRecorder.addCacheRecords("Bus30-mach1",      // mach id 
					MachineState,    // record type
					DStabOutSymbol.OUT_SYMBOL_MACH_PM,       // state variable name
					0.01,                                      // time steps for recording 
					10);                                      // total points to record
			ssRecorder.addCacheRecords("Bus30-mach1",      // mach id 
					StateVarRecType.MachineState,    // record type
					DStabOutSymbol.OUT_SYMBOL_MACH_Efd,       // state variable name
					0.01,                                      // time steps for recording 
					10);
			// set the output handler
			dstabAlgo.setSimuOutputHandler(ssRecorder);
			
			IpssLogger.getLogger().setLevel(Level.FINE);
			if (dstabAlgo.initialization()) {
				//System.out.println(dsNet.net2String());
				System.out.println("Running DStab simulation ...");
				dstabAlgo.performSimulation();
				//dstabAlgo.solveDEqnStep(true);

			}
			/*
			 * Init angle 
	         */
			// output recorded simulation results
			List<StateRecord> list = ssRecorder.getMachineRecords(
					"Bus30-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_ANG);
			System.out.println("\n\n Bus30 Machine Anagle");
			for (StateRecord rec : list) {
				System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
			}

			
			list = ssRecorder.getMachineRecords(
					"Bus30-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_PM);
			System.out.println("\n\n Bus30 Machine PM");
			for (StateRecord rec : list) {
				System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
			}
			list = ssRecorder.getMachineRecords(
					"Bus30-mach1", StateVarRecType.MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_Efd);
			System.out.println("\n\n Bus30 Machine Efd");
			for (StateRecord rec : list) {
				System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
			}
			
			/*
			 * Bus30 Machine Anagle
				0.0000, -7.41579
				0.0010, -7.41579
				0.0020, -7.41579
				0.0030, -7.41579
				0.0040, -7.41579
				0.0050, -7.41579
				0.0060, -7.41579
				0.0070, -7.41579
				0.0080, -7.41579
				0.0090, -7.41579
				
				
				 Bus30 Machine PM
				0.0000, 0.83333
				0.0010, 0.83333
				0.0020, 0.83333
				0.0030, 0.83333
				0.0040, 0.83333
				0.0050, 0.83333
				0.0060, 0.83333
				0.0070, 0.83333
				0.0080, 0.83333
				0.0090, 0.83333
				
				
				 Bus30 Machine Efd
				0.0000, 2.65386
				0.0010, 2.65386
				0.0020, 2.65386
				0.0030, 2.65386
				0.0040, 2.65386
				0.0050, 2.65386
				0.0060, 2.65386
				0.0070, 2.65386
				0.0080, 2.65386
				0.0090, 2.65386
				*/
			
		}
		
		@Test
		public void IEEE9_Dstab_benchMark(){
			IpssCorePlugin.init();
			PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
			assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
					"testData/adpter/psse/v30/IEEE39Bus/IEEE39bus_v30.raw",
					//"testData/adpter/psse/v30/IEEE39Bus/ieee9.seq",
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
			dstabAlgo.setTotalSimuTimeSec(0.1);
			dstabAlgo.setRefMachine(dsNet.getMachine("Bus31-mach1"));
			
			double[] timePoints   = {0.0,    0.004,    0.007,    0.009},
		      			 machPePoints = {0.83333, 0.83333,   0.83333,   0.83333},
		      			 machAngPoints  = {-7.41583, -7.41583,  -7.41583,   -7.41583},
		      			 machEfdPoints  = {2.65386, 2.65386,   2.65386,   2.65386};
			
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
						DStabOutSymbol.OUT_SYMBOL_MACH_PE) < 0.0001);
			assertTrue(stateTestRecorder.diffTotal("Bus30-mach1", MachineState, 
						DStabOutSymbol.OUT_SYMBOL_MACH_ANG) < 0.0001);
			assertTrue(stateTestRecorder.diffTotal("Bus30-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_Efd) < 0.0001);
		}

}
