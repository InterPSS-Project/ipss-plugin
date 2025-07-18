package org.interpss.core.adapter.psse.raw.aclf;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.odm.mapper.ODMAclfParserMapper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2TVSC;
import com.interpss.core.aclf.hvdc.VSCConverter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class Kundur_2Area_VSCHVDC2T_Test extends CorePluginTestSetup {
	@Test
	public void test_VSCHVDC_DeepCopy() throws Exception {
		AclfNetwork net = createTestCaseV30();
		
		AclfNetwork netCopy = net.hzCopy();
		
		test_VSCHVDC_Data(netCopy);
	}
	
	@Test
	public void test_VSCHVDC_JSonCopy() throws Exception {
		AclfNetwork net = createTestCaseV30();
		
		AclfNetwork netCopy = net.jsonCopy();
		
		test_VSCHVDC_Data(netCopy);
	}
	
	@Test
	public void test_VSCHVDC_Loadflow() throws Exception {
		AclfNetwork net = createBasicTestCaseV35(); // Updated to use createBasicTestCaseV30()
		//System.out.println(net.net2String());

		for(AclfBus bus : net.getBusList()) {
			System.out.println(bus.mismatch(AclfMethodType.NR));
		}
		 
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	algo.loadflow();
  		//System.out.println(net.net2String());
	  	
  		assertTrue(net.isLfConverged());
  		
  		System.out.println(AclfOutFunc.loadFlowSummary(net));
  		AclfBus bus1 = net.getBus("Bus1");
  		// TODO test failed after the ThyConverter.converterType change
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
		//TODO: this results is different from the PSS/E results, because the converter loss is not modeled in InterPSS yet
  		//Rec Power: 2.0900   + j 0.68695
  		//Inv Power: -2.086 + j 0.6297
  		assertTrue("", NumericUtil.equals(vscHVDC.getRecConverter().powerIntoConverter(), new Complex(2.0900, 0.68695), 0.0001));
  		assertTrue("", NumericUtil.equals(vscHVDC.getInvConverter().powerIntoConverter(), new Complex(-2.086, 0.6297), 0.001));
	}
	
	@Test
	public void test_VSCHVDC_DataInput() throws Exception {
		AclfNetwork net = createTestCaseV30();
		test_VSCHVDC_Data(net);
	}

	@Test
	public void test_VSCHVDC_DataInput_RemoteBus() throws Exception {
		AclfNetwork net = createTestCaseV35();
		assertTrue(net.getSpecialBranchList().size()==1);
		
		assertTrue(!net.getBus("Bus7").isGen());
		assertTrue(!net.getBus("Bus9").isGen());
		
		HvdcLine2TVSC<AclfBus> vscHVDC = (HvdcLine2TVSC<AclfBus>) net.getSpecialBranchList().get(0);
		//System.out.println(vscHVDC.getId());
		//System.out.println(vscHVDC.getName());
		
	    assertTrue(vscHVDC.getRecConverter() != null);
	    assertTrue(vscHVDC.getInvConverter() != null);

		VSCConverter<AclfBus> vscInv = vscHVDC.getInvConverter();

		// check the remote bus id
		assertTrue(vscInv.getRemoteControlBusId().equals("Bus10"));
		assertTrue(vscInv.getRemoteControlPercent() == 100.0);
		assertTrue(NumericUtil.equals(vscInv.getAcSetPoint(), 0.99, 0.0001));

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
	
	private AclfNetwork createTestCaseV30() {
		IODMAdapter adapter = new PSSERawAdapter(PSSEAdapter.PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile("testData/adpter/psse/v30/Kundur_2area/Kundur_2area_vschvdc_v30.raw"));
		
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
		
		System.out.println("VSC HVDC: " + vscHVDC.toString(net.getBaseKva()));
/*
VSC HVDC: com.interpss.core.aclf.hvdc.impl.HvdcLine2TVSCImpl@6b3e12b5 (
	id: Bus7->Bus9(cirId), name: VDCLINE1, desc: , number: 0, status: true) 
	(booleanFlag: false, intFlag: 0, weight: (0.0, 0.0), sortNumber: 0, areaId: 1, zoneId: 1, ownerId: , 
	statusChangeInfo: NoChange) (extensionObject: null) (circuitNumber: VDCLINE1, fromSideMetered: true) 
	(rdc: 0.71, mvaRating: 0.0, imax: 0.0, dcLineNumber: 0) (iDcKa: 0.0)
	
	com.interpss.core.aclf.hvdc.impl.VSCConverterImpl@5aac4250 (id: Rec, name: , desc: , number: 0, status: true)
	 (converterType: rectifier, refBusId: Bus7) (vdc: 0.0, pdc: 0.0, dcSetPoint: -209.0, 
	 acSetPoint: 0.95, mvaRating: 400.0, acCurrentRating: 0.0, qMvarLimit: ( 100.0, -110.0 ), 
	 acControlMode: acPowerFactor, dcControlMode: dcPower, remoteControlBusId: null, 
	 remoteControlPercent: 100.0)
	 
	com.interpss.core.aclf.hvdc.impl.VSCConverterImpl@1338fb5 (id: Inv, name: , desc: , number: 0, status: true)
	 (converterType: inverter, refBusId: Bus9) (vdc: 0.0, pdc: 0.0, dcSetPoint: 300.0, 
	 acSetPoint: 0.98, mvaRating: 350.0, acCurrentRating: 0.0, qMvarLimit: ( 150.0, -140.0 ), 
	 acControlMode: acVoltage, dcControlMode: dcVoltage, remoteControlBusId: null, 
	 remoteControlPercent: 100.0)	
 */
		
		return net;
	}

	private AclfNetwork createBasicTestCaseV35() {
		IODMAdapter adapter = new PSSERawAdapter(PSSEAdapter.PsseVersion.PSSE_35);
		assertTrue(adapter.parseInputFile("testData/adpter/psse/v35/Kundur_2area_vschvdc_v35.raw"));
		
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
		
		System.out.println("VSC HVDC: " + vscHVDC.toString(net.getBaseKva()));
/*
VSC HVDC: com.interpss.core.aclf.hvdc.impl.HvdcLine2TVSCImpl@6b3e12b5 (
	id: Bus7->Bus9(cirId), name: VDCLINE1, desc: , number: 0, status: true) 
	(booleanFlag: false, intFlag: 0, weight: (0.0, 0.0), sortNumber: 0, areaId: 1, zoneId: 1, ownerId: , 
	statusChangeInfo: NoChange) (extensionObject: null) (circuitNumber: VDCLINE1, fromSideMetered: true) 
	(rdc: 0.71, mvaRating: 0.0, imax: 0.0, dcLineNumber: 0) (iDcKa: 0.0)
	
	com.interpss.core.aclf.hvdc.impl.VSCConverterImpl@5aac4250 (id: Rec, name: , desc: , number: 0, status: true)
	 (converterType: rectifier, refBusId: Bus7) (vdc: 0.0, pdc: 0.0, dcSetPoint: -209.0, 
	 acSetPoint: 0.95, mvaRating: 400.0, acCurrentRating: 0.0, qMvarLimit: ( 100.0, -110.0 ), 
	 acControlMode: acPowerFactor, dcControlMode: dcPower, remoteControlBusId: null, 
	 remoteControlPercent: 100.0)
	 
	com.interpss.core.aclf.hvdc.impl.VSCConverterImpl@1338fb5 (id: Inv, name: , desc: , number: 0, status: true)
	 (converterType: inverter, refBusId: Bus9) (vdc: 0.0, pdc: 0.0, dcSetPoint: 300.0, 
	 acSetPoint: 0.98, mvaRating: 350.0, acCurrentRating: 0.0, qMvarLimit: ( 150.0, -140.0 ), 
	 acControlMode: acVoltage, dcControlMode: dcVoltage, remoteControlBusId: null, 
	 remoteControlPercent: 100.0)	
 */
		
		return net;
	}

	private AclfNetwork createTestCaseV35() {
		IODMAdapter adapter = new PSSERawAdapter(PSSEAdapter.PsseVersion.PSSE_35);
		assertTrue(adapter.parseInputFile("testData/psse/v35/Kundur_2area_vschvdc_remotebus_v35.raw"));
		
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
