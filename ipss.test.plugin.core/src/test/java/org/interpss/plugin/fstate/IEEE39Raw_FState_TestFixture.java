package org.interpss.plugin.fstate;

import org.interpss.fstate.IEEE39_RAW_Info_Sample;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.algo.fstate.FStateDclfAlgorithm;
import com.interpss.algo.fstate.datatype.FStateAlgoConfig;
import com.interpss.algo.fstate.plan.model.PlanMaintainModel;
import com.interpss.algo.fstate.util.FStateDclfAlgoHelper;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.Substation;

/**
 * Test fixture mirroring {@code org.interpss.fstate.IEEE39_RAW_Info_Sample} with
 * paths relative to the {@code ipss.test.plugin.core} module.
 */
public final class IEEE39Raw_FState_TestFixture {

    private static final String IEEE39_RAW =
            "../ipss.plugin.core/testData/psse/v30/IEEE39bus_v30.raw";

    private IEEE39Raw_FState_TestFixture() {
    }

    public static AclfNetwork loadIEEE39Raw() throws Exception {
        AclfNetwork aclfNet = IpssAdapter.importAclfNet(IEEE39_RAW)
                .setFormat(IpssAdapter.FileFormat.PSSE)
                .setPsseVersion(IpssAdapter.PsseVersion.PSSE_30)
                .load()
                .getImportedObj();
        IEEE39_RAW_Info_Sample.addInfo2Network(aclfNet);
        ensureSubstations(aclfNet);
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

    /**
     * PSSE import has no substations; sample enrichment may be absent when tests run
     * against a stale {@code ipss.plugin.core} jar built before substation setup was added.
     */
    static void ensureSubstations(AclfNetwork aclfNet) {
        if (!aclfNet.getSubstationMap().isEmpty()) {
            return;
        }
        String[][] substationBuses = {
                {"Sub01", "Bus1", "Bus39"},
                {"Sub02", "Bus2", "Bus30"},
                {"Sub03", "Bus3"},
                {"Sub04", "Bus4"},
                {"Sub05", "Bus5"},
                {"Sub06", "Bus6", "Bus31"},
                {"Sub07", "Bus7"},
                {"Sub08", "Bus8"},
                {"Sub09", "Bus9"},
                {"Sub10", "Bus10", "Bus32"},
                {"Sub11", "Bus11"},
                {"Sub12", "Bus12"},
                {"Sub13", "Bus13"},
                {"Sub14", "Bus14"},
                {"Sub15", "Bus15"},
                {"Sub16", "Bus16"},
                {"Sub17", "Bus17"},
                {"Sub18", "Bus18"},
                {"Sub19", "Bus19", "Bus33"},
                {"Sub20", "Bus20", "Bus34"},
                {"Sub21", "Bus21"},
                {"Sub22", "Bus22", "Bus35"},
                {"Sub23", "Bus23", "Bus36"},
                {"Sub24", "Bus24"},
                {"Sub25", "Bus25", "Bus37"},
                {"Sub26", "Bus26"},
                {"Sub27", "Bus27"},
                {"Sub28", "Bus28"},
                {"Sub29", "Bus29", "Bus38"},
        };
        for (String[] entry : substationBuses) {
            Substation substation = CoreObjectFactory.createSubstation(entry[0], aclfNet);
            double maxVoltage = 0.0;
            for (int i = 1; i < entry.length; i++) {
                var bus = aclfNet.getBus(entry[i]);
                bus.setSubstation(substation);
                maxVoltage = Math.max(maxVoltage, bus.getBaseVoltage());
            }
            substation.setVoltLevel(maxVoltage);
        }
    }
}
