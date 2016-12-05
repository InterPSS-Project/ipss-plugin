package org.interpss.core.dstab;

import static com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType.MachineState;
import static org.junit.Assert.assertTrue;

import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.util.Number2String;
import org.interpss.numeric.util.PerformanceTimer;
import org.junit.Test;

import com.interpss.CoreCommonFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.cache.StateVariableRecorder;
import com.interpss.dstab.cache.StateVariableRecorder.StateRecord;
import com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;



public class testCustomCurInjDstab {
	
	
	@Test
	public void test_1_Port_custom_cur_inj_dtsab() throws Exception{
		double[] recvDataAry =null;
		double[] sendDataAry =null;
		
		/*
		 * load transient stability system data set into DynamicStabilityNetwork object
		 */
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonFactory.getIpssMsgHub();
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/IEEE9Bus/ieee9.raw",
				//"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/adpter/psse/v30/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabilityNetwork dsNet =simuCtx.getDStabilityNet();

	    /*
	     * run load flow to initialize the system
	     */
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
		//TSA Simulation Time Step, must be the same as the Time step defined in the Socket_Component in the PSCAD side
	  	dstabAlgo.setSimuStepSec(0.005);
	  	
	  	/*
	  	 * Total simulation time, again it must be the consistent with the PSCAD side
	  	 * Note, the PSCAD requires some time(0.2-1.0 sec, depnding on the system) to initialize the network such that
	  	 * a steady state of the system is achieved. With this considered, the following equation holds:
	  	 * 
	  	 *   TIME_IPSS_TOTAL = TIME_PSCAD_TOTAL - TIME_PSCAD_INIT
	  	*/
	  	dstabAlgo.setTotalSimuTimeSec(1.0);
        dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		

		
		StateVariableRecorder ssRecorder = new StateVariableRecorder(0.003);
		ssRecorder.addCacheRecords("Bus2-mach1",      // mach id 
				MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_MACH_ANG,       // state variable name
				0.01,                                      // time steps for recording 
				100); 
		ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
				MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_MACH_ANG,       // state variable name
				0.01,                                      // time steps for recording 
				100);                                      // total points to record 
		
		ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
				MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_MACH_PM,       // state variable name
				0.01,                                      // time steps for recording 
				100);                                      // total points to record
		ssRecorder.addCacheRecords("Bus3-mach1",      // mach id 
				StateVarRecType.MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
				0.01,                                      // time steps for recording 
				100);
		ssRecorder.addCacheRecords("Bus4",      // mach id 
				StateVarRecType.BusState,    // record type
				DStabOutSymbol.OUT_SYMBOL_BUS_VMAG,       // state variable name
				0.01,                                      // time steps for recording 
				100);
		ssRecorder.addCacheRecords("Bus5",      // mach id 
				StateVarRecType.BusState,    // record type
				DStabOutSymbol.OUT_SYMBOL_BUS_VANG,       // state variable name
				0.01,                                      // time steps for recording 
				100);
		// set the output handler
		dstabAlgo.setSimuOutputHandler(ssRecorder);
	
	  

	  	// The load of bus 5 is assumed to be modeled by equivalent current injections
	    //Need to set the load/gen/switchShunt at the boundary bus to be offline
		  
		  		DStabBus<?,?> dsBus = dsNet.getBus("Bus5");
		  		for(AclfLoad load: dsBus.getContributeLoadList()){
		  			load.setStatus(false);
		  		}
		  		for(AclfGen gen: dsBus.getContributeGenList()){
		  			gen.setStatus(false);
		  		}
		 
		    /*
	  		 * Bus5 Inj=new Complex(-1.2177100311058122, 0.5874647169492455);
	  	     */
	  	
		   	Hashtable<String, Complex> boundaryBusCurInjTable = new Hashtable();
		  			
		   	boundaryBusCurInjTable.put("Bus5", new Complex(-1.2177100311058122, 0.5874647169492455));
		 
		  	 System.out.println(boundaryBusCurInjTable);
		  	
	  	
	  	PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger()) ;
	  	// make sure the dstab algo is initialized successfully
	  	if(dstabAlgo.initialization()){
	  	
	  	   while(true){

	  		   dsNet.setCustomBusCurrInjHashtable(boundaryBusCurInjTable);

	 	  	   dstabAlgo.solveDEqnStep(true);
		  
	  		  //check the simulation time if it reaches the total simulation time
	  		  if(dstabAlgo.getSimuTime()>dstabAlgo.getTotalSimuTimeSec()){
	  			  System.out.println("Simulation Time is up, and simulation succefully ends!");
	  			 
	  			  break;   
	  		   }
	  		  
	  		   
	  		  }
	  	
	  	}

	  	
	  	
	  	/**
	  	 * Initialization Machine angle, ref Bus1-Mach1
	  	 * 
	  	 * Gen2: 57.5628
	  	 * Gen3: 50.6173
	  	 */
	 // output recorded simulation results

	  	  
	 		List<StateRecord> list = ssRecorder.getMachineRecords(
	 				"Bus3-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_ANG);
	 		System.out.println("\n\n Bus3 Machine Angle");
	 		
	 		
	 		for (StateRecord rec : list) {
	 		
	 			System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
	 		}
	 		
	 		
	 		assertTrue(getSumAbsDiff(list )<1.0E-3);
	 		
	 		list = ssRecorder.getMachineRecords(
	 				"Bus2-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_ANG);
	 		System.out.println("\n\n Bus2 Machine Angle");
	 		for (StateRecord rec : list) {
	 			System.out.println( Number2String.toStr(rec.variableValue));
	 		}
	 		
	 		assertTrue(getSumAbsDiff(list )<1.0E-3);
	 		
	 		list = ssRecorder.getMachineRecords(
	 				"Bus3-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_PM);
	 		System.out.println("\n\n Bus3 Machine PM");
	 		for (StateRecord rec : list) {
	 			System.out.println( Number2String.toStr(rec.variableValue));
	 		}
	 		
	 		assertTrue(getSumAbsDiff(list )<1.0E-4);
	 		
	 		list = ssRecorder.getMachineRecords(
	 				"Bus3-mach1", StateVarRecType.MachineState, DStabOutSymbol.OUT_SYMBOL_BUS_VMAG);
	 		System.out.println("\n\n Bus3 voltage mag");
	 		for (StateRecord rec : list) {
	 			System.out.println(Number2String.toStr(rec.variableValue));
	 		}
	 		
	 		assertTrue(getSumAbsDiff(list )<1.0E-4);
	 		
	 		list = ssRecorder.getMachineRecords(
	 				"Bus4", StateVarRecType.BusState, DStabOutSymbol.OUT_SYMBOL_BUS_VMAG);
	 		System.out.println("\n\n Bus4 voltage mag");
	 		for (StateRecord rec : list) {
	 			System.out.println(Number2String.toStr(rec.variableValue));
	 		}
	 		
	 		assertTrue(getSumAbsDiff(list )<1.0E-4);
	 		
	 		list = ssRecorder.getMachineRecords(
	 				"Bus5", StateVarRecType.BusState, DStabOutSymbol.OUT_SYMBOL_BUS_VANG);
	 		System.out.println("\n\n Bus5 voltage Angle");
	 		for (StateRecord rec : list) {
	 			System.out.println(Number2String.toStr(rec.variableValue));
	 		}
	 		
	 		assertTrue(getSumAbsDiff(list )<1.0E-4);
	}
	
	private double getSumAbsDiff(List<StateRecord> list ){
		double sumDiff = 0;
		int i = 0;
		double oldValue = 0;
		for (StateRecord rec : list) {
			if(i==0) oldValue  = rec.variableValue;
			else{
				sumDiff+=Math.abs(rec.variableValue-oldValue);
				oldValue  = rec.variableValue;
				i++;
			}
				
 			
 		}
		return sumDiff;
	}

}
