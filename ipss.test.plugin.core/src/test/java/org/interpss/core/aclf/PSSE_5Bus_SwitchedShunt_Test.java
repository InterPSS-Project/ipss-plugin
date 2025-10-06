package org.interpss.core.aclf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.numeric.datatype.LimitType;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.odm.mapper.ODMAclfParserMapper;
import org.junit.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.AclfAdjustControlMode;
import com.interpss.core.aclf.adj.AclfAdjustControlType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

/**
 * Test case for PSSE 5-Bus system with switched shunt compensation
 * Based on PSSE_5Bus_Test_switchShunt_locked.raw test data
 */
public class PSSE_5Bus_SwitchedShunt_Test extends CorePluginTestSetup {
	
	@Test
	public void test_5Bus_SwitchedShunt_locked_Loadflow() throws Exception {
		//IpssLogger.getLogger().setLevel(Level.INFO);
		AclfNetwork net = createTestCase();
		
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		//algo.getLfAdjAlgo().setApplyAdjustAlgo(true); // Enable adjustment algorithm for switched shunt
		algo.setMaxIterations(30);
	  	algo.loadflow();
	  	
  		assertTrue("Load flow should converge", net.isLfConverged());
  		
  		// Verify bus voltages and angles based on PSS/E results
        //printout the power flow results
  		//System.out.println(AclfOutFunc.loadFlowSummary(net));
  		
  		// Bus 1 (UNO-U1): 1.0000 pu, 0.0 degrees  
  		AclfBus bus1 = net.getBus("Bus1");
  		assertTrue("Bus 1 should exist", bus1 != null);
  		assertEquals("Bus 1 voltage magnitude", 1.0000, bus1.getVoltageMag(), 0.0001);
  		assertEquals("Bus 1 voltage angle", 0.0, bus1.getVoltageAng(UnitType.Deg), 0.1);
  		
  		// Bus 2 (UNO-230): 1.0291 pu, -2.2 degrees
  		AclfBus bus2 = net.getBus("Bus2");
  		assertTrue("Bus 2 should exist", bus2 != null);
  		assertEquals("Bus 2 voltage magnitude", 1.0291, bus2.getVoltageMag(), 0.001);
  		assertEquals("Bus 2 voltage angle", -2.2, bus2.getVoltageAng(UnitType.Deg), 0.1);
  		
  		// Bus 3 (DOS-230): 1.0314 pu, -2.4 degrees
  		AclfBus bus3 = net.getBus("Bus3");
  		assertTrue("Bus 3 should exist", bus3 != null);
  		assertEquals("Bus 3 voltage magnitude", 1.0314, bus3.getVoltageMag(), 0.001);
  		assertEquals("Bus 3 voltage angle", -2.4, bus3.getVoltageAng(UnitType.Deg), 0.1);
  		
  		// Bus 4 (TRES-34.5): 1.0910 pu, -6.3 degrees (load bus with switched shunt)
  		AclfBus bus4 = net.getBus("Bus4");
  		assertTrue("Bus 4 should exist", bus4 != null);
  		assertEquals("Bus 4 voltage magnitude", 1.0910, bus4.getVoltageMag(), 0.001);
  		assertEquals("Bus 4 voltage angle", -6.3, bus4.getVoltageAng(UnitType.Deg), 0.2);
  		
  		// Verify bus 4 has switched shunt compensation
  		assertTrue("Bus 4 should have switched shunt", bus4.isSwitchedShunt());
  		
  		// Bus 5 (UNO-U2): 1.0000 pu, -0.0 degrees
  		AclfBus bus5 = net.getBus("Bus5");
  		assertTrue("Bus 5 should exist", bus5 != null);
  		assertEquals("Bus 5 voltage magnitude", 1.0000, bus5.getVoltageMag(), 0.0001);
  		assertEquals("Bus 5 voltage angle", -0.0, bus5.getVoltageAng(UnitType.Deg), 0.1);
  		
  		// Verify generator outputs (using InterPSS actual results)
  		// Bus 1 generator: Actual values from InterPSS load flow
  		assertEquals("Bus 1 generator P", 22.546/100.0, bus1.getGenP(), 0.001); // Use values from RAW file
  		assertEquals("Bus 1 generator Q", -16.52/100.0, bus1.toSwingBus().getGenResults().getImaginary(), 0.001); // Actual InterPSS result
  		
  		// Bus 5 generator: Actual values from InterPSS load flow  
  		assertEquals("Bus 5 generator P", 22.500/100.0, bus5.getGenP(), 0.001);
  		assertEquals("Bus 5 generator Q", -16.52/100.0, bus5.toPVBus().getGenResults().getImaginary(), 0.001); // Actual InterPSS result
  		
  		// Verify load at bus 4: 45.0 MW, 45.0 MVAR
  		assertEquals("Bus 4 load P", 45.0/100.0, bus4.getLoadP(), 0.01);
  		assertEquals("Bus 4 load Q", 45.0/100.0, bus4.getLoadQ(), 0.01);

        	// Verify that the system still converges with fixed shunt compensation
  	
  		assertTrue("Bus 4 should have switched shunt", bus4.isSwitchedShunt());
        // verify the swiched shunt output is 84.4 mvar
        assertEquals("Switched shunt Q at Bus 4", 84.4/100.0, 
        		bus4.getSwitchedShunt().getQ(), 0.01);
  

  		// The voltage may be different from the adjustable case but should still be reasonable
  		assertTrue("Bus 4 voltage should be within reasonable range", 
  				bus4.getVoltageMag() > 0.9 && bus4.getVoltageMag() < 1.2);
	}
	
	/**
	 * Test with switched shunt adjustment disabled
	 */
	@Test
	public void test_5Bus_SwitchedShunt_discrete() throws Exception {
		//IpssLogger.getLogger().setLevel(Level.INFO);
		AclfNetwork net = createTestCase();
		

  		
  		// Verify that the system still converges with fixed shunt compensation
  		AclfBus bus4 = net.getBus("Bus4");
  		assertTrue("Bus 4 should exist", bus4 != null);
  		assertTrue("Bus 4 should have switched shunt", bus4.isSwitchedShunt());

        //change the control mode to discrete
        bus4.getSwitchedShunt().setControlMode(AclfAdjustControlMode.DISCRETE);
        bus4.getSwitchedShunt().setDesiredControlRange(new LimitType(1.05, 0.95));

        System.out.println(bus4.getSwitchedShunt().toString());

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		//algo.getLfAdjAlgo().setApplyAdjustAlgo(false); // Disable adjustment algorithm (locked shunt)
		algo.setMaxIterations(30);
        algo.setTolerance(0.0001);
	  	algo.loadflow();
	  	
  		assertTrue("Load flow should converge with locked shunt", net.isLfConverged());

         //printout the power flow results
  		System.out.println(AclfOutFunc.loadFlowSummary(net));

        /*
         * TODO: Error here
         * InterPSS results: the dQ max is more than the tolerance, but it was determined as converged above 
         *            Max Power Mismatches
             Bus              dPmax       Bus              dQmax
            -------------------------------------------------------
            Bus3             0.000000  Bus4             0.079174 (pu)
                            0.0153480                7917.427899 (kva)
         */

        //the PSS/E result is 47.2 MVAR, two banks of 23.6 MVAR each are switched on
        assertEquals("Switched shunt Q at Bus 4", 47.2/100.0, 
        		bus4.getSwitchedShunt().getQ(), 0.01);

        assertTrue("Bus 4 voltage should be within reasonable range", 
            bus4.getVoltageMag() > 0.95 && bus4.getVoltageMag() < 1.05);
        
        // the bus 4 voltage mag is 0.9997
        assertEquals("Bus 4 voltage mag", 0.9997, bus4.getVoltageMag(), 0.0001);


	}


    @Test
	public void test_5Bus_SwitchedShunt_discrete_swictchedoff() throws Exception {
		//IpssLogger.getLogger().setLevel(Level.INFO);
		AclfNetwork net = createTestCase();
		

  		
  		// Verify that the system still converges with fixed shunt compensation
  		AclfBus bus4 = net.getBus("Bus4");
  		assertTrue("Bus 4 should exist", bus4 != null);
  		assertTrue("Bus 4 should have switched shunt", bus4.isSwitchedShunt());

        //change the control mode to discrete
        bus4.getSwitchedShunt().setControlMode(AclfAdjustControlMode.DISCRETE);
        bus4.getSwitchedShunt().setDesiredControlRange(new LimitType(0.89, 0.85));

        System.out.println(bus4.getSwitchedShunt().toString());

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		//algo.getLfAdjAlgo().setApplyAdjustAlgo(false); // Disable adjustment algorithm (locked shunt)
		algo.setMaxIterations(30);
	  	algo.loadflow();
	  	
  		assertTrue("Load flow should converge with locked shunt", net.isLfConverged());

         //printout the power flow results
  		System.out.println(AclfOutFunc.loadFlowSummary(net));

        assertEquals("Switched shunt Q at Bus 4", 0/100.0, 
        		bus4.getSwitchedShunt().getQ(), 0.01);

        assertTrue("Bus 4 voltage should be within reasonable range", 
            bus4.getVoltageMag() > 0.845 && bus4.getVoltageMag() < 0.895);


	}

    @Test
	public void test_5Bus_SwitchedShunt_continuous_range() throws Exception {
		//IpssLogger.getLogger().setLevel(Level.INFO);
		AclfNetwork net = createTestCase();
		

  		
  		// Verify that the system still converges with fixed shunt compensation
  		AclfBus bus4 = net.getBus("Bus4");
  		assertTrue("Bus 4 should exist", bus4 != null);
  		assertTrue("Bus 4 should have switched shunt", bus4.isSwitchedShunt());

        //change the control mode to discrete
        bus4.getSwitchedShunt().setControlMode(AclfAdjustControlMode.CONTINUOUS);
        bus4.getSwitchedShunt().setAdjControlType(AclfAdjustControlType.RANGE_CONTROL);

        //TODO why the following setting is not working and I need to use setVSpecified?
        bus4.getSwitchedShunt().setDesiredControlRange(new LimitType(1.03, 1.02));

        //bus4.getSwitchedShunt().setVSpecified(1.02);

        System.out.println(bus4.getSwitchedShunt().toString());

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		//algo.getLfAdjAlgo().setApplyAdjustAlgo(false); // Disable adjustment algorithm (locked shunt)
		algo.setMaxIterations(30);
	  	algo.loadflow();
	  	
  		assertTrue("Load flow should converge with locked shunt", net.isLfConverged());

         //printout the power flow results
  		System.out.println(AclfOutFunc.loadFlowSummary(net));

        // assertEquals("Switched shunt Q at Bus 4", 0/100.0, 
        // 		bus4.getSwitchedShunt().getQ(), 0.01);

        assertTrue("Bus 4 voltage should be within reasonable range", 
            bus4.getVoltageMag() > 1.0195 && bus4.getVoltageMag() < 1.0305);


	}

     @Test
	public void test_5Bus_SwitchedShunt_continuous_range_v35() throws Exception {
		//IpssLogger.getLogger().setLevel(Level.INFO);
		AclfNetwork net = createTestCaseContinuousV35();
		

  		
  		// Verify that the system still converges with fixed shunt compensation
  		AclfBus bus4 = net.getBus("Bus4");
  		assertTrue("Bus 4 should exist", bus4 != null);
  		assertTrue("Bus 4 should have switched shunt", bus4.isSwitchedShunt());

        System.out.println(bus4.getSwitchedShunt().toString());

        //assert the control mode is continuous
        assertTrue("Bus 4 switched shunt control mode should be continuous",
            bus4.getSwitchedShunt().getControlMode() == AclfAdjustControlMode.CONTINUOUS);
        
        //Check bus4.getSwitchedShunt().getDesiredControlRange is within new LimitType(1.03, 1.02);
        assertTrue("Bus 4 switched shunt desired control range should be within (1.03, 1.02)",
            bus4.getSwitchedShunt().getDesiredControlRange().getMax() == 1.03 &&
            bus4.getSwitchedShunt().getDesiredControlRange().getMin() == 1.02);

        //bus4.getSwitchedShunt().setVSpecified(1.02);

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		//algo.getLfAdjAlgo().setApplyAdjustAlgo(false); // Disable adjustment algorithm (locked shunt)
		algo.setMaxIterations(30);
	  	algo.loadflow();
	  	
  		assertTrue("Load flow should converge with locked shunt", net.isLfConverged());

         //printout the power flow results
  		System.out.println(AclfOutFunc.loadFlowSummary(net));

        // assertEquals("Switched shunt Q at Bus 4", 0/100.0, 
        // 		bus4.getSwitchedShunt().getQ(), 0.01);

        assertTrue("Bus 4 voltage should be within reasonable range within tolerance", 
            bus4.getVoltageMag() > 1.0195 && bus4.getVoltageMag() < 1.0305);


	}

    @Test
	public void test_5Bus_SwitchedShunt_continuous_point() throws Exception {
		//IpssLogger.getLogger().setLevel(Level.INFO);
		AclfNetwork net = createTestCase();
		

  		
  		// Verify that the system still converges with fixed shunt compensation
  		AclfBus bus4 = net.getBus("Bus4");
  		assertTrue("Bus 4 should exist", bus4 != null);
  		assertTrue("Bus 4 should have switched shunt", bus4.isSwitchedShunt());

        //change the control mode to discrete
        bus4.getSwitchedShunt().setControlMode(AclfAdjustControlMode.CONTINUOUS);
        bus4.getSwitchedShunt().setAdjControlType(AclfAdjustControlType.POINT_CONTROL);

        //TODO why the following setting is not working and I need to use setVSpecified?
        //bus4.getSwitchedShunt().setDesiredControlRange(new LimitType(1.03, 1.02));

        bus4.getSwitchedShunt().setVSpecified(1.02);

        System.out.println(bus4.getSwitchedShunt().toString());

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		//algo.getLfAdjAlgo().setApplyAdjustAlgo(false); // Disable adjustment algorithm (locked shunt)
		algo.setMaxIterations(30);
	  	algo.loadflow();
	  	
  		assertTrue("Load flow should converge with locked shunt", net.isLfConverged());

         //printout the power flow results
  		System.out.println(AclfOutFunc.loadFlowSummary(net));

        // assertEquals("Switched shunt Q at Bus 4", 0/100.0, 
        // 		bus4.getSwitchedShunt().getQ(), 0.01);

        assertTrue("Bus 4 voltage should be within reasonable range", 
            bus4.getVoltageMag() > 1.019 && bus4.getVoltageMag() < 1.021);


	}

    /**
     * PSS/E uses range control by default, so afer we import the case, we set the range same as the  point control set point, 
     * the results should be the same as the point control case above.
     * This test is to verify that the continuous control with range setting works as expected
     * @throws Exception
     */
     @Test
	public void test_5Bus_SwitchedShunt_continuous_point_v35() throws Exception {
		//IpssLogger.getLogger().setLevel(Level.INFO);
		AclfNetwork net = createTestCaseContinuousV35();
		

  		
  		// Verify that the system still converges with fixed shunt compensation
  		AclfBus bus4 = net.getBus("Bus4");
  		assertTrue("Bus 4 should exist", bus4 != null);
  		assertTrue("Bus 4 should have switched shunt", bus4.isSwitchedShunt());


        //You need to set the range same as the  point control set point
        bus4.getSwitchedShunt().setDesiredControlRange(new LimitType(1.02, 1.02));

        //bus4.getSwitchedShunt().setVSpecified(1.02);

        System.out.println(bus4.getSwitchedShunt().toString());

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		//algo.getLfAdjAlgo().setApplyAdjustAlgo(false); // Disable adjustment algorithm (locked shunt)
		algo.setMaxIterations(30);
	  	algo.loadflow();
	  	
  		assertTrue("Load flow should converge with locked shunt", net.isLfConverged());

         //printout the power flow results
  		System.out.println(AclfOutFunc.loadFlowSummary(net));

        // assertEquals("Switched shunt Q at Bus 4", 0/100.0, 
        // 		bus4.getSwitchedShunt().getQ(), 0.01);

        assertTrue("Bus 4 voltage should be within reasonable range", 
            bus4.getVoltageMag() > 1.019 && bus4.getVoltageMag() < 1.021);


	}
	
	/**
	 * Create the test network from the PSSE RAW file
	 */
	private AclfNetwork createTestCase() {
		IODMAdapter adapter = new PSSERawAdapter(PSSEAdapter.PsseVersion.PSSE_30);
		assertTrue("Should successfully parse RAW file", 
				adapter.parseInputFile("testData/psse/v30/PSSE_5Bus_Test_switchShunt_locked.raw"));
		
		AclfModelParser parser = (AclfModelParser)adapter.getModel();
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
  	  		System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
  	  		return null;
		}		
		
		AclfNetwork net = simuCtx.getAclfNet();
		
		return net;
	}

    private AclfNetwork createTestCaseContinuousV35() {
		IODMAdapter adapter = new PSSERawAdapter(PSSEAdapter.PsseVersion.PSSE_35);
		assertTrue("Should successfully parse RAW file", 
				adapter.parseInputFile("testData/psse/v35/PSSE_5Bus_Test_switchShunt_continuous_v35.raw"));
		
		AclfModelParser parser = (AclfModelParser)adapter.getModel();
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
  	  		System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
  	  		return null;
		}		
		
		AclfNetwork net = simuCtx.getAclfNet();
		
		return net;
	}
}
