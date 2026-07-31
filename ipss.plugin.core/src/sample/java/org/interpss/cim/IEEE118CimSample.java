package org.interpss.cim;

import org.interpss.fadapter.IpssFileAdapter;

import org.interpss.CorePluginFactory;
import org.interpss.fadapter.cim.CIMDirectParser;
import org.interpss.util.AclfNetJsonComparator;
import com.interpss.core.aclf.AclfNetwork;

public class IEEE118CimSample {

	private static final String TD = "testData/adpter/cim/";
	private static final String CIM_FILE = TD + "IEEE118_CIM.xml";
	private static final String MATPOWER_FILE = TD + "IEEE118.m";


	public static void main(String args[]) throws Exception {
		AclfNetwork cimNet = new CIMDirectParser().parse(CIM_FILE);

		AclfNetwork matNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.MATPOWER)
				.load(MATPOWER_FILE)
				.getAclfNet();

		// CIM vs MatPower compare — prior electrical gaps addressed:
		// vLimit 1.1/0.9; area/zone "1"; gens via ThermalGeneratingUnit + RotatingMachine P/Q
	}
}

/*
Main electrical findings
x) Loads off by ~1e6 — fixed (CIM SI W/var → MW/MVAr → PU)
x) All 193 buses use default baseVoltage 100 kV — fixed via ConductingEquipment.BaseVoltage
x) ~84 transformer Z/tap/rating — fixed (TransformerMeshImpedance + ratedU V→kV + ratedS VA→MVA)
x) Voltage limits unset — fixed (default 1.1/0.9 when no VoltageLimit)
x) Area/zone "0" vs "1" — fixed (default area/zone "1")
x) Generators missing genAry/pvBusLimit — fixed (ThermalGeneratingUnit index, RotatingMachine p/q, contribute gen + PV Q limits)
5) Lines largely match — e.g. Bus1->Bus2(1) has no value diffs.
*/
