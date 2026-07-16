package org.interpss.core.adapter.bpa;

import static org.interpss.CorePluginFunction.aclfResultBusStyle;

import org.interpss.core.dstab.DStabTestSetupBase;
import org.interpss.fadapter.bpa.BPADirectParser;
import org.junit.jupiter.api.Test;

import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class Bpa07c_0615_Test extends DStabTestSetupBase {
	@Test
	public void sys2011_lfTestCase() throws Exception {
		AclfNetwork net = new BPADirectParser().parse("testData/adpter/bpa/07c_0615_notBE.dat");
		
		LoadflowAlgorithm  algo=LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		net.accept(algo);
		System.out.println(aclfResultBusStyle.apply(net));
	}
}
