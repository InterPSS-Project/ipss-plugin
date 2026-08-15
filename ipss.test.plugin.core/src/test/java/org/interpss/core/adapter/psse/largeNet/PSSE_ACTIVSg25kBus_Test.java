package org.interpss.core.adapter.psse.largeNet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;

import org.interpss.fadapter.psse.PSSEDirectParser;
public class PSSE_ACTIVSg25kBus_Test  extends CorePluginTestSetup {
	
	@Test
	public void testAclf() throws InterpssException{
		IpssCorePlugin.init();

		// load the test data V33
		AclfNetwork net = new PSSEDirectParser().parse("testData/psse/v33/ACTIVSg25k.RAW");
	  
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		
		//aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

		//aclfAlgo.getLfAdjAlgo().setPowerAdjAppType(AdjustApplyType.POST_ITERATION);
		//aclfAlgo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		aclfAlgo.setTolerance(1.0E-6);
		assertTrue(aclfAlgo.loadflow());
		
		/*
		net.getBusList().forEach(b -> {
			if (b.isSwing()) {
				System.out.println("Swing bus: " + b.getId() + ", " + b.getName());
			}
		});
		*/
		
	  	AclfBus swingBus = net.getBus("Bus62120");
	  	AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		System.out.println("Swing bus Gen Results: " + p);
		// With all adjustments disabled, the swing supplies only the fixed network balance.
		assertEquals(5.3614408, p.getReal(), 0.0001);
		assertEquals(1.2017916, p.getImaginary(), 0.0001);
	}
}
