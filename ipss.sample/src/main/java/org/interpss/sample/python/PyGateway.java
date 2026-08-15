package org.interpss.sample.python;

import org.interpss.CorePluginFunction;
import org.interpss.IpssCorePlugin;
import org.interpss.plugin.pssl.simu.IpssAclf;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;

import py4j.GatewayServer;

import org.interpss.fadapter.ieeecdf.IeeeCDFDirectParser;
public class PyGateway {
	private AclfNetwork net;
	
	public String lf(String filename) {
		String rntStr = "";
		
		IpssCorePlugin.init();
		
		try {
			net = new IeeeCDFDirectParser().parse(filename);
			
		  	IpssAclf.createAclfAlgo(net)
  					.lfMethod(AclfMethodType.NR)
  					.nonDivergent(true)
  					.runLoadflow();	
		  	
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
