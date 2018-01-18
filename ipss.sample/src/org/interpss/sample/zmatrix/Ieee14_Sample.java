 /*
  * @(#)Ieee14_CASample.java   
  *
  * Copyright (C) 2006-2015 www.interpss.org
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
  * @Date 03/15/2015
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.sample.zmatrix;

import org.interpss.IpssCorePlugin;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.pssl.common.PSSLException;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.simu.IpssDclf;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.contingency.OutageBranch;
import com.interpss.core.dclf.LODFSenAnalysisType;
import com.interpss.core.dclf.common.OutageConnectivityException;
import com.interpss.core.dclf.common.ReferenceBusException;

public class Ieee14_Sample {
	public static void main(String args[]) throws Exception {
		IpssCorePlugin.init();
		
		sample();
	}
	
	public static void sample() throws InterpssException, ReferenceBusException, IpssNumericException, PSSLException  {
		AclfNetwork net = getSampleNet();
	}

	
	public static AclfNetwork getSampleNet() throws InterpssException {
		AclfNetwork net = IpssAdapter.importAclfNet("testData/ieee14.ieee")
				.setFormat(IpssAdapter.FileFormat.IEEECommonFormat)
				.load()
				.getImportedObj();		
		
		// set branch mva rating, since it is not defined in the input file.
		for (AclfBranch branch : net.getBranchList()) {
			branch.setRatingMva1(100.0);
		}
		
		return net;
	}
}

