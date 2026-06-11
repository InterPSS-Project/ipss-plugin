package org.interpss.plugin.optadj.texas2K;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.definition.MonitoredBranchRecord;
import org.interpss.plugin.contingency.util.ContingencyFileUtil;
import org.interpss.plugin.contingency.util.DclfContingencyHelper;
import org.interpss.plugin.pssl.plugin.IpssAdapter;

import com.interpss.core.DclfAlgoObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.ContingencyBranchOutageType;
import com.interpss.core.contingency.dclf.DclfBranchOutage;
import com.interpss.core.contingency.dclf.DclfOutageBranch;

/** Shared Texas-2K test network setup for optadj regression tests. */
public class Texas2K_TestCaseInfo extends CorePluginTestSetup {

	private static final String TEXAS2K_RAW = TEST_ROOT
			+ "testData/psse/v36/Texas2k/Texas2k_series24_case1_2016summerPeak_v36.RAW";
	private static final String CONTINGENCY_JSON = TEST_ROOT
			+ "testData/psse/v36/Texas2k/2k_contingencies_115kVAbove.json";
	private static final String MONITORED_JSON = TEST_ROOT
			+ "testData/psse/v36/Texas2k/2k_monitored_branches.json";

	public static AclfNetwork createTestCaseNetwork() throws Exception {
		AclfNetwork net = IpssAdapter.importAclfNet(TEXAS2K_RAW)
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_36)
				.load()
				.getImportedObj();

		//net.getBranchList().forEach(branch -> branch.setName(branch.getId()));
		return net;
	}

	public static List<DclfBranchOutage> createContingencyList(ContingencyAnalysisAlgorithm dclfAlgo) throws Exception {
		List<BranchContingencyRecord> contingencies = ContingencyFileUtil
				.importContingenciesFromJson(new File(CONTINGENCY_JSON));
		return new DclfContingencyHelper(dclfAlgo).createDclfContList(contingencies);
	}

	public static Set<String> createMonitoredBranchIds() throws Exception {
		List<MonitoredBranchRecord> monitoredBranches = ContingencyFileUtil
				.importMonitoredBranchRecordsFromJson(new File(MONITORED_JSON));
		return monitoredBranches.stream()
				.map(MonitoredBranchRecord::getBranchId)
				.collect(Collectors.toSet());
	}

	/** N-1 branch-outage contingencies for every non-reference-connected branch. */
	public static List<DclfBranchOutage> createBranchOutageContingencyList(
			AclfNetwork net, ContingencyAnalysisAlgorithm dclfAlgo) {
		List<DclfBranchOutage> contList = new ArrayList<>();
		net.getBranchList().stream()
				.filter(branch -> !((AclfBranch) branch).isConnect2RefBus())
				.forEach(branch -> {
					DclfBranchOutage cont = DclfAlgoObjectFactory.createContingency("contBranch:" + branch.getId());
					DclfOutageBranch outage = DclfAlgoObjectFactory.createCaOutageBranch(
							dclfAlgo.getDclfAlgoBranch(branch.getId()),
							ContingencyBranchOutageType.OPEN);
					cont.setOutageEquip(outage);
					contList.add(cont);
				});
		return contList;
	}
}
