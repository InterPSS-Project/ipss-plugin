package org.interpss.dstab.bpa;

import static org.junit.Assert.assertTrue;

import java.util.logging.Level;

import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.bpa.BPAAdapter;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.dstab.DStabTestSetupBase;
import org.interpss.dstab.output.TextSimuOutputHandler;
import org.interpss.mapper.odm.ODMDStabDataMapper;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class ODMMaper_IEEE9BusTest  extends DStabTestSetupBase {
	//@Test
	public void lfTestCase() throws Exception {
		IODMAdapter adapter = new BPAAdapter();
		assertTrue(adapter.parseInputFile(IODMAdapter.NetType.DStabNet,
				new String[] { "testdata/bpa/IEEE9.dat", 
				               "testdata/bpa/IEEE9-dyn.swi"}));
		
		DStabModelParser parser = (DStabModelParser)adapter.getModel();
		
		//parser.stdout();
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabDataMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}	

		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		DStabilityNetwork dstabNet = simuCtx.getDStabilityNet();

		LoadflowAlgorithm lfAlgo = dstabAlgo.getAclfAlgorithm();
		lfAlgo.loadflow();
		assertTrue(dstabNet.isLfConverged());
		//System.out.println(AclfOutFunc.loadFlowSummary(dstabNet));
		//System.out.println("bus2 Angle(deg)="+dstabNet.getDStabBus("Bus2").getVoltageAng(UnitType.Deg));
		assertTrue(Math.abs(dstabNet.getDStabBus("Bus2").getVoltageMag() - 1.039) < 0.001);
		assertTrue(Math.abs(dstabNet.getDStabBus("Bus2").getVoltageAng(UnitType.Deg) + 3.43) < 0.01);
	}
	
	/*
	 * This is a sample to show how to debug InterPSS DStab 
	 */
	
	@Test
	public void noFaultTestCase() throws Exception {
		/*
		 * Load BPA Loadflow and DStab files, into translate to an ODM file
		 */
		IODMAdapter adapter = new BPAAdapter();
		assertTrue(adapter.parseInputFile(IODMAdapter.NetType.DStabNet,
				new String[] { "testdata/bpa/IEEE9.dat", 
				               "testdata/bpa/IEEE9-dyn.swi"}));
		DStabModelParser parser = (DStabModelParser)adapter.getModel();
		// print out ODM file
		parser.stdout();

		/*
		 * map ODM to InterPSS DStab object
		 */
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMDStabDataMapper(msg)
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}	

		/*
		 * Define DStab Algo
		 */
		DynamicSimuAlgorithm dstabAlgo = simuCtx.getDynSimuAlgorithm();
		dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		dstabAlgo.setSimuStepSec(0.001);
		dstabAlgo.setTotalSimuTimeSec(0.01);

		/*
		 * Run Loadflow
		 */
		LoadflowAlgorithm aclfAlgo = dstabAlgo.getAclfAlgorithm();
		aclfAlgo.loadflow();
		assertTrue(simuCtx.getDStabilityNet().isLfConverged());

		/*
		 * Change debug Level to INFO. You can also turn on CML field level debug
	   		@AnControllerField(
	      		type= CMLFieldEnum.ControlBlock,
	      		input="this.refPoint + pss.vs - mach.vt - this.washoutBlock.y",
	      		parameter={"type.NonWindup", "this.ka", "this.ta", "this.vrmax", "this.vrmin"},
	      		y0="this.delayBlock.u0 + this.seFunc.y" // ,debug=true
	   		)
	   	 */
		IpssLogger.getLogger().setLevel(Level.INFO);

		/*
		 * Use the Text output handler to print simu info to the Console 
		 */
		dstabAlgo.setSimuOutputHandler(new TextSimuOutputHandler());

		if (dstabAlgo.initialization()) {
			/*
			 * Print out DStab object
			 */
			// we need to print out the DStab object after the init, since
			// machine annotation controllers need to be initialized
			//System.out.println(simuCtx.getDStabilityNet().net2String());
			
			System.out.println("Running DStab simulation ...");
			dstabAlgo.performSimulation();
		}		
	}
}
