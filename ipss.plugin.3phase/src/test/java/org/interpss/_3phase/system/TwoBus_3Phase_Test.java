package org.interpss._3phase.system;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.sparse.ISparseEqnComplexMatrix3x3;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.basic.dstab.DStab3PGen;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.dynamic.impl.DStabNetwork3phaseImpl;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.DStabObjectFactory;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.mach.EConstMachine;
import com.interpss.dstab.mach.MachineModelType;
import com.interpss.dstab.mach.RoundRotorMachine;

public class TwoBus_3Phase_Test {

	//@Test
	public void test3PhaseTransformerYabc(){

	}


	@Test
	public void testInitBasedOnLF() throws InterpssException{

		IpssCorePlugin.init();

		DStabNetwork3Phase net = create2BusSys();

		// initGenLoad-- summarize the effects of contributive Gen/Load to make equivGen/load for power flow calculation
		net.initContributeGenLoad(false);

		//create a load flow algorithm object
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	//run load flow using default setting


	  	assertTrue(algo.loadflow())	;
		//output load flow summary result
	  	/*
	  	 *
       BusID          Code           Volt(pu)   Angle(deg)     P(pu)     Q(pu)      Bus Name
  -------------------------------------------------------------------------------------------
  Bus1         PV                   1.04000        3.85       0.7164    0.0719   Bus 1
  Bus3         Swing                1.02500        0.00      -0.7164   -0.2347   Bus 2
	  	 */
		System.out.println(AclfOutFunc.loadFlowSummary(net));

		   net.initThreePhaseFromLfResult();

			  for(BaseDStabBus<?,?> bus: net.getBusList()){
				  if(bus instanceof DStab3PBus){
					  DStab3PBus ph3Bus = (DStab3PBus) bus;

					  System.out.println(bus.getId() +": Vabc =  "+ph3Bus.get3PhaseVotlages());
	                  /*
	                   *Bus1: Vabc =  1.03765 + j0.06989  -0.57935 + j0.86368  -0.4583 + j-0.93358

						Bus3: Vabc =  1.0250 + j0.0000  -0.5125 + j0.88768  -0.5125 + j-0.88768
	                   */
					  if(bus.getId().equals("Bus1")){

						  //phase a: 5.77 - 30 = -24.23
						  assertTrue(ph3Bus.get3PhaseVotlages().a_0.subtract(new Complex(1.03765,0.06989)).abs()<5.0E-5);

					  }
				  }
			  }

	}

	@Test
	public void testYMatrixabc() throws Exception{

		IpssCorePlugin.init();

		DStabNetwork3Phase net = create2BusSys();


		// initGenLoad-- summarize the effects of contributive Gen/Load to make equivGen/load for power flow calculation
		net.initContributeGenLoad(false);

//		//create a load flow algorithm object
//	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
//	  	//run load flow using default setting
//
//
//
//	  	assertTrue(algo.loadflow())	;

	  	ISparseEqnComplexMatrix3x3  Yabc = net.formYMatrixABC();

	  //	System.out.println(Yabc.getSparseEqnComplex());
	  //	MatrixUtil.matrixToMatlabMFile("output/twoBusYabc.m", Yabc.getSparseEqnComplex());

	  	/**
	  	 * y12_012 = diag([-3.33i,-10i,-10i])

			y12_012 =

			   0.0000 - 3.3300i   0.0000 + 0.0000i   0.0000 + 0.0000i
			   0.0000 + 0.0000i   0.0000 -10.0000i   0.0000 + 0.0000i
			   0.0000 + 0.0000i   0.0000 + 0.0000i   0.0000 -10.0000i

            >> y12ABC=T*y12_012*inv(T)

		     y12ABC =

		   0.0000 - 7.7777i  -0.0000 + 2.2222i  -0.0000 + 2.2222i
		   0.0000 + 2.2222i   0.0000 - 7.7777i  -0.0000 + 2.2222i
		  -0.0000 + 2.2222i   0.0000 + 2.2222i   0.0000 - 7.7777i
	  	 */

	  	Complex3x3 y12 = Yabc.getA(0, 1);
	  	assertTrue(NumericUtil.equals(y12.aa, new Complex(0.0000, 7.7777),5.0E-4));
	  	assertTrue(NumericUtil.equals(y12.ab, new Complex(0.0000, -2.2222),5.0E-4));
	    /*
	     *  yABC_bus1_gen =
	     *    0.0000 - 3.3333i  -0.0000 + 1.6667i  -0.0000 + 1.6667i
			   0.0000 + 1.6667i   0.0000 - 3.3333i  -0.0000 + 1.6667i
			  -0.0000 + 1.6667i  -0.0000 + 1.6667i   0.0000 - 3.3333i
	     */


	  	Complex3x3 y11 = Yabc.getA(0, 0);
	  	//System.out.println("y11 = \n"+y11);
	  	assertTrue(NumericUtil.equals(y11.aa, new Complex(0.04999,-11.01036),1.0E-4));
	  	assertTrue(NumericUtil.equals(y11.ab, new Complex(-0.02499,3.88851),1.0E-4));


	    /*
	     * yABC_bus2_gen =

		   0.3300 - 3.3003i  -0.1650 + 1.6502i  -0.1650 + 1.6502i
		  -0.1650 + 1.6502i   0.3300 - 3.3003i  -0.1650 + 1.6502i
		  -0.1650 + 1.6502i  -0.1650 + 1.6502i   0.3300 - 3.3003i
	     */
	}

    @Test
	public void testSolvNetwork() throws Exception{

		IpssCorePlugin.init();
		IpssLogger.getLogger().setLevel(Level.INFO);
		DStabNetwork3Phase net = create2BusSys();
		
		//net.initBusVoltage();

		// initGenLoad-- summarize the effects of contributive Gen/Load to make equivGen/load for power flow calculation
		net.initContributeGenLoad(false);

		DynamicSimuAlgorithm dstabAlgo =DStabObjectFactory.createDynamicSimuAlgorithm(
				net, IpssCorePlugin.getMsgHub());

		//create a load flow algorithm object
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	//run load flow using default setting



	  	assertTrue(algo.loadflow())	;
	  	
	  	System.out.println(AclfOutFunc.loadFlowSummary(net));

		StateMonitor sm = new StateMonitor();
		//sm.addGeneratorStdMonitor(new String[]{"Bus1-mach1","Bus2-mach1"});
		sm.addBusStdMonitor(new String[]{"Bus1","Bus2"});
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);


	  	assertTrue(dstabAlgo.initialization());
	  	//assertTrue(net.initDStabNet());
	  	//dstabAlgo.solveDEqnStep(true);
	  	assertTrue(net.solveNetEqn());

	  	 for(BaseDStabBus<?,?> bus: net.getBusList()){
			  if(bus instanceof DStab3PBus){
				  DStab3PBus ph3Bus = (DStab3PBus) bus;

				 System.out.println(bus.getId() +": Vabc =  "+ph3Bus.get3PhaseVotlages());
                 /*
                  *Bus1: Vabc =  1.03765 + j0.06989    -0.4583 + j-0.93358  -0.57935 + j0.86368

					Bus3: Vabc =  1.0250 + j0.0000    -0.5125 + j-0.88768  -0.5125 + j0.88768
                  */
				  if(bus.getId().equals("Bus1")){

					  //phase a: 5.77 - 30 = -24.23
					  assertTrue(ph3Bus.get3PhaseVotlages().a_0.subtract(new Complex(1.03765,0.06989)).abs()<5.0E-5);
					  assertTrue(Math.abs(ph3Bus.getThreeSeqVoltage().b_1.abs()-1.040)<1.0E-4);
					  assertTrue(ph3Bus.getThreeSeqVoltage().b_1.subtract(new Complex(1.0376487360089972, 0.0698927284775248)).abs()<5.0E-5);
				  }

                 if(bus.getId().equals("Bus2")){

					  //phase a: 5.77 - 30 = -24.23
					  assertTrue(ph3Bus.get3PhaseVotlages().b_1.subtract(new Complex(-0.5125, -0.88768)).abs()<5.0E-5);
					  assertTrue(Math.abs(ph3Bus.getThreeSeqVoltage().b_1.abs()-1.025)<1.0E-4);
					  assertTrue(ph3Bus.getThreeSeqVoltage().b_1.subtract(new Complex(1.025, 0.0)).abs()<5.0E-5);

				  }
			  }
		  }


	 //    ISparseEqnComplexMatrix3x3  Yabc = net.getYMatrixABC();
	 //  	System.out.println(Yabc.getSparseEqnComplex());
	 // 	MatrixUtil.matrixToMatlabMFile("output/twoBusYabc.m", Yabc.getSparseEqnComplex());


	}

	@Test
	public void testDstab() throws Exception{

		IpssCorePlugin.init();
		IpssLogger.getLogger().setLevel(Level.INFO);
		DStabNetwork3Phase net = create2BusSys();


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
		dstabAlgo.setTotalSimuTimeSec(0.005);

		//dstabAlgo.setRefMachine(net.getMachine("Bus3-mach1"));
		//net.addDynamicEvent(create3PhaseFaultEvent("Bus5",net,1.0d,0.05),"3phaseFault@Bus5");


		StateMonitor sm = new StateMonitor();
		sm.addBusStdMonitor(new String[]{"Bus1"});
		// set the output handler
		dstabAlgo.setSimuOutputHandler(sm);
		dstabAlgo.setOutPutPerSteps(1);

	  	if(dstabAlgo.initialization()){
	  	    System.out.print(net.getYMatrixABC().getSparseEqnComplex().toString());
	  		dstabAlgo.performSimulation();
	  	}
	  	System.out.println(sm.toCSVString(sm.getBusAngleTable()));
	  	System.out.println(sm.toCSVString(sm.getBusVoltTable()));
	  	
	  	//assertTrue(sm.getBusAngleTable().get(")
	  	
	}

private DStabNetwork3Phase create2BusSys() throws InterpssException{

		DStabNetwork3Phase net = new DStabNetwork3phaseImpl();

		double baseKva = 100000.0;

		// set system basekva for loadflow calculation
		net.setBaseKva(baseKva);

	   //Bus 1
		DStab3PBus bus1 = ThreePhaseObjectFactory.create3PDStabBus("Bus1", net);
  		// set bus name and description attributes
  		bus1.setAttributes("Bus 1", "");
  		// set bus base voltage
  		bus1.setBaseVoltage(230000.0);
  		// set bus to be a swing bus
  		bus1.setGenCode(AclfGenCode.GEN_PV);
  		// adapt the bus object to a swing bus object
  		
  		bus1.setDesiredVoltMag(1.04);
  		bus1.setGenP(0.7164);

  		// create contribute generator
  		// MVABase, power, sourceZ1/2/0

  		DStab3PGen gen1 = ThreePhaseObjectFactory.create3PGenerator("Gen1");
  		gen1.setMvaBase(100.0);
  		gen1.setDesiredVoltMag(1.04);
  		gen1.setGen(new Complex(0.7164,0.2710));
  		gen1.setPosGenZ(new Complex(0.003,0.2));
  		gen1.setNegGenZ(new Complex(0.003,0.2));
  		gen1.setZeroGenZ(new Complex(0.000,1.0E9));

  		//add to contributed gen list
  		bus1.getContributeGenList().add(gen1);
  		bus1.setSortNumber(0);

  		RoundRotorMachine mach = (RoundRotorMachine)DStabObjectFactory.
				createMachine("1", "Mach-1", MachineModelType.EQ11_ED11_ROUND_ROTOR, net, "Bus1", "Gen1");

  		mach.setRating(100, UnitType.mVA, net.getBaseKva());
		mach.setRatedVoltage(230000.0);
		mach.setH(5.0);
		mach.setD(0.01);
		mach.setRa(0.003);
		mach.setXl(0.14);
		mach.setXd(1.1);
		mach.setXq(1.08);
		mach.setXd1(0.23);
		mach.setTd01(5.6);
		mach.setXq1(0.23);
		mach.setTq01(1.5);
		mach.setXd11(0.20);
		mach.setTq011(0.05);
		mach.setXq11(0.20);
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
  		bus2.setGenCode(AclfGenCode.SWING);
  		
  		bus2.setDesiredVoltMag(1.025);

  		bus2.setSortNumber(1);

  		DStab3PGen gen2 = ThreePhaseObjectFactory.create3PGenerator("Gen2");
  		gen2.setMvaBase(100.0);
  		gen2.setDesiredVoltMag(1.025);
  		//gen2.setGen(new Complex(0.7164,0.2710));
  		gen2.setPosGenZ(new Complex(0.02,0.2));
  		gen2.setNegGenZ(new Complex(0.02,0.2));
  		gen2.setZeroGenZ(new Complex(0.000,1.0E9));

  		//add to contributed gen list
  		bus2.getContributeGenList().add(gen2);

  		EConstMachine mach2 = (EConstMachine)DStabObjectFactory.
				createMachine("1", "Mach-1", MachineModelType.ECONSTANT, net, "Bus2", "Gen2");

  		mach2.setRating(100, UnitType.mVA, net.getBaseKva());
		mach2.setRatedVoltage(230000.0);
		mach2.calMultiFactors();
		mach2.setH(5.0E6);
		mach2.setD(0.01);
		mach2.setRa(0.02);
		mach2.setXd1(0.20);



  		DStab3PBranch bra = ThreePhaseObjectFactory.create3PBranch("Bus1", "Bus2", "0", net);
		bra.setBranchCode(AclfBranchCode.LINE);
		bra.setZ( new Complex(0.000,   0.100));
		bra.setHShuntY(new Complex(0, 0.200/2));
		bra.setZ0( new Complex(0.0,	  0.3));
		bra.setHB0(0.200/2);


		//net.setBusNumberArranged(true);
  		return net;

	}




}
