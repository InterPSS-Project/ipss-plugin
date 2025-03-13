package org.interpss.threePhase.system;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.powerflow.DistributionPowerFlowAlgorithm;
import org.interpss.threePhase.powerflow.impl.DistPowerFlowOutFunc;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;
import org.junit.Test;

public class TestIEEETestFeederPowerFlow {


	@Test
	public void testIEEE123BusPowerflow(){

		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);

		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.parseFeederData("testData/feeder/IEEE123","IEEE123Master_Modified_v2.dss");

		parser.calcVoltageBases();

		double mvaBase = 1.0;
		parser.convertActualValuesToPU(mvaBase);

		DStabNetwork3Phase distNet = parser.getDistNetwork();

//		String netStrFileName = "testData\\feeder\\IEEE123\\ieee123_modified_netString.dat";
//		try {
//			Files.write(Paths.get(netStrFileName), parser.getDistNetwork().net2String().getBytes());
//		} catch (IOException e) {
//
//			e.printStackTrace();
//		}

		// set the  turn ratios of regulators
		parser.getBranchByName("reg1a").setToTurnRatio(1.0438);

		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);

		assertTrue(distPFAlgo.powerflow());


//		for(AclfBus bus:distNet.getBusList()){
//			Bus3Phase bus3P = (Bus3Phase) bus;
//			System.out.println("Vabc of bus -"+bus3P.getId()+","+bus3P.get3PhaseVotlages().toString());
//
//		}

		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(distNet));

		DStab3PBus bus150r = distNet.getBus("150r");
		Complex3x1 vabc_150r = bus150r.get3PhaseVotlages();
		/*
		 * 150r,1.0436958711168833,-2.3401129732907437E-4,1.043751961975151,4.188644578364089,1.0437277807878227,2.094204336986655,1.0437 + j-0.00024  -0.52201 + j-0.90384  -0.52169 + j0.90399
         */
		assertTrue(vabc_150r.subtract(new Complex3x1(new Complex(1.0437, -0.00024),new Complex(-0.52201,-0.90384),new Complex(-0.52169, 0.90399))).absMax()<1.0E-4);

		/// Compared with IEEE TEST FEEDER RESULTS
		//RG1   |  1.0437 at    .00  |  1.0438 at -120.00  |  1.0438 at  120.00 |
		assertTrue(Math.abs(vabc_150r.a_0.abs()-1.0437)<1.0E-3);
		assertTrue(Math.abs(vabc_150r.b_1.abs()-1.0438)<1.0E-3);
		assertTrue(Math.abs(vabc_150r.c_2.abs()-1.0438)<1.0E-3);

		DStab3PBus bus21 = distNet.getBus("21");
		Complex3x1 vabc_21 = bus21.get3PhaseVotlages();
		/*
		 * 21,0.9976629334520096,-0.039839533149384404,1.0320577255690082,4.166775074256249,1.0114596191804741,2.0742543870511136,0.99687 + j-0.03974  -0.53558 + j-0.88221  -0.48799 + j0.88596
         */


		/// Compared with IEEE TEST FEEDER RESULTS
		//  21    |   .9983 at  -2.34  |  1.0320 at -121.22  |  1.0111 at  118.81 |    .441
		assertTrue(Math.abs(vabc_21.a_0.abs()-0.9983)<5.0E-3); //0.9983 is IEEE TEST FEEDER RESULT
		assertTrue(Math.abs(vabc_21.b_1.abs()-1.0320)<6.0E-3);
		assertTrue(Math.abs(vabc_21.c_2.abs()-1.0111)<5.0E-3);

		DStab3PBus bus30 = distNet.getBus("30");
		Complex3x1 vabc_30 = bus30.get3PhaseVotlages();

		/*
		 *30,0.9963094030535219,-0.04263498620437,1.0332257087662153,4.167548363697641,1.008218641157312,2.073577445296613,0.9954 + j-0.04246  -0.5355 + j-0.88362  -0.48582 + j0.88345
         */

		/// Compared with IEEE TEST FEEDER RESULTS
		// 30    |   .9969 at  -2.50  |  1.0331 at -121.18  |  1.0078 at  118.77 |    .701
		assertTrue(Math.abs(vabc_30.a_0.abs()-0.9969)<5.0E-3);
		assertTrue(Math.abs(vabc_30.b_1.abs()-1.0331)<6.0E-3);
		assertTrue(Math.abs(vabc_30.c_2.abs()-1.0078)<5.0E-3);
	}

	@Test
	public void testIEEE123BusPowerflow_2(){

		IpssCorePlugin.init();
		IpssCorePlugin.setLoggerLevel(Level.INFO);

		OpenDSSDataParser parser = new OpenDSSDataParser();
		parser.parseFeederData("testData/feeder/IEEE123","IEEE123Master_Modified.dss");

		parser.calcVoltageBases();

		double mvaBase = 1.0;
		parser.convertActualValuesToPU(mvaBase);

		DStabNetwork3Phase distNet = parser.getDistNetwork();

//		String netStrFileName = "testData\\feeder\\IEEE123\\ieee123_modified_netString.dat";
//		try {
//			Files.write(Paths.get(netStrFileName), parser.getDistNetwork().net2String().getBytes());
//		} catch (IOException e) {
//
//			e.printStackTrace();
//		}

		// set the  turn ratios of regulators
		parser.getBranchByName("reg1a").setToTurnRatio(1.0438);

		DistributionPowerFlowAlgorithm distPFAlgo = ThreePhaseObjectFactory.createDistPowerFlowAlgorithm(distNet);
		//distPFAlgo.orderDistributionBuses(true);
		distPFAlgo.setInitBusVoltageEnabled(true);
		//distPFAlgo.setMaxIteration(15);
		distPFAlgo.setTolerance(1.0E-4); // tolearnce = 5 kva
		assertTrue(distPFAlgo.powerflow());

		/*
		 *  Vabc of bus -Bus1,1.0100 + j0.0000  -0.5050 + j-0.87469  -0.5050 + j0.87469
			Vabc of bus -Bus2,0.99636 + j-0.05941  -0.54963 + j-0.83317  -0.44673 + j0.89258
			Vabc of bus -Bus3,0.99075 + j-0.07914  -0.56392 + j-0.81844  -0.42683 + j0.89759
			Vabc of bus -Bus4,0.98834 + j-0.09907  -0.57997 + j-0.80639  -0.40837 + j0.90546
		 */
//		for(AclfBus bus:distNet.getBusList()){
//			Bus3Phase bus3P = (Bus3Phase) bus;
//			System.out.println("Vabc of bus -"+bus3P.getId()+","+bus3P.get3PhaseVotlages().toString());
//
//		}

		System.out.println(DistPowerFlowOutFunc.powerflowResultSummary(distNet));

		DStab3PBus bus150r = distNet.getBus("150r");
		Complex3x1 vabc_150r = bus150r.get3PhaseVotlages();
		/*
		 * 150r,1.0436958711168833,-2.3401129732907437E-4,1.043751961975151,4.188644578364089,1.0437277807878227,2.094204336986655,1.0437 + j-0.00024  -0.52201 + j-0.90384  -0.52169 + j0.90399
         */
		assertTrue(vabc_150r.subtract(new Complex3x1(new Complex(1.0437, -0.00024),new Complex(-0.52201,-0.90384),new Complex(-0.52169, 0.90399))).absMax()<1.0E-4);

		/// Compared with IEEE TEST FEEDER RESULTS
		//RG1   |  1.0437 at    .00  |  1.0438 at -120.00  |  1.0438 at  120.00 |
		assertTrue(Math.abs(vabc_150r.a_0.abs()-1.0437)<1.0E-3);
		assertTrue(Math.abs(vabc_150r.b_1.abs()-1.0438)<1.0E-3);
		assertTrue(Math.abs(vabc_150r.c_2.abs()-1.0438)<1.0E-3);

		DStab3PBus bus21 = distNet.getBus("21");
		Complex3x1 vabc_21 = bus21.get3PhaseVotlages();
		/*
		 * 21,0.9976629334520096,-0.039839533149384404,1.0320577255690082,4.166775074256249,1.0114596191804741,2.0742543870511136,0.99687 + j-0.03974  -0.53558 + j-0.88221  -0.48799 + j0.88596
         */


		/// Compared with IEEE TEST FEEDER RESULTS
		//  21    |   .9983 at  -2.34  |  1.0320 at -121.22  |  1.0111 at  118.81 |    .441
		assertTrue(Math.abs(vabc_21.a_0.abs()-0.9983)<5.0E-3); //0.9983 is IEEE TEST FEEDER RESULT
		assertTrue(Math.abs(vabc_21.b_1.abs()-1.0320)<6.0E-3);
		assertTrue(Math.abs(vabc_21.c_2.abs()-1.0111)<5.0E-3);

		DStab3PBus bus30 = distNet.getBus("30");
		Complex3x1 vabc_30 = bus30.get3PhaseVotlages();

		/*
		 *30,0.9963094030535219,-0.04263498620437,1.0332257087662153,4.167548363697641,1.008218641157312,2.073577445296613,0.9954 + j-0.04246  -0.5355 + j-0.88362  -0.48582 + j0.88345
         */

		/// Compared with IEEE TEST FEEDER RESULTS
		// 30    |   .9969 at  -2.50  |  1.0331 at -121.18  |  1.0078 at  118.77 |    .701
		assertTrue(Math.abs(vabc_30.a_0.abs()-0.9969)<5.0E-3);
		assertTrue(Math.abs(vabc_30.b_1.abs()-1.0331)<6.0E-3);
		assertTrue(Math.abs(vabc_30.c_2.abs()-1.0078)<5.0E-3);
	}
}
