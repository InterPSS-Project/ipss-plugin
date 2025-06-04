package org.interpss.gvy;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.script.gvy.AclfNetGvyScriptProcessor;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

import groovy.lang.GroovyShell;

public class GvySample {
    public static void main(String[] args) throws InterpssException {
    	GroovyShell shell = new GroovyShell();
    	String groovyCode = "def name = 'Alice'; println \"Hello, ${name}!\"; return name";
    	Object result = shell.evaluate(groovyCode);
    	System.out.println("Result: " + result);
    	
		IpssCorePlugin.init();
		
		// load the IEEE-14 Bus system
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
	  	
	  	AclfNetGvyScriptProcessor gvyProcessor = new AclfNetGvyScriptProcessor(net);
    	groovyCode = "aclfnet.id = 'Modified'; aclfnet.getBus('Bus14').loadP = 0.18;" +
    				 "return 'Net name: ' + aclfnet.id + ', ' + aclfnet.getBus('Bus14').loadP; ";
    	result = gvyProcessor.getShell().evaluate(groovyCode);
    	System.out.println("Result: " + result);
    }
}
