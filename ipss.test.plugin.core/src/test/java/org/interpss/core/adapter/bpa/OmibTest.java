package org.interpss.core.adapter.bpa;

import static com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType.GovernorState;
import static com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType.MachineState;
import static org.interpss.CorePluginFunction.aclfResultBusStyle;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.ODMObjectFactory;
import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.bpa.BPAAdapter;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.core.dstab.DStabTestSetupBase;
import org.interpss.dstab.output.TextSimuOutputHandler;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.util.Number2String;
import org.interpss.odm.mapper.ODMDStabParserMapper;
import org.junit.Test;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateVariableRecorder;
import com.interpss.dstab.cache.StateVariableRecorder.StateRecord;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.devent.DynamicSimuEvent;
import com.interpss.dstab.devent.DynamicSimuEventType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class OmibTest extends DStabTestSetupBase{
	@Test
	public void OMIBTestCase() throws Exception {
		IODMAdapter adapter = new BPAAdapter();
		assertTrue(adapter.parseInputFile(IODMAdapter.NetType.DStabNet,
				new String[] { "testdata/bpa/EQG009_omib.dat", 
				               "testdata/bpa/EQG009_omib.swi"}));//"testdata/bpa/07c_onlyMach.swi"
		//assertTrue(adapter.parseInputFile("testdata/bpa/07c.dat" ));
		
		DStabModelParser parser=(DStabModelParser) adapter.getModel();

		//parser.stdout();
		/*
		String xml=parser.toXmlDoc(false);
		FileOutputStream out=new FileOutputStream(new File("testdata/ieee_odm/EQG007_OMIB.xml"));
		out.write(xml.getBytes());
		out.flush();
		out.close();
        */
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}	
		
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();

		BaseDStabNetwork<?,?> net = simuCtx.getDStabilityNet();

		//System.out.println(net.getDStabBus("Bus2").getMachine().getExciter().getDataXmlString());
		// run load flow test case
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(aclfResultBusStyle.apply(net));
		//System.out.println("GENq="+net.getAclfBus("Bus2").getGenResults().getImaginary());
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.001);
		dstabAlgo.setTotalSimuTimeSec(0.30);
		dstabAlgo.setRefMachine(net.getMachine("Bus3-mach1"));
		
		// create fault
		create3PFaultEvent(net, "Bus1", "Bus1",0.1,0.04);
		
		StateVariableRecorder ssRecorder = new StateVariableRecorder(0.0001);
		ssRecorder.addCacheRecords("Bus2-mach1",      // mach id 
				MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_MACH_ANG,       // state variable name
				0.05,                                      // time steps for recording 
				300);                                      // total points to record 
		ssRecorder.addCacheRecords("Bus2-mach1",      // mach id 
				MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_MACH_Efd,       // state variable name
				0.01,                                      // time steps for recording 
				300);                                      // total points to record 
		ssRecorder.addCacheRecords("Bus2-mach1",      // mach id 
				MachineState,    // record type
				DStabOutSymbol.OUT_SYMBOL_MACH_PM,       // state variable name
				0.05,                                      // time steps for recording 
				300);                                      // total points to record
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(ssRecorder);
		
		IpssLogger.getLogger().setLevel(Level.INFO);
		//dstabAlgo.setSimuOutputHandler(new TextSimuOutputHandler());
		if (dstabAlgo.initialization()) {
			System.out.println("Running DStab simulation ...");
			dstabAlgo.performSimulation();
		}
		
		// output recorded simulation results
		List<StateRecord> list = ssRecorder.getMachineRecords(
				"Bus2-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_ANG);
		System.out.println("\n\n Bus2 Machine Anagle");
		for (StateRecord rec : list) {
			System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
		}
		list = ssRecorder.getMachineRecords(
				"Bus2-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_Efd);
		System.out.println("\n\n Bus2 Machine EXC");
		for (StateRecord rec : list) {
			System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
		}
		
		list = ssRecorder.getMachineRecords(
				"Bus2-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_PM);
		System.out.println("\n\n Bus2 Machine PM");
		for (StateRecord rec : list) {
			System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
		}
			
	}
	//@Test
	public void XmlDstabtestCase() throws Exception {
			
			File file = new File("testData/odm/OMIB.xml");
			DStabModelParser parser = ODMObjectFactory.createDStabModelParser();
			if (parser.parse(new FileInputStream(file))) {
				//System.out.println(parser.toXmlDoc(false));

				SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
				if (!new ODMDStabParserMapper(msg)
							.map2Model(parser, simuCtx)) {
					System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
					return;
				}

				BaseDStabNetwork<?,?> net = simuCtx.getDStabilityNet();

				DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
				
				// run load flow test case
				LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
				assertTrue(aclfAlgo.loadflow());
				
				dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
				dstabAlgo.setSimuStepSec(0.001);
				dstabAlgo.setTotalSimuTimeSec(0.5);

				
				// create fault
				create3PFaultEvent(net, "Bus1", "Bus1",0.2,0.1);
				
				StateVariableRecorder ssRecorder = new StateVariableRecorder(0.0001);
				ssRecorder.addCacheRecords("Bus2-mach1",      // mach id 
						MachineState,    // record type
						DStabOutSymbol.OUT_SYMBOL_MACH_ANG,       // state variable name
						0.1,                                      // time steps for recording 
						300);                                      // total points to record 
				ssRecorder.addCacheRecords("Bus2-mach1",      // mach id 
						GovernorState,    // record type
						DStabOutSymbol.OUT_SYMBOL_GOV_PM,       // state variable name
						0.1,                                      // time steps for recording 
						300);                                      // total points to record 
				// set the output handler
				dstabAlgo.setSimuOutputHandler(ssRecorder);
				
				dstabAlgo.setSimuOutputHandler(new TextSimuOutputHandler());
				if (dstabAlgo.initialization()) {
					System.out.println("Running DStab simulation ...");
					dstabAlgo.performSimulation();
				}
				
				// output recorded simulation results
				List<StateRecord> list = ssRecorder.getMachineRecords(
						"Bus2-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_ANG);
				System.out.println("\n\n Bus2 Machine Anagle");
				for (StateRecord rec : list) {
					System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
				}
				list = ssRecorder.getMachineRecords(
						"Bus2-mach1", GovernorState, DStabOutSymbol.OUT_SYMBOL_GOV_PM);
				System.out.println("\n\n Bus2 Machine PM");
				for (StateRecord rec : list) {
					System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
				}
				
				
			}
				
				
		}		
		
	
	private void create3PFaultEvent(BaseDStabNetwork<?,?> net, String busId, 
			String busName, double startTime,double duration) {
		// define a bus fault event
		DynamicSimuEvent event1 = DStabObjectFactory.createDEvent(
				"BusFault3P@"+busId, "Bus Fault 3P @"+busName, 
				DynamicSimuEventType.BUS_FAULT, net);
		event1.setStartTimeSec(startTime);
		event1.setDurationSec(duration);
		
		// define a 3P fault

		BaseDStabBus<?,?> faultBus = net.getDStabBus(busId);

		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus Fault 3P@"+busId, net, true /* cacheBusScVolt */);
  		fault.setBus(faultBus);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(NumericConstant.SmallScZ);
		fault.setZLLFault(new Complex(0.0, 0.0));
		
		// add the fault to the event
		event1.setBusFault(fault);		
	}


}
