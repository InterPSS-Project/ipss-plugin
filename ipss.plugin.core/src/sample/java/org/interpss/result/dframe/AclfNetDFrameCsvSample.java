package org.interpss.result.dframe;

import org.dflib.DataFrame;
import org.dflib.Printers;
import org.dflib.csv.Csv;
import org.dflib.json.Json;
import org.interpss.CorePluginFactory;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.result.dframe.AclfNetDFrameAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class AclfNetDFrameCsvSample {
	private static final String TEST_ROOT = "ipss.plugin.core/";
    
    public static void main(String[] args) throws InterpssException {
		// load the IEEE-14 Bus system
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load(TEST_ROOT + "testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
	  	
	  	AclfNetDFrameAdapter dfAdapter = new AclfNetDFrameAdapter();
	  	dfAdapter.adapt(net);
	  	
    	DataFrame dfBus = dfAdapter.getDfBus();    	

		// print # of rows in dfBus
		System.out.println("Number of rows in dfBus: " + dfBus.height());
		
		// write the dfBus to a csv file
		Csv.saver().save(dfBus, TEST_ROOT + "output/Ieee14Bus_bus.csv");
		System.out.println("Save to csv file: output/Ieee14Bus_bus.csv");
    }
}