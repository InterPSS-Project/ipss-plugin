package org.interpss.core.dstab;

import static com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType.MachineState;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.logging.Level;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.acsc.AcscModelParser;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMAcscParserMapper;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.Number2String;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateVariableRecorder;
import com.interpss.dstab.cache.StateVariableRecorder.StateRecord;
import com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class DStab_IEEE9Bus_Test extends DStabTestSetupBase{
	
	//@Test
	public void test_IEEE9Bus_Dstab(){
		IpssCorePlugin.init();
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
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
	    //System.out.println(dsNet.net2String());
	    
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.001);
		dstabAlgo.setTotalSimuTimeSec(0.01);
		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		StateVariableRecorder ssRecorder = new StateVariableRecorder(0.0001);
		ssRecorder.addCacheRecords("Bus2-mach1",      // mach id 
				MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_MACH_ANG,       // state variable name
				0.001,                                      // time steps for recording 
				10);                                      // total points to record 
		
		ssRecorder.addCacheRecords("Bus2-mach1",      // mach id 
				MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_MACH_PM,       // state variable name
				0.001,                                      // time steps for recording 
				10);                                      // total points to record
		ssRecorder.addCacheRecords("Bus2-mach1",      // mach id 
				StateVarRecType.MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_MACH_Efd,       // state variable name
				0.001,                                      // time steps for recording 
				10);
		// set the output handler
		dstabAlgo.setSimuOutputHandler(ssRecorder);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		if (dstabAlgo.initialization()) {
			//System.out.println(dsNet.net2String());
			System.out.println("Running DStab simulation ...");
			dstabAlgo.performSimulation();
			//dstabAlgo.performOneStepSimulation();

		}
		/*
		 * Init angle 
		 * bus1-mach1: 0.06257886961702232 Rad
		 * bus2-mach2: 1.0672402573811874  Rad  -> relative angle = 57.5628 deg
		 */
		// output recorded simulation results
		List<StateRecord> list = ssRecorder.getMachineRecords(
				"Bus2-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_ANG);
		System.out.println("\n\n Bus2 Machine Anagle");
		for (StateRecord rec : list) {
			System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
		}

		
		list = ssRecorder.getMachineRecords(
				"Bus2-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_PM);
		System.out.println("\n\n Bus2 Machine PM");
		for (StateRecord rec : list) {
			System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
		}
		list = ssRecorder.getMachineRecords(
				"Bus2-mach1", StateVarRecType.MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_Efd);
		System.out.println("\n\n Bus2 Machine Efd");
		for (StateRecord rec : list) {
			System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
		}
		
		
		/*
		 *  Bus2 Machine Anagle
				0.0000, 57.56288
				0.0010, 57.56288
				0.0020, 57.56288
				0.0030, 57.56288
				0.0040, 57.56288
				0.0050, 57.56288
				0.0060, 57.56288
				0.0070, 57.56288
				0.0080, 57.56288
				0.0090, 57.56288
		 */
		
		/*
		 *  Bus2 Machine PM
			0.0000, 1.6300
			0.0010, 1.6300
			0.0020, 1.6300
			0.0030, 1.6300
			0.0040, 1.6300
			0.0050, 1.6300
			0.0060, 1.6300
			0.0070, 1.6300
			0.0080, 1.6300
			0.0090, 1.6300
		 */
		
		/*
		 *  Bus2 Machine Efd
			0.0000, 1.78898
			0.0010, 1.78898
			0.0020, 1.78898
			0.0030, 1.78898
			0.0040, 1.78898
			0.0050, 1.78898
			0.0060, 1.78898
			0.0070, 1.78898
			0.0080, 1.78898
			0.0090, 1.78898
		 */
	}
	
	@Test
	public void IEEE9_Dstab_benchMark(){
		IpssCorePlugin.init();
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
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
		
		
	    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
	    //System.out.println(dsNet.net2String());
   
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.001);
		dstabAlgo.setTotalSimuTimeSec(0.01);
		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		double[] timePoints   = {0.0,    0.004,    0.007,    0.009},
	      			 machPmPoints = {1.6300, 1.6300,   1.6300,   1.6300},
	      			 machAngPoints  = {57.56288, 57.56288,  57.56288,   57.56288},
	      			 machEfdPoints  = {1.78898, 1.78898,   1.78898,   1.78898};
		
		StateVariableRecorder stateTestRecorder = new StateVariableRecorder(0.0001);
		stateTestRecorder.addTestRecords("Bus2-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_PM, timePoints, machPmPoints);
		stateTestRecorder.addTestRecords("Bus2-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_ANG, timePoints, machAngPoints);
		stateTestRecorder.addTestRecords("Bus2-mach1", MachineState, 
				DStabOutSymbol.OUT_SYMBOL_MACH_Efd, timePoints, machEfdPoints);
		dstabAlgo.setSimuOutputHandler(stateTestRecorder);
			

		if (dstabAlgo.initialization()) {
			//System.out.println(simuCtx.getDStabilityNet().net2String());

			System.out.println("Running DStab simulation ...");
			assertTrue(dstabAlgo.performSimulation());
		}
			
		assertTrue(stateTestRecorder.diffTotal("Bus2-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_PM) < 0.0001);
		assertTrue(stateTestRecorder.diffTotal("Bus2-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_ANG) < 0.0001);
		assertTrue(stateTestRecorder.diffTotal("Bus2-mach1", MachineState, 
				DStabOutSymbol.OUT_SYMBOL_MACH_Efd) < 0.0001);
	}
	
	@Test
    public void IEEE9_Dstab_multiGen_Test(){
            IpssCorePlugin.init();
            IpssLogger.getLogger().setLevel(Level.INFO);
            PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
            assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
                            "testData/adpter/psse/v30/IEEE9Bus/ieee9_multiGen.raw",
                            //"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
                            "testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_multiGen.dyr"
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
            aclfAlgo.setTolerance(1.0E-4);
            assertTrue(aclfAlgo.loadflow());
            System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
            
            dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
            dstabAlgo.setSimuStepSec(0.001);
            dstabAlgo.setTotalSimuTimeSec(0.01);
            dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
            
            double[] timePoints   = {0.0,    0.004,    0.007,    0.009},
                                   machPmPoints = {1.6300, 1.6300,   1.6300,   1.6300},
                                   machAngPoints  = {57.56288, 57.56288,  57.56288,   57.56288},
                                   machEfdPoints  = {1.78898, 1.78898,   1.78898,   1.78898};
            
            StateVariableRecorder stateTestRecorder = new StateVariableRecorder(0.0001);
            stateTestRecorder.addTestRecords("Bus2-mach1", MachineState, 
                                    DStabOutSymbol.OUT_SYMBOL_MACH_PM, timePoints, machPmPoints);
            stateTestRecorder.addTestRecords("Bus2-mach1", MachineState, 
                                    DStabOutSymbol.OUT_SYMBOL_MACH_ANG, timePoints, machAngPoints);
            stateTestRecorder.addTestRecords("Bus2-mach1", MachineState, 
                            DStabOutSymbol.OUT_SYMBOL_MACH_Efd, timePoints, machEfdPoints);
            dstabAlgo.setSimuOutputHandler(stateTestRecorder);
                    

            if (dstabAlgo.initialization()) {
                    //System.out.println(simuCtx.getDStabilityNet().net2String());

                    System.out.println("Running DStab simulation ...");
                    assertTrue(dstabAlgo.performSimulation());
            }
                    
            assertTrue(stateTestRecorder.diffTotal("Bus2-mach1", MachineState, 
                                    DStabOutSymbol.OUT_SYMBOL_MACH_PM) < 0.0001);
            assertTrue(stateTestRecorder.diffTotal("Bus2-mach1", MachineState, 
                                    DStabOutSymbol.OUT_SYMBOL_MACH_ANG) < 0.0001);
            assertTrue(stateTestRecorder.diffTotal("Bus2-mach1", MachineState, 
                            DStabOutSymbol.OUT_SYMBOL_MACH_Efd) < 0.0001);
    }

}
