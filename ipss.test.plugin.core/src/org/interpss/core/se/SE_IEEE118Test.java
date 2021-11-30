package org.interpss.core.se;

import static org.interpss.pssl.plugin.IpssAdapter.FileFormat.IEEECommonFormat;
import static org.junit.Assert.assertTrue;

import org.interpss.IpssCorePlugin;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.se.SENetwork;
import com.interpss.se.SENetworkHelper;
import com.interpss.se.SEObjectFactory;
import com.interpss.se.algo.SEAlgorithm;

public class SE_IEEE118Test {
	double errorPQ = 0.1;
	double errorV = 0.01;

	@Test
	public void test() throws InterpssException, IpssNumericException, Exception {
		
		IpssCorePlugin.init();
		
		// Load a Loadflow case
		AclfNetwork aclfNet = IpssAdapter.importAclfNet("testData/se/ieee118.ieee")
				.setFormat(IEEECommonFormat)
				.load()
				.getImportedObj();
		
		// run Loadflow 
		LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(aclfNet);
		algo.loadflow();
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));
		assertTrue("Loadflow should converged! ", aclfNet.isLfConverged());		
		
		SENetwork seNet = SENetworkHelper.createSENetwrok(aclfNet);
		
		seNet.getBranchList().forEach(branch -> {
			branch.getFromSideRec().getPSeRec().setMeasure(RandomError(branch.powerFrom2To().getReal(), errorPQ));
			branch.getFromSideRec().getQSeRec().setMeasure(RandomError(branch.powerFrom2To().getImaginary(), errorPQ));
			branch.getFromSideRec().getPSeRec().setQuality(true);
			branch.getFromSideRec().getQSeRec().setQuality(true);
			
			branch.getToSideRec().getPSeRec().setMeasure(RandomError(branch.powerTo2From().getReal(), errorPQ));
			branch.getToSideRec().getQSeRec().setMeasure(RandomError(branch.powerTo2From().getImaginary(), errorPQ));
			branch.getToSideRec().getPSeRec().setQuality(true);
			branch.getToSideRec().getQSeRec().setQuality(true);
		});

		seNet.getBusList().forEach(bus -> {
			bus.getVoltSeRec().setMeasure(RandomError(bus.getVoltageMag(), errorV));
			bus.getVoltSeRec().setQuality(true);

			bus.getContributeGenList().forEach(gen -> {
				gen.getPSeRec().setMeasure(RandomError(gen.getGen().getReal(), errorPQ));
				gen.getPSeRec().setQuality(true);
				gen.getQSeRec().setMeasure(RandomError(gen.getGen().getImaginary(), errorPQ));
				gen.getQSeRec().setQuality(true);
			});
			
			bus.getContributeLoadList().forEach(load -> {
				load.setMvaBase(seNet.getBaseMva());
				load.getPSeRec().setMeasure(RandomError(load.getLoadCP().getReal(), errorPQ));
				load.getPSeRec().setQuality(true);
				load.getQSeRec().setMeasure(RandomError(load.getLoadCP().getImaginary(), errorPQ));
				load.getQSeRec().setQuality(true);
			});
		});

		SEAlgorithm seAlgo = SEObjectFactory.createSEAlgorithm(seNet);

		// qer: Qualified Estimation Rate
		double qer = seAlgo.se();
		assertTrue("QER should be larger than 95% ", qer > 0.95);		
		
		double maxResidual = seNet.calMaxResidual();
		assertTrue("Max residual should be less than 2% ", maxResidual < 0.02);
	}

	private double RandomError(double measure, double error) {
		measure *= error * 2 * Math.random() + 1 - error;
		return measure;
	}
}
