package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.odm.mapper.ODMAclfParserMapper;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.BusBranchControlType;
import com.interpss.core.aclf.hvdc.HvdcControlMode;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.funcImpl.AclfNetObjectComparator;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class Kundur_2Area_LCCHVDC2T_Test extends CorePluginTestSetup {
	//@Test
	public void test_LCCHVDC_DeepCopy() throws Exception {
		AclfNetwork net = createTestCase();
		
		AclfNetwork netCopy = net.hzCopy();
		
		//assertTrue("", net.diffState(netCopy));
  		AclfNetObjectComparator comp = new AclfNetObjectComparator(net, netCopy);
  		comp.compareNetwork();
  		
  		System.out.println("Differences found: " + comp.getDiffMsgList());
  		assertTrue("" + comp.getDiffMsgList(), comp.getDiffMsgList().size() == 0);
		
		test_LCCHVDC_Data(netCopy);
	}
	
	@Test
	public void test_LCCHVDC_JSonCopy() throws Exception {
		AclfNetwork net = createTestCase();
		
		HvdcLine2TLCC<AclfBus> lccHVDC = (HvdcLine2TLCC<AclfBus>) net.getSpecialBranchList().get(0);
		// we need to set the name, otherwise the json copy will not work
		lccHVDC.setName("Hvdc line: Bus7->Bus9(1)");
		assertTrue(lccHVDC.getRectifier().getParentHvdc() != null);
		assertTrue(lccHVDC.getInverter().getParentHvdc() != null);
		
		AclfNetwork netCopy = net.jsonCopy();
		
		//AclfBus bus7 = net.getBus("Bus7");
		//AclfBus bus7Copy = netCopy.getBus("Bus7");
		
		//assertTrue("", net.diffState(netCopy));
  		AclfNetObjectComparator comp = new AclfNetObjectComparator(net, netCopy);
  		comp.compareNetwork();
  		
  		System.out.println("Differences found: " + comp.getDiffMsgList());
  		assertTrue("" + comp.getDiffMsgList(), comp.getDiffMsgList().size() == 0);
		
		test_LCCHVDC_Data(netCopy);
	}
	
	@Test
	public void test_LCCHVDC_Loadflow() throws Exception {
		AclfNetwork net = createTestCase();
		//System.out.println(net.net2String());

		HvdcLine2TLCC<AclfBus> lccHVDC = (HvdcLine2TLCC<AclfBus>) net.getSpecialBranchList().get(0);
		//Note: this is now handled in the odm mapper level, so no need to set it here
		//lccHVDC.setPuBasedPowerFlowAlgo(false);
		 
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		algo.setMaxIterations(30);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());
  		
  		System.out.println(AclfOutFunc.loadFlowSummary(net));
  		AclfBus bus1 = net.getBus("Bus1");
  		assertEquals(bus1.getVoltageAng(UnitType.Deg),69.43,0.01);

  		
  		/*
  		 * PSS/E power flow results

			BUS      1     BUS1   AR1  20.000 CKT     MW     MVAR     MVA   % 1.0300PU   69.43  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X      1
			FROM GENERATION                        551.3   150.7R  571.5  64 20.600KV               MW     MVAR    1                 1
			TO      5     BUS5   AR1  230.00  1    551.3   150.7   571.5     1.0000LK              0.00   51.31    1                 1

			BUS      2     BUS2   AR1  20.000 CKT     MW     MVAR     MVA   % 1.0100PU   63.67  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X      2
			FROM GENERATION                        741.0   228.6R  775.5  86 20.199KV               MW     MVAR    1                 1
			TO      6     BUS6   AR1  230.00  1    740.9   228.3   775.3     1.0000LK              0.00   98.22    1                 1

			BUS      3     BUS3   AR2  20.000 CKT     MW     MVAR     MVA   % 1.0300PU   -6.80  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X      3
			FROM GENERATION                        773.3   204.4R  799.8  89 20.600KV               MW     MVAR    2                 2
			TO     11     BUS11   AR2 230.00  1    773.3   204.4   799.8     1.0000LK              0.00  100.50    2                 2

			BUS      4     BUS4   AR2  20.000 CKT     MW     MVAR     MVA   % 1.0100PU  -17.72  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X      4
			FROM GENERATION                        766.0   245.2R  804.3  89 20.200KV               MW     MVAR    2                 2
			TO     10     BUS10   AR2 230.00  1    766.0   245.2   804.3     1.0000LK              0.00  105.69    2                 2

			BUS      5     BUS5   AR1  230.00 CKT     MW     MVAR     MVA   % 1.0096PU   64.36  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X      5
																			232.20KV               MW     MVAR    1                 1
			TO      1     BUS1   AR1  20.000  1   -551.3   -99.4   560.2     1.0000UN              0.00   51.31    1                 1
			TO      6     BUS6   AR1  230.00  1    551.3    99.4   560.2                           7.71   77.07    1                 1

			BUS      6     BUS6   AR1  230.00 CKT     MW     MVAR     MVA   % 0.9800PU   56.50  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X      6
																			225.39KV               MW     MVAR    1                 1
			TO      2     BUS2   AR1  20.000  1   -740.9  -130.0   752.3     1.0000UN              0.00   98.22    1                 1
			TO      5     BUS5   AR1  230.00  1   -543.6   -26.6   544.2                           7.71   77.07    1                 1
			TO      7     BUS7   L    230.00  1   1284.4   157.5  1294.0                          17.44  174.39    1                 1
			M I S M A T C H                         0.1    -0.8     0.8

			BUS      7     BUS7   L    230.00 CKT     MW     MVAR     MVA   % 0.9595PU   48.75  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X      7
																			220.68KV               MW     MVAR    1                 1
			TO LOAD-PQ                             767.0   100.0   773.5
			TO SHUNT                                 0.0  -184.1   184.1
			TO SWITCHED SHUNT                        0.0  -230.1   230.1
			TO      9     BUS9   L    230.00 2DR   500.0   298.8   582.5     0.9375LK   25.71RG    4.90  583.77    2                 2              "1"
			TO      6     BUS6   AR1  230.00  1  -1266.9    15.3  1267.0                          17.44  174.39    1                 1

			BUS      9     BUS9   L    230.00 CKT     MW     MVAR     MVA   % 0.9647PU  -34.39  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X      9
																			221.88KV               MW     MVAR    2                 2
			TO LOAD-PQ                            1967.0   100.0  1969.5
			TO SHUNT                                27.9  -297.8   299.1
			TO SWITCHED SHUNT                        0.0  -232.7   232.7
			TO      7     BUS7   L    230.00 2DI  -495.1   285.0   571.3     0.9438LK   24.61RG    4.90  583.77    1                 1              "1"
			TO     10     BUS10   AR2 230.00  1  -1499.8   145.7  1506.8                          24.40  244.02    2                 2

			BUS     10     BUS10   AR2 230.00 CKT     MW     MVAR     MVA   % 0.9777PU  -25.15  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X     10
																			224.88KV               MW     MVAR    2                 2
			TO      4     BUS4   AR2  20.000  1   -766.0  -139.6   778.6     1.0000UN              0.00  105.69    2                 2
			TO      9     BUS9   L    230.00  1   1524.2    96.7  1527.3                          24.40  244.02    2                 2
			TO     11     BUS11   AR2 230.00  1   -758.2    42.8   759.4                          15.09  150.86    2                 2

			BUS     11     BUS11   AR2 230.00 CKT     MW     MVAR     MVA   % 1.0048PU  -13.95  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X     11
																			231.09KV               MW     MVAR    2                 2
			TO      3     BUS3   AR2  20.000  1   -773.3  -103.9   780.2     1.0000UN              0.00  100.50    2                 2
			TO     10     BUS10   AR2 230.00  1    773.3   103.8   780.2                          15.09  150.86    2                 2

  		 */
  		assertTrue(NumericUtil.equals(net.getBus("Bus7").getVoltageMag(),0.9595,0.0001));
  		assertTrue(NumericUtil.equals(net.getBus("Bus7").getVoltageAng(),48.75/(180/Math.PI),0.01));
  		assertTrue(NumericUtil.equals(net.getBus("Bus9").getVoltageMag(),0.9647,0.0001));


  		//System.out.println("Rec Power: " + ComplexFunc.toStr(lccHVDC.getRectifier().powerIntoConverter()));
  		//System.out.println("Inv Power: " + ComplexFunc.toStr(lccHVDC.getInverter().powerIntoConverter()));

  		//Rec Power: 5.0000 + j2.9871
  		//Inv Power: -4.95098 + j2.84946
  		assertTrue("", NumericUtil.equals(lccHVDC.getRectifier().powerIntoConverter(), new Complex(5.0000, 2.9871), 0.0001));
  		assertTrue("", NumericUtil.equals(lccHVDC.getInverter().powerIntoConverter(), new Complex(-4.95098, 2.84946), 0.0001));

		
		// firing angle
		//System.out.println("rec firing angle:" + lccHVDC.getRectifier().getFiringAng());
		//System.out.println("inv firing angle:" + lccHVDC.getInverter().getFiringAng());
		//rec firing angle:25.69999086949784
        //inv firing angle:24.60609281132968
		assertEquals(lccHVDC.getRectifier().getFiringAng(), 25.69, 0.01);
		assertEquals(lccHVDC.getInverter().getFiringAng(), 24.60, 0.01);

		//Tap ratio 
		//System.out.println("Tap ratio:" + lccHVDC.getRectifier().getXformerTapSetting());
		assertEquals(lccHVDC.getRectifier().getXformerTapSetting(), 0.93750, 0.0001);
		assertEquals(lccHVDC.getInverter().getXformerTapSetting(), 0.94375, 0.0001);
	

  

	}

	@Test
	public void test_LCCHVDC_Loadflow_FireAngleLimit() throws Exception {
		AclfNetwork net = createTestCase();
		//System.out.println(net.net2String());
		
		String branchId = "Bus1->Bus5(1)";
		AclfBranch branch = net.getBranch(branchId);
		assertTrue(branch.isTapControl());
		assertTrue(branch.getTapControl().getTapControlType() == BusBranchControlType.BUS_VOLTAGE);
		// TODO: this is not set in the odm parser, need to check why
		assertTrue(branch.getTapControl().getVcBus() != null);

		HvdcLine2TLCC<AclfBus> lccHVDC = (HvdcLine2TLCC<AclfBus>) net.getSpecialBranchList().get(0);
		// change limits
		lccHVDC.getRectifier().setFiringAngLimit(new LimitType(22,15));
		lccHVDC.getInverter().setFiringAngLimit(new LimitType(22,15));

		lccHVDC.setPuBasedPowerFlowAlgo(false);
	
		 
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		algo.setMaxIterations(30);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());
  		
  		System.out.println(AclfOutFunc.loadFlowSummary(net));
  		AclfBus bus1 = net.getBus("Bus1");
  		assertEquals(bus1.getVoltageAng(UnitType.Deg),69.43,0.01);

  		
  		/*
  		 * The results below is similar to Powerworld results, but the taps position are different PSS/E
								Max Power Mismatches
					Bus              dPmax       Bus              dQmax
					-------------------------------------------------------
					Bus9             0.000000  Bus9             0.000093 (pu)
									0.0001450                   9.315502 (kva)

			BusID          Code           Volt(pu)   Angle(deg)      Pg(pu)    Qg(pu)    Pl(pu)    Ql(pu)    Bus Name   
		----------------------------------------------------------------------------------------------------------------
		Bus1         Swing                1.03000       69.43       5.5030    1.2134    0.0000    0.0000   BUS1   AR1 
		Bus2         PV                   1.01000       63.72       7.4100    1.5722    0.0000    0.0000   BUS2   AR1 
		Bus3         Swing                1.03000       -6.80       7.7366    1.7238    0.0000    0.0000   BUS3   AR2 
		Bus4         PV                   1.01000      -17.63       7.6600    1.6860    0.0000    0.0000   BUS4   AR2 
		Bus5                              1.01428       64.39       0.0000    0.0000    0.0000    0.0000   BUS5   AR1 
		Bus6                              0.99162       56.64       0.0000    0.0000    0.0000    0.0000   BUS6   AR1 
		Bus7                ConstP        0.98059       49.09       0.0000    2.4039    7.6700    1.0000   BUS7   L   
		Bus9                ConstP        0.98708      -33.94       0.0000    2.4358   19.6700    1.0000   BUS9   L   
		Bus10                             0.99028      -24.96       0.0000    0.0000    0.0000    0.0000   BUS10   AR2 
		Bus11                             1.00990      -13.92       0.0000    0.0000    0.0000    0.0000   BUS11   AR2 
  		 */
  		assertTrue(NumericUtil.equals(net.getBus("Bus7").getVoltageMag(),0.98059,0.0001));
  		assertTrue(NumericUtil.equals(net.getBus("Bus7").getVoltageAng(),48.95/(180/Math.PI),0.01));
  		assertTrue(NumericUtil.equals(net.getBus("Bus9").getVoltageMag(),0.98708,0.0001));


  		System.out.println("Rec Power: " + ComplexFunc.toStr(lccHVDC.getRectifier().powerIntoConverter()));
  		System.out.println("Inv Power: " + ComplexFunc.toStr(lccHVDC.getInverter().powerIntoConverter()));

  		//Rec Power: 5.0000 + j2.3077
  		//Inv Power: -4.95098 + j2.1799
  		assertTrue("", NumericUtil.equals(lccHVDC.getRectifier().powerIntoConverter(), new Complex(5.0000, 2.3077), 0.0001));
  		assertTrue("", NumericUtil.equals(lccHVDC.getInverter().powerIntoConverter(), new Complex(-4.95098, 2.1799), 0.0001));

		
		// firing angle
		System.out.println("rec firing angle:" + lccHVDC.getRectifier().getFiringAng());
		System.out.println("inv firing angle:" + lccHVDC.getInverter().getFiringAng());
		// rec firing angle:17.784206407367336
        // inv firing angle:16.429126699468245
		assertEquals(lccHVDC.getRectifier().getFiringAng(), 17.784206407367336, 0.001);
		assertEquals(lccHVDC.getInverter().getFiringAng(), 16.429126699468245, 0.001);

		//Tap ratio 
		System.out.println("Tap ratio:" + lccHVDC.getRectifier().getXformerTapSetting());
		System.out.println("Tap ratio:" + lccHVDC.getInverter().getXformerTapSetting());
		assertEquals(lccHVDC.getRectifier().getXformerTapSetting(), 1.0125, 0.0001);
		assertEquals(lccHVDC.getInverter().getXformerTapSetting(), 1.01875, 0.0001);
	

  

	}
	
	@Test
	public void test_LCCHVDC_DataInput() throws Exception {
		AclfNetwork net = createTestCase();
		test_LCCHVDC_Data(net);
	}
	
	private void test_LCCHVDC_Data(AclfNetwork net) {
		assertTrue(net.getSpecialBranchList().size()==1);
		
		assertTrue(!net.getBus("Bus7").isGen());
		assertTrue(!net.getBus("Bus9").isGen());
		
		HvdcLine2TLCC<AclfBus> lccHVDC = (HvdcLine2TLCC<AclfBus>) net.getSpecialBranchList().get(0);
		//System.out.println(vscHVDC.getId());
		//System.out.println(vscHVDC.getName());
		assertTrue(lccHVDC.getRdc(UnitType.Ohm)==5.0);

		
	    assertTrue(lccHVDC.getRectifier() != null);
	    assertTrue(lccHVDC.getInverter() != null);

		assertEquals(lccHVDC.getRectifier().getCommutingZ().getReal(), 0.0, 0.0001);
		assertEquals(lccHVDC.getRectifier().getCommutingZ().getImaginary(),  13.594, 0.001);

		assertEquals(lccHVDC.getInverter().getCommutingZ().getReal(), 0.0, 0.0001);
		assertEquals(lccHVDC.getInverter().getCommutingZ().getImaginary(),   13.317, 0.001);

		//check fire angle limits on both rectifier and inverter
		assertEquals(lccHVDC.getRectifier().getFiringAngLimit().getMin(), 15.0, 0.0001);
		assertEquals(lccHVDC.getRectifier().getFiringAngLimit().getMax(), 90.0, 0.0001);

		assertEquals(lccHVDC.getInverter().getFiringAngLimit().getMin(), 15.0, 0.0001);
		assertEquals(lccHVDC.getInverter().getFiringAngLimit().getMax(), 90.0, 0.0001);

		
		assertEquals(lccHVDC.getRectifier().getXformerTapSetting(), 0.93750, 0.0001);
		assertEquals(lccHVDC.getInverter().getXformerTapSetting(), 0.94375, 0.0001);

	    //assertTrue(lccHVDC.getRectifier().getParentHvdc() != null);
	    //assertTrue(lccHVDC.getInverter().getParentHvdc() != null);

		
	    // System.out.println("Rectifier Control Mode: " + lccHVDC.getRectifierControlMode());
		// System.out.println("Inverter Control Mode: " + lccHVDC.getInverterControlMode());
		assertTrue(lccHVDC.getRectifierControlMode() == HvdcControlMode.DC_CURRENT);
		assertTrue(lccHVDC.getInverterControlMode() == HvdcControlMode.DC_VOLTAGE);
		 
		//System.out.println(net.net2String());
	}

	@Test
	public void test_LCCHVDC_PsetZero_Loadflow() throws Exception {

		//IpssLogger.getLogger().setLevel(Level.INFO);
		IODMAdapter adapter = new PSSERawAdapter(PSSEAdapter.PsseVersion.PSSE_33);
		assertTrue(adapter.parseInputFile("testData/psse/v33/Kundur_2area_LCC_HVDC_PsetZero.raw"));
		
		AclfModelParser parser = (AclfModelParser)adapter.getModel();
		//parser.stdout();
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
  	  		System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
		}		
		
		AclfNetwork net = simuCtx.getAclfNet();

		HvdcLine2TLCC<AclfBus> lccHVDC = (HvdcLine2TLCC<AclfBus>) net.getSpecialBranchList().get(0);
		assertTrue(!lccHVDC.isActive());

		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		algo.setInitBusVoltage(true);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());
  		
  		System.out.println(AclfOutFunc.loadFlowSummary(net));
  		
		/*
					*     BusID          Code           Volt(pu)   Angle(deg)      Pg(pu)    Qg(pu)    Pl(pu)    Ql(pu)    Bus Name   
			----------------------------------------------------------------------------------------------------------------
			Bus1         Swing                1.03000        0.00       2.6225   -0.2418    0.0000    0.0000   BUS1   AR1 
			Bus2         PV                   1.01000        0.87       7.4100   -1.2796    0.0000    0.0000   BUS2   AR1 
			Bus3         PV                   1.03000       -1.51      10.8000    2.8911    0.0000    0.0000   BUS3   AR2 
			Bus4         PV                   1.01000      -20.01       7.6600    2.0093    0.0000    0.0000   BUS4   AR2 
			Bus5                              1.03478       -2.35       0.0000    0.0000    0.0000    0.0000   BUS5   AR1 
			Bus6                              1.03834       -5.89       0.0000    0.0000    0.0000    0.0000   BUS6   AR1 
			Bus7                ConstP        1.05896      -11.26       0.0000    0.0000    7.6700    1.0000   BUS7   L   
			Bus8                              1.00908      -24.32       0.0000    0.0000    0.0000    0.0000   BUS8       
			Bus9                ConstP        0.99397      -38.13       0.0000    0.0000   19.6700    1.0000   BUS9   L   
			Bus10                             0.98499      -27.38       0.0000    0.0000    0.0000    0.0000   BUS10   AR2 
			Bus11                             0.99863      -11.59       0.0000    0.0000    0.0000    0.0000   BUS11   AR2 
		 */
		AclfBus bus1 = net.getBus("Bus1");
  		assertEquals(bus1.getVoltageAng(UnitType.Deg),0,0.01);
  		assertEquals(bus1.getVoltageMag(UnitType.PU), 1.03, 0.0001);
  		assertEquals(net.getBus("Bus2").getVoltageMag(UnitType.PU) , 1.0100, 0.0001);
  		assertEquals(net.getBus("Bus3").getVoltageMag(UnitType.PU) , 1.0300, 0.0001);
		assertEquals(net.getBus("Bus4").getVoltageMag(UnitType.PU) , 1.0100, 0.0001);
		assertEquals(net.getBus("Bus5").getVoltageMag(UnitType.PU) , 1.03478, 0.0001);
		assertEquals(net.getBus("Bus6").getVoltageMag(UnitType.PU) , 1.03834, 0.0001);
		assertEquals(net.getBus("Bus7").getVoltageMag(UnitType.PU) , 1.05896, 0.0001);
		assertEquals(net.getBus("Bus9").getVoltageMag(UnitType.PU) , 0.99397, 0.0001);
		assertEquals(net.getBus("Bus10").getVoltageMag(UnitType.PU) , 0.98499, 0.0001);
		assertEquals(net.getBus("Bus11").getVoltageMag(UnitType.PU) , 0.99863, 0.0001);




	}

	private AclfNetwork createTestCase() {
		System.out.println("Kundur 2-area LCC HVDC test case creation ...");
		
		IODMAdapter adapter = new PSSERawAdapter(PSSEAdapter.PsseVersion.PSSE_33);
		assertTrue(adapter.parseInputFile("testData/adpter/psse/v33/Kundur_2area_LCC_HVDC.raw"));
		
		AclfModelParser parser = (AclfModelParser)adapter.getModel();
		//parser.stdout();
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
  	  		System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
  	  		return null;
		}		
		
		AclfNetwork net = simuCtx.getAclfNet();
		//System.out.println(net.net2String());		

		HvdcLine2TLCC<AclfBus> lccHVDC = (HvdcLine2TLCC<AclfBus>) net.getSpecialBranchList().get(0);
		assertTrue(lccHVDC.getRectifier().getParentHvdc() != null);
		assertTrue(lccHVDC.getInverter().getParentHvdc() != null);
		
		System.out.println("Kundur 2-area LCC HVDC test case created");
		
		return net;
	}
}
