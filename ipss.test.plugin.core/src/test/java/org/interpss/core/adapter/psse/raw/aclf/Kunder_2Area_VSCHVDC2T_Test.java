package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.mapper.odm.ODMAclfParserMapper;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2TVSC;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class Kunder_2Area_VSCHVDC2T_Test extends CorePluginTestSetup {
	@Test
	public void test_VSCHVDC_DeepCopy() throws Exception {
		AclfNetwork net = createTestCase();
		
		AclfNetwork netCopy = net.hzCopy();
		
		assertTrue("", net.diffState(netCopy));
		
		test_VSCHVDC_Data(netCopy);
	}
	
	@Test
	public void test_VSCHVDC_JSonCopy() throws Exception {
		AclfNetwork net = createTestCase();
		
		AclfNetwork netCopy = net.jsonCopy();
		
		assertTrue("", net.diffState(netCopy));
		
		test_VSCHVDC_Data(netCopy);
	}
	
	@Test
	public void test_VSCHVDC_Loadflow() throws Exception {
		AclfNetwork net = createTestCase();
		//System.out.println(net.net2String());
		 
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());
  		
  		//System.out.println(AclfOutFunc.loadFlowSummary(net));
  		AclfBus bus1 = net.getBus("Bus1");
  		assertEquals(bus1.getVoltageAng(UnitType.Deg),13.15,0.01);
  		
  		/*
  		 * PSS/E power flow results
  		 * 
  		 * Bus  Number	Bus  Name	Base kV	 Area Num	 Area Name	Zone Num	Zone Name	Owner Num	Owner Name	Code	Voltage (pu)	Angle (deg)	Normal Vmax (pu)	Normal Vmin (pu)	Emergency Vmax (pu)	Emergency Vmin (pu)
			1	BUS1   AR1	20.0	1	AREA1	1		1		2	1.0300	13.15	1.1000	0.9000	1.1000	0.9000
			2	BUS2   AR1	20.0	1	AREA1	1		1		2	1.0100	3.38	1.1000	0.9000	1.1000	0.9000
			3	BUS3   AR2	20.0	2	AREA2	2		1		3	1.0300	0.00	1.1000	0.9000	1.1000	0.9000
			4	BUS4   AR2	20.0	2	AREA2	2		1		2	1.0100	-9.79	1.1000	0.9000	1.1000	0.9000
			5	BUS5   AR1	230.0	1	AREA1	1		1		1	1.0061	6.68	1.1000	0.9000	1.1000	0.9000
			6	BUS6   AR1	230.0	1	AREA1	1		1		1	0.9773	-3.41	1.1000	0.9000	1.1000	0.9000
			7	BUS7   L	230.0	1	AREA1	1		1		1	0.9595	-11.84	1.1000	0.9000	1.1000	0.9000
			8	BUS8	    230.0	2	AREA2	2		1		1	0.9739	-18.41	1.1000	0.9000	1.1000	0.9000
			9	BUS9   L	230.0	2	AREA2	2		1		1	0.9800	-24.73	1.1000	0.9000	1.1000	0.9000
			10	BUS10   AR2	230.0	2	AREA2	2		1		1	0.9886	-16.50	1.1000	0.9000	1.1000	0.9000
			11	BUS11   AR2	230.0	2	AREA2	2		1		1	1.0107	-6.47	1.1000	0.9000	1.1000	0.9000
															

  		 */
  		
  		/*
  		 * BUS      7 BUS7   L    230.00 CKT     MW     MVAR     MVA   % 0.9595PU  -11.84  X--- LOSSES ---X X---- AREA -----X X---- ZONE -----X      7
                                                               220.68KV               MW     MVAR    1 AREA1           1
		  TO LOAD-PQ                         967.0   100.0   972.2
		  TO SHUNT                             0.0  -184.1   184.1
		  TO      9 BUS9   L    230.00 VSC   209.0    68.7   220.0  55                       0.71  131.49    2 AREA2           2              "VDCLINE1"
		  TO      6 BUS6   AR1  230.00  1  -1367.3    66.3  1368.9                          20.36  203.56    1 AREA1           1
		  TO      8 BUS8        230.00  1     95.6   -25.4    99.0                           1.13   11.26    2 AREA2           2
		  TO      8 BUS8        230.00  2     95.6   -25.4    99.0                           1.13   11.26    2 AREA2           2

  		 */
  		assertTrue(NumericUtil.equals(net.getBus("Bus7").getGenP(), -2.09, 0.001));
  		assertTrue(NumericUtil.equals(net.getBus("Bus7").getGenQ(), -0.687,0.001));
  		assertTrue(NumericUtil.equals(net.getBus("Bus7").getVoltageMag(),0.95946,0.00001));
  		assertTrue(NumericUtil.equals(net.getBus("Bus7").getVoltageAng(),-11.84/(180/Math.PI),0.01));
  		assertTrue(NumericUtil.equals(net.getBus("Bus9").getVoltageMag(),0.98,0.00001));
  		
		HvdcLine2TVSC<AclfBus> vscHVDC = (HvdcLine2TVSC<AclfBus>) net.getSpecialBranchList().get(0);
  		//System.out.println("Rec Power: " + ComplexFunc.toStr(vscHVDC.getRecConverter().powerIntoConverter()));
  		//System.out.println("Inv Power: " + ComplexFunc.toStr(vscHVDC.getInvConverter().powerIntoConverter()));
  		//Rec Power: 2.0900   + j 0.68695
  		//Inv Power: -2.08315 + j 0.62802
  		assertTrue("", NumericUtil.equals(vscHVDC.getRecConverter().powerIntoConverter(), new Complex(2.0900, 0.68695), 0.00001));
  		assertTrue("", NumericUtil.equals(vscHVDC.getInvConverter().powerIntoConverter(), new Complex(-2.08315, 0.62802), 0.00001));
	}
	
	@Test
	public void test_VSCHVDC_DataInput() throws Exception {
		AclfNetwork net = createTestCase();
		test_VSCHVDC_Data(net);
	}
	
	private void test_VSCHVDC_Data(AclfNetwork net) {
		assertTrue(net.getSpecialBranchList().size()==1);
		
		assertTrue(!net.getBus("Bus7").isGen());
		assertTrue(!net.getBus("Bus9").isGen());
		
		HvdcLine2TVSC<AclfBus> vscHVDC = (HvdcLine2TVSC<AclfBus>) net.getSpecialBranchList().get(0);
		//System.out.println(vscHVDC.getId());
		//System.out.println(vscHVDC.getName());
		
	    assertTrue(vscHVDC.getRecConverter() != null);
	    assertTrue(vscHVDC.getInvConverter() != null);

	    assertTrue(vscHVDC.getRecConverter().getParentHvdc() != null);
	    assertTrue(vscHVDC.getInvConverter().getParentHvdc() != null);
		 
		//System.out.println(net.net2String());
	}
	
	private AclfNetwork createTestCase() {
		IODMAdapter adapter = new PSSERawAdapter(PSSEAdapter.PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile("testdata/adpter/psse/v30/Kunder_2area/Kunder_2area_vschvdc_v30.raw"));
		
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

		HvdcLine2TVSC<AclfBus> vscHVDC = (HvdcLine2TVSC<AclfBus>) net.getSpecialBranchList().get(0);
		vscHVDC.setId("Bus7->Bus9(cirId)");
		
		vscHVDC.getInvConverter().setId("Inv");
		
		vscHVDC.getRecConverter().setId("Rec");
		
		return net;
	}
}
