package org.interpss.sample.adapter;

import static org.interpss.CorePluginFunction.aclfResultBusStyle;

import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.bpa.BPAAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.IpssCorePlugin;
import org.interpss.mapper.odm.ODMAclfParserMapper;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

public class BPADataImportSample {

	public static void main(String[] args) throws InterpssException {
		IpssCorePlugin.init();
		
		IODMAdapter adapter = new BPAAdapter();
		adapter.parseInputFile("testData/bpa/07c_0615_notBE.dat"); 
		
		AclfModelParser parser=(AclfModelParser) adapter.getModel();
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
			  System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			  return;
	    }
		AclfNetwork net=simuCtx.getAclfNet();
		
		LoadflowAlgorithm  algo=CoreObjectFactory.createLoadflowAlgorithm(net);
		net.accept(algo);
		System.out.println(aclfResultBusStyle.apply(net));
	}
}
