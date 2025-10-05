package org.interpss.core.dclf;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.dclf.DclfAlgorithm;
import com.interpss.core.algo.dclf.DclfMethod;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBus;
import com.interpss.core.algo.dclf.solver.IDclfSolver.CacheType;

public class Dclf_PSSE_ACTIVSg25kBus_Test  extends CorePluginTestSetup {
	@Test 
	public void dclfLossTest() throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = IpssAdapter.importAclfNet("testData/psse/v33/ACTIVSg25k.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();	
		
		DclfAlgorithm dclfAlgo = DclfAlgoObjectFactory.createDclfAlgorithm(aclfNet, CacheType.SenNotCached, true);
		dclfAlgo.calculateDclf(DclfMethod.INC_LOSS);

		//System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false));

		DclfAlgoBus dclfBus = dclfAlgo.getDclfAlgoBus("Bus62120");
		//AclfBus bus1 = dclfBus.getBus();
		//int n1 = bus1.getSortNumber();
		double pgen = dclfAlgo.getBusPower(dclfBus) * aclfNet.getBaseMva(); 
		assertTrue(""+pgen, NumericUtil.equals(pgen, 1224.42, 0.01));		
	}

	@Test 
	public void dclfTest() throws Exception {
		// load the test data V33
		AclfNetwork aclfNet = IpssAdapter.importAclfNet("testData/psse/v33/ACTIVSg25k.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();	
		
		DclfAlgorithm dclfAlgo = DclfAlgoObjectFactory.createDclfAlgorithm(aclfNet);
		dclfAlgo.calculateDclf();

		//System.out.println(DclfOutFunc.dclfResults(dclfAlgo, false));

		DclfAlgoBus dclfBus = dclfAlgo.getDclfAlgoBus("Bus62120");
		//AclfBus bus1 = dclfBus.getBus();
		//int n1 = bus1.getSortNumber();
		double pgen = dclfAlgo.getBusPower(dclfBus) * aclfNet.getBaseMva(); 
		assertTrue(""+pgen, NumericUtil.equals(pgen, -4620.32, 0.01));			
	}
	
	@Test
	public void testAclf() throws InterpssException{
		IpssCorePlugin.init();
		//IpssLogger.getLogger().setLevel(Level.WARNING);

		// load the test data V33
		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v33/ACTIVSg25k.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_33) 
				.load()
				.getImportedObj();
	  
		LoadflowAlgorithm aclfAlgo = CoreObjectFactory.createLoadflowAlgorithm(net);
		
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
  		assertTrue(Math.abs(p.getReal()-5.36144)<0.0001);
  		assertTrue(Math.abs(p.getImaginary()-1.20179)<0.0001);
	}
}
