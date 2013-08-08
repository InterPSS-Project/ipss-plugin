package org.interpss.dstab.bpa;

import static com.interpss.dstab.cache.StateVariableRecorder.StateVarRecType.MachineState;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.ODMObjectFactory;
import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.bpa.BPAAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.display.AclfOutFunc;
import org.interpss.dstab.DStabTestSetupBase;
import org.interpss.dstab.output.TextSimuOutputHandler;
import org.interpss.mapper.odm.ODMAclfParserMapper;
import org.interpss.mapper.odm.ODMDStabDataMapper;
import org.interpss.numeric.NumericConstant;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.Bus;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateVariableRecorder;
import com.interpss.dstab.common.DStabOutSymbol;
import com.interpss.dstab.devent.DynamicEvent;
import com.interpss.dstab.devent.DynamicEventType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class BpaO7CTest extends DStabTestSetupBase {
	//@Test
	public void sys2011_lfTestCase() throws Exception {
		IODMAdapter adapter = new BPAAdapter();
		assertTrue(adapter.parseInputFile("testData/bpa/07c-dc2load.dat")); 
		AclfModelParser parser=(AclfModelParser) adapter.getModel();
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
			  System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			  return;
	    }
		AclfNetwork net=simuCtx.getAclfNet();
		System.out.print("branch num="+net.getBranchList().size());
		System.out.print("bus num="+net.getBusList().size());
		assertTrue(net.getBranchList().size()==707);
		assertTrue(net.getBusList().size()==536);
		
		LoadflowAlgorithm  algo=CoreObjectFactory.createLoadflowAlgorithm(net);
		net.accept(algo);
		System.out.println(AclfOutFunc.loadFlowSummary(net));
		
		
	    
	}
	/*Test data: 
	 * 07c_0615.dat : explicitly add switch shuntVar to compensate the un-planned shuntVar of BPA for BE type Bus
	 * [test data updated by Tony 06/15]
	 * 07c_0615_notBE.dat: change BE type for non-Gen Buses to B type.
	 */
	//@Test
	public void sys2010_lfTestCase() throws Exception {
		IODMAdapter adapter = new BPAAdapter();
		assertTrue(adapter.parseInputFile("testData/bpa/07c_0615_notBE.dat")); 
		AclfModelParser parser=(AclfModelParser) adapter.getModel();
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
			  System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			  return;
	    }
		String xml=parser.toXmlDoc();
		FileOutputStream out=new FileOutputStream(new File("testdata/ieee_odm/07c_0615_notBE.xml"));
		out.write(xml.getBytes());
		out.flush();
		out.close();
		
		AclfNetwork net=simuCtx.getAclfNet();
		System.out.print("branch num="+net.getBranchList().size());
		System.out.print("bus num="+net.getBusList().size());
		//assertTrue(net.getBranchList().size()==215);
		assertTrue(net.getBusList().size()==141);
		
		LoadflowAlgorithm  algo=CoreObjectFactory.createLoadflowAlgorithm(net);
		assertTrue(net.accept(algo));

		//get the genResult
		
		for(Bus b:net.getBusList()){
			AclfBus bus=(AclfBus) b;
			if(bus.isGen()){
				System.out.println(bus.getName()+", "+bus.getId()+" ,p= "+bus.getGenResults().getReal()+",q= "+bus.getGenResults().getImaginary());
			}
		}

		assertTrue(Math.abs(net.getBus("Bus1").getVoltageMag()-1.02484)<0.0001);
		AclfBranch bra= (AclfBranch) net.getBranchList().get(0);
		assertTrue(Math.abs(bra.powerFrom2To().getReal()-16.86)<0.001);
	}
	//@Test
	public void sys2010_noFaultTestCase() throws Exception {
		IODMAdapter adapter = new BPAAdapter();
		assertTrue(adapter.parseInputFile(IODMAdapter.NetType.DStabNet,
				new String[] { "testdata/bpa/07c_0615_notBE.dat", 
				               "testdata/bpa/07c_mach_exc_noSE_EA_FJ_FK.swi"}));//"testdata/bpa/07c_onlyMach.swi"
		
		DStabModelParser parser=(DStabModelParser) adapter.getModel();
	
		//parser.stdout();
		
		String xml=parser.toXmlDoc();
		FileOutputStream out=new FileOutputStream(new File("testdata/ieee_odm/07c_2010_Mach_Exc0627.xml"));
		out.write(xml.getBytes());
		out.flush();
		out.close();
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabDataMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}	
		
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		
		DStabilityNetwork net=simuCtx.getDStabilityNet();
		dstabAlgo.setRefMachine(simuCtx.getDStabilityNet().getMachine("Bus78-mach1"));
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.001);
		dstabAlgo.setTotalSimuTimeSec(0.001);
		
		// run load flow first before initialization 
		//LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		LoadflowAlgorithm  algo=CoreObjectFactory.createLoadflowAlgorithm(net);
		assertTrue(net.accept(algo));
		//get the genResult
		/*
		System.out.println("Dstab network lf result");
		for(Bus b:net.getBusList()){
			AclfBus bus=(AclfBus) b;
			if(bus.isGen()){
				System.out.println(bus.getName()+", "+bus.getId()+" ,p= "+bus.getGenResults().getReal()+",q= "+bus.getGenResults().getImaginary());
			}
		}
		*/
		
		//dstabAlgo.setSimuOutputHandler(new TextSimuOutputHandler());
		if (dstabAlgo.initialization()) {
			System.out.println("Running DStab simulation ...");
			dstabAlgo.performSimulation();
		}
		/*
		for(Bus b:net.getBusList()){
			AclfBus bus=(AclfBus) b;
			if( bus.isGen()){
				String id=b.getId();
				System.out.println("Machine:"+b.getName()+","+net.getDStabBus(id).getMachine().getEfd());
			}
		}
		*/	
	}
	
	/***************************************************
	 * test data:
	 * 1)  07c_2010_Mach_Exc.xml:  machine and exciter, not consider saturation
	 * 2)  07c_2010_OnlyMach_noSe0615.xml :has normal load  type(P+j*Q) at Gen buses 
	 * 3)  07c_2010_OnlyMach_noSe0616.xml :load at Gen buses changed to shuntY format:
	 * 4)  07c_2010_Mach_Exc.xml:  machine and exciter, not consider saturation
	 * 
	 * 14/06 [Tony]: with 07c_2010_Mach_Exc_noSe0614.xml, the initial machine angles are the same!
	 * 17/06 [Tony]: test with normal load type at Gen bus data (07c_2010_OnlyMach_noSe0615.xml) not stable,
	 *  but it was stable once they are changed to shuntY format(with 07c_2010_OnlyMach_noSe0616.xml)
	 * 22/06[Tony]
	 */
	@Test
	public void sys2010_XmlDstabtestCase() throws Exception {
		
		File file = new File("testData/ieee_odm/07c_2010_Mach_Exc0627.xml");
		DStabModelParser parser = ODMObjectFactory.createDStabModelParser();
		if (parser.parse(new FileInputStream(file))) {
			//System.out.println(parser.toXmlDoc(false));

			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabDataMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			DStabilityNetwork net = simuCtx.getDStabilityNet();
			assertTrue(net.checkData(CoreObjectFactory.createDefultDataCheckConfiguration()));
			assertTrue(net.getBranchList().size()==308);
			assertTrue(net.getBusList().size()==141);
			 
			//System.out.println(net.net2String());
			 
			//setDynamicEventData(net);
			// System.out.println(net.getMachine("Bus59-mach1").getMachData().toString());
			// System.out.println(net.getMachine("Bus56-mach1").getMachData().getXl());
			/* 
			 FileOutputStream out=new FileOutputStream(new File("d:/07c_2010_MachExc_noSe_netString.txt"));
				out.write(net.net2String().getBytes());
				out.flush();
				out.close();
			
			 */ 
		 
			DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
			dstabAlgo.setRefMachine(net.getMachine("Bus141-mach1"));
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.001);
			dstabAlgo.setTotalSimuTimeSec(0.002);
			
			// run load flow test case
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			assertTrue(aclfAlgo.loadflow());
			//System.out.println(AclfOutFunc.lfResultsBusStyle(net));
			/*
			//get the genResult
			System.out.println("Dstab network lf result");
			for(Bus b:net.getBusList()){
				AclfBus bus=(AclfBus) b;
				if(bus.isGen()){
					System.out.println(bus.getName()+", "+bus.getId()+" ,p= "+bus.getGenResults().getReal()+",q= "+bus.getGenResults().getImaginary());
				}
			}
			*/	
			// create fault
			//create3PFaultEvent(net, "Bus50", "luopingg", 0.2, 0.04);

			// create state variable recorder to record simulation results

			StateVariableRecorder stateRecorder = new StateVariableRecorder(0.0001);
			
			stateRecorder.addCacheRecords("Bus85-mach1",      // mach id 
					MachineState,    // record type
					DStabOutSymbol.OUT_SYMBOL_MACH_ANG,       // state variable name
					0.1,                                      // time steps for recording 
					300);
			stateRecorder.addCacheRecords("Bus78-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_PE, 0.1, 100);
			
			stateRecorder.addCacheRecords("Bus64-mach1",      // mach id 
					MachineState,    // record type
					DStabOutSymbol.OUT_SYMBOL_MACH_ANG,       // state variable name
					0.1,                                      // time steps for recording 
					300);                                      // total points to record 
			stateRecorder.addCacheRecords("Bus64-mach1", MachineState, 
					DStabOutSymbol.OUT_SYMBOL_MACH_PE, 0.1, 300);
			
			//dstabAlgo.setSimuOutputHandler(stateRecorder);
			
			//IpssLogger.getLogger().setLevel(Level.INFO);
			dstabAlgo.setSimuOutputHandler(new TextSimuOutputHandler());
			if (dstabAlgo.initialization()) {
				System.out.println("Running DStab simulation ...");
				assertTrue(dstabAlgo.performSimulation());
			}
          
			// output recorded simulation results
			/*
			List<StateVariableRecorder.Record> list = stateRecorder.getMachineRecords(
					"Bus64-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_ANG);
			System.out.println("\n\nMachine Anagle");
			for (Record rec : list) {
				System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
			}
			
			list = stateRecorder.getMachineRecords(
					"Bus85-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_ANG);
			System.out.println("\n\n Bus85-mach1 (EQG030) Angle");
			for (Record rec : list) {
				System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
			}

			list = stateRecorder.getMachineRecords(
					"Bus64-mach1", MachineState, DStabOutSymbol.OUT_SYMBOL_MACH_PE);
			System.out.println("\n\nMachine Power");
			for (Record rec : list) {
				System.out.println(Number2String.toStr(rec.t) + ", " + Number2String.toStr(rec.variableValue));
			}
			*/
		}
	}
	
	/************************************************
	 * lf test data:
	 * 1) 07c_2010_OnlyMach_lf.xml
	 * 
	 * Status:
	 * 06/12 Mike : Lf run can converge
	 */
	//@Test
	public void sys2010_XmlLftestCase() throws Exception {
		File file = new File("testData/ieee_odm/07c_2010_OnlyMach_lf.xml");
		AclfModelParser parser = ODMObjectFactory.createAclfModelParser();
		if (parser.parse(new FileInputStream(file))) {
			//System.out.println(parser.toXmlDoc(false));

			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
			if (!new ODMAclfParserMapper()
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			 AclfNetwork net=simuCtx.getAclfNet();
			 assertTrue(net.checkData(CoreObjectFactory.createDefultDataCheckConfiguration()));
			 assertTrue(net.getBranchList().size()==308);
			 assertTrue(net.getBusList().size()==141);
			 //System.out.println(net.net2String());
			/*
			 FileOutputStream out=new FileOutputStream(new File("d:/07c_2010_OnlyMach_netString.txt"));
				out.write(net.net2String().getBytes());
				out.flush();
				out.close();
			 */
			  
				/*
				 * Run Loadflow
				 */
			
				LoadflowAlgorithm aclfAlgo =CoreObjectFactory.createLoadflowAlgorithm(net);
				aclfAlgo.loadflow();
				System.out.println(AclfOutFunc.loadFlowSummary(net));
		}
	}
	
	private void create3PFaultEvent(DStabilityNetwork net, String busId, String busName, double startTime,double duration) {
		// define a bus fault event
		DynamicEvent event1 = DStabObjectFactory.createDEvent(
				"BusFault3P@"+busId, "Bus Fault 3P @"+busName, 
				DynamicEventType.BUS_FAULT, net);
		event1.setStartTimeSec(startTime);
		event1.setDurationSec(duration);
		
		// define a 3P fault
		DStabBus faultBus = net.getDStabBus(busId);
		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus Fault 3P@"+busId, net);
  		fault.setAcscBus(faultBus);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(NumericConstant.SmallScZ);
		fault.setZLLFault(new Complex(0.0, 0.0));
		
		// add the fault to the event
		event1.setBusFault(fault);		
	}
}
