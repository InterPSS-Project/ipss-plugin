package org.interpss.gvy;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.script.gvy.AclfNetGvyScriptProcessor;
import org.interpss.script.gvy.BaseGvyScriptProcessor;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

import groovy.lang.GroovyShell;

public class GvySample {
    public static void main(String[] args) throws InterpssException {
    	GroovyShell shell = new GroovyShell();
    	String groovyCode = """
    						def name = 'Alice'; 
							println "Hello, ${name}!"; 
							return name;
						""";
    	Object result = shell.evaluate(groovyCode);
    	System.out.println("Result: " + result);
		
		// load the IEEE-14 Bus system
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("ipss.plugin.core/testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
	  	
	  	AclfNetGvyScriptProcessor gvyProcessor = new AclfNetGvyScriptProcessor(net);
    	groovyCode = """
    			aclfnet.id = 'Modified';
    			return 'Net name: ' + aclfnet.id; 		 
    		""";
    	result = gvyProcessor.evaluate(groovyCode);
    	System.out.println("Result: " + result);
    	
    	groovyCode = """
    			aclfnet.getBus('Bus14').loadP = 0.18;
				return 'Bus load: ' + aclfnet.getBus('Bus14').loadP; 
    		""";
		result = gvyProcessor.evaluate(groovyCode);
		System.out.println("Result: " + result);
		
    	groovyCode = """
    			branch = aclfnet.getBranch("Bus1", "Bus2", "1"); 
    			branch.z = new Complex(0.02, 0.06 );
				return 'Branch z : ' + aclfnet.getBranch("Bus1", "Bus2", "1").z; 
    		""";
		result = gvyProcessor.evaluate(groovyCode);
		System.out.println("Result: " + result);
		
    	groovyCode = """
    			bus = aclfnet.getBus("Bus14"); 
    			load = bus.getContributeLoad("Bus14-L1"); 
    			load.loadCP = new Complex(0.18, 0.07);
				return 'Bus contribute load: ' + aclfnet.getBus("Bus14").getContributeLoad("Bus14-L1").loadCP; 
    		""";
		result = gvyProcessor.evaluate(groovyCode);
		System.out.println("Result: " + result);

		// Bus14 dV/dQ via SenAnalysisAlgorithm (QVOLTAGE) from Groovy
		groovyCode = """
    			dVdQ = senAlgo.calBusSensitivity(SenAnalysisType.QVOLTAGE, 'Bus14', 'Bus13')
    			return 'dV(Bus13)/dQ(Bus14): ' + dVdQ
    		""";
		result = gvyProcessor.evaluate(groovyCode);
		System.out.println("Result: " + result);

		// Bus8 - Bus5->Bus6(1) GSF via SenAnalysisAlgorithm (PBRANCH) from Groovy
		groovyCode = """
				branch = aclfnet.getBranch('Bus5->Bus6(1)');
    			gsf = senAlgo.calGenShiftFactor('Bus8', branch)
    			return 'Bus8 - Bus5->Bus6(1) GSF: ' + gsf
    		""";
		result = gvyProcessor.evaluate(groovyCode);
		System.out.println("Result: " + result);

		// Weighted gen-transfer factor via senAlgo inject/withdraw lists from Groovy
		groovyCode = """
    			senAlgo.injectBusList.clear()
    			senAlgo.addInjectBus(aclfnet.getBus('Bus2'), 1.0)
    			senAlgo.withdrawBusList.clear()
    			senAlgo.addWithdrawBus(aclfnet.getBus('Bus14'), 0.9)
    			senAlgo.addWithdrawBus(aclfnet.getBus('Bus13'), 0.1)
    			branch = aclfnet.getBranch('Bus9->Bus14(1)')
    			f = senAlgo.genTransferDistFactor(branch)
    			return 'Bus2 inject / Bus14(0.9)+Bus13(0.1) withdraw on Distrbution factor on Brancgh: Bus9->Bus14(1): ' + f
    		""";
		result = gvyProcessor.evaluate(groovyCode);
		System.out.println("Result: " + result);

		// Line outage DFactor via DclfAlgoObjectFactory from Groovy
		groovyCode = """
			dclfBranch = senAlgo.getDclfAlgoBranch("Bus13->Bus14(1)");
			outBranch = DclfAlgoObjectFactory.createCaOutageBranch(dclfBranch, ContingencyBranchOutageType.OPEN);	
			lodf = senAlgo.lineOutageDFactor(outBranch, aclfnet.getBranch("Bus9->Bus14(1)"));		
			return 'Line outage DFactor: ' + lodf
		""";
		result = gvyProcessor.evaluate(groovyCode);
		System.out.println("Result: " + result);
    }
}
