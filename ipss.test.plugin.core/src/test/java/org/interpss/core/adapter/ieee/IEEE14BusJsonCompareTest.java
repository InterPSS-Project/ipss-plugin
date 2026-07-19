package org.interpss.core.adapter.ieee;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.util.AclfNetJsonComparator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.LoadflowAlgorithm;

public class IEEE14BusJsonCompareTest extends CorePluginTestSetup {
	@Test 
	public void testCase1() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14_comma.ieee")
				.getAclfNet();	
		
		String jsonFile = "testData/json/ieee14Bus.json";
		
		// output aclfNet state to json file
		//FileUtil.writeText2File(jsonFile, new AclfNetworkState(aclfNet).toString());
		
		// compare the json file with the aclfNet
		assertTrue(new AclfNetJsonComparator("IEEE Common format ieee14Bus",
				path -> !path.matches("/busAry\\[[^\\]]+\\]/desc")
						&& !path.endsWith("/extUID"))
							.compareJson(aclfNet, new File(jsonFile)));
	}
}

