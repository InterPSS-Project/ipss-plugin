package org.interpss.plugin.piecewise;

import org.interpss.IpssCorePlugin;
import org.junit.jupiter.api.BeforeAll;

import com.interpss.common.CoreCommonFactory;

public class PiecewiseAlgoTestSetup {

	@BeforeAll  
	public static void initTestEnv() {
		IpssCorePlugin.init();
	}
}

