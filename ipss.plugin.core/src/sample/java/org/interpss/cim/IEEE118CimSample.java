package org.interpss.cim;

import org.interpss.fadapter.IpssFileAdapter;

import org.interpss.CorePluginFactory;
import org.interpss.fadapter.cim.CIMDirectParser;
import org.interpss.util.AclfNetJsonComparator;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class IEEE118CimSample {

	private static final String TD = "testData/adpter/cim/";
	private static final String CIM_FILE = TD + "IEEE118_CIM.xml";
	private static final String MATPOWER_FILE = TD + "IEEE118.m";

	public static void main(String args[]) throws Exception {
		AclfNetwork cimNet = new CIMDirectParser().parse(CIM_FILE);

		AclfNetwork matNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.MATPOWER)
				.load(MATPOWER_FILE)
				.getAclfNet();

		System.out .println("CIM Network: " + cimNet.getNoBus() + " buses, " + cimNet.getNoBranch() + " branches");
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(cimNet);
		algo.loadflow();

		System.out.println("MatPower Network: " + matNet.getNoBus() + " buses, " + matNet.getNoBranch() + " branches");
		algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(matNet);
		algo.loadflow();
	}
}
