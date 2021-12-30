package org.interpss.core.se;

import static org.interpss.pssl.plugin.IpssAdapter.FileFormat.IEEECommonFormat;
import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.hazelcast.internal.serialization.SerializationService;
import com.hazelcast.internal.serialization.impl.DefaultSerializationServiceBuilder;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.se.SEBranch;
import com.interpss.se.SEBus;
import com.interpss.se.SENetwork;
import com.interpss.se.SENetworkHelper;
import com.interpss.se.SEObjectFactory;
import com.interpss.se.algo.SEAlgorithm;
import com.interpss.state.se.SEBranchState;
import com.interpss.state.se.SEBusState;
import com.interpss.state.se.SENetworkState;

public class SE_IEEE118Test extends CorePluginTestSetup {
	double errorPQ = 0.05;
	double errorV = 0.01;
	
	@Test
	public void testDeepCopy() throws InterpssException, IpssNumericException, Exception {
		SENetwork seNet = createTestCase();
		
		SerializationService serializeService = new DefaultSerializationServiceBuilder().build();  
		SEBus busCopy = seNet.getBus("Bus1").deepCopy(serializeService);
		//System.out.println(seNet.getBus("Bus1"));
		//System.out.println(busCopy);
		
		SEBranch braCopy = seNet.getBranch("Bus2->Bus1(1)").deepCopy(serializeService);
		//System.out.println(seNet.getBranch("Bus2->Bus1(1)"));
		//System.out.println(braCopy);
		
		// copy through Hz
		SENetwork seNetCopy = seNet.deepCopy();
		
		assertTrue("", seNet.diffState(seNetCopy));
	}
	
	@Test
	public void testJsonCopy() throws InterpssException, IpssNumericException, Exception {
		SENetwork seNet = createTestCase();
		
		SEBus bus = seNet.getBus("Bus1");
		SEBus busCopy = SEBusState.create(new SEBusState(bus));
		//System.out.println(seNet.getBus("Bus1"));
		//System.out.println(busCopy);
		
		SEBranch branch = seNet.getBranch("Bus2->Bus1(1)");
		SEBranch braCopy = SEBranchState.create(new SEBranchState(branch));
		//System.out.println(seNet.getBranch("Bus2->Bus1(1)"));
		//System.out.println(braCopy);
	
		// copy through JSon serialization/deserialization
		SENetwork seNetCopy = SENetworkState.create(new SENetworkState(seNet));
		
		assertTrue("", seNet.diffState(seNetCopy));
	}	
	
	@Test
	public void testSEAlgo() throws InterpssException, IpssNumericException, Exception {
		SENetwork seNet = createTestCase();

		SEAlgorithm seAlgo = SEObjectFactory.createSEAlgorithm(seNet);

		// qer: Qualified Estimation Rate
		double qer = seAlgo.se();
		assertTrue("QER should be larger than 95% ", qer > 0.95);		
		
		double maxResidual = seNet.calMaxResidual();
		assertTrue("Max residual should be less than 2% ", maxResidual < 0.02);
	}
	
	@Test
	public void testDeepCopySEAlgo() throws InterpssException, IpssNumericException, Exception {
		SENetwork seNet = createTestCase();
		SENetworkState seNetBean = new SENetworkState(seNet);
		FileUtil.writeText2File("output/temp1.json",seNetBean.toString());
		
		SENetwork seNetCopy = seNet.deepCopy();
		SENetworkState seNetCopyBean = new SENetworkState(seNetCopy);
		FileUtil.writeText2File("output/temp2.json",seNetCopyBean.toString());
		
		SEAlgorithm seAlgo = SEObjectFactory.createSEAlgorithm(seNetCopy);

		// qer: Qualified Estimation Rate
		double qer = seAlgo.se();
		assertTrue("QER should be larger than 95% ", qer > 0.95);		
		
		double maxResidual = seNetCopy.calMaxResidual();
		assertTrue("Max residual should be less than 2% ", maxResidual < 0.02);
	}
	
	@Test
	public void testJSonCopySEAlgo() throws InterpssException, IpssNumericException, Exception {
		SENetwork seNet = createTestCase();
		
		SENetwork seNetCopy = SENetworkState.create(new SENetworkState(seNet));

		SEAlgorithm seAlgo = SEObjectFactory.createSEAlgorithm(seNetCopy);

		// qer: Qualified Estimation Rate
		double qer = seAlgo.se();
		assertTrue("QER should be larger than 95% ", qer > 0.95);		
		
		double maxResidual = seNetCopy.calMaxResidual();
		assertTrue("Max residual should be less than 2% ", maxResidual < 0.02);
	}
	
	private SENetwork createTestCase() throws InterpssException {
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
		seNet.setId("SE Test Net");
		
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
		
		return seNet;
	}

	private double RandomError(double measure, double error) {
		measure *= error * 2 * Math.random() + 1 - error;
		return measure;
	}
}
