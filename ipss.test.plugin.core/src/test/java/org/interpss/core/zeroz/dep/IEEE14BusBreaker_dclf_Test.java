package org.interpss.core.zeroz.dep;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.SenAnalysisAlgorithm;
import com.interpss.core.common.ReferenceBusException;
import com.interpss.core.funcImpl.zeroz.dep.ZeroZBranchProcesor;

@Deprecated
public class IEEE14BusBreaker_dclf_Test extends CorePluginTestSetup {
	//@Test 
	public void case1_regularMethod() throws  InterpssException, ReferenceBusException, IpssNumericException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/zeroz/Ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());

		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		algo.calculateDclf();

		//System.out.println(DclfOutFunc.dclfResults(algo, false));
  		assertTrue(Math.abs(algo.getBusPower(algo.getDclfAlgoBus("Bus1"))-2.1900)<0.01);

		//algo.destroy();	
    }	
	
	//@Test 
	public void case1_smallZ() throws  InterpssException, ReferenceBusException, IpssNumericException {
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/zeroz/Ieee14Bus_breaker.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());
		
	  	net.accept(new ZeroZBranchProcesor(true));
	  	//assertTrue(net.isZeroZBranchModel());

		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		algo.calculateDclf();

		//System.out.println(DclfOutFunc.dclfResults(algo, false));
 		assertTrue(Math.abs(algo.getBusPower(algo.getDclfAlgoBus("Bus1"))-2.1900)<0.01);

		//algo.destroy();	
    }	

	//@Test 
	public void case1_smallZ_1() throws  InterpssException, ReferenceBusException, IpssNumericException {
		// test casa with a small-Z brach loop at Bus-14
		
		// Create an AclfNetwork object
		AclfNetwork net = IpssAdapter.importAclfNet("testData/odm/zeroz/ieee14Bus_breaker_1.xml")
				.setFormat(IpssAdapter.FileFormat.IEEE_ODM)
				.load()
				.getImportedObj();
	  	//System.out.println(net.net2String());
		
	  	net.accept(new ZeroZBranchProcesor(true));
	  	//assertTrue(net.isZeroZBranchModel());

		SenAnalysisAlgorithm algo = DclfAlgoObjectFactory.createSenAnalysisAlgorithm(net);
		algo.calculateDclf();

		//System.out.println(DclfOutFunc.dclfResults(algo, false));
 		assertTrue(Math.abs(algo.getBusPower(algo.getDclfAlgoBus("Bus1"))-2.1900)<0.01);

		//algo.destroy();	
    }
}
