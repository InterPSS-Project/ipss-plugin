 /*
  * @(#) ISONE_CompreResult.java   
  *
  * Copyright (C) 2008 www.interpss.org
  *
  * This program is free software; you can redistribute it and/or
  * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
  * as published by the Free Software Foundation; either version 2.1
  * of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * @Author Mike Zhou
  * @Version 1.0
  * @Date 02/15/2008
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.QA.test.compare;

import java.util.List;
import java.util.logging.Level;

import org.ieee.odm.common.ODMLogger;
import org.interpss.IpssCorePlugin;
import org.interpss.QA.compare.impl.PWDResultComparator;
import org.interpss.QA.rfile.aclf.PWDResultFileProcessor;
import org.interpss.QA.test.QATestSetup;
import org.interpss.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.CoreObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;

public class ISONE_CompreResult extends QATestSetup {
	@Test
	public void loadPWDResult() throws Exception {
		IpssCorePlugin.init();
		ODMLogger.getLogger().setLevel(Level.WARNING);

		AclfNetwork net = IpssAdapter.importAclfNet("testData/pwd/neiso_test.aux")
					.setFormat(IpssAdapter.FileFormat.PWD)
					.load()
					.getImportedObj();
		
		net.checkData(CoreObjectFactory.createDefultDataCheckConfiguration());
 		
		PWDResultFileProcessor proc = new PWDResultFileProcessor();
		proc.load("testData/pwd/pwd_bus.csv", "testData/pwd/pwd_branch.csv");
		
		PWDResultComparator comp = new PWDResultComparator(net, proc.getQAResultSet());

		// compare network element
		comp.compareNetElement();
		if (comp.getErrMsgList().size() > 0) {
			System.out.println(comp.getErrMsgList().toString());
			//FileUtil.writeText2File("output/neiso/out.txt", comp.getErrMsgList().toString());
		}	
		
		AclfBus bus = comp.getLargestMisBus(0.0005);
		
		// output mis info and InterPSS object model bus LF info
		System.out.println(comp.busInfo(bus));	
		
		// flow Bus765->Bus768 0 + j-149.5 MVA
		
		List<AclfBus> busList = comp.getLargeMisBusList(10, 0.0005);
		System.out.println("Bus list size: " + busList.size());
		
		/*
		for (AclfBus b : busList) {
			System.out.println(comp.busInfo(b));	
		}
		*/
	}
}

