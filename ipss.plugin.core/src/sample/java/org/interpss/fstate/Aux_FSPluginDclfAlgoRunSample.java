package org.interpss.fstate;

import java.nio.file.Path;

import org.interpss.plugin.fstate.aux_fmt.Aux2PlanMaintainAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.algo.fstate.FStateDclfAlgorithm;
import com.interpss.algo.fstate.datatype.FStateAlgoConfig;
import com.interpss.algo.fstate.plan.model.PlanMaintainModel;
import com.interpss.algo.fstate.result.adapter.dclf.FStateDclfBranchInfoAdapter;
import com.interpss.algo.fstate.result.adapter.dclf.FStateDclfBusInfoAdapter;
import com.interpss.algo.fstate.result.adapter.dclf.FStateDclfSubStationInfoAdapter;
import com.interpss.algo.fstate.util.FStateDclfAlgoHelper;
import com.interpss.core.aclf.AclfNetwork;

public class Aux_FSPluginDclfAlgoRunSample {
	public static void main(String[] args) throws Exception {
		AclfNetwork aclfNet = IEEE39_RAW_Info_Sample.loadIEEE39Raw();

		Path IEEE39_PW_DIR = Path.of("ipss.plugin.core/testData/powerworld/ieee39");
		PlanMaintainModel model = Aux2PlanMaintainAdapter.load(IEEE39_PW_DIR);

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

		//System.out.println(fsAlgo.getSimuSummaryInfoMap().toString());
		/* 
		var netSeries = new FStateDclfNetInfoAdapter().adapt(fsAlgo);
		System.out.println("Net info series:");
		netSeries.forEach((tpoint, rec) -> System.out.printf("  T%d: %s%n", tpoint, rec.toString()));
		*/
		String sampleBranch = "Bus22_to_Bus35_cirId_1";
		var branchSeries = new FStateDclfBranchInfoAdapter(sampleBranch).adapt(fsAlgo);
		System.out.println("Branch flow series (" + sampleBranch + ", MW):");
		branchSeries.forEach((tpoint, rec) -> System.out.printf("  T%d: %.4f%n", tpoint, rec.getDclfFlow()));

		String sampleBus = "LBUS21";
		var busSeries = new FStateDclfBusInfoAdapter(sampleBus).adapt(fsAlgo);
		System.out.println("Bus angle/power series (" + sampleBus + "):");
		busSeries.forEach((tpoint, rec) -> System.out.printf("  T%d: angle=%.4f power=%.4f%n",
				tpoint, rec.getBusAngle(), rec.getBusPower()));

		String sampleSub = "Sub01";
		var subSeries = new FStateDclfSubStationInfoAdapter(sampleSub).adapt(fsAlgo);
		System.out.println("Sub station info series (" + sampleSub + "):");
		subSeries.forEach((tpoint, rec) -> System.out.printf("  T%d: %s%n", tpoint, rec.toString()));
	}
}
