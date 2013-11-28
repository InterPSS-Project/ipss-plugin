 /*
  * @(#)QAObjectFactory.java   
  *
  * Copyright (C) 2006-2011 www.interpss.com
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
  * @Date 04/15/2009
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.QA.compare.dep;

import org.interpss.QA.rfile.QAFileReader;

import com.interpss.core.aclf.AclfNetwork;

public class QAObjectFactory {
	public static IQAFileProcessor createFileProcessor(AclfNetwork net, QAFileReader.Type type) {
		return createFileProcessor(net, type, false);
	}
	
	public static IQAFileProcessor createFileProcessor(AclfNetwork net, QAFileReader.Type type, boolean netOnly) {
		if (type == QAFileReader.Type.PSSEAclfResult)
			return new DepPSSECompareFileProcessor(net, netOnly);
		else if (type == QAFileReader.Type.BPAAclfResult)
			return new DepPSLFComapreFileProcessor(net, netOnly);
		else if (type == QAFileReader.Type.PSLFAclfResult)
			return new DepPSLFComapreFileProcessor(net, netOnly);
		else
			return null;
		
	}

}
