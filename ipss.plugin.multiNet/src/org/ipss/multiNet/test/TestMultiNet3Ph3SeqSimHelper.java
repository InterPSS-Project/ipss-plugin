package org.ipss.multiNet.test;

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
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.matrix.MatrixUtil;
import org.interpss.numeric.util.PerformanceTimer;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDStabSimuHelper;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDStabSolverImpl;
import org.ipss.multiNet.algo.SubNetworkProcessor;
import org.ipss.multiNet.equivalent.NetworkEquivalent;
import org.ipss.multiNet.equivalent.NetworkEquivalent.Coordinate;
import org.ipss.threePhase.dynamic.DStabNetwork3Phase;
import org.ipss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class TestMultiNet3Ph3SeqSimHelper {
	
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
	//@Test
	public void test_3phase3SeqSubNetEquiv_IEEE9Bus() throws InterpssException{
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
		
		DStabBus bus5 = dsNet.getBus("Bus5");
		
		Complex bus5LoadYeq = bus5.getLoadPQ().conjugate().divide(bus5.getVoltageMag()*bus5.getVoltageMag()) ;
		
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
		  
		  
		  Hashtable<String, NetworkEquivalent> equivTable = mNetHelper.getSubNetEquivTable();
		  
		  // this subnetwork only includes bus 5;
		  NetworkEquivalent equiv_subNet1=equivTable.get("SubNet-1") ;
		  assertTrue(equiv_subNet1.getEquivCoordinate()==Coordinate.Three_sequence);
		  
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
		  assertTrue(equiv_subNet2.getEquivCoordinate()==Coordinate.Three_sequence); 
		  assertTrue(equiv_subNet2.getDimension()==1); 
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
	 * Test the calculation of boundary tie-line current
	 * @throws InterpssException
	 */
	@Test
	public void test_MultiSubNetTieLineCurrent_IEEE9Bus() throws InterpssException{
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
			 assertTrue(equivSubNet_1.getDimension()==2);
			 
			 Complex3x1[] Vth1Ary = equivSubNet_1.getSource3x1();
			 
			 assertTrue(Vth1Ary[0].subtract(new Complex3x1(new Complex(0,0),new Complex(1.05349,-0.00194),new Complex(0,0))).abs()<5.0E-5);
			 assertTrue(Vth1Ary[1].subtract(new Complex3x1(new Complex(0,0),new Complex(1.03674 ,0.15596),new Complex(0,0))).abs()<5.0E-5);
			 
			 System.out.println("Vth of subNet1 = "+MatrixUtil.complex3x1Ary2String(Vth1Ary));
			 
			 NetworkEquivalent equivSubNet_2 = equivTable.get("SubNet-2");
			 assertTrue(equivSubNet_2.getDimension()==1);
			 
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
			 assertTrue(Iinj_bus5.b_1.add(I_5_7).add(I_5_4).abs()<1.0E-5);
			 
			// subnet-1, Iinj @ Bus4 and Bus7
			 Hashtable<String, Complex3x1> subNet1_curInj = curInjTable.get("SubNet-1");
			 Complex3x1 Iinj_bus4 = subNet1_curInj.get("Bus4");
			 
			 // compare with I_5_4;
			 assertTrue(Iinj_bus4.b_1.subtract(I_5_4).abs()<1.0E-5);
			 
			 Complex3x1 Iinj_bus7 = subNet1_curInj.get("Bus7");
			 assertTrue(Iinj_bus7.b_1.subtract(I_5_7).abs()<1.0E-5);
			 
	
	}
	
	
	/**
	 * test 3ph/3-seq co-simulation
	 * 
	 */
	//@Test
	public void test_3phase3SeqMultiSubNetTS_IEEE9Bus() throws InterpssException{
		
	}

}
