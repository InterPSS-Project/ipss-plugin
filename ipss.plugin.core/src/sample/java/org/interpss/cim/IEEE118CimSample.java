package org.interpss.cim;

import org.interpss.fadapter.cim.CIMDirectParser;
import org.interpss.util.AclfNetJsonComparator;
import com.interpss.core.aclf.AclfNetwork;

public class IEEE118CimSample {

	private static final String TD = "testData/adpter/cim/";
	private static final String CIM_FILE = TD + "IEEE118_CIM.xml";

	
	public static void main(String args[]) throws Exception {
		AclfNetwork cimNet = new CIMDirectParser().parse(CIM_FILE);
	}
}

/*
Main electrical findings
1) Loads off by ~1e6 — e.g. Bus1 loadP 510000.0 vs 0.51 (CIM likely leaving W/var instead of PU).
2) All 193 buses use default baseVoltage 100 kV — MatPower has 138/345/13.8 kV correctly.
3)Voltage limits unset on CIM — vLimit 0/0 vs MatPower 1.1/0.9.
4) Area/zone — CIM "0" vs MatPower "1".
5) Lines largely match — e.g. Bus1->Bus2(1) has no value diffs.
6) ~84 transformer branches have bad Z — CIM z.im ≈ 0 (or tiny) vs MatPower expected PU (same count as 2W xfrs); ratings also 0 vs MatPower MVA.
7) Generators — many LV buses missing genAry / pvBusLimit on CIM; genCode / genP/genQ differ where present.
*/
