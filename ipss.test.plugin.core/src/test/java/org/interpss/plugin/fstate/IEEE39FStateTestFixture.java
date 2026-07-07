package org.interpss.plugin.fstate;

import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.algo.fstate.FStateDclfAlgorithm;
import com.interpss.algo.fstate.datatype.FStateAlgoConfig;
import com.interpss.algo.fstate.plan.model.PlanMaintainModel;
import com.interpss.algo.fstate.util.FStateDclfAlgoHelper;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;

/**
 * Test fixture mirroring {@code org.interpss.fstate.IEEE39_RAW_Info_Sample} with
 * paths relative to the {@code ipss.test.plugin.core} module.
 */
public final class IEEE39FStateTestFixture {

    private static final String IEEE39_RAW =
            "../ipss.plugin.core/testData/psse/v30/IEEE39bus_v30.raw";

    private IEEE39FStateTestFixture() {
    }

    public static AclfNetwork loadIEEE39Raw() throws Exception {
        AclfNetwork aclfNet = IpssAdapter.importAclfNet(IEEE39_RAW)
                .setFormat(IpssAdapter.FileFormat.PSSE)
                .setPsseVersion(IpssAdapter.PsseVersion.PSSE_30)
                .load()
                .getImportedObj();
        addInfo2Network(aclfNet);
        return aclfNet;
    }

    public static FStateDclfAlgorithm runDclfAssessment(PlanMaintainModel model) throws Exception {
        AclfNetwork aclfNet = loadIEEE39Raw();
        FStateAlgoConfig fStateAlgoConfig = new FStateAlgoConfig();
        FStateDclfAlgorithm fsAlgo = new FStateDclfAlgorithm(aclfNet, model, fStateAlgoConfig);
        fsAlgo.buildFStateAlgo();
        new FStateDclfAlgoHelper(fsAlgo).processPlanDataInfo(true);
        fsAlgo.performAssessment(false);
        return fsAlgo;
    }

    static void addInfo2Network(AclfNetwork aclfNet) {
        aclfNet.getBusList().forEach(bus -> {
            if (bus.getGenP() == 0) {
                bus.getContributeGenList().clear();
            }
        });

        applyInterpssDeviceNames(aclfNet);

        aclfNet.createAclfGenUIDLookupTable(true);
        aclfNet.createAclfLoadUIDLookupTable(true);

        aclfNet.getAclfGenUIDLookupTable().values().forEach(gen -> {
            if (gen.getName().equals("Bus39-G1")) {
                gen.setPGenLimit(new LimitType(10, 0));
            } else if (gen.getName().equals("Bus38-G1")) {
                gen.setPGenLimit(new LimitType(8.3, 0));
            } else {
                gen.setPGenLimit(new LimitType(7, 0));
            }
        });

        aclfNet.getBranchList().forEach(branch -> {
            AclfBranch aclfBranch = (AclfBranch) branch;
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
