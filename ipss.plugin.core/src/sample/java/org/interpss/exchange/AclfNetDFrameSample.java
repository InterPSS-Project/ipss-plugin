package org.interpss.exchange;

import org.dflib.DataFrame;
import org.dflib.Printers;
import org.interpss.CorePluginFactory;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.exchange.dframe.AclfBranchDFrameAdapter;
import org.interpss.plugin.exchange.dframe.AclfBusDFrameAdapter;
import org.interpss.plugin.exchange.dframe.AclfGenDFrameAdapter;
import org.interpss.plugin.exchange.dframe.AclfLoadDFrameAdapter;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

public class AclfNetDFrameSample {
    
    public static void main(String[] args) throws InterpssException {
		// load the IEEE-14 Bus system
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	  	algo.loadflow();
	  	
    	// 2. Adapt the AclfNetwork bus data to DataFrame
    	DataFrame dfBus = new AclfBusDFrameAdapter().adapt(net);    	
        System.out.println("\n--- Bus Data ---");
        Printers.tabular.print(dfBus);
        
    	// 3. Extend the DataFrame with new calculated columns
	  	double baseMva = net.getBaseMva();
	  	
	  	dfBus.forEach(row -> {
	  		double nomVoltKv = row.getDouble("NomVolt") * 0.001;
	  		double voltAngDeg = Math.toDegrees(row.getDouble("VoltAng"));
	  		double genMw = row.getDouble("GenP") * baseMva;
	  		double genMvar = row.getDouble("GenQ") * baseMva;
	  		double loadMw = row.getDouble("LoadP") * baseMva;
	  		double loadMvar = row.getDouble("LoadQ") * baseMva;
	  		System.out.println("Bus ID: " + row.get("ID") + 
	  				", NomVoltKv: " + nomVoltKv + 
	  				", VoltAngDeg: " + voltAngDeg +
	  				", GenMw: " + genMw +
	  				", GenMvar: " + genMvar +
	  				", LoadMw: " + loadMw +
	  				", LoadMvar: " + loadMvar);
	  	});

        System.out.println("\n--- Bus Data ---");
        Printers.tabular.print(dfBus);
        
       	// 2. Adapt the AclfNetwork bus data to DataFrame
    	DataFrame dfGen = new AclfGenDFrameAdapter().adapt(net);   
    	
    	System.out.println("\n--- Gen Data ---");
		Printers.tabular.print(dfGen);
		
	   	// 2. Adapt the AclfNetwork bus data to DataFrame
		DataFrame dfLoad = new AclfLoadDFrameAdapter().adapt(net);   
		
		System.out.println("\n--- Load Data ---");	
		Printers.tabular.print(dfLoad);
		
		// 2. Adapt the AclfNetwork branch data to DataFrame
		DataFrame dfBranch = new AclfBranchDFrameAdapter().adapt(net);    	
		System.out.println("\n--- Branch Data ---");
		Printers.tabular.print(dfBranch);
			
		dfBranch.forEach(row -> {
			AclfBranchCode code = row.get("BranchCode", AclfBranchCode.class);
			String deviceType = code == AclfBranchCode.XFORMER ? "Transformer" :
					code == AclfBranchCode.PS_XFORMER ? "PSTransformer" :
						code == AclfBranchCode.LINE ? "Line" : code.toString();
			double flowFromSideMva = row.getDouble("FlowFromSide") * baseMva;
			double loadingPct = 0.0;
			if (row.getDouble("LimMvaA") != 0.0)
				loadingPct = (flowFromSideMva / row.getDouble("LimMvaA")) * 100.0;
			System.out.println("Device Type: " + deviceType + 
					", FlowFromSideMva: " + flowFromSideMva +
					", LoadingPct: " + loadingPct + "%");
		});
    }
}