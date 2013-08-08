 /*
  * @(#)IeeeCommonFormat.java   
  *
  * Copyright (C) 2006 www.interpss.org
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
  * @Date 09/15/2006
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.fadapter;

import org.ieee.odm.ODMFileFormatEnum;
import org.interpss.fadapter.impl.IpssFileAdapterBase;

import com.interpss.common.msg.IPSSMsgHub;

/**
 *  Custom input file adapter for IEEE Common Format. It loads a data file in the format and create an
 *  AclfAdjNetwork object. The data fields could be positional or separeted by comma  
 */

public class IeeeCDFFormat extends IpssFileAdapterBase {
	
	public IeeeCDFFormat(IPSSMsgHub msgHub) {
		super(msgHub, ODMFileFormatEnum.IeeeCDF);
	}
}