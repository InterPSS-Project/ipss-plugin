package org.interpss.fstate;

import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;

public class IEEE39_RAW_Info_Sample {
	public static AclfNetwork loadIEEE39Raw() throws Exception {
		String PSSE_FILE = "ipss.plugin.core/testData/psse/v30/IEEE39bus_v30.raw";
		AclfNetwork aclfNet = IpssAdapter.importAclfNet(PSSE_FILE)
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_30) 
				.load()
				.getImportedObj();

		addInfo2Network(aclfNet);
		return aclfNet;
	}

	public static void addInfo2Network(AclfNetwork aclfNet) throws Exception {
	    // Clear zero-generation contributions
	    aclfNet.getBusList().forEach(bus -> {
	        if (bus.getGenP() == 0)
	            bus.getContributeGenList().clear();
	    });

	    // PSSE import names devices Gen:1(31) / Load:1(31); plan JSON uses Bus31-G1 / Bus31-L1.
	    applyInterpssDeviceNames(aclfNet);

	    aclfNet.createAclfGenUIDLookupTable(true);
	    aclfNet.createAclfLoadUIDLookupTable(true);
		
		String namePrefix = "";
	    aclfNet.getAclfGenUIDLookupTable().values()
			.forEach(gen -> {
				if (gen.getName().equals(namePrefix + "Bus39-G1")) {
					gen.setPGenLimit(new LimitType(10, 0));
				}
				else if (gen.getName().equals(namePrefix + "Bus38-G1")) {
					gen.setPGenLimit(new LimitType(8.3, 0));
				}
				else
					gen.setPGenLimit(new LimitType(7, 0));
			});

		// set the branch rating.
		aclfNet.getBranchList().stream() 
			.forEach(branch -> {
				AclfBranch aclfBranch = (AclfBranch) branch;
				//System.out.println("Branch: " + aclfBranch.getName() + " " + aclfBranch.getBranchCode());
				//aclfBranch.setName(aclfBranch.getId());
				// Mva1 is used for basecase loading limit
				aclfBranch.setRatingMva1(600.0);
			});
	}

	private static void applyInterpssDeviceNames(AclfNetwork aclfNet) {
		aclfNet.getBusList().forEach(bus -> {
			bus.getContributeGenList().forEach(gen -> {
				if (gen.getName() != null && gen.getName().startsWith("Gen:")) {
					gen.setName(interpssGenName(bus.getId(), gen.getId()));
				}
			});
			bus.getContributeLoadList().forEach(load -> {
				if (load.getName() != null && load.getName().startsWith("Load:")) {
					load.setName(interpssLoadName(bus.getId(), load.getId()));
				}
			});
		});
	}

	private static String interpssGenName(String busId, String machineId) {
		return busId + "-G" + trimMachineId(machineId);
	}

	private static String interpssLoadName(String busId, String machineId) {
		return busId + "-L" + trimMachineId(machineId);
	}

	private static String trimMachineId(String machineId) {
		if (machineId == null || machineId.isBlank()) {
			return "1";
		}
		return machineId.replace("'", "").trim();
	}
}
