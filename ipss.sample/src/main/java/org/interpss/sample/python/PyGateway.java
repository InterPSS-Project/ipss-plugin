package org.interpss.sample.python;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.IEEECommonFormat;

import org.interpss.CorePluginFunction;
import org.interpss.IpssCorePlugin;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

import py4j.GatewayServer;

public class PyGateway {
	private AclfNetwork net;
	
	public String lf(String filename) {
		String rntStr = "";
		
		IpssCorePlugin.init();
		
		try {
			net = IpssAdapter.importAclfNet(filename)
					.setFormat(IEEECommonFormat)
					.load()
					.getImportedObj();
		  	
		  	// create the default loadflow algorithm
		  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);

		  	// use the loadflow algorithm to perform loadflow calculation
		  	algo.loadflow();
		  	
		  	rntStr = CorePluginFunction.aclfResultSummary.apply(net).toString();
		} catch ( InterpssException e) {
			e.printStackTrace();
		}
		
		return rntStr;
	}
	
	public static void main(String[] args) {
		PyGateway app = new PyGateway();
		// app is now the gateway.entry_point
		GatewayServer server = new GatewayServer(app);
		System.out.println("Starting Py4J Gateway ...");
		server.start();
	}	

}
