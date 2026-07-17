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
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.net.Branch;

/*
 * This test case compares InterPSS and PSS/E xfr branch model
 */
public class Mod_SixBus_DclfPsXfr extends CorePluginTestSetup {
	@Test
	public void aclf() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);


		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v30/Mod_SixBus_2WPsXfr.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
					.load()
					.getImportedObj();

	  	LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)
			 			 .loadflow();
	  	
  		assertTrue(net.isLfConverged());
  		
  		AclfSwingBusAdapter swing = net.getBus("Bus1").toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-3.2955)<0.001);
  		assertTrue(Math.abs(p.getImaginary()-0.9571)<0.001);	   		
 	}
	
	@Test
	public void aclf1() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);


		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v30/Mod_SixBus_2WPsXfr.raw")
					.setFormat(IpssAdapter.FileFormat.PSSE)
					.setPsseVersion(PsseVersion.PSSE_30)
					.load()
					.getImportedObj();
  		//System.out.println(net.net2String());

	  	LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)
			 			 .loadflow();
	  	
  		assertTrue(net.isLfConverged());
  		
		//System.out.println(CorePluginFunction.aclfResultBusStyle.apply(net));
  		AclfSwingBusAdapter swing = net.getBus("Bus1").toSwingBus();
  		Complex p = swing.getGenResults(UnitType.PU);
  		assertTrue(Math.abs(p.getReal()-3.2955)<0.001);
  		assertTrue(Math.abs(p.getImaginary()-0.9571)<0.001);	   		
 	}
	
	@Test
	public void dclf() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);


		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v30/Mod_SixBus_2WPsXfr.raw")
					.format(IpssAdapter.FileFormat.PSSE)
					.psseVersion(PsseVersion.PSSE_30)
					.load()
					.getImportedObj();
  		//System.out.println(net.net2String());
		/*
		net.accept(CoreObjectFactory.createBusNoArrangeVisitor());
		for (Bus b : net.getBusList())
			System.out.println(b.getId() + ": " + b.getSortNumber());
 		System.out.println(net.formB1Matrix());
		*/
		
		// because of InterPSS xfrBranchModel, we need to convert to the PSS/E model
		for (Branch b : net.getBranchList()) {
			AclfBranch branch = (AclfBranch)b;
			if (branch.isXfr() || branch.isPSXfr()) {
				if (branch.getToTurnRatio() != 1.0) {
					branch.setZ(branch.getZ().multiply(branch.getToTurnRatio()*branch.getToTurnRatio()));
					branch.setFromTurnRatio(branch.getFromTurnRatio()/branch.getToTurnRatio());
					branch.setToTurnRatio(1.0);
					if (branch.isPSXfr()) {
						branch.setFromPSXfrAngle(branch.getFromPSXfrAngle() - branch.getToPSXfrAngle());
						branch.setToPSXfrAngle(0.0);
					}
				}
			}
		} 
		
		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		algo.calculateDclf();

		//System.out.println(DclfOutFunc.dclfResults(algo, false));
  		assertTrue(Math.abs(algo.getBusPower(algo.getDclfAlgoBus("Bus1"))-3.0723)<0.0001);

		//algo.destroy();			
	}

	@Test
	public void dclf1() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);


		AclfNetwork net = IpssAdapter.importAclfNet("testData/adpter/psse/v30/Mod_SixBus_2WPsXfr.raw")
					.format(IpssAdapter.FileFormat.PSSE)
					.psseVersion(PsseVersion.PSSE_30)
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

		//algo.destroy();			
	}
}

