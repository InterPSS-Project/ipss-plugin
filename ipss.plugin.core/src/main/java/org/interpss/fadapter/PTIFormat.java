/*
  * @(#)PTIFormat.java   
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

public class PTIFormat extends IpssFileAdapterBase {
	public PTIFormat(IPSSMsgHub msgHub) {
		super(msgHub, ODMFileFormatEnum.PsseV30);
	}

	public PTIFormat(IpssFileAdapter.Version v, IPSSMsgHub msgHub) {
		super(msgHub, mapVersionToFormat(v));
	}
	
	private static ODMFileFormatEnum mapVersionToFormat(IpssFileAdapter.Version v) {
		switch(v) {
			case PSSE_26: return ODMFileFormatEnum.PsseV26;
			case PSSE_29: return ODMFileFormatEnum.PsseV29;
			case PSSE_30: return ODMFileFormatEnum.PsseV30;
			case PSSE_31: return ODMFileFormatEnum.PsseV31;
			case PSSE_32: return ODMFileFormatEnum.PsseV32;
			case PSSE_33: return ODMFileFormatEnum.PsseV33;
			case PSSE_34: return ODMFileFormatEnum.PsseV34;
			case PSSE_35: return ODMFileFormatEnum.PsseV35;
			case PSSE_36: return ODMFileFormatEnum.PsseV36;
			default: return ODMFileFormatEnum.PsseV30; // Default to V30 if version not recognized
		}
	}
}