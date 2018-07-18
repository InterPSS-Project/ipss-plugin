package com.interpss.pssl.test;


import org.interpss.IpssCorePlugin;
import org.interpss.pssl.simu.BaseDSL;
import org.junit.BeforeClass;

import com.interpss.CoreCommonFactory;
import com.interpss.common.msg.IPSSMsgHub;

public class BaseTestSetup {
	protected static IPSSMsgHub msg = null;
	
	@BeforeClass
	public static void setSpringAppCtx() {
		IpssCorePlugin.init();
		msg = CoreCommonFactory.getIpssMsgHub();
		BaseDSL.setMsgHub(msg);
	}
}

