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
    
    public static void main(String[] args) throws InterpssException {
		// load the IEEE-14 Bus system
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
	  	
	  	AclfNetDFrameAdapter dfAdapter = new AclfNetDFrameAdapter();
	  	dfAdapter.adapt(net);
	  	
    	DataFrame dfBus = dfAdapter.getDfBus();    	
		
		// write the dfBus to a csv file
		Csv.saver().save(dfBus, "output/Ieee14Bus_bus.csv");
		System.out.println("Save to csv file: output/Ieee14Bus_bus.csv");
    }
}