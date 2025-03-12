package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.interpss.CorePluginTestSetup;
import org.interpss.display.AclfOutFunc;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.junit.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;

public class PSSEV31_v36_IEEE9_Test extends CorePluginTestSetup {


	
	
@Test
public void testV31() throws Exception {
	AclfNetwork net = IpssAdapter.importAclfNet("testdata/psse/v31/ieee9_v31.raw")
			.setFormat(FileFormat.PSSE)
			.setPsseVersion(PsseVersion.PSSE_31)
			.load()
			.getImportedObj();
	
	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
  	//algo.setLfMethod(AclfMethod.PQ);
	algo.setNonDivergent(true);
  	algo.setLfMethod(AclfMethodType.NR);
  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
  	
  	algo.loadflow();
	

	assertTrue(net.isLfConverged());
	
	System.out.println(AclfOutFunc.loadFlowSummary(net));
	
	checkData(net);

}

	
@Test
public void testV32() throws Exception {
	AclfNetwork net = IpssAdapter.importAclfNet("testdata/psse/v32/ieee9_v32.raw")
			.setFormat(FileFormat.PSSE)
			.setPsseVersion(PsseVersion.PSSE_32)
			.load()
			.getImportedObj();
	
	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
  	//algo.setLfMethod(AclfMethod.PQ);
	algo.setNonDivergent(true);
  	algo.setLfMethod(AclfMethodType.NR);
  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
  	
  	algo.loadflow();
	

	assertTrue(net.isLfConverged());
	
	//System.out.println(AclfOutFunc.loadFlowSummary(net));
	
	checkData(net);

}

@Test
public void testV33() throws Exception {
	AclfNetwork net = IpssAdapter.importAclfNet("testdata/psse/v33/ieee9_v33.raw")
			.setFormat(FileFormat.PSSE)
			.setPsseVersion(PsseVersion.PSSE_33)
			.load()
			.getImportedObj();
	
	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
  	//algo.setLfMethod(AclfMethod.PQ);
	algo.setNonDivergent(true);
  	algo.setLfMethod(AclfMethodType.NR);
  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
  	
  	algo.loadflow();
	

	assertTrue(net.isLfConverged());
	
	//System.out.println(AclfOutFunc.loadFlowSummary(net));
	
	checkData(net);
}

@Test
public void testV34() throws Exception {
	AclfNetwork net = IpssAdapter.importAclfNet("testdata/psse/v34/ieee9_v34.raw")
			.setFormat(FileFormat.PSSE)
			.setPsseVersion(PsseVersion.PSSE_34)
			.load()
			.getImportedObj();
	
	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
  	//algo.setLfMethod(AclfMethod.PQ);
	algo.setNonDivergent(true);
  	algo.setLfMethod(AclfMethodType.NR);
  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
  	
  	algo.loadflow();
	

	assertTrue(net.isLfConverged());
	
	//System.out.println(AclfOutFunc.loadFlowSummary(net));
	
	checkData(net);
}

@Test
public void testV35() throws Exception {
	AclfNetwork net = IpssAdapter.importAclfNet("testdata/psse/v35/ieee9_v35.raw")
			.setFormat(FileFormat.PSSE)
			.setPsseVersion(PsseVersion.PSSE_35)
			.load()
			.getImportedObj();
	
	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
  	//algo.setLfMethod(AclfMethod.PQ);
	algo.setNonDivergent(true);
  	algo.setLfMethod(AclfMethodType.NR);
  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
  	
  	algo.loadflow();
	

	assertTrue(net.isLfConverged());
	
	//System.out.println(AclfOutFunc.loadFlowSummary(net));
	
	checkData(net);
	
}

@Test
public void testV36() throws Exception {
	AclfNetwork net = IpssAdapter.importAclfNet("testdata/psse/v36/ieee9_v36.raw")
			.setFormat(FileFormat.PSSE)
			.setPsseVersion(PsseVersion.PSSE_36)
			.load()
			.getImportedObj();
	
	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
  	//algo.setLfMethod(AclfMethod.PQ);
	algo.setNonDivergent(true);
  	algo.setLfMethod(AclfMethodType.NR);
  	algo.getLfAdjAlgo().setApplyAdjustAlgo(false);
  	
  	algo.loadflow();
	

	assertTrue(net.isLfConverged());
	
	//System.out.println(AclfOutFunc.loadFlowSummary(net));
	
	checkData(net);

	
}

private void checkData(AclfNetwork net) {
	
	/*
	 *  Load Flow Summary

                         Max Power Mismatches
             Bus              dPmax       Bus              dQmax
            -------------------------------------------------------
            Bus4             0.000013  Bus4             0.000024 (pu)
                            1.3413500                   2.416326 (kva)

     BusID          Code           Volt(pu)   Angle(deg)      Pg(pu)    Qg(pu)    Pl(pu)    Ql(pu)    Bus Name   
  ----------------------------------------------------------------------------------------------------------------
  Bus1         Swing                1.04000        0.00       0.7164    0.2710    0.0000    0.0000   BUS-1      
  Bus2         PV                   1.02500        9.32       1.6300    0.0659    0.0000    0.0000   BUS-2      
  Bus3         PV                   1.02500        4.70       0.8500   -0.1092    0.0000    0.0000   BUS-3      
  Bus4                              1.02597       -2.18       0.0000    0.0000    0.0000    0.0000   BUS-4      
  Bus5                ConstP        0.99577       -3.95       0.0000    0.0000    1.2500    0.5000   BUS-5      
  Bus6                ConstP        1.01279       -3.65       0.0000    0.0000    0.9000    0.3000   BUS-6      
  Bus7                              1.02581        3.76       0.0000    0.0000    0.0000    0.0000   BUS-7      
  Bus8                ConstP        1.01592        0.76       0.0000    0.0000    1.0000    0.3500   BUS-8      
  Bus9                              1.03239        2.00       0.0000    0.0000    0.0000    0.0000   BUS-9
	 */
	
	AclfGen gen1 = net.getBus("Bus1").getContributeGenList().get(0);
	assertEquals(gen1.getGen().getReal(),0.7164,0.0001);
	assertEquals(gen1.getGen().getImaginary(),0.271,0.0001);
	
	
	AclfGen gen2 = net.getBus("Bus2").getContributeGenList().get(0);
	assertEquals(gen2.getGen().getReal(),1.63,0.0001);
	assertEquals(gen2.getGen().getImaginary(),0.0659,0.0001);
	
	assertEquals(net.getBus("Bus4").getVoltageMag(),1.02597,0.0001);
	
	assertEquals(net.getBus("Bus9").getVoltageMag(),1.03239,0.0001);
  
}

}



