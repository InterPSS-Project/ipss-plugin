package org.interpss.support;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.pssl.plugin.IpssAdapter;

import com.interpss.core.aclf.AclfNetwork;

public class Ipss_plugin_issue_18 {
	public static void main(String args[]) throws Exception {
		IpssCorePlugin.init();
		
		AclfNetwork net = IpssAdapter.importAclfNet("testData/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();		
		
		System.out.println("Before : " + net.getBus("Bus12").getLoadPQ());
		
		net.getBus("Bus12").setLoadPQ(new Complex(0.8, 0.8));
	
		System.out.println("After : " + net.getBus("Bus12").getLoadPQ());
	}
}

