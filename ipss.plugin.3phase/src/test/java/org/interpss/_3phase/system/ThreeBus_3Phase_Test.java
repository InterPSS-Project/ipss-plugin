package org.interpss._3phase.system;

import static com.interpss.core.funcImpl.AcscFunction.acscXfrAptr;
import static org.interpss.threePhase.util.ThreePhaseUtilFunction.threePhaseXfrAptr;
import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.mapper.odm.ODMDStabParserMapper;
import org.interpss.numeric.NumericConstant;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.sparse.ISparseEqnComplexMatrix3x3;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.threePhase.basic.IEEEFeederLineCode;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.basic.dstab.DStab3PLoad;
import org.interpss.threePhase.basic.dstab.impl.DStab3PLoadImpl;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.algo.DynamicEventProcessor3Phase;
import org.interpss.threePhase.dynamic.impl.DStabNetwork3phaseImpl;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.util.ThreePhaseAclfOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.abc.Static3PXformer;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.acsc.BusGroundCode;
import com.interpss.core.acsc.XFormerConnectCode;
import com.interpss.core.acsc.adpter.AcscXformerAdapter;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.net.NetworkType;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.devent.DynamicSimuEvent;
import com.interpss.dstab.devent.DynamicSimuEventType;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineModelType;
import com.interpss.dstab.mach.RoundRotorMachine;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class ThreeBus_3Phase_Test {

	@Test
	public void testYMatrixabc() throws Exception{

		IpssCorePlugin.init();
		IpssLogger.getLogger().setLevel(Level.INFO);

		DStabNetwork3Phase net = create3BusSys();


		// initGenLoad-- summarize the effects of contributive Gen/Load to make equivGen/load for power flow calculation
		net.initContributeGenLoad(false);

		//create a load flow algorithm object
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	//run load flow using default setting



	  	assertTrue(algo.loadflow())	;

	 	net.initDStabNet();

	  	ISparseEqnComplexMatrix3x3  Yabc = net.getYMatrixABC();

	    //System.out.println(Yabc.getSparseEqnComplex());
	    // MatrixOutputUtil.matrixToMatlabMFile("output/ThreeBusYabc.m", Yabc.getSparseEqnComplex());

	  	BaseDStabBus<?,?> bus1 = net.getBus("Bus1");
	  	DStab3PBus bus13p = (DStab3PBus) bus1;
	  	DStab3PGen gen1 = (DStab3PGen) bus1.getContributeGen("Gen1");
	  	Complex3x3 yg1abc= gen1.getYabc(false);

	  	/*
	  	 * Gen1 yabc =
			aa = (0.049988758448491294, -3.332583699594674),ab = (-0.024994379224245654, 1.6662918492973369),ac = (-0.024994379224245647, 1.6662918492973375)
			ba = (-0.024994379224245643, 1.6662918492973369),bb = (0.0499887584784707, -3.3325837005937737),bc = (-0.024994379254225044, 1.666291850296437)
			ca = (-0.02499437922424565, 1.6662918492973369),cb = (-0.024994379254225044, 1.666291850296437),cc = (0.049988758478470695, -3.332583700593774)
		 */
	  	System.out.println("Gen1 yabc = \n"+yg1abc);

	  	 assertTrue(NumericUtil.equals(yg1abc.aa, new Complex(0.049988758448491294, -3.332583699594674),1.0E-5));
	  	 assertTrue(NumericUtil.equals(yg1abc.ab, new Complex(-0.024994379224245654, 1.6662918492973369),1.0E-5));

	  	DStab3PBranch xfr = net.getBranch("Bus2->Bus1(0)");
	  	System.out.println("xfr yabc = \n"+xfr.getBranchYabc());


	  	/*
	  	 * xfr yffabc =
			aa = (0.0, -20.0),ab = (0.0, 0.0),ac = (0.0, 0.0)
			ba = (0.0, 0.0),bb = (0.0, -20.0),bc = (0.0, 0.0)
			ca = (0.0, 0.0),cb = (0.0, 0.0),cc = (0.0, -20.0)
	  	 */

	  	Complex3x3 yffabc  =	xfr.getYffabc();
	  	System.out.println("xfr yffabc = \n"+yffabc);

	  	assertTrue(NumericUtil.equals(yffabc.aa, new Complex(0, -20.0),1.0E-5));
	  	 assertTrue(NumericUtil.equals(yffabc.ab, new Complex(0, 0),1.0E-5));

	  	/*
			xfr yftabc =
			aa = (-0.0, 11.547005383792515),ab = (0.0, -11.547005383792515),ac = (-0.0, -0.0)
			ba = (-0.0, -0.0),bb = (-0.0, 11.547005383792515),bc = (0.0, -11.547005383792515)
			ca = (0.0, -11.547005383792515),cb = (-0.0, -0.0),cc = (-0.0, 11.547005383792515)
	  	 */

	  	Complex3x3 yftabc  =	xfr.getYftabc();
	  	System.out.println("xfr yftabc = \n"+yftabc );
	  	System.out.println("xfr yft120 = \n"+yftabc.To120() );
	  	assertTrue(NumericUtil.equals(yftabc.aa, new Complex(-0.0, 11.547005383792515),1.0E-5));
	  	assertTrue(NumericUtil.equals(yftabc.ab, new Complex(-0.0, -11.547005383792515),1.0E-5));
	  	assertTrue(NumericUtil.equals(yftabc.ba, new Complex(-0.0, 0),1.0E-5));
	  	assertTrue(NumericUtil.equals(yftabc.ca, new Complex(0.0, -11.547005383792515),1.0E-5));

	  	/*
	  	 * xfr yttabc =
				aa = (0.0, -13.333333333333334),ab = (-0.0, 6.666666666666667),ac = (-0.0, 6.666666666666667)
				ba = (-0.0, 6.666666666666667),bb = (0.0, -13.333333333333334),bc = (-0.0, 6.666666666666667)
				ca = (-0.0, 6.666666666666667),cb = (-0.0, 6.666666666666667),cc = (0.0, -13.333333333333334)
	  	 */
	  	Complex3x3 yttabc  =	xfr.getYttabc();
	  	System.out.println("xfr yttabc = \n"+yttabc);

	  	assertTrue(NumericUtil.equals(yttabc.aa, new Complex(0.0, -13.333333333333334),1.0E-5));
	  	assertTrue(NumericUtil.equals(yttabc.ab, new Complex(-0.0, 6.666666666666667),1.0E-5));
	  	assertTrue(NumericUtil.equals(yttabc.ba, new Complex(-0.0, 6.666666666666667),1.0E-5));


	  	/**
	  	 * xfr ytfabc =
	  	 * aa = (-0.0, 11.547005383792515),ab = (-0.0, -0.0),ac = (0.0, -11.547005383792515)
			ba = (0.0, -11.547005383792515),bb = (-0.0, 11.547005383792515),bc = (-0.0, -0.0)
			ca = (-0.0, -0.0),cb = (0.0, -11.547005383792515),cc = (-0.0, 11.547005383792515)
	  	 */
	  	Complex3x3 ytfabc  =	xfr.getYtfabc();
	  	System.out.println("xfr ytfabc = \n"+ytfabc);

	  	/*
	  	 *  ytfabc = yftabc.transpose()
	  	 */
	  	assertTrue(NumericUtil.equals(ytfabc.aa, yftabc.aa,1.0E-5));
	  	assertTrue(NumericUtil.equals(ytfabc.ab, yftabc.ba,1.0E-5));
	  	assertTrue(NumericUtil.equals(ytfabc.ba, yftabc.ab,1.0E-5));

	  	BaseDStabBus<?,?> bus3 = net.getBus("Bus3");
	  	DStab3PBus bus33p = (DStab3PBus) bus3;
	  	DStab3PGen gen2 = (DStab3PGen) bus3.getContributeGen("Gen2");
	  	Complex3x3 yg2abc= gen2.getYabc(false);

	  	/*
	  	 * yg2_012 = diag([0;1/(0.02+0.2i);1/(0.02+0.2i)])

		yg2_012 =

		   0.0000 + 0.0000i   0.0000 + 0.0000i   0.0000 + 0.0000i
		   0.0000 + 0.0000i   0.4950 - 4.9505i   0.0000 + 0.0000i
		   0.0000 + 0.0000i   0.0000 + 0.0000i   0.4950 - 4.9505i

		>> yg2_abc=T*yg2_012*inv(T)

		yg2_abc =

		   0.3300 - 3.3003i  -0.1650 + 1.6502i  -0.1650 + 1.6502i
		  -0.1650 + 1.6502i   0.3300 - 3.3003i  -0.1650 + 1.6502i
		  -0.1650 + 1.6502i  -0.1650 + 1.6502i   0.3300 - 3.3003i
	  	 */
	  	System.out.println("Gen2@Bus3 yabc = \n"+yg2abc);



	  	DStab3PBranch line23 = net.getBranch("Bus2->Bus3(0)");

	  	/*
	  	 * line23 yttabc =
			aa = (1.8391102453069232E-32, -7.677777777777778),ab = (-3.697785493223493E-32, 2.2222222222222228),ac = (-3.697785493223493E-32, 2.2222222222222237)
			ba = (-2.201062793585413E-32, 2.2222222222222228),bb = (-6.93889390390723E-16, -7.67777777777778),bc = (6.938893903907232E-16, 2.2222222222222223)
			ca = (-2.3967128196818943E-32, 2.222222222222223),cb = (6.938893903907231E-16, 2.2222222222222223),cc = (-6.938893903907231E-16, -7.677777777777781)

	  	 */
	  	Complex3x3 yttabc_line23 = line23.getYttabc();
	  	System.out.println("line23 yttabc = \n"+yttabc_line23);

	  	assertTrue(NumericUtil.equals(yttabc_line23.ba, new Complex(0.0, 2.2222222222222228),1.0E-5));
	  	assertTrue(NumericUtil.equals(yttabc_line23.bb, new Complex(0.0, -7.6777777777777),1.0E-5));
	}


	@Test
	public void testDstab3Phase() throws Exception{

		IpssCorePlugin.init();
		IpssLogger.getLogger().setLevel(Level.INFO);
		DStabNetwork3Phase net = create3BusSys();


		// initGenLoad-- summarize the effects of contributive Gen/Load to make equivGen/load for power flow calculation
		net.initContributeGenLoad(false);

		DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
				net, IpssCorePlugin.getMsgHub());

		//create a load flow algorithm object
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	//run load flow using default setting



	  	assertTrue(algo.loadflow())	;


	  	dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.5);
	    //dstabAlgo.setRefMachine(net.getMachine("Bus3-mach1"));
		net.addDynamicEvent(create3PhaseFaultEvent("Bus2",net,0.2,0.05),"3phaseFault@Bus2");


		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus3-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus3","Bus1"});
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);

		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());

	  	if(dstabAlgo.initialization()){
	  		System.out.println(ThreePhaseAclfOutFunc.busLfSummary(net));
	  		System.out.println(net.getMachineInitCondition());

	  		dstabAlgo.performSimulation();
	  	}
	  	System.out.println(sm.toCSVString(sm.getBusAngleTable()));
	  	System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	}

	@Test
	public void test_3busfeeder_unbalanced_dstab() throws Exception{

		IpssCorePlugin.init();
		IpssLogger.getLogger().setLevel(Level.INFO);
		DStabNetwork3Phase net = create3BusFeeder_unbalanced();

		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(net);
		//distPFAlgo.orderDistributionBuses(true);

		assertTrue(distPFAlgo.powerflow());

		Complex3x1 iabc = net.getBranch("Bus650", "Bus611", "0").calc3PhaseCurrentFrom2To();
	  	System.out.println("Iabc bus(Bus650, Bus611) = "+net.getBranch("Bus650", "Bus611", "0").calc3PhaseCurrentFrom2To());
		assertTrue(iabc.a_0.abs()==0.0);
		assertTrue(iabc.b_1.abs()==0.0);
		assertTrue(iabc.c_2.subtract(new Complex(-0.03531,0.57015)).abs()<1.0E-5);

		for(BaseDStabBus<?,?> bus: net.getBusList()){
			System.out.println("id, sortNum: "+bus.getId()+","+bus.getSortNumber());
		}

		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(net));
		System.out.println(DistPowerFlowOutFunc.busLfSummary(net));

        for(DStabBranch bra: net.getBranchList()){

			DStab3PBranch bra3p = (DStab3PBranch) bra;
			System.out.println(bra.getId()+"Yabc= "+bra3p.getBranchYabc().toString());
		}

		DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
				net, IpssCorePlugin.getMsgHub());




		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.5);
	    //dstabAlgo.setRefMachine(net.getMachine("Bus3-mach1"));
//		net.addDynamicEvent(create3PhaseFaultEvent("Bus2",net,0.2,0.05),"3phaseFault@Bus2");
//

		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"SubBus-mach1"});
		sm.addBusStdMonitor(new String[]{"SubBus","Bus650"});
		sm.add3PhaseBusStdMonitor(new String[]{"Bus650","Bus611"});

		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);

		dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());

	  	if(dstabAlgo.initialization()){


	  		System.out.println(ThreePhaseAclfOutFunc.busLfSummary(net));
	  		System.out.println(net.getMachineInitCondition());

	  		System.out.println(net.getYMatrixABC().toString());

	  		//dstabAlgo.performSimulation();
	  		while(dstabAlgo.getSimuTime()<=dstabAlgo.getTotalSimuTimeSec()){

				for(String busId: sm.getBusPhAVoltTable().keySet()){

					 sm.addBusPhaseVoltageMonitorRecord( busId,dstabAlgo.getSimuTime(), net.getBus(busId).get3PhaseVotlages());
				}

				dstabAlgo.solveDEqnStep(true);


			}

	  	}
	  	System.out.println(sm.toCSVString(sm.getBusAngleTable()));
	  	System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	  	System.out.println(sm.toCSVString(sm.getBusPhAVoltTable()));
	  	System.out.println(sm.toCSVString(sm.getBusPhCVoltTable()));
	}

	@Test
	public void testDstabPosSeq() throws Exception{

		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(NetType.DStabNet, new String[]{
				"testData/threeBusSys.raw",
				//"testData/adpter/psse/v30/IEEE9Bus/ieee9.seq",
				"testData/threeBusSys.dyr"
		}));
		DStabModelParser parser =(DStabModelParser) adapter.getModel();

		//System.out.println(parser.toXmlDoc());



		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabParserMapper(IpssCorePlugin.getMsgHub())
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}


	    BaseDStabNetwork net =simuCtx.getDStabilityNet();


		// initGenLoad-- summarize the effects of contributive Gen/Load to make equivGen/load for power flow calculation
		//net.initContributeGenLoad();

		DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
				net, IpssCorePlugin.getMsgHub());


		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));

		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.005d);
		dstabAlgo.setTotalSimuTimeSec(0.5);

	    dstabAlgo.setRefMachine(net.getMachine("Bus3-mach1"));
		net.addDynamicEvent(create3PhaseFaultEvent("Bus2",net,0.2,0.05),"3phaseFault@Bus2");


		StateMonitor sm = new StateMonitor();
		sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus3","Bus1"});
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);

		//dstabAlgo.setDynamicEventHandler(new DynamicEventProcessor3Phase());

	  	if(dstabAlgo.initialization()){
	  		System.out.println(net.getMachineInitCondition());
	  		dstabAlgo.performSimulation();
	  	}

	  	System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	}

	@Test
	public void testSolvNetwork() throws Exception{

		IpssCorePlugin.init();
		IpssLogger.getLogger().setLevel(Level.INFO);

		DStabNetwork3Phase net = create3BusSys();




		// initGenLoad-- summarize the effects of contributive Gen/Load to make equivGen/load for power flow calculation
		net.initContributeGenLoad(false);

		//create a load flow algorithm object
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	//run load flow using default setting



	  	assertTrue(algo.loadflow())	;
	  	System.out.println(AclfOutFunc.loadFlowSummary(net));


		net.initDStabNet();

		  for(BaseDStabBus<?,?> bus: net.getBusList()){
			  if(bus instanceof DStab3PBus){
				  DStab3PBus ph3Bus = (DStab3PBus) bus;

				  System.out.println(bus.getId() +": Vabc =  "+ph3Bus.get3PhaseVotlages());
			  }
		  }
		  assertTrue(NumericUtil.equals(net.getBus("Bus1").get3PhaseVotlages().a_0, new Complex(0.90406,-0.51407 ),1.0E-4));
		/*
		 * Bus1: Vabc =  0.90406 + j-0.51407  -0.89723 + j-0.52591  -0.00683 + j1.03998
		Bus2: Vabc =  1.03109 + j-0.02767  -0.53951 + j-0.87912  -0.49159 + j0.90679
		Bus3: Vabc =  1.0250 + j0.0000  -0.5125 + j-0.88768  -0.5125 + j0.88768
		 */

	  //	ISparseEqnComplexMatrix3x3  Yabc = net.getYMatrixABC();
	   //	System.out.println(Yabc.getSparseEqnComplex());
	   // MatrixOutputUtil.matrixToMatlabMFile("output/ThreeBusYabc.m", Yabc.getSparseEqnComplex());
	  /**
	   * Xfr :Wye-g Wye-g connection
	   *
	   * Bus, Igen:Bus1,1.29185 + j-5.1606  -5.11514 + j1.46152  3.82329 + j3.69908     ->abs(Ia)     =5.3198
         Bus, Igen:Bus3,-0.1915 + j-4.87234  -4.12382 + j2.60201  4.31532 + j2.27032    ->abs(Ig2_c)  =4.8761



         Xfr: Delta-Wye-g
         Bus, Igen:Bus1,-1.46152 + j-5.11514  -3.69908 + j3.82329  5.1606 + j1.29185   ->>Same mag, phase shifted 30 deg
         Bus, Igen:Bus3,-0.1915 + j-4.87234  -4.12382 + j2.60201  4.31532 + j2.27032
	   */
	    net.setStaticLoadIncludedInYMatrix(true);

	    net.solveNetEqn();

		System.out.println(ThreePhaseAclfOutFunc.busLfSummary(net));

	    for(BaseDStabBus<?,?> bus: net.getBusList()){
			  if(bus instanceof DStab3PBus){
				  DStab3PBus bus3p = (DStab3PBus) bus;

				  if(bus.getId().equals("Bus1")){
					  System.out.println("Bus1 Vabc =:"+bus3p.get3PhaseVotlages());
					  assertTrue(NumericUtil.equals(bus3p.get3PhaseVotlages().a_0, new Complex(0.90406,-0.51407 ),1.0E-4));
				  }
				  else if(bus.getId().equals("Bus3")){
					  assertTrue(NumericUtil.equals(bus3p.get3PhaseVotlages().a_0, new Complex(1.0250,0),1.0E-4));
				  }
			  }
		  }


	}

	private DStabNetwork3Phase create3BusFeeder_unbalanced() throws InterpssException{
		   double ft2mile = 1.0/5280.0;
		   double baseVolt115kV = 115000.0;
		   double baseVolt4160 = 4160.0; //4.16 kV
		   double baseVolt480 = 480.0;
		   double vabase = 1.0E6; // 1MW
		   double kvabase = 1000.0;

		   double zBase4160 = baseVolt4160*baseVolt4160/vabase;
		   double zBase480  = baseVolt480*baseVolt480/vabase;

		   double loadScaleFactor =3;

		   DStabNetwork3Phase net = ThreePhaseObjectFactory.create3PhaseDStabNetwork();

			net.setBaseKva(kvabase);
			// identify this is a distribution network
			net.setNetworkType(NetworkType.DISTRIBUTION);

			DStab3PBus source = ThreePhaseObjectFactory.create3PDStabBus("SubBus", net);
			source.setAttributes("subsation bus", "");
			source.setBaseVoltage(baseVolt115kV);
			// set the bus to a non-generator bus
			source.setGenCode(AclfGenCode.SWING);
			// set the bus to a constant power load bus
			source.setLoadCode(AclfLoadCode.NON_LOAD);
			source.setVoltage(new Complex(1.0,0));
		    //source.set3PhaseVoltages(new Complex());

			DStab3PGen constantGen = ThreePhaseObjectFactory.create3PGenerator("1");
			constantGen.setMvaBase(1.0);
			constantGen.setPosGenZ(new Complex(0.0,0.05));
			constantGen.setNegGenZ(new Complex(0.0,0.05));
			constantGen.setZeroGenZ(new Complex(0.0,0.05));
			source.getContributeGenList().add(constantGen);


			EConstMachine mach = (EConstMachine)DStabObjectFactory.
					createMachine("MachId", "MachName", MachineModelType.ECONSTANT, net, "SubBus", "1");

			mach.setRating(1, UnitType.mVA, net.getBaseKva());
			mach.setRatedVoltage(baseVolt115kV);
			mach.setH(50000.0);
			mach.setXd1(0.05);



			DStab3PBus bus650 = ThreePhaseObjectFactory.create3PDStabBus("Bus650", net);
			bus650.setAttributes("feeder 650", "");
			bus650.setBaseVoltage(baseVolt4160);
			// set the bus to a non-generator bus
			bus650.setGenCode(AclfGenCode.NON_GEN);
			// set the bus to a constant power load bus
			bus650.setLoadCode(AclfLoadCode.NON_LOAD);



//			Bus3Phase bus632 = ThreePhaseObjectFactory.create3PDStabBus("Bus632", net);
//			bus632.setAttributes("feeder 632", "");
//			bus632.setBaseVoltage(baseVolt4160);
//			// set the bus to a non-generator bus
//
//			// set the bus to a constant power load bus
//			bus632.setLoadCode(AclfLoadCode.NON_LOAD);


//			Bus3Phase bus633 = ThreePhaseObjectFactory.create3PDStabBus("Bus633", net);
//			bus633.setAttributes("feeder 633", "");
//			bus633.setBaseVoltage(baseVolt4160);
//
//			// set the bus to a constant power load bus
//			bus633.setLoadCode(AclfLoadCode.NON_LOAD);
//
//
//			Bus3Phase bus634 = ThreePhaseObjectFactory.create3PDStabBus("Bus634", net);
//			bus634.setAttributes("feeder 634", "");
//			bus634.setBaseVoltage(baseVolt480);
//			// set the bus to a constant power load bus
//			bus634.setLoadCode(AclfLoadCode.CONST_P);
//			/*
//			 * New Load.634a Bus1=634.1     Phases=1 Conn=Wye  Model=1 kV=0.277  kW=160   kvar=110
//            New Load.634b Bus1=634.2     Phases=1 Conn=Wye  Model=1 kV=0.277  kW=120   kvar=90
//            New Load.634c Bus1=634.3     Phases=1 Conn=Wye  Model=1 kV=0.277  kW=120   kvar=90
//			 */
//			Load3Phase load634 = new Load3PhaseImpl();
//			load634.set3PhaseLoad( new Complex3x1(new Complex(0.160,0.11),new Complex(0.120,0.09),new Complex(0.120,0.090)).multiply(loadScaleFactor));
//			bus634.getThreePhaseLoadList().add(load634);
//
//
//
//
//			Bus3Phase bus645 = ThreePhaseObjectFactory.create3PDStabBus("Bus645", net);
//			bus645.setAttributes("feeder 645", "");
//			bus645.setBaseVoltage(baseVolt4160);
//			// set the bus to a constant power load bus
//			bus645.setLoadCode(AclfLoadCode.CONST_P);
//			//New Load.645 Bus1=645.2       Phases=1 Conn=Wye  Model=1 kV=2.4      kW=170   kvar=125
//			Load3Phase load645 = new Load3PhaseImpl();
//			load645.set3PhaseLoad( new Complex3x1(new Complex(0.0),new Complex(0.170,0.125),new Complex(0)).multiply(loadScaleFactor));
//			bus645.getThreePhaseLoadList().add(load645);
//
//
//
//
//
//			Bus3Phase bus646 = ThreePhaseObjectFactory.create3PDStabBus("Bus646", net);
//			bus646.setAttributes("feeder 646", "");
//			bus646.setBaseVoltage(baseVolt4160);
//			// set the bus to a constant power load bus
//
//			bus646.setLoadCode(AclfLoadCode.CONST_P);
//			//New Load.646 Bus1=646.2.3    Phases=1 Conn=Delta Model=2 kV=4.16    kW=230   kvar=132
//			Load3Phase load646 = new Load3PhaseImpl();
//			load646.set3PhaseLoad( new Complex3x1(new Complex(0.0),new Complex(0.230/2,0.132/2),new Complex(0.230/2,0.132/2)).multiply(loadScaleFactor));
//			bus646.getThreePhaseLoadList().add(load646);
//
//
//
//
//			Bus3Phase bus671 = ThreePhaseObjectFactory.create3PDStabBus("Bus671", net);
//			bus671.setAttributes("feeder 671", "");
//			bus671.setBaseVoltage(baseVolt4160);
//			// set the bus to a constant power load bus
//			bus671.setLoadCode(AclfLoadCode.CONST_P);
//			// New Load.671 Bus1=671.1.2.3  Phases=3 Conn=Delta Model=1 kV=4.16   kW=1155 kvar=660
//			Load3Phase load671 = new Load3PhaseImpl();
//			load671.set3PhaseLoad(new Complex3x1(new Complex(1.155/3,0.660/3),new Complex(1.155/3,0.660/3),new Complex(1.155/3,0.660/3)).multiply(loadScaleFactor));
//			bus671.getThreePhaseLoadList().add(load671);
//
//
//
//
//			Bus3Phase bus684 = ThreePhaseObjectFactory.create3PDStabBus("Bus684", net);
//			bus684.setAttributes("feeder 684", "");
//			bus684.setBaseVoltage(baseVolt4160);
//			// set the bus to a constant power load bus
//			bus684.setLoadCode(AclfLoadCode.NON_LOAD);
//

			DStab3PBus bus611 = ThreePhaseObjectFactory.create3PDStabBus("Bus611", net);
			bus611.setAttributes("feeder 611", "");
			bus611.setBaseVoltage(baseVolt4160);
			// set the bus to a constant power load bus
			bus611.setLoadCode(AclfLoadCode.CONST_P);
			//New Load.611 Bus1=611.3      Phases=1 Conn=Wye  Model=5 kV=2.4  kW=170   kvar=80
			DStab3PLoad load611 = new DStab3PLoadImpl();
			load611.set3PhaseLoad(new Complex3x1(new Complex(0),new Complex(0),new Complex(0.170,0.080)).multiply(loadScaleFactor));
			bus611.getThreePhaseLoadList().add(load611);




			////////////////////////////////// transformers ////////////////////////////////////////////////////////

			DStab3PBranch xfr1_2 = ThreePhaseObjectFactory.create3PBranch("SubBus", "Bus650", "0", net);
			xfr1_2.setBranchCode(AclfBranchCode.XFORMER);
			xfr1_2.setToTurnRatio(1.0);
			xfr1_2.setZ( new Complex( 0.0, 0.04 ));


		    AcscXformerAdapter xfr0 = acscXfrAptr.apply(xfr1_2);
			xfr0.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);
			xfr0.setToGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);



			///////////////////////////////////////////////////////// LINES ////////////////////////////////////////



			// New Line.684611    Phases=1 Bus1=684.3        Bus2=611.3      LineCode=mtx605 Length=300  units=ft

			DStab3PBranch Line650_611 = ThreePhaseObjectFactory.create3PBranch("Bus650", "Bus611", "0", net);
			Line650_611.setBranchCode(AclfBranchCode.LINE);
			double length = 300.0*ft2mile; // convert to miles
			Complex3x3 zabc_pu = IEEEFeederLineCode.zMtx605.multiply(length/zBase4160);
			Line650_611.setZabc(zabc_pu);




			return net;

	}

private DStabNetwork3Phase create3BusSys() throws InterpssException{

		DStabNetwork3Phase net = new DStabNetwork3phaseImpl();

		net.setNetworkType(NetworkType.TRANSMISSION);

		double baseKva = 100000.0;

		// set system basekva for loadflow calculation
		net.setBaseKva(baseKva);

	//Bus 1
		DStab3PBus bus1 = ThreePhaseObjectFactory.create3PDStabBus("Bus1", net);
  		// set bus name and description attributes
  		bus1.setAttributes("Bus 1", "");
  		// set bus base voltage
  		bus1.setBaseVoltage(16500.0);
  		// set bus to be a swing bus
  		bus1.setGenCode(AclfGenCode.GEN_PV);
  		bus1.setDesiredVoltMag(1.04);
  		bus1.setGenP(0.7164);

  		// create contribute generator
  		// MVABase, power, sourceZ1/2/0

  		DStab3PGen gen1 = ThreePhaseObjectFactory.create3PGenerator("Gen1");
  		gen1.setMvaBase(100.0);
  		gen1.setPosGenZ(new Complex(0.003,0.2));
  		gen1.setNegGenZ(new Complex(0.003,0.2));
  		gen1.setZeroGenZ(new Complex(0,1.0E9));

  		//add to contributed gen list
  		bus1.getContributeGenList().add(gen1);
  		bus1.setSortNumber(0);

  		RoundRotorMachine mach = (RoundRotorMachine)DStabObjectFactory.
				createMachine("1", "Mach-1", MachineModelType.EQ11_ED11_ROUND_ROTOR, net, "Bus1", "Gen1");

  		mach.setRating(100, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(16500.0);
		mach.calMultiFactors();
		mach.setH(5.0);
		mach.setD(0.00);
		mach.setRa(0.003);
		mach.setXl(0.14);
		mach.setXd(1.1);
		mach.setXq(1.08);
		mach.setXd1(0.23);
		mach.setTd01(5.6);
		mach.setXq1(0.23);
		mach.setTq01(1.5);
		mach.setXd11(0.2);
		mach.setTq011(0.05);
		mach.setXq11(0.2);
		mach.setTd011(0.03);
		mach.setSliner(2.0);  // no saturation
		mach.setSe100(0.0);   // no saturation
		mach.setSe120(0.0);



  	// Bus 2
		DStab3PBus bus2 = ThreePhaseObjectFactory.create3PDStabBus("Bus2", net);
  		// set bus name and description attributes
  		bus2.setAttributes("Bus 2", "");
  		// set bus base voltage
  		bus2.setBaseVoltage(230000.0);
  		// set bus to be a swing bus
  		bus2.setGenCode(AclfGenCode.NON_GEN);
  		bus2.setLoadCode(AclfLoadCode.CONST_P);

  		//TODO Three-sequence load
  		//AclfLoad load = CoreObjectFactory.createAclfLoad("1");
  		DStab3PLoad load = new DStab3PLoadImpl();

  		load.setLoadCP(new Complex (1.0, 0.2));
  		//load.set3PhaseLoad(new Complex3x1(phaseLoad,phaseLoad,phaseLoad));
  		load.setCode(AclfLoadCode.CONST_P);
  		bus2.getContributeLoadList().add(load);



  		bus2.setSortNumber(1);




  	  	// Bus 3
  		DStab3PBus bus3 = ThreePhaseObjectFactory.create3PDStabBus("Bus3", net);
  		// set bus name and description attributes
  		bus3.setAttributes("Bus 3", "");
  		// set bus base voltage
  		bus3.setBaseVoltage(230000.0);
  		// set bus to be a swing bus
  		bus3.setGenCode(AclfGenCode.SWING);
  		bus3.setDesiredVoltMag(1.025);

  		bus3.setSortNumber(2);

  		DStab3PGen gen2 = ThreePhaseObjectFactory.create3PGenerator("Gen2");
  		gen2.setMvaBase(100.0);
  		gen2.setPosGenZ(new Complex(0.02,0.2));
  		gen2.setNegGenZ(new Complex(0.02,0.2));
  		gen2.setZeroGenZ(new Complex(0,1.0E9));

  		//add to contributed gen list
  		bus3.getContributeGenList().add(gen2);

  		EConstMachine mach2 = (EConstMachine)DStabObjectFactory.
				createMachine("1", "Mach-1", MachineModelType.ECONSTANT, net, "Bus3", "Gen2");

  		mach2.setRating(100, UnitType.mVA, net.getBaseKva());
		mach2.setRatedVoltage(230000.0);
		mach2.calMultiFactors();
		mach2.setH(5.0E6);
		mach2.setD(0.01);
		mach2.setRa(0.02);
		mach2.setXd1(0.20);




		//////////////////transformers///////////////////////////////////////////
		DStab3PBranch xfr12 = ThreePhaseObjectFactory.create3PBranch("Bus2", "Bus1", "0", net);
		xfr12.setBranchCode(AclfBranchCode.XFORMER);
		xfr12.setZ( new Complex( 0.0, 0.05 ));
		xfr12.setZ0( new Complex(0.0, 0.05 ));
		Static3PXformer xfr = threePhaseXfrAptr.apply(xfr12);
		//TODO change for testing

		xfr.setToGrounding(BusGroundCode.UNGROUNDED, XFormerConnectCode.DELTA, new Complex(0.0,0.0), UnitType.PU);
		//xfr.setFromConnectGroundZ(XfrConnectCode.WYE_SOLID_GROUNDED, new Complex(0.0,0.0), UnitType.PU);
		xfr.setFromGrounding(BusGroundCode.SOLID_GROUNDED, XFormerConnectCode.WYE, new Complex(0.0,0.0), UnitType.PU);

  		DStab3PBranch bra23 = ThreePhaseObjectFactory.create3PBranch("Bus2", "Bus3", "0", net);
		bra23.setBranchCode(AclfBranchCode.LINE);
		bra23.setZ( new Complex(0.000,   0.100));
		bra23.setHShuntY(new Complex(0, 0.200/2));
		bra23.setZ0( new Complex(0.0,	  0.3));
		bra23.setHB0(0.200/2);


		//net.setBusNumberArranged(true);
  		return net;

	}

	private DynamicSimuEvent create3PhaseFaultEvent(String faultBusId, BaseDStabNetwork net,double startTime, double durationTime){
	    // define an event, set the event id and event type.
			DynamicSimuEvent event1 = DStabObjectFactory.createDEvent("BusFault3P@"+faultBusId, "Bus Fault 3P@"+faultBusId,
					DynamicSimuEventType.BUS_FAULT, net);
			event1.setStartTimeSec(startTime);
			event1.setDurationSec(durationTime);

	   // define a bus fault
			BaseDStabBus<?,?> faultBus = net.getDStabBus(faultBusId);
			AcscBusFault fault = CoreObjectFactory.createAcscBusFault("Bus Fault 3P@"+faultBusId, net, true /* cacheBusScVolt */);
			fault.setBus(faultBus);
			fault.setFaultCode(SimpleFaultCode.GROUND_3P);
			fault.setZLGFault(NumericConstant.SmallScZ);

	   // add this fault to the event, must be consist with event type definition before.
			event1.setBusFault(fault);
			return event1;
	}

}
