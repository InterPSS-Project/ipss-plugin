package org.interpss.core.script.gvy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.util.NumericUtil;
import org.interpss.script.gvy.AclfNetGvyScriptProcessor;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.core.aclf.AclfNetwork;

public class GvyScriptEval_Test extends CorePluginTestSetup {
	@Test 
	public void bus14testCase() throws Exception {
		// load the IEEE-14 Bus system
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();		
		
 		assertTrue("", net.isContributeGenLoadModel());
 		
	  	AclfNetGvyScriptProcessor gvyProcessor = new AclfNetGvyScriptProcessor(net);
    	String groovyCode = "aclfnet.id = 'Modified';";
    	Object result = gvyProcessor.evaluate(groovyCode);
    	//System.out.println("Result: " + result);
    	assertTrue("Net name should be 'Modified'", net.getId().equals("Modified"));
    	
    	groovyCode = "aclfnet.getBus('Bus14').loadP = 0.18;";
		result = gvyProcessor.evaluate(groovyCode);
		//System.out.println("Result: " + result);
		assertTrue("Bus load should be 0.18", NumericUtil.equals(net.getBus("Bus14").getLoadP(), 0.18, 1.0E-4));
		
    	groovyCode = """
    			bus = aclfnet.getBus('Bus14');
    			load = bus.getContributeLoad('Bus14-L1');
    			load.loadCP = new Complex(0.18, 0.07); 
    		""";
		result = gvyProcessor.evaluate(groovyCode);
		System.out.println("Result: " + result);
		assertTrue("Bus contribute load should be 0.18 + j0.07", 
				NumericUtil.equals(net.getBus("Bus14").getContributeLoad("Bus14-L1").getLoadCP(), new Complex(0.18, 0.07), 1.0E-4));

		groovyCode = """
    			branch = aclfnet.getBranch("Bus1", "Bus2", "1"); 
    			branch.z = new Complex(0.02, 0.06 );
    		""";
		result = gvyProcessor.evaluate(groovyCode);
		System.out.println("Result: " + result);
		assertTrue("Branch z should be 0.02+j0.06", 
				NumericUtil.equals(net.getBranch("Bus1", "Bus2", "1").getZ(), new Complex(0.02, 0.06), 1.0E-4));
	}
	
	@Test 
	public void bus14ScriptFileTestCase() throws Exception {
		// load the IEEE-14 Bus system
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();		
		
 		assertTrue("", net.isContributeGenLoadModel());
 		
	  	AclfNetGvyScriptProcessor gvyProcessor = new AclfNetGvyScriptProcessor(net);

	  	String scriptFile = "script/ieee14_adjBus14.gvy";
	  	// Load Groovy script from the file as a string
	  	String groovyCode = FileUtil.readFileAsString(scriptFile);
	  	//System.out.println("Groovy Code: " + groovyCode);
		
		// Evaluate the Groovy code
		Object result = gvyProcessor.evaluate(groovyCode);
		System.out.println("Result: " + result);
		assertTrue("Bus contribute load should be 0.18 + j0.07", 
				NumericUtil.equals(net.getBus("Bus14").getContributeLoad("Bus14-L1").getLoadCP(), new Complex(0.18, 0.07), 1.0E-4));

		scriptFile = "script/ieee14_adjBranch1_2.gvy";
	  	// Load Groovy script from the file as a string
	  	groovyCode = FileUtil.readFileAsString(scriptFile);
	  	//System.out.println("Groovy Code: " + groovyCode);
		
		// Evaluate the Groovy code
		result = gvyProcessor.evaluate(groovyCode);
		System.out.println("Result: " + result);
		assertTrue("Branch z should be 0.02+j0.06", 
				NumericUtil.equals(net.getBranch("Bus1", "Bus2", "1").getZ(), new Complex(0.02, 0.06), 1.0E-4));
		assertFalse("Branch should be off", net.getBranch("Bus1", "Bus2", "1").isActive());
	}
}
