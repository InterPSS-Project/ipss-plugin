package org.interpss.fstate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.algo.fstate.FStateDclfAlgorithm;
import com.interpss.algo.fstate.datatype.FStateAlgoConfig;
import com.interpss.algo.fstate.plan.PlanMaintainModelBuilder;
import com.interpss.algo.fstate.plan.model.PlanMaintainModel;
import com.interpss.algo.fstate.result.adapter.dclf.FStateDclfBranchInfoAdapter;
import com.interpss.algo.fstate.result.adapter.dclf.FStateDclfBusInfoAdapter;
import com.interpss.algo.fstate.result.adapter.dclf.FStateDclfSubStationInfoAdapter;
import com.interpss.algo.fstate.util.FStateDclfAlgoHelper;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;

public class FSPluginDclfAlgoRunSample {
	public static void main(String[] args) throws Exception {
		String PSSE_FILE = "ipss.plugin.core/testData/psse/v30/IEEE39bus_v30.raw";
		AclfNetwork aclfNet = IpssAdapter.importAclfNet(PSSE_FILE)
				.setFormat(IpssAdapter.FileFormat.PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_30) 
				.load()
				.getImportedObj();

		addInfo2Network(aclfNet);

		String JSON_FILE = "ipss.plugin.core/testData/psse/v30/ieee39_dayahead_plan_maintain_plan.json";
		Path path = Path.of(JSON_FILE);
		PlanMaintainModel model = PlanMaintainModelBuilder.fromJson(Files.readString(path, StandardCharsets.UTF_8));

		FStateAlgoConfig fStateAlgoConfig = new FStateAlgoConfig();
		FStateDclfAlgorithm fsAlgo = new FStateDclfAlgorithm(aclfNet, model, fStateAlgoConfig);
		fsAlgo.buildFStateAlgo();

		new FStateDclfAlgoHelper(fsAlgo).processPlanDataInfo(true);
		
		fsAlgo.performAssessment(false);

		int numAlgos = fsAlgo.getMStateDclfAlgo().getMsDclfAlgoList().size();
		int numNets = fsAlgo.getMStateDclfAlgo().getMsAclfNetList().size();
		var dclfAlgo = fsAlgo.getDclfAlgo(0);
		double refBusP = dclfAlgo.getBusPower(dclfAlgo.getNetwork().getRefBusId());
		System.out.println(" DCLF: periods=" + model.getNumsOfPeriods()
				+ ", clonedNets=" + numNets + ", dclfAlgos=" + numAlgos
				+ ", T0 refBusP(pu)=" + String.format("%.4f", refBusP));

		System.out.println(fsAlgo.getSimuSummaryInfoMap().toString());
		/* 
		var netSeries = new FStateDclfNetInfoAdapter().adapt(fsAlgo);
		System.out.println("Net info series:");
		netSeries.forEach((tpoint, rec) -> System.out.printf("  T%d: %s%n", tpoint, rec.toString()));
		*/
		String sampleBranch = "Bus22_to_Bus35_cirId_1";
		var branchSeries = new FStateDclfBranchInfoAdapter(sampleBranch).adapt(fsAlgo);
		System.out.println("Branch flow series (" + sampleBranch + ", MW):");
		branchSeries.forEach((tpoint, rec) -> System.out.printf("  T%d: %.4f%n", tpoint, rec.getDclfFlow()));

		String sampleBus = "BUS-21  100";
		var busSeries = new FStateDclfBusInfoAdapter(sampleBus).adapt(fsAlgo);
		System.out.println("Bus angle/power series (" + sampleBus + "):");
		busSeries.forEach((tpoint, rec) -> System.out.printf("  T%d: angle=%.4f power=%.4f%n",
				tpoint, rec.getBusAngle(), rec.getBusPower()));

		String sampleSub = "Sub01";
		var subSeries = new FStateDclfSubStationInfoAdapter(sampleSub).adapt(fsAlgo);
		System.out.println("Sub station info series (" + sampleSub + "):");
		subSeries.forEach((tpoint, rec) -> System.out.printf("  T%d: %s%n", tpoint, rec.toString()));
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
