 /*
  * @(#)QABranchRec.java   
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
package org.interpss.plugin.QA.result;

@Deprecated
public abstract class QABranchRec extends QABaseRec {
	public String fromId;
	public String toId;
	public String cirId;
	
	public QABranchRec(String fId, String tId, String cirId) {
		this.fromId = fId;
		this.toId = tId;
		this.cirId = cirId;
	}
}
