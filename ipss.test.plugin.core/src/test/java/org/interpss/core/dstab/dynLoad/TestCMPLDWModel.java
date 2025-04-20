package org.interpss.core.dstab.dynLoad;

import java.util.logging.Level;

import org.ieee.odm.ODMFileFormatEnum;
import org.ieee.odm.adapter.GenericODMAdapter;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.core.dstab.mach.TestSetupBase;
import org.interpss.dstab.dynLoad.DynLoadCMPLDW;
import org.interpss.dstab.dynLoad.impl.DynLoadCMPLDWImpl;
import org.interpss.odm.mapper.ODMDStabParserMapper;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.common.CoreCommonFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.DynDeviceType;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class TestCMPLDWModel extends TestSetupBase {


	@Test
	public void test_CMPLDW_Single_Model() throws InterpssException {
		// create a machine in a two-bus network. The loadflow already converged
		DStabilityNetwork net = create2BusSystem();
		assertTrue(net.isLfConverged());

		DStabBus bus1 = (DStabBus) net.getDStabBus("Bus1");

		DynLoadCMPLDW cmpldw = new DynLoadCMPLDWImpl("CMPLDW_1", bus1);

		cmpldw.setId("CMPLDW_1");

		cmpldw.setMvaBase(100);
		
		cmpldw.getDistEquivalent().setBSubStation(0.04);
		
		cmpldw.getDistEquivalent().setRFdr(0.04);
		
		cmpldw.getDistEquivalent().setXFdr(0.04);
		
		cmpldw.getDistEquivalent().setFB(0.0);
		
		cmpldw.getDistEquivalent().setXXf(0.06);
		
		cmpldw.getDistEquivalent().setTFixHS(1);
		
		cmpldw.getDistEquivalent().setTFixLS(1);
		
		cmpldw.getDistEquivalent().setLTC(1);
		
		cmpldw.getDistEquivalent().setTMin(0.9);
		
		cmpldw.getDistEquivalent().setTMax(1.1);
		
		cmpldw.getDistEquivalent().setStep(0.00625);
		
		cmpldw.getDistEquivalent().setVMin(1.0);
		
		cmpldw.getDistEquivalent().setVMax(1.04);
		
		cmpldw.getDistEquivalent().setTDelay(30);
		
		cmpldw.getDistEquivalent().setTTap(5);
		
		cmpldw.getDistEquivalent().setRComp(0);
		
		cmpldw.getDistEquivalent().setXComp(0);
		
		// load percentages of the dynamic load component
		
		cmpldw.setFmA(0.0);
		cmpldw.setFmB(0.0);
		cmpldw.setFmC(0.0);
		cmpldw.setFmD(0.3);
		cmpldw.setFel(0.0);
		
		// motor types
		cmpldw.setMotorTypeA(3);
		cmpldw.setMotorTypeB(3);
		cmpldw.setMotorTypeC(3);
		cmpldw.setMotorTypeD(1);
		
		// Electronic loads
		//TODO
		
		
//		// Motor A 
//		//cmpldw.setFmA(cmpldwXml.getFma());
//		cmpldw.getInductionMotorA().setLoadPercent(0.1 * 100);
//		cmpldw.getInductionMotorA().setLoadFactor(0.8);
//		cmpldw.getInductionMotorA().setRa(0.04);  //Stator resistor
//		cmpldw.getInductionMotorA().setXs(3.07); // Synchronous reactance
//		cmpldw.getInductionMotorA().setXp(0.3427); // Transient reactance
//		//cmpldw.getInductionMotorA().setXpp(0.104); // Sub-Transient reactance
//		cmpldw.getInductionMotorA().setTp0(0.875352); // Transient open circuit time constant
//		//cmpldw.getInductionMotorA().setTpp0(0.0021); // Sub-Transient open circuit time constant
//		cmpldw.getInductionMotorA().setH(0.1); // 
//		cmpldw.getInductionMotorA().setA(0.0);
//		cmpldw.getInductionMotorA().setB(0.0);
//		cmpldw.getInductionMotorA().setC(1.0); // assuming Etrq = 2.0; since Tm = (A+B*W+C*W^2)*Tm0
		
//		cmpldw.getInductionMotorA().setXm(3.0);
//		cmpldw.getInductionMotorA().setXl(0.07);
//		cmpldw.getInductionMotorA().setRa(0.04);
//		cmpldw.getInductionMotorA().setXr1(0.3);
//		cmpldw.getInductionMotorA().setRr1(0.01);
//		cmpldw.getInductionMotorA().setMvaBase(0.1 * 100 * mvaRating);
//		cmpldw.getInductionMotorA().setH(0.3);
//		cmpldw.getInductionMotorA().setA(0.0);
//		cmpldw.getInductionMotorA().setB(0.0);
//		cmpldw.getInductionMotorA().setC(1.0);
		
		
		
		// Motor B
		
//		cmpldw.getInductionMotorB().setLoadPercent(0.1 * 100);
//		cmpldw.getInductionMotorB().setLoadFactor(0.8);
//		cmpldw.getInductionMotorB().setRa(0.04);  //Stator resistor
//		cmpldw.getInductionMotorB().setXs(3.07); // Synchronous reactance
//		cmpldw.getInductionMotorB().setXp(0.3427); // Transient reactance
//		//cmpldw.getInductionMotorB().setXpp(0.14); // Sub-Transient reactance
//		cmpldw.getInductionMotorB().setTp0(0.875352); // Transient open circuit time constant
//		//cmpldw.getInductionMotorB().setTpp0(0.0026); // Sub-Transient open circuit time constant
//		cmpldw.getInductionMotorB().setH(0.5); // 
//		cmpldw.getInductionMotorB().setA(0.0);
//		cmpldw.getInductionMotorB().setB(0.0);
//		cmpldw.getInductionMotorB().setC(1.0); // assuming Etrq = 2.0; since Tm = (A+B*W+C*W^2)*Tm0
//		// all the protections are not implemented at this stage.
		
//		cmpldw.getInductionMotorB().setXm(3.0);
//		cmpldw.getInductionMotorB().setXl(0.07);
//		cmpldw.getInductionMotorB().setRa(0.04);
//		cmpldw.getInductionMotorB().setXr1(0.3);
//		cmpldw.getInductionMotorB().setRr1(0.01);
//		cmpldw.getInductionMotorB().setMvaBase(0.1 * 100 * mvaRating);
//		cmpldw.getInductionMotorB().setH(0.3);
//		cmpldw.getInductionMotorB().setA(0.0);
//		cmpldw.getInductionMotorB().setB(0.0);
//		cmpldw.getInductionMotorB().setC(1.0);
		
		
		
		// Motor C
		
		
//		cmpldw.getInductionMotorC().setLoadPercent(0.1 * 100);
//		cmpldw.getInductionMotorC().setLoadFactor(0.8);
//		cmpldw.getInductionMotorC().setRa(0.04);  //Stator resistor
//		cmpldw.getInductionMotorC().setXs(3.07); // Synchronous reactance
//		cmpldw.getInductionMotorC().setXp(0.3427); // Transient reactance
//		//cmpldw.getInductionMotorC().setXpp(0.14); // Sub-Transient reactance
//		cmpldw.getInductionMotorC().setTp0(0.875352); // Transient open circuit time constant
//		//cmpldw.getInductionMotorC().setTpp0(0.0026); // Sub-Transient open circuit time constant
//		cmpldw.getInductionMotorC().setH(0.1); // ppp
//		cmpldw.getInductionMotorC().setA(0.0);
//		cmpldw.getInductionMotorC().setB(0.0);
//		cmpldw.getInductionMotorC().setC(1.0); // assuming Etrq = 2.0; since Tm = (A+B*W+C*W^2)*Tm0
//		// all the protections are not implemented at this stage.
		
//		cmpldw.getInductionMotorC().setXm(3.0);
//		cmpldw.getInductionMotorC().setXl(0.07);
//		cmpldw.getInductionMotorC().setRa(0.04);
//		cmpldw.getInductionMotorC().setXr1(0.3);
//		cmpldw.getInductionMotorC().setRr1(0.01);
//		cmpldw.getInductionMotorC().setMvaBase(0.1 * 100 * mvaRating);
//		cmpldw.getInductionMotorC().setH(0.3);
//		cmpldw.getInductionMotorC().setA(0.0);
//		cmpldw.getInductionMotorC().setB(0.0);
//		cmpldw.getInductionMotorC().setC(1.0);
		
		
		
		// Motor D - single phase induction motor
		//cmpldw.get1PhaseACMotor().setId("1"); // no use, overrided by the initialization
		cmpldw.get1PhaseACMotor().setLoadPercent(0.3 * 100);
		//cmpldw.get1PhaseACMotor().setPowerFactor(0.98);
		cmpldw.get1PhaseACMotor().setVstall(0.55);
		cmpldw.get1PhaseACMotor().setRstall(0.1);
		cmpldw.get1PhaseACMotor().setXstall(0.1);
		cmpldw.get1PhaseACMotor().setTstall(0.033);
		cmpldw.get1PhaseACMotor().setFrst(0.2);
		cmpldw.get1PhaseACMotor().setVrst(0.95);
		cmpldw.get1PhaseACMotor().setTrst(0.3);
		cmpldw.get1PhaseACMotor().setFuvr(0.1);
		cmpldw.get1PhaseACMotor().setUVtr1(0.6);
		cmpldw.get1PhaseACMotor().setTtr1(0.02);
		cmpldw.get1PhaseACMotor().setUVtr2(1);
		cmpldw.get1PhaseACMotor().setTtr2(9999);
		cmpldw.get1PhaseACMotor().setVc1off(0.5);
		cmpldw.get1PhaseACMotor().setVc2off(0.4);
		cmpldw.get1PhaseACMotor().setVc1on(0.6);
		cmpldw.get1PhaseACMotor().setVc2on(0.5);
		cmpldw.get1PhaseACMotor().setTth(15);
		cmpldw.get1PhaseACMotor().setTh1t(0.7);
		cmpldw.get1PhaseACMotor().setTh2t(1.9);

		//net.initDStabNet();

		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(net, msg);
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		// System.out.println(AclfOutFunc.loadFlowSummary(net));

		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(1);

		dstabAlgo.setRefMachine(net.getMachine("Swing-mach1"));
		// net.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus1", net, SimpleFaultCode.GROUND_3P, 0.5d, 0.1),
		// 		"3phaseFault@Bus5");

		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[] { "Swing-mach1" });
		sm.addBusStdMonitor(new String[] { "Bus1" });
		// extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getDStabBus().getId();
		sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_CMPLDW_1@Bus1_loadBus");
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(5);

		IpssLogger.getLogger().setLevel(Level.FINE);

		
		if (dstabAlgo.initialization()) {
	
		
			System.out.println("Running DStab simulation ...");
			//System.out.println(dsNet.getMachineInitCondition());
			dstabAlgo.performSimulation();
		

		}
//		System.out.println(sm.toCSVString(sm.getMachPeTable()));
		System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
		System.out.println(sm.toCSVString(sm.getAcMotorQTable()));

		assertTrue(Math.abs(sm.getBusVoltTable().get("Bus1").get(0).value-1.00932)<1.0E-4);
		assertTrue(Math.abs(sm.getBusVoltTable().get("Bus1").get(20).value-1.00932)<1.0E-4);

		assertTrue(Math.abs(sm.getAcMotorPTable().get("ACMotor_CMPLDW_1@Bus1_loadBus").get(0).value-0.23211)<1.0E-4);
		assertTrue(Math.abs(sm.getAcMotorPTable().get("ACMotor_CMPLDW_1@Bus1_loadBus").get(20).value-0.23211)<1.0E-4);

		assertTrue(Math.abs(sm.getAcMotorQTable().get("ACMotor_CMPLDW_1@Bus1_loadBus").get(0).value-0.05817)<1.0E-4);
		assertTrue(Math.abs(sm.getAcMotorQTable().get("ACMotor_CMPLDW_1@Bus1_loadBus").get(20).value-0.05817)<1.0E-4);


	}
	
	//@Test
	public void testCMPLDWInit() throws InterpssException{
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonFactory.getIpssMsgHub();
		IpssLogger.getLogger().setLevel(Level.WARNING);
		
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/adpter/psse/v30/threeBus_cmpldw.raw",
				"testData/adpter/psse/v30/threeBus_cmpldw.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabilityNetwork dsNet =(DStabilityNetwork) simuCtx.getDStabilityNet();
	    
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
	  	algo.setLfMethod(AclfMethodType.PQ);
	  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  	
  		assertTrue( dsNet.isLfConverged());
  		
  		//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
  		
  		
	    
	}
	
	
	//@Test
	public void testCMPLDWPSLFData() throws InterpssException{
		
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonFactory.getIpssMsgHub();
		IpssLogger.getLogger().setLevel(Level.WARNING);
    		
          GenericODMAdapter adapter = new GenericODMAdapter(ODMFileFormatEnum.PsseV30,ODMFileFormatEnum.GePSLF);
		  
		  adapter.parseInputFile(NetType.DStabNet, new String[]{
				  "testData/adpter/psse/v30/threeBus_cmpldw.raw",
				  "testData/adpter/psse/v30/threeBus_cmpldw.dyd"
			//"testData/ge/ieee9_onlyGen_GE.dyd"
	        });
		  

		   DStabModelParser parser =(DStabModelParser) adapter.getModel();
		   
		   //System.out.println(parser.toXmlDoc());
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    DStabilityNetwork dsNet =(DStabilityNetwork) simuCtx.getDStabilityNet();
		    dsNet.setFrequency(60.0);
		  
	  		/*
	  		 *  load 
	  		 */
		  
		    System.out.println(dsNet.getBus("Bus3").getInfoOnlyDynModel());
	  		
	  		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, msg);
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			assertTrue(aclfAlgo.loadflow());
			//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1);

			//dstabAlgo.setRefMachine(dsNet.getMachine("Swing-mach1"));
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus3",dsNet,SimpleFaultCode.GROUND_3P,0.5d,0.05),"3phaseFault@Bus5");
	        
	        
			
			StateMonitor sm = new StateMonitor();
			//sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
			sm.addBusStdMonitor(new String[]{"Bus1","Bus3","Bus3_lowBus","Bus3_loadBus"});
			//extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getDStabBus().getId();
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus3_loadBus");
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(5);
			
			IpssLogger.getLogger().setLevel(Level.FINE);
			
			
			if (dstabAlgo.initialization()) {
				System.out.println(dsNet.getMachineInitCondition());
				
				System.out.println("Running DStab simulation ...");
			    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
				     dstabAlgo.solveDEqnStep(true);
				
				}
		
			}
		    System.out.println(dsNet.getBus("Bus1").getDynamicBusDeviceList());
		    System.out.println(dsNet.getBus("Bus3").getDynamicBusDeviceList());
		    
			//System.out.println(sm.toCSVString(sm.getMachAngleTable()));
			System.out.println(sm.toCSVString(sm.getBusVoltTable()));
			System.out.println(sm.toCSVString(sm.getAcMotorPTable()));
			
			/*
			 * time,Bus3_loadBus, Bus3_lowBus, Bus3, Bus1
				 0.0000,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.0200,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.0450,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.0700,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.0950,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.1200,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.1450,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.1700,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.1950,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.2200,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.2450,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.2700,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.2950,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.3200,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.3450,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.3700,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.3950,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.4200,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.4450,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.4700,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.4950,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.5000,    0.99868,    1.02177,    1.00012,    1.01000,
				 0.5200,    0.01738,    0.00998,    0.00000,    0.81895,
				 0.5450,    0.00403,    0.00231,    0.00000,    0.81895,
				 0.5500,    0.00403,    0.00231,    0.00000,    0.81895,
				 0.5700,    0.95547,    0.99303,    0.98655,    1.00754,
				 0.5950,    0.97060,    1.00337,    0.99135,    1.00843,
				 0.6200,    0.98671,    1.01359,    0.99632,    1.00930,
				 0.6450,    0.99499,    1.01892,    0.99888,    1.00975,
				 0.6700,    0.99620,    1.02029,    0.99937,    1.00988,
				 0.6950,    0.99909,    1.02156,    1.00015,    1.00997,
				 0.7200,    1.00001,    1.02234,    1.00047,    1.01004,
				 0.7450,    0.99914,    1.02199,    1.00024,    1.01002,
				 0.7700,    1.00001,    1.02228,    1.00046,    1.01004,
				 0.7950,    0.99966,    1.02227,    1.00039,    1.01004,
				 0.8200,    0.99945,    1.02210,    1.00032,    1.01002,
				 0.8450,    0.99991,    1.02229,    1.00044,    1.01004,
				 0.8700,    0.99958,    1.02220,    1.00037,    1.01003,
				 0.8950,    0.99967,    1.02219,    1.00038,    1.01003,
				 0.9200,    0.99980,    1.02227,    1.00042,    1.01004,
				 0.9450,    0.99960,    1.02219,    1.00037,    1.01003,
				 0.9700,    0.99973,    1.02222,    1.00040,    1.01003,
				 0.9950,    0.99972,    1.02224,    1.00040,    1.01004,

			 */
		  
	}

	@Test
	public void test_CMPLDW_init_methods() throws InterpssException{
		
		IpssCorePlugin.init();
		IPSSMsgHub msg = CoreCommonFactory.getIpssMsgHub();
		IpssLogger.getLogger().setLevel(Level.WARNING);
    		
          GenericODMAdapter adapter = new GenericODMAdapter(ODMFileFormatEnum.PsseV30,ODMFileFormatEnum.GePSLF);
		  
		  adapter.parseInputFile(NetType.DStabNet, new String[]{
				  "testData/adpter/psse/v30/twoBus_cmpldw.raw",
				  "testData/adpter/psse/v30/threeBus_cmpldw_vloadbusmin.dyd"
			//"testData/ge/ieee9_onlyGen_GE.dyd"
	        });
		  

		   DStabModelParser parser =(DStabModelParser) adapter.getModel();
		   
		   //System.out.println(parser.toXmlDoc());
			
			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabParserMapper(msg)
						.map2Model(parser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			
		    DStabilityNetwork dsNet =(DStabilityNetwork) simuCtx.getDStabilityNet();
		    dsNet.setFrequency(60.0);
		    
		    //dsNet.getBus("Bus1").setVoltageMag(0.99);
		  
	  		/*
	  		 *  load 
	  		 */
	  		
	  		DynamicSimuAlgorithm dstabAlgo = DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, msg);
			LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
			assertTrue(aclfAlgo.loadflow());
			//System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
			
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(20);

			//dstabAlgo.setRefMachine(dsNet.getMachine("Swing-mach1"));
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus3",dsNet,SimpleFaultCode.GROUND_3P,0.5d,0.05),"3phaseFault@Bus5");
	        
	        
			
			StateMonitor sm = new StateMonitor();
			//sm.addGeneratorStdMonitor(new String[]{"Swing-mach1"});
			sm.addBusStdMonitor(new String[]{"Bus1","Bus3","Bus3_lowBus","Bus3_loadBus"});
			//extended_device_Id = "ACMotor_"+this.getId()+"@"+this.getDStabBus().getId();
			sm.addDynDeviceMonitor(DynDeviceType.ACMotor, "ACMotor_1@Bus3_loadBus");
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(5);
			
			IpssLogger.getLogger().setLevel(Level.FINE);
			
			
			if (dstabAlgo.initialization()) {
				System.out.println(dsNet.getMachineInitCondition());				
				System.out.println("Running DStab simulation ...");
			    while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){
				     dstabAlgo.solveDEqnStep(true);
				
				}
		
			}
			//System.out.println(sm.toCSVString(sm.getMachAngleTable()));
			System.out.println(sm.toCSVString(sm.getBusVoltTable()));
			//System.out.println(sm.toCSVString(sm.getAcMotorPTable()));

			assertTrue(Math.abs(sm.getBusVoltTable().get("Bus1").get(0).value-0.98)<1.0E-4);
			assertTrue(Math.abs(sm.getBusVoltTable().get("Bus1").get(15).value-0.98)<1.0E-4);

			assertTrue(Math.abs(sm.getBusVoltTable().get("Bus3_loadBus").get(0).value-0.95021)<1.0E-4);
			assertTrue(Math.abs(sm.getBusVoltTable().get("Bus3_loadBus").get(15).value-0.95021)<1.0E-4);
		  
	}

	
}
