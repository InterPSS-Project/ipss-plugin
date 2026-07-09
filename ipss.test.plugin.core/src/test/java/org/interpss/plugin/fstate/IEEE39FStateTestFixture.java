package org.interpss.plugin.fstate;

import org.interpss.fstate.IEEE39_RAW_Info_Sample;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.algo.fstate.FStateDclfAlgorithm;
import com.interpss.algo.fstate.datatype.FStateAlgoConfig;
import com.interpss.algo.fstate.plan.model.PlanMaintainModel;
import com.interpss.algo.fstate.util.FStateDclfAlgoHelper;
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
        IEEE39_RAW_Info_Sample.addInfo2Network(aclfNet);
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
}
