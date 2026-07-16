package org.interpss.sample.adapter;

import static org.interpss.CorePluginFunction.aclfResultBusStyle;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.bpa.BPADirectParser;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class BPADataImportSample {

	public static void main(String[] args) throws InterpssException {
		IpssCorePlugin.init();
		
		AclfNetwork net = new BPADirectParser().parse("ipss-plugin/ipss.sample/testData/bpa/07c_0615_notBE.dat");
		
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		net.accept(algo);
		System.out.println(aclfResultBusStyle.apply(net));
	}
}
