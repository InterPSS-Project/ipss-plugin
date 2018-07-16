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
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.matrix.MatrixUtil;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDStabSimuHelper;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDStabSolverImpl;
import org.ipss.multiNet.algo.MultiNet3Ph3SeqDynEventProcessor;
import org.ipss.multiNet.algo.SubNetworkProcessor;
import org.ipss.multiNet.equivalent.NetworkEquivalent;
import org.ipss.threePhase.basic.Bus3Phase;
import org.ipss.threePhase.dynamic.DStabNetwork3Phase;
import org.ipss.threePhase.odm.ODM3PhaseDStabParserMapper;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.NetCoordinate;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class investigate_3PHSubNetYabc {
	
	
	//@Test
	public void check_Yabc_IEEE9Bus() throws InterpssException{
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
		 aclfAlgo.setTolerance(1.0E-10);
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
		 //proc.set3PhaseSubNetByBusId("Bus8");   
		    
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
			dstabAlgo.setTotalSimuTimeSec(0.050d);
			
			StateMonitor sm = new StateMonitor();
			sm.addBusStdMonitor(new String[]{"Bus2","Bus7"});
			sm.addGeneratorStdMonitor(new String[]{"Bus3-mach1","Bus2-mach1","Bus1-mach1"});
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
				 System.out.println(dsNet.getMachineInitCondition());
				 dstabAlgo.performSimulation();
			 }

		   
			System.out.println("Gen Pe: \n"+sm.toCSVString(sm.getMachPeTable()));
			System.out.println("Gen Angle: \n"+sm.toCSVString(sm.getMachAngleTable()));
			System.out.println("Bus Volt Angle: \n"+sm.toCSVString(sm.getBusAngleTable()));	
		   System.out.println("Bus Volt Mag: \n"+sm.toCSVString(sm.getBusVoltTable()));
			 
			 
			 
			 // checking the yftABC and ytfABC of xft 2->7
			 
			 //subNet_2.getb
			 
			 
			 
//			 for (Bus b:subNet_2.getBusList()){
//				 System.out.println(b.getId()+","+b.getSortNumber());
//			 }
//			 
//			 ISparseEqnComplexMatrix3x3 yabc = null;
//			try {
//				yabc = subNet_2.formYMatrixABC();
//			} catch (Exception e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//			 
//			 //MatrixOutputUtil.matrixToMatlabMFile("subNet2_yabc.m", yabc.getSparseEqnComplex());
//			 for(int i =0;i<3;i++){
//				 for (int j =0;j<3;j++){
//			        Complex3x3  yij_abc = yabc.getA(i,j);
//			        
//			        System.out.println("\n i,j, yij_ABC =" +i+","+j+"\n"+yij_abc.toString());
//			        Complex3x3  yij_120 = Complex3x3.abc_to_120(yij_abc);
//			        System.out.println("\n i,j, yij_120 =" +i+","+j+"\n"+yij_120.toString());
//				}
//			 }
			 
//			 ISparseEqnComplex ypos = subNet_2.getYMatrix();
//			 ISparseEqnComplex yneg = subNet_2.getNegSeqYMatrix();
//			 ISparseEqnComplex yzero = subNet_2.getZeroSeqYMatrix();
//			 
//			 for(int i =0;i<3;i++){
//				 for (int j =0;j<3;j++){
//					 
//					 System.out.println("\n i,j, yij_120 =" +i+","+j+"\n"+ypos.getA(i, j).toString()
//							 +","+yneg.getA(i, j).toString()+","+yzero.getA(i, j).toString());
//					 
//				 }
//			 }
//			 
			 
				
		   //print out the equiv for testing
			 NetworkEquivalent equiv_2= mNetHelper.getSubNetEquivTable().get("SubNet-2");
			 
			 assertTrue(equiv_2.getSource3x1().length==2);
			 
			 System.out.println("Zth_equiv2(120) = \n"+MatrixUtil.complex3x32DAry2String(equiv_2.getMatrix3x3()));   
			 
			 
			 System.out.println("Vth_equiv2 = \n"+MatrixUtil.complex3x1Ary2String(equiv_2.getSource3x1())); 
			 
//			 
//			 ISparseEqnComplex ypos1 = subNet_1.getYMatrix();
//			 ISparseEqnComplex yneg1 = subNet_1.getNegSeqYMatrix();
//			 ISparseEqnComplex yzero1 = subNet_1.getZeroSeqYMatrix();
//			 
//			 
//			 for (Bus b:subNet_1.getBusList()){
//				 System.out.println(b.getId()+","+b.getSortNumber());
//			 }
//			 
//			 /*
//			  * Bus4,0
//				Bus1,1
//				Bus6,2
//				Bus9,3
//				Bus8,4
//				Bus3,5
//
//			  */
//			 
//			 int i =0, j =0;
////			 for(int i =0;i<3;i++){
////				 for (int j =0;j<3;j++){
//					 
//					 System.out.println("\n i,j, yij_120 =" +i+","+j+"\n"+ypos1.getA(i, j).toString()
//							 +","+yneg1.getA(i, j).toString()+","+yzero1.getA(i, j).toString());
//					 
////				 }
////			 }
//	          
//			 i =4;
//			 j =4;
//			 System.out.println("\n i,j, yij_120 =" +i+","+j+"\n"+ypos1.getA(i, j).toString()
//					 +","+yneg1.getA(i, j).toString()+","+yzero1.getA(i, j).toString());
//			 
//			 
//			 
//			 Hashtable<String, NetworkEquivalent>  equivTable =  mNetHelper.getSubNetEquivTable();
//			 NetworkEquivalent equiv_subNet1=equivTable.get("SubNet-1") ;
//			  assertTrue(equiv_subNet1.getEquivCoordinate()==Coordinate.Three_sequence);
//			 Complex3x3[][] equivZMatrix = equiv_subNet1.getMatrix3x3();
//			 
//			 /*
//			  * z(0,0)
//			  * aa = (0.006112387800872096, 0.07952815643935975),ab = (0.0, 0.0),ac = (0.0, 0.0)
//				ba = (0.0, 0.0),bb = (0.006112387800872096, 0.07952815643935975),bc = (0.0, 0.0)
//				ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (7.539822263951868E-4, 0.05323524399674695)
//				
//				z(0,1)
//				, aa = (0.0056247409015727804, 0.02842605562067255),ab = (0.0, 0.0),ac = (0.0, 0.0)
//				ba = (0.0, 0.0),bb = (0.0056247409015727804, 0.02842605562067255),bc = (0.0, 0.0)
//				ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (-7.842980950798573E-4, 0.004623809599653661)
//				
//			    z(1,0)
//				aa = (0.005624740901572781, 0.02842605562067255),ab = (0.0, 0.0),ac = (0.0, 0.0)
//				ba = (0.0, 0.0),bb = (0.005624740901572781, 0.02842605562067255),bc = (0.0, 0.0)
//				ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (-7.842980950798574E-4, 0.004623809599653664)
//				
//				z(1,1)
//				, aa = (0.058441502171763686, 0.19529838105182823),ab = (0.0, 0.0),ac = (0.0, 0.0)
//				ba = (0.0, 0.0),bb = (0.058441502171763686, 0.19529838105182823),bc = (0.0, 0.0)
//				ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (0.03261134419192529, 0.3173513544956359)
//			  */
//			 System.out.println(MatrixUtil.complex3x32DAry2String(equivZMatrix));
//			 
//			 
//			 NetworkEquivalent equiv_subNet2=equivTable.get("SubNet-2") ;
//			  assertTrue(equiv_subNet2.getEquivCoordinate()==Coordinate.Three_sequence);
//			 Complex3x3[][] equivZMatrix2 = equiv_subNet2.getMatrix3x3();
//			 
//			 /*
//			  * equivZMatrix2 in 3-seq 
//			  * Z(0,0)
//			  * aa = (0.11337683562544085, 0.24325417157416276),ab = (0.0, 0.0),ac = (0.0, 0.0)
//				ba = (0.0, 0.0),bb = (0.11337683562544085, 0.24325417157416276),bc = (0.0, 0.0)
//				ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (0.09272675387360696, 0.5000937948481665)
//				
//				Z(0,1)
//				aa = (0.04343351865455726, 0.1244334898321662),ab = (0.0, 0.0),ac = (0.0, 0.0)
//				ba = (0.0, 0.0),bb = (0.04343351865455726, 0.1244334898321662),bc = (0.0, 0.0)
//				ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (8.952605119770054E-4, 0.06793174421372922)
//				 
//				Z(1,0)
//				aa = (0.04343351865455726, 0.12443348983216619),ab = (0.0, 0.0),ac = (0.0, 0.0)
//				ba = (0.0, 0.0),bb = (0.04343351865455726, 0.12443348983216619),bc = (0.0, 0.0)
//				ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (8.952605119770088E-4, 0.06793174421372922)
//				
//				Z(1,1)
//				, aa = (0.022819398397765443, 0.14317671541236235),ab = (0.0, 0.0),ac = (0.0, 0.0)
//				ba = (0.0, 0.0),bb = (0.022819398397765443, 0.14317671541236235),bc = (0.0, 0.0)
//				ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (8.643582901627389E-6, 0.06375929556708093)
//			  */
//			 
//			 System.out.println(MatrixUtil.complex3x32DAry2String(equivZMatrix2));
//			 
//			 /*
//			  * equivZMatrix2 in 3-phase
//			  * Z(0,0) in ABC =
//			  * aa = (0.10649347504149621, 0.32886737933216403),ab = (-0.00688336058394462, 0.08561320775800127),ac = (-0.00688336058394462, 0.08561320775800127)
//				ba = (-0.006883360583944627, 0.08561320775800126),bb = (0.1064934750414962, 0.328867379332164),bc = (-0.006883360583944606, 0.08561320775800124)
//				ca = (-0.006883360583944627, 0.08561320775800126),cb = (-0.006883360583944606, 0.08561320775800124),cc = (0.1064934750414962, 0.328867379332164)
//				
//				 Z(0,1) in ABC =
//				aa = (0.029254099273697175, 0.10559957462602053),ab = (-0.014179419380860085, -0.018833915206145652),ac = (-0.014179419380860085, -0.018833915206145652)
//				ba = (-0.014179419380860085, -0.018833915206145652),bb = (0.029254099273697164, 0.10559957462602053),bc = (-0.014179419380860071, -0.018833915206145666)
//				ca = (-0.014179419380860085, -0.018833915206145652),cb = (-0.014179419380860071, -0.018833915206145666),cc = (0.029254099273697164, 0.10559957462602053)
//				
//				 Z(1,0) in ABC =
//				aa = (0.029254099273697175, 0.10559957462602053),ab = (-0.014179419380860083, -0.018833915206145652),ac = (-0.014179419380860083, -0.018833915206145652)
//				ba = (-0.014179419380860077, -0.018833915206145652),bb = (0.02925409927369716, 0.10559957462602053),bc = (-0.01417941938086008, -0.01883391520614566)
//				ca = (-0.014179419380860077, -0.018833915206145652),cb = (-0.01417941938086008, -0.01883391520614566),cc = (0.02925409927369716, 0.10559957462602053)
//				
//				 Z(1,1) in ABC =
//				aa = (0.015215813459477505, 0.11670424213060186),ab = (-0.007603584938287939, -0.026472473281760462),ac = (-0.007603584938287939, -0.026472473281760462)
//				ba = (-0.007603584938287946, -0.02647247328176047),bb = (0.015215813459477498, 0.11670424213060186),bc = (-0.007603584938287925, -0.026472473281760476)
//				ca = (-0.007603584938287946, -0.02647247328176047),cb = (-0.007603584938287925, -0.026472473281760476),cc = (0.015215813459477498, 0.11670424213060186)
//
//			  */
//			 System.out.println(" Z(0,0) in ABC = \n"+ Complex3x3.z12_to_abc(equivZMatrix2[0][0]).toString());
//			 System.out.println(" Z(0,1) in ABC = \n"+  Complex3x3.z12_to_abc(equivZMatrix2[0][1]).toString());
//			 System.out.println(" Z(1,0) in ABC = \n"+  Complex3x3.z12_to_abc(equivZMatrix2[1][0]).toString());
//			 System.out.println(" Z(1,1) in ABC = \n"+  Complex3x3.z12_to_abc(equivZMatrix2[1][1]).toString());
			 
//			 
//		     // calculate the Vth, check that the Vth(bus5) are zeros
//			 mNetHelper.solvSubNetAndUpdateEquivSource();
//			 
//		
//			 
//			 // solve the boundary subsystem
//			  
//			 mNetHelper.solveBoundarySubSystem();
//			 
//			 Hashtable<String, Hashtable<String, Complex3x1>> curInjTable = mNetHelper.getSubNet3SeqCurrInjTable();
//		  
//			 System.out.println("I_5_4 = "+I_5_4);
//			 System.out.println("I_7_8 = "+I_7_8);
//	         
//			 // subnet-2, Iinj@Bus5
//			 Hashtable<String, Complex3x1> subNet2_curInj = curInjTable.get("SubNet-2");
//			 Complex3x1 Iinj_bus5 = subNet2_curInj.get("Bus5");
//			 System.out.println( "Iinj@Bus5 = "+Iinj_bus5.b_1);
//			 assertTrue(Iinj_bus5.b_1.add(I_5_4).abs()<1.0E-5); // inj_bus5 = I_5_4
//			 
//			 // compare Iinj @Bus7 with I_7_8;
//			 Complex3x1 Iinj_bus7 = subNet2_curInj.get("Bus7");
//			 assertTrue(Iinj_bus7.b_1.add(I_7_8).abs()<1.0E-5);
//			 
//			// subnet-1, Iinj @ Bus4
//			 Hashtable<String, Complex3x1> subNet1_curInj = curInjTable.get("SubNet-1");
//			 Complex3x1 Iinj_bus4 = subNet1_curInj.get("Bus4");
//			 
//			 // compare Iinj @ Bus4 with I_5_4;
//			 assertTrue(Iinj_bus4.b_1.subtract(I_5_4).abs()<1.0E-5);
//			 
//			 
//             Complex3x1 Iinj_bus8 = subNet1_curInj.get("Bus8");
//			 
//			 // compare Iinj @ Bus8 with I_7_8;
//			 assertTrue(Iinj_bus8.b_1.subtract(I_7_8).abs()<1.0E-5);
//			 
//			
//			
//		// solve subsystems with boundary current injections
//			mNetHelper.solveSubNetWithBoundaryCurrInjection();
//			 
//		/*
//		 * time,Bus7, Bus5, Bus4
//          0.0000,    1.02581,    0.99577,    1.02597,
//		 */
//		Complex v5new =proc.getSubNetwork("SubNet-2").getBus("Bus5").getVoltage();
//		Complex v7new =proc.getSubNetwork("SubNet-2").getBus("Bus7").getVoltage();
//		
//		Complex v4new =proc.getSubNetwork("SubNet-1").getBus("Bus4").getVoltage();
//		Complex v8new =proc.getSubNetwork("SubNet-1").getBus("Bus8").getVoltage();
//		
//		
//	    System.out.println("bus5 Volt = "+v5new.toString());
//	    System.out.println("bus7 Volt = "+v7new.toString());
//	    
//	    assertTrue(NumericUtil.equals(v4new,v4,1.0E-6));
//	    assertTrue(NumericUtil.equals(v5new,v5,1.0E-6));
//	    assertTrue(NumericUtil.equals(v7new,v7,1.0E-6));
//	    assertTrue(NumericUtil.equals(v8new,v8,1.0E-6));
//	    
//		assertTrue(Math.abs(proc.getSubNetwork("SubNet-2").getBus("Bus5").getVoltageMag()-0.99577)<5.0e-5)	; 
//		assertTrue(Math.abs(proc.getSubNetwork("SubNet-2").getBus("Bus7").getVoltageMag()-1.02581)<5.0e-5)	; 
//		assertTrue(Math.abs(proc.getSubNetwork("SubNet-1").getBus("Bus4").getVoltageMag()-1.02597)<5.0e-5)	; 
////		  
		
	}
	
	
	//@Test
	public void test_3phaseSub_IEEE9Bus() throws InterpssException{
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
		 aclfAlgo.setTolerance(1.0E-10);
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
		 proc.addSubNetInterfaceBranch("Bus4->Bus5(0)",false);
		 proc.addSubNetInterfaceBranch("Bus7->Bus8(0)",true);
		
		    
		 proc.splitFullSystemIntoSubsystems(false);
		    
		    //TODO now one needs to set the three-phase modeling subnetwork by one of the bus the subnetwork contains
		 // proc.set3PhaseSubNetByBusId("Bus5");
		 // proc.set3PhaseSubNetByBusId("Bus8");   
		    
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
			dstabAlgo.setTotalSimuTimeSec(0.10d);
			
			StateMonitor sm = new StateMonitor();
			//sm.addBusStdMonitor(new String[]{"Bus2","Bus7"});
			sm.addBusStdMonitor(new String[]{"Bus4","Bus8"});
			// set the output handler
			dstabAlgo.setSimuOutputHandler(sm);
			dstabAlgo.setOutPutPerSteps(1);
			
			IpssLogger.getLogger().setLevel(Level.INFO);
			
			//dsNet.addDynamicEvent(DStabObjectFactory.createBusFaultEvent("Bus5",proc.getSubNetwork("SubNet-2"),SimpleFaultCode.GROUND_LG,0.5d,0.05),"3phaseFault@Bus5");
			
	        // TODO a special 3-phase 3seq dstab algorithm object, with the following two setting as default
			dstabAlgo.setSolver( new MultiNet3Ph3SeqDStabSolverImpl(dstabAlgo, mNetHelper));
			dstabAlgo.setDynamicEventHandler(new MultiNet3Ph3SeqDynEventProcessor(mNetHelper));
		  
			 if(dstabAlgo.initialization()){
				 dstabAlgo.performSimulation();
			 }

		   
			// System.out.println(sm.toCSVString(sm.getMachPeTable()));
			System.out.println(sm.toCSVString(sm.getBusAngleTable()));	
		    System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	}
	
	
	@Test
	public void test_phaseShift_SeqEquiv_IEEE9Bus_1port() throws InterpssException{
		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSEAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/IEEE9Bus/ieee9_deltaY.raw", 
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
		
		Bus3Phase bus5 = dsNet.getBus("Bus5");
		
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
		  assertTrue(equiv_subNet2.getSource3x1().length==1); 
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

}
