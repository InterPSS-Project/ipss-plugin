package org.interpss.sample.aclf;

import java.io.File;
import java.util.logging.Level;

import org.interpss.IpssCorePlugin;
import org.interpss.util.FileUtil;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class LoadIpssAclfNetObject {
	@Test
	public void run3WXfrOffCase() throws Exception {
		IpssCorePlugin.init();
		
		byte[] fileContent = FileUtil.readFile(new File("testData/wu072011_network.xml"));
		//System.out.println(new String(fileContent));

		AclfNetwork net = CoreObjectFactory.createAclfNetwork(new String(fileContent));
	  	LoadflowAlgorithm algo = CoreObjectFactory.createLoadflowAlgorithm(net);
	  	IpssLogger.getLogger().setLevel(Level.INFO);
	  	//System.out.println(net.net2String());
	  	algo.loadflow();
	  	//System.out.println(net.net2String());
	}			
}
