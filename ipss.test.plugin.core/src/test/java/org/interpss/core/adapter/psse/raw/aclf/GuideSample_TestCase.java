package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class GuideSample_TestCase extends CorePluginTestSetup {
	@Test
	public void testCase() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.PSSE, IpssFileAdapter.Version.PSSE_30)
				.loadAclfNet("testData/adpter/psse/PSSE_GuideSample.raw");

	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.setLfMethod(AclfMethodType.NR);
		algo.getNrMethodConfig().setNonDivergent(true);
        assertTrue(algo.loadflow());
	  	
  		AclfBus swingBus = net.getBus("Bus3011");
		AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.mW);
        // Native PSS/E 36.7, explicit v30 import, discrete taps normalized.
        // See docs/benchmarks/transformer-tap-data-check-2026-10-15.md.
        assertEquals(258.63946533, p.getReal(), 0.01);
        assertEquals(103.60634613, p.getImaginary(), 0.01);
	}

	@Test
	public void testCase1() throws Exception {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.PSSE, IpssFileAdapter.Version.PSSE_30)
				.loadAclfNet("testData/adpter/psse/PSSE_GuideSample.raw");

	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
        // Preserve the historical off-grid RAW ratios when explicitly requested.
        algo.getDataCheckConfig().setAutoSnapTransformerTap(false);
	  	algo.setLfMethod(AclfMethodType.NR);
		algo.getNrMethodConfig().setNonDivergent(true);
        assertTrue(algo.loadflow());
	  	
  		AclfBus swingBus = net.getBus("Bus3011");
		AclfSwingBusAdapter swing = swingBus.toSwingBus();
  		Complex p = swing.getGenResults(UnitType.mW);
  		assertTrue(Math.abs(p.getReal()-258.657)<0.01);
  		assertTrue(Math.abs(p.getImaginary()-104.045)<0.01);
	}
}

