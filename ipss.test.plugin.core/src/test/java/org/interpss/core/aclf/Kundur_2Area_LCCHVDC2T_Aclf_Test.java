package org.interpss.core.aclf;

import java.util.logging.Level;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.datatype.ComplexFunc;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.odm.mapper.ODMAclfParserMapper;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class Kundur_2Area_LCCHVDC2T_Aclf_Test extends CorePluginTestSetup {
	
	
	@Test
	public void test_LCCHVDC_Loadflow_PsetOnInv() throws Exception {
		IpssLogger.getLogger().setLevel(Level.INFO);
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
  		
  		//System.out.println(AclfOutFunc.loadFlowSummary(net));
  		AclfBus bus1 = net.getBus("Bus1");
  		assertEquals(bus1.getVoltageAng(UnitType.Deg),69.43,0.01);

  		/**
		 *      BusID          Code           Volt(pu)   Angle(deg)      Pg(pu)    Qg(pu)    Pl(pu)    Ql(pu)    Bus Name   
				----------------------------------------------------------------------------------------------------------------
				Bus1         Swing                1.03000       69.43       5.5648    1.5351    0.0000    0.0000   BUS1   AR1 
				Bus2         PV                   1.01000       63.54       7.4100    2.3322    0.0000    0.0000   BUS2   AR1 
				Bus3         Swing                1.03000       -6.80       7.6803    2.0267    0.0000    0.0000   BUS3   AR2 
				Bus4         PV                   1.01000      -17.59       7.6600    2.4465    0.0000    0.0000   BUS4   AR2 
				Bus5                              1.00919       64.31       0.0000    0.0000    0.0000    0.0000   BUS5   AR1 
				Bus6                              0.97918       56.37       0.0000    0.0000    0.0000    0.0000   BUS6   AR1 
				Bus7                ConstP        0.95830       48.57       0.0000    0.0000    7.6700    1.0000   BUS7   L   
				Bus9                ConstP        0.96461      -34.22       0.0000    0.0000   19.6700    1.0000   BUS9   L   
				Bus10                             0.97783      -25.01       0.0000    0.0000    0.0000    0.0000   BUS10   AR2 
				Bus11                             1.00492      -13.90       0.0000    0.0000    0.0000    0.0000   BUS11   AR2 
		 */
  		
  		assertTrue(NumericUtil.equals(net.getBus("Bus7").getVoltageMag(),0.95830,0.0001));
  		assertTrue(NumericUtil.equals(net.getBus("Bus7").getVoltageAng(),48.75/(180/Math.PI),0.01));
  		assertTrue(NumericUtil.equals(net.getBus("Bus9").getVoltageMag(),0.96461,0.0001));


  		System.out.println("Rec Power: " + ComplexFunc.toStr(lccHVDC.getRectifier().powerIntoConverter()));
  		System.out.println("Inv Power: " + ComplexFunc.toStr(lccHVDC.getInverter().powerIntoConverter()));

  		/*
		 *  Rec Power: 5.0500 + j3.00162
			Inv Power: -5.0000 + j2.87694
		 */
  		assertTrue("", NumericUtil.equals(lccHVDC.getRectifier().powerIntoConverter(), new Complex(5.0500, 3.00162), 0.0001));
  		assertTrue("", NumericUtil.equals(lccHVDC.getInverter().powerIntoConverter(), new Complex(-5.0000, 2.87694), 0.0001));

		
		// firing angle
		//System.out.println("rec firing angle:" + lccHVDC.getRectifier().getFiringAng());
		//System.out.println("inv firing angle:" + lccHVDC.getInverter().getFiringAng());
		//rec firing angle:25.69999086949784
        //inv firing angle:24.60609281132968
		//assertEquals(lccHVDC.getRectifier().getFiringAng(), 25.69, 0.01);
		//assertEquals(lccHVDC.getInverter().getFiringAng(), 24.60, 0.01);

		//Tap ratio
		/*
			Rec Tap ratio:0.9375
			Inv Tap ratio:0.94375
		 */ 
		//System.out.println("Rec Tap ratio:" + lccHVDC.getRectifier().getXformerTapSetting());
		//System.out.println("Inv Tap ratio:" + lccHVDC.getInverter().getXformerTapSetting());
		assertEquals(lccHVDC.getRectifier().getXformerTapSetting(), 0.93750, 0.0001);
		assertEquals(lccHVDC.getInverter().getXformerTapSetting(), 0.94375, 0.0001);
	

  

	}

	//@Test
	public void test_LCCHVDC_Equiv() throws Exception {
		AclfNetwork net = createTestCase();
		//System.out.println(net.net2String());

		// HvdcLine2TLCC<AclfBus> lccHVDC = (HvdcLine2TLCC<AclfBus>) net.getSpecialBranchList().get(0);
		// // change limits
		// lccHVDC.getRectifier().setFiringAngLimit(new LimitType(22,15));
		// lccHVDC.getInverter().setFiringAngLimit(new LimitType(22,15));

		// lccHVDC.setPuBasedPowerFlowAlgo(false);
	
		 
		// LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
		// algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
	  	// algo.loadflow();
  		// //System.out.println(net.net2String());
	  	
  		// assertTrue(net.isLfConverged());
  		
  		// System.out.println(AclfOutFunc.loadFlowSummary(net));
  		// AclfBus bus1 = net.getBus("Bus1");
  		// assertEquals(bus1.getVoltageAng(UnitType.Deg),69.43,0.01);

  		
  		/*
  		 * The similar below is similar to Powerworld results, but the taps position are different PSS/E
								Max Power Mismatches
					Bus              dPmax       Bus              dQmax
					-------------------------------------------------------
					Bus9             0.000000  Bus9             0.000093 (pu)
									0.0001450                   9.315502 (kva)

			BusID          Code           Volt(pu)   Angle(deg)      Pg(pu)    Qg(pu)    Pl(pu)    Ql(pu)    Bus Name   
		----------------------------------------------------------------------------------------------------------------
		Bus1         Swing                1.03000       69.43       5.5018    1.1638    0.0000    0.0000   BUS1   AR1 
		Bus2         PV                   1.01000       63.73       7.4100    1.4516    0.0000    0.0000   BUS2   AR1 
		Bus3         Swing                1.03000       -6.80       7.7361    1.7508    0.0000    0.0000   BUS3   AR2 
		Bus4         PV                   1.01000      -17.63       7.6600    1.7509    0.0000    0.0000   BUS4   AR2 
		Bus5                              1.01508       64.40       0.0000    0.0000    0.0000    0.0000   BUS5   AR1 
		Bus6                              0.99360       56.66       0.0000    0.0000    0.0000    0.0000   BUS6   AR1 
		Bus7                ConstP        0.98416       49.14       0.0000    0.0000    7.6700    1.0000   BUS7   L   
		Bus9                ConstP        0.98518      -33.97       0.0000    0.0000   19.6700    1.0000   BUS9   L   
		Bus10                             0.98922      -24.98       0.0000    0.0000    0.0000    0.0000   BUS10   AR2 
		Bus11                             1.00946      -13.92       0.0000    0.0000    0.0000    0.0000   BUS11   AR2 
  		 */
  		// assertTrue(NumericUtil.equals(net.getBus("Bus7").getVoltageMag(),0.9842,0.0001));
  		// assertTrue(NumericUtil.equals(net.getBus("Bus7").getVoltageAng(),48.95/(180/Math.PI),0.01));
  		// assertTrue(NumericUtil.equals(net.getBus("Bus9").getVoltageMag(),0.98518,0.0001));


	

  

	}
	

	
	private AclfNetwork createTestCase() {
		IODMAdapter adapter = new PSSERawAdapter(PSSEAdapter.PsseVersion.PSSE_33);
		assertTrue(adapter.parseInputFile("testData/adpter/psse/v33/Kundur_2area_LCC_HVDC_PsetOnInv.raw"));
		
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
