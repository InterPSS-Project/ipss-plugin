 /*
  * @(#)TestSetupBase.java   
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

package org.interpss.core.dstab.mach;

import org.interpss.CorePluginTestSetup;

import com.interpss.common.msg.IPSSMsgHub;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.spring.CoreCommonSpringFactory;

public class TestSetupBase extends CorePluginTestSetup {
	protected IPSSMsgHub msg;
	protected DStabilityNetwork net = null;

	public TestSetupBase() { 
		msg = CoreCommonSpringFactory.getIpssMsgHub();
	}
}

