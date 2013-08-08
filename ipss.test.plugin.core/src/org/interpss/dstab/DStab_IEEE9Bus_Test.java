package org.interpss.dstab;

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
import org.interpss.mapper.odm.ODMAcscDataMapper;
import org.interpss.mapper.odm.ODMDStabDataMapper;
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
	
	@Test
	public void test_IEEE9Bus_Dstab(){
		IpssCorePlugin.init();
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/psse/v30/IEEE9Bus/ieee9.raw",
				"testData/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabDataMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();
	    //System.out.println(dsNet.net2String());
//	    if(!dsNet.isSaturatedMachineParameter()){
//	    	dsNet.setSaturatedMachineParameter(true);
//	    }
	    
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
				StateVarRecType.BusState,    // record type
				DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
				0.001,                                      // time steps for recording 
				10);
		// set the output handler
		dstabAlgo.setSimuOutputHandler(ssRecorder);
		
		IpssLogger.getLogger().setLevel(Level.FINE);
		if (dstabAlgo.initialization()) {
			//System.out.println(dsNet.net2String());
			System.out.println("Running DStab simulation ...");
			//dstabAlgo.performSimulation();
			//dstabAlgo.performOneStepSimulation();
			//System.out.println("Time:"+ dstabAlgo.getInstantSimuTime());
			
			System.out.println("Volt@Bus1 : "+dsNet.getDStabBus("Bus1").getVoltageMag()+" , "
			                    +dsNet.getDStabBus("Bus1").getVoltageAng(UnitType.Deg));
			for(int i = 0;i<10; i++)
			//dstabAlgo.performOneStepSimulation();
			//System.out.println("Time:"+ dstabAlgo.getInstantSimuTime());
			System.out.println("Volt@Bus2 : "+dsNet.getDStabBus("Bus2").getVoltageMag()+" , "
                    +dsNet.getDStabBus("Bus2").getVoltageAng(UnitType.Deg));
			System.out.println("Volt@Bus1 : "+dsNet.getDStabBus("Bus1").getVoltageMag()+" , "
                    +dsNet.getDStabBus("Bus1").getVoltageAng(UnitType.Deg));
		}
		
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
				"Bus2-mach1", StateVarRecType.BusState, DStabOutSymbol.OUT_SYMBOL_BUS_VMAG);
		System.out.println("\n\n Bus2 voltage mag");
		for (StateRecord rec : list) {
			System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
		}
		
	}

}
