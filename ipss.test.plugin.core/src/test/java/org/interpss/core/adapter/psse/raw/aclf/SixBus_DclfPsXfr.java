package org.interpss.core.adapter.psse.raw.aclf;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.algo.dclf.adapter.DclfAlgoBus;
import com.interpss.core.net.RefBusType;

public class SixBus_DclfPsXfr extends CorePluginTestSetup {
	@Test
	public void aclf() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);


		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v30/SixBus_2WPsXfr.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
					.load()
					.getImportedObj();
		//for (AclfBus bus : net.getBusList())
		//	bus.initMultiGen();
  		//System.out.println(net.net2String());

	  	LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)
			 			 .loadflow();
	  	
  		assertTrue(net.isLfConverged());
  		
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
  		AclfSwingBusAdapter swing = net.getBus("Bus1").toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-3.1032)<0.0001);
  		assertTrue(Math.abs(p.getImaginary()-0.5212)<0.0001);	   		
 	}
	
	@Test
	public void dclf() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);


		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v30/SixBus_2WPsXfr.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
					.load()
					.getImportedObj();
  		//System.out.println(net.net2String());
		/*
		net.accept(CoreObjectFactory.createBusNoArrangeVisitor());
		for (Bus b : net.getBusList())
			System.out.println(b.getId() + ": " + b.getSortNumber());
 		System.out.println(net.formB1Matrix());
		*/
		
		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		algo.calculateDclf();

		//System.out.println(DclfOutFunc.dclfResults(algo, false));
  		assertTrue(Math.abs(algo.getBusPower(algo.getDclfAlgoBus("Bus1"))-3.0723)<0.0001);
  		
		for (DclfAlgoBus dclfBus : algo.getDclfAlgoBusList()) {
			//System.out.println(b.getId() + " mismatch " + algo.getMismatch((AclfBus)b));
			if (!(dclfBus.getBus()).isRefBus())
				assertTrue(Math.abs(algo.getMismatch(dclfBus)) < 0.00001);
		}
		//algo.destroy();			
	}
	
	@Test
	public void dclfRef() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);


		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v30/SixBus_2WPsXfr.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
					.load()
					.getImportedObj();

		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		
		net.setRefBusId("Bus3");
		net.setRefBusType(RefBusType.USER_DEFINED);
		
//		net.getBus("Bus1").setGenP(3.0723);
		
		algo.calculateDclf();

		//System.out.println(DclfOutFunc.dclfResults(algo, false));
		//System.out.println(algo.getBusPower(net.getBus("Bus1")) + ", " + Math.toDegrees(algo.getBusAngle("Bus1")));
  		assertTrue(Math.abs(algo.getBusPower(algo.getDclfAlgoBus("Bus1"))-1.99)<0.0001);
  		assertTrue(Math.abs(Math.toDegrees(algo.getBusAngle("Bus1"))-2.848746)<0.001);

		//algo.destroy();			
	}

	//@Test
	public void aclf1() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);


		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v30/SixBus_2WPsXfr_1.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
					.load()
					.getImportedObj();
  		//System.out.println(net.net2String());

	  	LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)
			 			 .loadflow();
	  	
  		assertTrue(net.isLfConverged());
  		
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
		//System.out.println(AclfOutFunc.loadFlowSummary(net));
  		AclfSwingBusAdapter swing = net.getBus("Bus1").toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-3.1032)<0.0001);
  		assertTrue(Math.abs(p.getImaginary()-0.5212)<0.0001);	  		
 	}
	
	@Test
	public void dclf1() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);


		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v30/SixBus_2WPsXfr_1.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
					.load()
					.getImportedObj();

		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		algo.calculateDclf();
		
		//System.out.println(DclfOutFunc.dclfResults(algo, false));
  		assertTrue(Math.abs(algo.getBusPower(algo.getDclfAlgoBus("Bus1"))-3.0723)<0.0001);
		
		//algo.destroy();	
	}
}

