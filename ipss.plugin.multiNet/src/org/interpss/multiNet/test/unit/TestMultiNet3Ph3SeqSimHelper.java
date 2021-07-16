package org.interpss.multiNet.test.unit;

import static org.junit.Assert.assertTrue;

import java.util.Hashtable;
import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDStabSimuHelper;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDStabSolverImpl;
import org.interpss.multiNet.algo.MultiNet3Ph3SeqDynEventProcessor;
import org.interpss.multiNet.algo.SubNetworkProcessor;
import org.interpss.multiNet.equivalent.NetworkEquivalent;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.matrix.MatrixUtil;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.numeric.util.PerformanceTimer;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.interpss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.junit.Test;

import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.NetCoordinate;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.cache.StateMonitor.MonitorRecord;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestMultiNet3Ph3SeqSimHelper {
	
	
	@Test
	public void test_IEEE9Bus_3phase_dstab() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
		DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(3);
		dsNet.setNetEqnIterationNoEvent(1);
		dsNet.setNetEqnIterationWithEvent(1);
		dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
		
		//applied the event
		dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,new Complex(0,0), null,1.0d,0.05),"3phaseFault@Bus5");
        
		
		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus1"});
		
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(5);
		
		IpssLogger.getLogger().setLevel(Level.WARNING);
		
		PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
		
        // Must use this dynamic event process to modify the YMatrixABC
		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());
		
		if (dstabAlgo.initialization()) {
			System.out.println(dsNet.getMachineInitCondition());
			
			System.out.println("Running 3Phase DStab simulation ...");
			
			dstabAlgo.performSimulation();
		}
		
		System.out.println(sm.toCSVString(sm.getMachPeTable()));
		
	    System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		
		
		
	}
	
	//Note:network equivalent part is tested by the NetworkEquivTest
	
	//This test case serves to test the 1)3ph/3seq subnetwork solution;2) boundary subsystem formation 
	//3) calculation of the  3seq current of boundary tie-lines; 4) 3ph/3-seq co-simulation
	
	/**
	 * Test the three-phase subnetwork is accurately equivalentized. 
	 * @throws InterpssException
	 */
	//@Test
	public void test_3phaseSubNet_IEEE9Bus() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		    proc.addSubNetInterfaceBranch("Bus4->Bus5(0)");
		    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)");
		
		    
		    proc.splitFullSystemIntoSubsystems(false);
		    
		    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
		    proc.set3PhaseSubNetByBusId("Bus5");
		    
		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		   // subnetwork only bus 5;
		  DStabNetwork3Phase subNet_1 = (DStabNetwork3Phase) proc.getSubNetworkList().get(1);
		  
		  
		   // mNetHelper.set3PhaseSubNetworkId(subNet_1.getId()); 
		  
		    DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(subNet_1, IpssCorePlugin.getMsgHub());
		    
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1.0d);

			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			//subNet_1.addDynamicEvent(create3PhaseFaultEvent("Bus6", subNet_1,1.01d,0.05),"3phaseFault@Bus6");
			//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,1.0d,0.05),"3phaseFault@Bus5");
	        
			
			StateMonitor sm = new StateMonitor();
			sm.addBusStdMonitor(new String[]{"Bus5"});
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			
			IpssLogger.getLogger().setLevel(Level.INFO);
			
			PerformanceTimer timer = new PerformanceTimer(IpssLogger.getLogger());
			
			
			if (dstabAlgo.initialization()) {
				
				System.out.println("Running DStab simulation ...");
				timer.start();
				dstabAlgo.performSimulation();
				
				timer.logStd("total simu time: ");
			 }
			
			// System.out.println(sm.toCSVString(sm.getMachPeTable()));
			
		     System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		  
		    
	}
	
	/**
	 * Test the subnetwork equivalent
	 * @throws InterpssException
	 */
	@Test
	public void test_3phase3SeqSubNetEquiv_IEEE9Bus_1port() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		DStab3PBus bus5 = dsNet.getBus("Bus5");
		
		Complex bus5LoadYeq = new Complex(bus5.getLoadP(),bus5.getLoadQ())
					.conjugate().divide(bus5.getVoltageMag()*bus5.getVoltageMag()) ;
		
		System.out.println("bus5LoadYeq = "+bus5LoadYeq);
		
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		    proc.addSubNetInterfaceBranch("Bus4->Bus5(0)",false);
		    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)",true);
		
		  //NOTE: network partitioning by adding a dummy bus
		    proc.splitFullSystemIntoSubsystems(true);
		    
		    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
		    // this must be set before initializing MultiNet3Ph3SeqDStabSimuHelper
		    proc.set3PhaseSubNetByBusId("Bus5");
		    
		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		  mNetHelper.calculateSubNetTheveninEquiv();
		  
		  Hashtable<String, NetworkEquivalent> equivTable = mNetHelper.getSubNetEquivTable();
		  
		  // this subnetwork only includes bus 5;
		  NetworkEquivalent equiv_subNet1=equivTable.get("SubNet-1") ;
		  assertTrue(equiv_subNet1.getEquivCoordinate()==NetCoordinate.THREE_SEQUENCE);
		  
		  Complex3x3[][] Zth1 = equiv_subNet1.getMatrix3x3();
		  /*
		   *  Zth of subnet1: 
				aa = (0.6838343524221626, 0.273534386410797),ab = (-2.7649442826960247E-7, 8.652066345871123E-7),ac = (4.76837158203125E-7, 0.0)
				ba = (1.364586642016441E-7, 1.4995089728242483E-7),bb = (0.6838339394690701, 0.2735336711550597),bc = (-2.384185791015625E-6, -1.430511474609375E-6)
				ca = (-1.9073486328125E-6, 9.5367431640625E-7),cb = (1.9073486328125E-6, 9.5367431640625E-7),cc = (-1.3676696797039316, 4.000005590181085E10)
				, 
		   */
		  System.out.println(" Zth of subnet1: \n"+MatrixUtil.complex3x32DAry2String(Zth1));
		  
		  assertTrue(Zth1[0][0].aa.subtract(new Complex(1.0,0).divide(bus5LoadYeq)).abs()<1.0E-6); 
		  
		  assertTrue(new Complex(1.0,0).divide(Zth1[0][0].cc).abs()<1.0E-6); 
		  
		  NetworkEquivalent equiv_subNet2=equivTable.get("SubNet-2") ;
		  assertTrue(equiv_subNet2.getEquivCoordinate()==NetCoordinate.THREE_SEQUENCE); 
		  assertTrue(equiv_subNet2.getMatrix3x3().length==1); 
		  Complex3x3 zth2 =equiv_subNet2.getMatrix3x3()[0][0];
		  System.out.print("Zth2 ="+zth2);
		  /*
		   * Zth2 =aa = (0.01272097274174288, 0.10801123285122859),ab = (0.0, 0.0),ac = (0.0, 0.0)
			ba = (0.0, 0.0),bb = (0.01272097274174288, 0.10801123285122859),bc = (0.0, 0.0)
			ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (0.022915300399352374, 0.1758893282503875)
		   
		   *
		   *previous equiv results 
		   *Boundary Bus Id	BaseVolt(kV)	Thevenin Voltage Mag(PU)	Thevenin Voltage Angle (deg)	Zpos Real #Bus5	Zpos Imag #Bus5	Zzero Real #Bus5	Zzero Imag #Bus5
           Bus5	230	1.073776215	2.957974716	0.012720973	0.108011233	0.0229153	0.175889328

		   *
		   */
		 Complex3x3 zth2Calc = new Complex3x3(new Complex(0.01272097274174288, 0.10801123285122859) , 
									 new Complex(0.01272097274174288, 0.10801123285122859), 
									 new Complex(0.022915300399352374, 0.1758893282503875));
		  //NOTE: because the equivalent of the interface branch from/to shuntY
		  assertTrue(zth2.subtract(zth2Calc).abs()<1.0E-6);
		  
	}
	
	
	/**
	 * Test the calculation of boundary tie-line current, with bus5 as the only boundary bus
	 * @throws InterpssException
	 */
	@Test
	public void test_MultiSubNetTieLineCurrent_IEEE9Bus_1port() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		Complex v5 = dsNet.getBus("Bus5").getVoltage();
		Complex v4 = dsNet.getBus("Bus4").getVoltage();
		Complex v7 = dsNet.getBus("Bus7").getVoltage();
		Complex z45 = dsNet.getBranch("Bus4->Bus5(0)").getZ();
		Complex z57 = dsNet.getBranch("Bus5->Bus7(0)").getZ();
		Complex I_5_4 = v5.subtract(v4).divide(z45);
		Complex I_5_7 = v5.subtract(v7).divide(z57);
		
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		    proc.addSubNetInterfaceBranch("Bus4->Bus5(0)");
		    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)");
		
		    
		    proc.splitFullSystemIntoSubsystems(false);
		    
		    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
		    proc.set3PhaseSubNetByBusId("Bus5");
		    
		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		    
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1.0d);

			//dstabAlgo.setRefMachine(dsNet.getMachine("Bus1-mach1"));
			//subNet_1.addDynamicEvent(create3PhaseFaultEvent("Bus6", subNet_1,1.01d,0.05),"3phaseFault@Bus6");
			//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",dsNet,SimpleFaultCode.GROUND_LG,1.0d,0.05),"3phaseFault@Bus5");
	        
			
			StateMonitor sm = new StateMonitor();
			sm.addBusStdMonitor(new String[]{"Bus5"});
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			
			IpssLogger.getLogger().setLevel(Level.INFO);
			
		  // first initialize the subsystems in order to solve them in the following steps.	
			 dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
		  
			 dstabAlgo.initialization();
		  
		
		// calculate the Vth, check that the Vth(bus5) are zeros
			 Hashtable<String, NetworkEquivalent>  equivTable = mNetHelper.solvSubNetAndUpdateEquivSource();
			 /*
			  * Vth of subNet1 = 0.0000 + j0.0000  1.05349 + j-0.00194  0.0000 + j0.0000, 0.0000 + j0.0000  1.03674 + j0.15596  0.0000 + j0.0000, 
                Vth of subNet2 = 0.0000 + j0.0000  0.0000 + j0.0000  0.0000 + j0.0000
			  */
			 
			 NetworkEquivalent equivSubNet_1 = equivTable.get("SubNet-1");
			 assertTrue(equivSubNet_1.getSource3x1().length==2);
			 
			 Complex3x1[] Vth1Ary = equivSubNet_1.getSource3x1();
			 
			 assertTrue(Vth1Ary[0].subtract(new Complex3x1(new Complex(0,0),new Complex(1.05349,-0.00194),new Complex(0,0))).abs()<5.0E-5);
			 assertTrue(Vth1Ary[1].subtract(new Complex3x1(new Complex(0,0),new Complex(1.03674 ,0.15596),new Complex(0,0))).abs()<5.0E-5);
			 
			 System.out.println("Vth of subNet1 = "+MatrixUtil.complex3x1Ary2String(Vth1Ary));
			 
			 NetworkEquivalent equivSubNet_2 = equivTable.get("SubNet-2");
			 assertTrue(equivSubNet_2.getSource3x1().length==1);
			 
			 Complex3x1[] Vth2Ary = equivSubNet_2.getSource3x1();
			 
			 Complex3x1 Vth2 = Vth2Ary[0];
			 System.out.println("Vth of subNet2 = "+Vth2.toString());
			 assertTrue(Vth2.b_1.abs()<1.0E-9 && Vth2.abs()<1.0E-9);
		 
	    // solve the boundary subsystem
		  
			 mNetHelper.solveBoundarySubSystem();
			 
			 Hashtable<String, Hashtable<String, Complex3x1>> curInjTable = mNetHelper.getSubNet3SeqCurrInjTable();
		  
			 System.out.println("I_5_4 = "+I_5_4);
			 System.out.println("I_5_7 = "+I_5_7);
	         
			 // subnet-2, Iinj@Bus5
			 Hashtable<String, Complex3x1> subNet2_curInj = curInjTable.get("SubNet-2");
			 Complex3x1 Iinj_bus5 = subNet2_curInj.get("Bus5");
			 assertTrue(Iinj_bus5.b_1.add(I_5_7).add(I_5_4).abs()<1.0E-5); // inj_bus5 = I_5_4 + I_5_7
			 
			// subnet-1, Iinj @ Bus4 and Bus7
			 Hashtable<String, Complex3x1> subNet1_curInj = curInjTable.get("SubNet-1");
			 Complex3x1 Iinj_bus4 = subNet1_curInj.get("Bus4");
			 
			 // compare Iinj @ Bus4 with I_5_4;
			 assertTrue(Iinj_bus4.b_1.subtract(I_5_4).abs()<1.0E-5);
			 
			 // compare Iinj @Bus7 with I_5_7;
			 Complex3x1 Iinj_bus7 = subNet1_curInj.get("Bus7");
			 assertTrue(Iinj_bus7.b_1.subtract(I_5_7).abs()<1.0E-5);
			 
		
		// solve subsystems with boundary current injections
			mNetHelper.solveSubNetWithBoundaryCurrInjection();
			 
		/*
		 * time,Bus7, Bus5, Bus4
          0.0000,    1.02581,    0.99577,    1.02597,
		 */
	    System.out.println("bus5 Volt = "+proc.getSubNetwork("SubNet-2").getBus("Bus5").getVoltageMag());
		assertTrue(Math.abs(proc.getSubNetwork("SubNet-2").getBus("Bus5").getVoltageMag()-0.99577)<5.0e-5)	; 
		assertTrue(Math.abs(proc.getSubNetwork("SubNet-1").getBus("Bus7").getVoltageMag()-1.02581)<5.0e-5)	; 
		assertTrue(Math.abs(proc.getSubNetwork("SubNet-1").getBus("Bus4").getVoltageMag()-1.02597)<5.0e-5)	; 
	}
	
	
	/**
	 * test 3ph/3-seq co-simulation with IEEE 9 Bus system and only 1 port at the interface
	 * 
	 */
	//@Test
	public void test_3phase3SeqMultiSubNetTS_IEEE9Bus_1port() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		    proc.addSubNetInterfaceBranch("Bus4->Bus5(0)");
		    proc.addSubNetInterfaceBranch("Bus5->Bus7(0)");
		
		    
		    proc.splitFullSystemIntoSubsystems(false);
		    
		    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
		    proc.set3PhaseSubNetByBusId("Bus5");
		    
		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		    
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1d);
			
			StateMonitor sm = new StateMonitor();
			sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus7"});
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			
			IpssLogger.getLogger().setLevel(Level.INFO);
			
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",proc.getSubNetwork("SubNet-2"),SimpleFaultCode.GROUND_LG,new Complex(0,0), null,0.5d,0.05),"3phaseFault@Bus5");
			
	        // TODO a special 3-phase 3seq dstab algorithm object, with the following two setting as default
			dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
			dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
		  
			 if(dstabAlgo.initialization()){
				 dstabAlgo.performSimulation();
			 }
		   
			// System.out.println(sm.toCSVString(sm.getMachPeTable()));
				
		     System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		  
		
	}
	
	
	/**
	 * test 3ph/3-seq co-simulation with IEEE 9 Bus system and  2 port at the interface
	 * 
	 *  InterfaceBranch("Bus4->Bus5(0)");
		InterfaceBranch("Bus7->Bus8(0)");
	 * 
	 */
	@Test
	public void test_3phase3SeqMultiSubNetTS_IEEE9Bus_bus57_2port() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		Complex v5 = dsNet.getBus("Bus5").getVoltage();
		Complex v4 = dsNet.getBus("Bus4").getVoltage();
		Complex v7 = dsNet.getBus("Bus7").getVoltage();
		Complex v8 = dsNet.getBus("Bus8").getVoltage();
		Complex z45 = dsNet.getBranch("Bus4->Bus5(0)").getZ();
		Complex z78 = dsNet.getBranch("Bus7->Bus8(0)").getZ();
		Complex I_5_4 = v5.subtract(v4).divide(z45);
		Complex I_7_8 = v7.subtract(v8).divide(z78);
		
		
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		    proc.addSubNetInterfaceBranch("Bus4->Bus5(0)",false);
		    proc.addSubNetInterfaceBranch("Bus2->Bus7(1)",false);
		    proc.addSubNetInterfaceBranch("Bus7->Bus8(0)",true);
		    
		    proc.splitFullSystemIntoSubsystems(true);
		    
		    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
		    proc.set3PhaseSubNetByBusId("Bus5");
		    
		    
		    DStabNetwork3Phase subNet_1 =  (DStabNetwork3Phase) proc.getSubNetwork("SubNet-1");
		    
		    //System.out.println(subNet_1.net2String());
		    assertTrue(subNet_1.getNoActiveBus() ==2);
		    
		    DStabNetwork3Phase subNet_2 =  (DStabNetwork3Phase) proc.getSubNetwork("SubNet-2");
		    
		   // System.out.println(subNet_2.net2String());
		   assertTrue(subNet_2.getNoActiveBus() ==9);
		    
		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		    
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1d);
			
			StateMonitor sm = new StateMonitor();
			sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus7","Bus8"});
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			
			IpssLogger.getLogger().setLevel(Level.INFO);
			
			//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",proc.getSubNetwork("SubNet-1"),SimpleFaultCode.GROUND_LG,0.5d,0.05),"3phaseFault@Bus5");
			
	        // TODO a special 3-phase 3seq dstab algorithm object, with the following two setting as default
			dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
			dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
		  
			 if(dstabAlgo.initialization()){
				 dstabAlgo.performSimulation();
			 }

		   
			// System.out.println(sm.toCSVString(sm.getMachPeTable()));
				
		    System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		    
		    Hashtable<Integer, MonitorRecord> bus5Volts = sm.getBusVoltTable().get("Bus5");
		    
		    double diffSum = 0;
		    MonitorRecord rec1 = null;
		    
		    int i =0;
		    for(i=0;i<90;i++){
		    	if(i ==0) rec1 = bus5Volts.get(i);
		    	if(i>0 )
		    		diffSum += Math.abs(rec1.value-bus5Volts.get(i).value);
		    	
		    }
		    System.out.println("Volts@Bus5 sum difference = "+diffSum);
		   
		    assertTrue(diffSum<5.0E-3);
		    
	}
	
	
	/**
	 * test multi-net 3ph/3-seq simu helper with IEEE 9 Bus system and  2 port at the interface
	 * 
	 * different from the former test case, there is a generator within the 3phase modeling subsystem
	 * 
	 *  InterfaceBranch("Bus4->Bus5(0)");
		InterfaceBranch("Bus7->Bus8(0)");
	 * 
	 */
	@Test
	public void test_3phase3SeqSubNetEquiv_IEEE9Bus_2port() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		 aclfAlgo.setTolerance(1.0E-6);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));
		
		Complex v5 = dsNet.getBus("Bus5").getVoltage();
		Complex v4 = dsNet.getBus("Bus4").getVoltage();
		Complex v7 = dsNet.getBus("Bus7").getVoltage();
		Complex v8 = dsNet.getBus("Bus8").getVoltage();
		Complex z45 = dsNet.getBranch("Bus4->Bus5(0)").getZ();
		Complex z78 = dsNet.getBranch("Bus7->Bus8(0)").getZ();
		Complex I_5_4 = v5.subtract(v4).divide(z45);
		Complex I_7_8 = v7.subtract(v8).divide(z78);
		
		System.out.println("I_5_4 = "+I_5_4);
		System.out.println("I_7_8 = "+I_7_8);
		
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		 proc.addSubNetInterfaceBranch("Bus4->Bus5(0)");
		 proc.addSubNetInterfaceBranch("Bus7->Bus8(0)");
		
		    
		 proc.splitFullSystemIntoSubsystems(false);
		    
		    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
		 proc.set3PhaseSubNetByBusId("Bus5");
		    
		    
		    DStabNetwork3Phase subNet_1 =  (DStabNetwork3Phase) proc.getSubNetwork("SubNet-1");
		    
		  //  System.out.println(subNet_1.net2String());
		  // assertTrue(subNet_1.getNoActiveBus() ==6);
		    
		    DStabNetwork3Phase subNet_2 =  (DStabNetwork3Phase) proc.getSubNetwork("SubNet-2");
		  // assertTrue(subNet_2.getNoActiveBus() ==3);
		    
		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		    
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1d);
			
			StateMonitor sm = new StateMonitor();
			sm.addBusStdMonitor(new String[]{"Bus2","Bus7"});
			//sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus7","Bus8"});
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			
			IpssLogger.getLogger().setLevel(Level.INFO);
			
			//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",proc.getSubNetwork("SubNet-2"),SimpleFaultCode.GROUND_LG,0.5d,0.05),"3phaseFault@Bus5");
			
	        // TODO a special 3-phase 3seq dstab algorithm object, with the following two setting as default
			dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
			dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
		  
			 if(dstabAlgo.initialization()){
				// dstabAlgo.performSimulation();
			 }

		   
			// System.out.println(sm.toCSVString(sm.getMachPeTable()));
			// System.out.println(sm.toCSVString(sm.getBusAngleTable()));	
		    // System.out.println(sm.toCSVString(sm.getBusVoltTable()));
			 
			 Hashtable<String, NetworkEquivalent>  equivTable =  mNetHelper.getSubNetEquivTable();
			 NetworkEquivalent equiv_subNet1=equivTable.get("SubNet-1") ;
			  assertTrue(equiv_subNet1.getEquivCoordinate()==NetCoordinate.THREE_SEQUENCE);
			 Complex3x3[][] equivZMatrix1 = equiv_subNet1.getMatrix3x3();
			 
			 /*
			  * z(0,0)
			  * aa = (0.006112394324929721, 0.07952815172230401),ab = (0.0, 0.0),ac = (0.0, 0.0)
                ba = (0.0, 0.0),bb = (0.006112394324929721, 0.07952815172230401),bc = (0.0, 0.0)
                ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (7.60542089712099E-4, 0.05348735912740485)
				
				z(0,1)
				aa = (0.005624752694559396, 0.028426043580969416),ab = (0.0, 0.0),ac = (0.0, 0.0)
                ba = (0.0, 0.0),bb = (0.005624752694559396, 0.028426043580969416),bc = (0.0, 0.0)
                ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (-7.949091419557447E-4, 0.004760098578873715)
				
			    z(1,0)
				aa = (0.005624752694559396, 0.02842604358096942),ab = (0.0, 0.0),ac = (0.0, 0.0)
                ba = (0.0, 0.0),bb = (0.005624752694559396, 0.02842604358096942),bc = (0.0, 0.0)
                ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (-7.949091419557457E-4, 0.0047600985788737145)
				
				z(1,1)
				, aa = (0.058441552182728826, 0.19529831586678487),ab = (0.0, 0.0),ac = (0.0, 0.0)
				ba = (0.0, 0.0),bb = (0.058441552182728826, 0.19529831586678487),bc = (0.0, 0.0)
				ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (0.0342089715331764, 0.32495287988637356)
				,
			  */
			 
			 
			 System.out.println(MatrixUtil.complex3x32DAry2String(equivZMatrix1));
			 
			 Complex3x3 equivZ1_00 = new Complex3x3();
			 equivZ1_00.aa = new Complex(0.006112394324929721, 0.07952815172230401);
			 equivZ1_00.bb = new Complex(0.006112394324929721, 0.07952815172230401);
			 equivZ1_00.cc = new Complex(7.60542089712099E-4, 0.05348735912740485);
			 assertTrue(equivZMatrix1[0][0].subtract(equivZ1_00).abs()<1.0E-8);
			 
			 
			 
			 Complex3x3 equivZ1_01 = new Complex3x3();
			 equivZ1_01.aa = new Complex(0.005624752694559396, 0.028426043580969416);
			 equivZ1_01.bb = new Complex(0.005624752694559396, 0.028426043580969416);
			 equivZ1_01.cc = new Complex(-7.949091419557457E-4, 0.0047600985788737145);
			 assertTrue(equivZMatrix1[0][1].subtract(equivZ1_01).abs()<1.0E-8);
			 
			 
			 NetworkEquivalent equiv_subNet2=equivTable.get("SubNet-2") ;
			  assertTrue(equiv_subNet2.getEquivCoordinate()==NetCoordinate.THREE_SEQUENCE);
			 Complex3x3[][] equivZMatrix2 = equiv_subNet2.getMatrix3x3();
			 
			 /*
			  * equivZMatrix2 in 3-seq 
			  * Z(0,0)
			  * aa = (0.11337689329515546, 0.24325399637308914),ab = (0.0, 0.0),ac = (0.0, 0.0)
				ba = (0.0, 0.0),bb = (0.11337689329515545, 0.24325399637308914),bc = (0.0, 0.0)
				ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (0.1014694673851629, 0.5226268860122745)
				
				Z(0,1)
				aa = (0.043433555449656915, 0.12443340706290658),ab = (0.0, 0.0),ac = (0.0, 0.0)
				ba = (0.0, 0.0),bb = (0.043433555449656915, 0.12443340706290658),bc = (0.0, 0.0)
				ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (0.0015504290148834112, 0.07138710283811112)
				 
				Z(1,0)
	            aa = (0.043433555449656915, 0.12443340706290658),ab = (0.0, 0.0),ac = (0.0, 0.0)
				ba = (0.0, 0.0),bb = (0.043433555449656915, 0.12443340706290658),bc = (0.0, 0.0)
				ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (0.0015504290148834112, 0.07138710283811112)
				
				Z(1,1)
				, aa = (0.022819420447181583, 0.14317667677986784),ab = (0.0, 0.0),ac = (0.0, 0.0)
				ba = (0.0, 0.0),bb = (0.022819420447181583, 0.14317667677986784),bc = (0.0, 0.0)
				ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (2.369018180678646E-5, 0.06449226766717943)
			  */
			 
			 System.out.println(MatrixUtil.complex3x32DAry2String(equivZMatrix2));
			 
			 
			 Complex3x3 equivZ2_10 = new Complex3x3();
			 equivZ2_10.aa = new Complex(0.043433555449656915, 0.12443340706290658);
			 equivZ2_10.bb = new Complex(0.043433555449656915, 0.12443340706290658);
			 equivZ2_10.cc = new Complex(0.0015504290148834112, 0.07138710283811112);
			 assertTrue(equivZMatrix2[1][0].subtract(equivZ2_10).abs()<1.0E-8);
			 
			 
			 
			 Complex3x3 equivZ2_11 = new Complex3x3();
			 equivZ2_11.aa = new Complex(0.022819420447181583, 0.14317667677986784);
			 equivZ2_11.bb = new Complex(0.022819420447181583, 0.14317667677986784);
			 equivZ2_11.cc = new Complex(2.369018180678646E-5, 0.06449226766717943);
			 assertTrue(equivZMatrix2[1][1].subtract(equivZ2_11).abs()<1.0E-8);
			 
			 /*
			  * equivZMatrix2 in 3-phase
			  * Z(0,0) in ABC =
			  * aa = (0.10649347504149621, 0.32886737933216403),ab = (-0.00688336058394462, 0.08561320775800127),ac = (-0.00688336058394462, 0.08561320775800127)
				ba = (-0.006883360583944627, 0.08561320775800126),bb = (0.1064934750414962, 0.328867379332164),bc = (-0.006883360583944606, 0.08561320775800124)
				ca = (-0.006883360583944627, 0.08561320775800126),cb = (-0.006883360583944606, 0.08561320775800124),cc = (0.1064934750414962, 0.328867379332164)
				
				 Z(0,1) in ABC =
				aa = (0.029254099273697175, 0.10559957462602053),ab = (-0.014179419380860085, -0.018833915206145652),ac = (-0.014179419380860085, -0.018833915206145652)
				ba = (-0.014179419380860085, -0.018833915206145652),bb = (0.029254099273697164, 0.10559957462602053),bc = (-0.014179419380860071, -0.018833915206145666)
				ca = (-0.014179419380860085, -0.018833915206145652),cb = (-0.014179419380860071, -0.018833915206145666),cc = (0.029254099273697164, 0.10559957462602053)
				
				 Z(1,0) in ABC =
				aa = (0.029254099273697175, 0.10559957462602053),ab = (-0.014179419380860083, -0.018833915206145652),ac = (-0.014179419380860083, -0.018833915206145652)
				ba = (-0.014179419380860077, -0.018833915206145652),bb = (0.02925409927369716, 0.10559957462602053),bc = (-0.01417941938086008, -0.01883391520614566)
				ca = (-0.014179419380860077, -0.018833915206145652),cb = (-0.01417941938086008, -0.01883391520614566),cc = (0.02925409927369716, 0.10559957462602053)
				
				 Z(1,1) in ABC =
				aa = (0.015215813459477505, 0.11670424213060186),ab = (-0.007603584938287939, -0.026472473281760462),ac = (-0.007603584938287939, -0.026472473281760462)
				ba = (-0.007603584938287946, -0.02647247328176047),bb = (0.015215813459477498, 0.11670424213060186),bc = (-0.007603584938287925, -0.026472473281760476)
				ca = (-0.007603584938287946, -0.02647247328176047),cb = (-0.007603584938287925, -0.026472473281760476),cc = (0.015215813459477498, 0.11670424213060186)

			  */
//			 System.out.println(" Z(0,0) in ABC = \n"+ Complex3x3.z12_to_abc(equivZMatrix2[0][0]).toString());
//			 System.out.println(" Z(0,1) in ABC = \n"+  Complex3x3.z12_to_abc(equivZMatrix2[0][1]).toString());
//			 System.out.println(" Z(1,0) in ABC = \n"+  Complex3x3.z12_to_abc(equivZMatrix2[1][0]).toString());
//			 System.out.println(" Z(1,1) in ABC = \n"+  Complex3x3.z12_to_abc(equivZMatrix2[1][1]).toString());
//			 
//			 
		     // calculate the Vth, check that the Vth(bus5) are zeros
			 mNetHelper.solvSubNetAndUpdateEquivSource();
			 
		
			 
			 // solve the boundary subsystem
			  
			 mNetHelper.solveBoundarySubSystem();
			 
			 Hashtable<String, Hashtable<String, Complex3x1>> curInjTable = mNetHelper.getSubNet3SeqCurrInjTable();
		  
			 System.out.println("I_5_4 = "+I_5_4);
			 System.out.println("I_7_8 = "+I_7_8);
	         
			 // subnet-2, Iinj@Bus5
			 Hashtable<String, Complex3x1> subNet2_curInj = curInjTable.get("SubNet-2");
			 Complex3x1 Iinj_bus5 = subNet2_curInj.get("Bus5");
			 System.out.println( "Iinj@Bus5 = "+Iinj_bus5.b_1);
			 assertTrue(Iinj_bus5.b_1.add(I_5_4).abs()<1.0E-5); // inj_bus5 = I_5_4
			 
			 // compare Iinj @Bus7 with I_7_8;
			 Complex3x1 Iinj_bus7 = subNet2_curInj.get("Bus7");
			 assertTrue(Iinj_bus7.b_1.add(I_7_8).abs()<1.0E-5);
			 
			// subnet-1, Iinj @ Bus4
			 Hashtable<String, Complex3x1> subNet1_curInj = curInjTable.get("SubNet-1");
			 Complex3x1 Iinj_bus4 = subNet1_curInj.get("Bus4");
			 
			 // compare Iinj @ Bus4 with I_5_4;
			 assertTrue(Iinj_bus4.b_1.subtract(I_5_4).abs()<1.0E-5);
			 
			 
             Complex3x1 Iinj_bus8 = subNet1_curInj.get("Bus8");
			 
			 // compare Iinj @ Bus8 with I_7_8;
			 assertTrue(Iinj_bus8.b_1.subtract(I_7_8).abs()<1.0E-5);
			 
			
			 
		
		// solve subsystems with boundary current injections
			mNetHelper.solveSubNetWithBoundaryCurrInjection();
			 
		/*
		 * time,Bus7, Bus5, Bus4
          0.0000,    1.02581,    0.99577,    1.02597,
		 */
		Complex v5new =proc.getSubNetwork("SubNet-2").getBus("Bus5").getVoltage();
		Complex v7new =proc.getSubNetwork("SubNet-2").getBus("Bus7").getVoltage();
		
		Complex v4new =proc.getSubNetwork("SubNet-1").getBus("Bus4").getVoltage();
		Complex v8new =proc.getSubNetwork("SubNet-1").getBus("Bus8").getVoltage();
		
		
	    System.out.println("bus5 Volt = "+v5new.toString());
	    System.out.println("bus7 Volt = "+v7new.toString());
	    
	    assertTrue(NumericUtil.equals(v4new,v4,1.0E-6));
	    assertTrue(NumericUtil.equals(v5new,v5,1.0E-6));
	    assertTrue(NumericUtil.equals(v7new,v7,1.0E-6));
	    assertTrue(NumericUtil.equals(v8new,v8,1.0E-6));
	    
		assertTrue(Math.abs(proc.getSubNetwork("SubNet-2").getBus("Bus5").getVoltageMag()-0.99577)<5.0e-5)	; 
		assertTrue(Math.abs(proc.getSubNetwork("SubNet-2").getBus("Bus7").getVoltageMag()-1.02581)<5.0e-5)	; 
		assertTrue(Math.abs(proc.getSubNetwork("SubNet-1").getBus("Bus4").getVoltageMag()-1.02597)<5.0e-5)	; 
//		  
		
	}
	
	/**
	 * test multi-net 3ph/3-seq multi subsystems TS with IEEE 9 Bus system and  2 port at the interface
	 * 
	 * different from the former test case, there is a generator and transformer within the 3phase modeling subsystem
	 * 
	 *  InterfaceBranch("Bus4->Bus5(0)");
		InterfaceBranch("Bus7->Bus8(0)");
	 * 
	 */
	@Test
	public void test_3phase3SeqMultiSubNetTS_IEEE9Bus_bus257_2port() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9.raw",
				"testData/IEEE9Bus/ieee9.seq",
				//"testData/IEEE9Bus/ieee9_dyn_onlyGen_saturation.dyr"
				"testData/IEEE9Bus/ieee9_dyn_onlyGen.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();
		
		//System.out.println(parser.toXmlDoc());

		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODM3PhaseDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    DStabNetwork3Phase dsNet =(DStabNetwork3Phase) simuCtx.getDStabilityNet();
	    
		
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(dsNet);
		 aclfAlgo.setTolerance(1.0E-6);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(dsNet));

		
		 SubNetworkProcessor proc = new SubNetworkProcessor(dsNet);
		 proc.addSubNetInterfaceBranch("Bus4->Bus5(0)");
		 proc.addSubNetInterfaceBranch("Bus7->Bus8(0)");
		
		    
		 proc.splitFullSystemIntoSubsystems(false);
		    
		    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
		 proc.set3PhaseSubNetByBusId("Bus5");
		    
		    
		    DStabNetwork3Phase subNet_1 =  (DStabNetwork3Phase) proc.getSubNetwork("SubNet-1");
		    
		  //  System.out.println(subNet_1.net2String());
		  // assertTrue(subNet_1.getNoActiveBus() ==6);
		    
		    DStabNetwork3Phase subNet_2 =  (DStabNetwork3Phase) proc.getSubNetwork("SubNet-2");
		  // assertTrue(subNet_2.getNoActiveBus() ==3);
		    
		  MultiNet3Ph3SeqDStabSimuHelper  mNetHelper = new MultiNet3Ph3SeqDStabSimuHelper(dsNet,proc);
		  
		  // create multiNet3Seq3PhDStabHelper and initialize the subsystem
		  DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(dsNet, IpssCorePlugin.getMsgHub());
		    
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
			dstabAlgo.setSimuStepSec(0.005d);
			dstabAlgo.setTotalSimuTimeSec(1d);
			
			StateMonitor sm = new StateMonitor();
			sm.addBusStdMonitor(new String[]{"Bus2","Bus4","Bus5","Bus7","Bus8"});
			sm.addGeneratorStdMonitor(new String[]{"Bus3-mach1","Bus2-mach1","Bus1-mach1"});
			//sm.addBusStdMonitor(new String[]{"Bus5","Bus4","Bus7","Bus8"});
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			
			IpssLogger.getLogger().setLevel(Level.INFO);
			
			dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",proc.getSubNetwork("SubNet-2"),SimpleFaultCode.GROUND_LG,new Complex(0,0),null,0.5d,0.05),"3phaseFault@Bus5");
			
	        // TODO a special 3-phase 3seq dstab algorithm object, with the following two setting as default
			dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
			dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
		  
			 if(dstabAlgo.initialization()){
				 dstabAlgo.performSimulation();
			 }

		   
			System.out.println(sm.toCSVString(sm.getMachPeTable()));
			System.out.println(sm.toCSVString(sm.getBusAngleTable()));	
		    System.out.println(sm.toCSVString(sm.getBusVoltTable()));
		   
		    double diffSum = 0;
		    MonitorRecord rec1 = null;
		    Hashtable<Integer, MonitorRecord> bus5Volts = sm.getBusVoltTable().get("Bus5");
		    
		    int i =0;
		    for(i=0;i<90;i++){
		    	if(i ==0) rec1 = bus5Volts.get(i);
		    	if(i>0 )
		    		diffSum += Math.abs(rec1.value-bus5Volts.get(i).value);
		    	
		    }
		    System.out.println("Volts@Bus5 sum difference = "+diffSum);
		    assertTrue(diffSum<1.0E-5);
		    
		    
            Hashtable<Integer, MonitorRecord> bus2MachPe = sm.getMachPeTable().get("Bus2-mach1");
		    
		    i =0;
		    diffSum = 0;
		    for(i=0;i<90;i++){
		    	if(i ==0) rec1 = bus2MachPe.get(i);
		    	if(i>0 )
		    		diffSum += Math.abs(rec1.value-bus2MachPe.get(i).value);
		    	
		    }
		    System.out.println("MachPe@Bus2 sum difference = "+diffSum);
		    assertTrue(diffSum<1.0E-5);
			
	}
	
	
	

}
