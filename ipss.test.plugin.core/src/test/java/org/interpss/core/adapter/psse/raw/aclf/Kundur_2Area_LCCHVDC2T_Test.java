package org.interpss.core.adapter.psse.raw.aclf;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.mapper.odm.ODMAclfParserMapper;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class Kundur_2Area_LCCHVDC2T_Test extends CorePluginTestSetup {
	@Test
	public void test_LCCHVDC_DeepCopy() throws Exception {
		AclfNetwork net = createTestCase();
		
		AclfNetwork netCopy = net.hzCopy();
		
		assertTrue("", net.diffState(netCopy));
		
		test_LCCHVDC_Data(netCopy);
	}
	
	@Test
	public void test_LCCHVDC_JSonCopy() throws Exception {
		AclfNetwork net = createTestCase();
		
		AclfNetwork netCopy = net.jsonCopy();
		
		assertTrue("", net.diffState(netCopy));
		
		test_LCCHVDC_Data(netCopy);
	}
	
	@Test
	public void test_LCCHVDC_Loadflow() throws Exception {
		AclfNetwork net = createTestCase();
		//System.out.println(net.net2String());
		 
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());
  		
  		//System.out.println(AclfOutFunc.loadFlowSummary(net));
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
  		assertTrue(NumericUtil.equals(net.getBus("Bus7").getVoltageMag(),0.9595,0.00001));
  		assertTrue(NumericUtil.equals(net.getBus("Bus7").getVoltageAng(),48.75/(180/Math.PI),0.01));
  		assertTrue(NumericUtil.equals(net.getBus("Bus9").getVoltageMag(),0.9647,0.00001));

		HvdcLine2TLCC<AclfBus> lccHVDC = (HvdcLine2TLCC<AclfBus>) net.getSpecialBranchList().get(0);
  		System.out.println("Rec Power: " + ComplexFunc.toStr(lccHVDC.getRectifier().powerIntoConverter()));
  		System.out.println("Inv Power: " + ComplexFunc.toStr(lccHVDC.getInverter().powerIntoConverter()));
  		//Rec Power: 2.0900   + j 0.68695
  		//Inv Power: -2.08315 + j 0.62802
  		assertTrue("", NumericUtil.equals(lccHVDC.getRectifier().powerIntoConverter(), new Complex(2.0900, 0.68695), 0.00001));
  		assertTrue("", NumericUtil.equals(lccHVDC.getRectifier().powerIntoConverter(), new Complex(-2.08315, 0.62802), 0.00001));
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
		assertTrue(lccHVDC.getRdc()==5.0);
		
	    assertTrue(lccHVDC.getRectifier() != null);
	    assertTrue(lccHVDC.getInverter() != null);

	    //assertTrue(lccHVDC.getRectifier().getParentHvdc() != null);
	    //assertTrue(lccHVDC.getInverter().getParentHvdc() != null);
		 
		//System.out.println(net.net2String());
	}
	
	private AclfNetwork createTestCase() {
		IODMAdapter adapter = new PSSERawAdapter(PSSEAdapter.PsseVersion.PSSE_33);
		assertTrue(adapter.parseInputFile("testdata/adpter/psse/v33/Kundur_2area_LCC_HVDC.raw"));
		
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

		//HvdcLine2TVSC<AclfBus> vscHVDC = (HvdcLine2TVSC<AclfBus>) net.getSpecialBranchList().get(0);
		
		
		return net;
	}
}
