package org.interpss.threePhase.qa;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;

import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.acsc.PhaseCode;
import com.interpss.core.net.Branch;

public final class OpenDssDataQaUtils {

	private OpenDssDataQaUtils() {
	}

	public static List<VoltageReference> readVoltageReferences(Class<?> resourceContext,
			String resourcePath) throws IOException {
		InputStream stream = resourceContext.getClassLoader().getResourceAsStream(resourcePath);
		if(stream == null) {
			throw new IllegalArgumentException("Missing reference resource: " + resourcePath);
		}
		List<VoltageReference> references = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line = reader.readLine();
			if(!"case,bus,phase,vmag_pu,angle_deg".equals(line)) {
				throw new IllegalArgumentException("Unexpected reference CSV header: " + line);
			}
			while ((line = reader.readLine()) != null) {
				String[] fields = line.split(",");
				if(fields.length != 5) {
					throw new IllegalArgumentException("Malformed reference CSV line: " + line);
				}
				references.add(new VoltageReference(
						fields[0],
						fields[1].toLowerCase(),
						Integer.parseInt(fields[2]),
						Double.parseDouble(fields[3]),
						Double.parseDouble(fields[4])));
			}
		}
		return references;
	}

	public static ComparisonResult compareVoltages(DStabNetwork3Phase distNet,
			List<VoltageReference> references) {
		return compareVoltages(distNet, references, null, 0);
	}

	public static ComparisonResult compareVoltages(DStabNetwork3Phase distNet,
			List<VoltageReference> references, String angleReferenceBus, int angleReferencePhase) {
		double maxMagError = 0.0;
		double maxAngleError = 0.0;
		String maxMagLabel = "";
		String maxAngleLabel = "";
		double angleOffsetDeg = angleReferenceBus == null ? 0.0
				: referenceAngleOffsetDeg(distNet, references, angleReferenceBus, angleReferencePhase);

		for(VoltageReference reference : references) {
			DStab3PBus bus = distNet.getBus(reference.bus);
			if(bus == null) {
				throw new IllegalArgumentException("Missing parsed bus: " + reference.bus);
			}
			Complex voltage = phaseVoltage(bus.get3PhaseVotlages(), reference.phase);
			double magError = Math.abs(voltage.abs() - reference.vmagPu);
			double angleError = Math.abs(wrappedAngleDeg(
					Math.toDegrees(voltage.getArgument()) + angleOffsetDeg - reference.angleDeg));
			if(magError > maxMagError) {
				maxMagError = magError;
				maxMagLabel = reference.label();
			}
			if(angleError > maxAngleError) {
				maxAngleError = angleError;
				maxAngleLabel = reference.label();
			}
		}
		return new ComparisonResult(maxMagError, maxMagLabel, maxAngleError, maxAngleLabel);
	}

	public static String voltageDepthCsv(DStabNetwork3Phase distNet, List<VoltageReference> references) {
		StringBuilder builder = new StringBuilder();
		builder.append("case,bus,phase,depth,interpss_vmag,dss_vmag,dv_pu,abs_dv_pu,interpss_angle_deg,dss_angle_deg,dangle_deg\n");
		Map<String, Integer> depths = new HashMap<>();
		for(VoltageReference reference : references) {
			depths.computeIfAbsent(reference.bus, busId -> {
				DStab3PBus bus = distNet.getBus(busId);
				if(bus == null) {
					return -1;
				}
				if("sourcebus".equalsIgnoreCase(busId)) {
					return 0;
				}
				try {
					return sourceToTargetPath(distNet, busId).size();
				} catch(IllegalStateException e) {
					return -1;
				}
			});
		}
		for(VoltageReference reference : references) {
			DStab3PBus bus = distNet.getBus(reference.bus);
			if(bus == null) {
				continue;
			}
			Complex voltage = phaseVoltage(bus.get3PhaseVotlages(), reference.phase);
			double interpssAngle = Math.toDegrees(voltage.getArgument());
			double dv = voltage.abs() - reference.vmagPu;
			builder.append(csv(reference.caseName)).append(",")
					.append(csv(reference.bus)).append(",")
					.append(reference.phase).append(",")
					.append(depths.getOrDefault(reference.bus, -1)).append(",")
					.append(String.format("%.12g", voltage.abs())).append(",")
					.append(String.format("%.12g", reference.vmagPu)).append(",")
					.append(String.format("%.12g", dv)).append(",")
					.append(String.format("%.12g", Math.abs(dv))).append(",")
					.append(String.format("%.12g", interpssAngle)).append(",")
					.append(String.format("%.12g", reference.angleDeg)).append(",")
					.append(String.format("%.12g", wrappedAngleDeg(interpssAngle - reference.angleDeg)))
					.append("\n");
		}
		return builder.toString();
	}

	public static void deactivateZeroBaseIslands(DStabNetwork3Phase distNet) {
		for(BaseAclfBus<?, ?> bus : distNet.getBusList()) {
			if(bus.isActive() && bus.getBaseVoltage() <= 0.0) {
				bus.setStatus(false);
			}
		}
		for(Branch branch : distNet.getBranchList()) {
			if(branch.isActive() && (!branch.getFromBus().isActive() || !branch.getToBus().isActive())) {
				branch.setStatus(false);
			}
		}
	}

	public static IslandDeactivationResult deactivateFloatingPhaseComponentBuses(DStabNetwork3Phase distNet,
			boolean loadedOnly) {
		YMatrixAudit audit = yMatrixAudit(distNet);
		Set<String> busIdsToDeactivate = new HashSet<>();
		for(ComponentAudit component : audit.components) {
			if(component.hasSwing || (loadedOnly && !component.hasLoad())) {
				continue;
			}
			for(int node : component.component) {
				busIdsToDeactivate.add(busBySortNumber(distNet, node / 3).getId());
			}
		}

		int inactiveBusCount = 0;
		int inactiveBranchCount = 0;
		for(String busId : busIdsToDeactivate) {
			DStab3PBus bus = distNet.getBus(busId);
			if(bus != null && bus.isActive()) {
				bus.setStatus(false);
				inactiveBusCount++;
			}
		}
		for(Branch branch : distNet.getBranchList()) {
			if(branch.isActive() && (!branch.getFromBus().isActive() || !branch.getToBus().isActive())) {
				branch.setStatus(false);
				inactiveBranchCount++;
			}
		}
		return new IslandDeactivationResult(inactiveBusCount, inactiveBranchCount, busIdsToDeactivate);
	}

	public static List<VoltageReference> activeReferences(DStabNetwork3Phase distNet,
			List<VoltageReference> references) {
		return references.stream()
				.filter(reference -> {
					DStab3PBus bus = distNet.getBus(reference.bus);
					return bus != null && bus.isActive();
				})
				.toList();
	}

	public static YMatrixAudit yMatrixAudit(DStabNetwork3Phase distNet) {
		List<List<Integer>> graph = phaseConnectivityGraph(distNet);
		List<ComponentAudit> audits = phaseComponentAudits(distNet, graph);
		audits.sort(Comparator
				.comparing(ComponentAudit::hasSwing)
				.thenComparing(ComponentAudit::hasLoad).reversed()
				.thenComparingDouble(ComponentAudit::minDiagAbs));

		long activeTransformerCount = 0;
		long explicitYTransformerCount = 0;
		for(Object branchObj : distNet.getBranchList()) {
			DStab3PBranch branch = (DStab3PBranch) branchObj;
			if(branch.isActive() && branch.isXfr()) {
				activeTransformerCount++;
				if(branch.hasExplicitYabc()) {
					explicitYTransformerCount++;
				}
			}
		}
		return new YMatrixAudit(distNet.getNoBus(), distNet.getNoBranch(),
				activeTransformerCount, explicitYTransformerCount, audits);
	}

	public static List<DStab3PBranch> branchesByAdmittance(DStabNetwork3Phase distNet) {
		List<DStab3PBranch> branches = new ArrayList<>();
		for(Object branchObj : distNet.getBranchList()) {
			DStab3PBranch branch = (DStab3PBranch) branchObj;
			if(branch.isActive()) {
				branches.add(branch);
			}
		}
		branches.sort(Comparator.comparingDouble(OpenDssDataQaUtils::branchMaxYAbs).reversed());
		return branches;
	}

	public static double branchMaxYAbs(DStab3PBranch branch) {
		return Math.max(
				Math.max(branch.getYffabc().absMax(), branch.getYftabc().absMax()),
				Math.max(branch.getYtfabc().absMax(), branch.getYttabc().absMax()));
	}

	public static Complex phaseVoltage(Complex3x1 voltage, int phase) {
		if(phase == 1) {
			return voltage.a_0;
		}
		if(phase == 2) {
			return voltage.b_1;
		}
		if(phase == 3) {
			return voltage.c_2;
		}
		throw new IllegalArgumentException("Unsupported phase: " + phase);
	}

	public static double wrappedAngleDeg(double angleDeg) {
		double wrapped = angleDeg;
		while(wrapped > 180.0) {
			wrapped -= 360.0;
		}
		while(wrapped <= -180.0) {
			wrapped += 360.0;
		}
		return wrapped;
	}

	public static List<Branch> sourceToTargetPath(DStabNetwork3Phase distNet, String targetBusId) {
		BaseAclfBus<?, ?> sourceBus = null;
		for(BaseAclfBus<?, ?> bus : distNet.getBusList()) {
			if(bus.isSwing()) {
				sourceBus = bus;
				break;
			}
		}
		if(sourceBus == null) {
			throw new IllegalStateException("No swing bus in distribution network");
		}
		BaseAclfBus<?, ?> targetBus = distNet.getBus(targetBusId);
		if(targetBus == null) {
			throw new IllegalArgumentException("Target bus not found: " + targetBusId);
		}

		ArrayDeque<BaseAclfBus<?, ?>> queue = new ArrayDeque<>();
		Set<String> seen = new HashSet<>();
		Map<String, Branch> parentBranch = new HashMap<>();
		queue.add(sourceBus);
		seen.add(sourceBus.getId());
		while(!queue.isEmpty()) {
			BaseAclfBus<?, ?> bus = queue.remove();
			if(bus.getId().equals(targetBusId)) {
				break;
			}
			for(Branch branch : bus.getBranchIterable()) {
				if(!branch.isActive()) {
					continue;
				}
				BaseAclfBus<?, ?> next = (BaseAclfBus<?, ?>) branch.getOppositeBus(bus);
				if(seen.add(next.getId())) {
					parentBranch.put(next.getId(), branch);
					queue.add(next);
				}
			}
		}
		if(!parentBranch.containsKey(targetBusId)) {
			throw new IllegalStateException("No path to " + targetBusId);
		}
		List<Branch> path = new ArrayList<>();
		String current = targetBusId;
		while(!current.equals(sourceBus.getId())) {
			Branch branch = parentBranch.get(current);
			path.add(branch);
			current = branch.getFromBus().getId().equals(current)
					? branch.getToBus().getId()
					: branch.getFromBus().getId();
		}
		Collections.reverse(path);
		return path;
	}

	public static String phaseComponentSample(DStabNetwork3Phase distNet, List<Integer> component, int limit) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < component.size() && i < limit; i++) {
			if(i > 0) {
				sb.append(", ");
			}
			int node = component.get(i);
			sb.append(busBySortNumber(distNet, node / 3).getId()).append('.').append(phaseLabel(node % 3));
		}
		return sb.toString();
	}

	public static String phaseLabel(int phase) {
		return phase == 0 ? "A" : phase == 1 ? "B" : "C";
	}

	private static double referenceAngleOffsetDeg(DStabNetwork3Phase distNet,
			List<VoltageReference> references, String busId, int phase) {
		VoltageReference reference = references.stream()
				.filter(ref -> ref.bus.equals(busId) && ref.phase == phase)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Missing angle reference: " + busId + "." + phase));
		DStab3PBus bus = distNet.getBus(busId);
		if(bus == null) {
			throw new IllegalArgumentException("Missing parsed angle-reference bus: " + busId);
		}
		return wrappedAngleDeg(reference.angleDeg
				- Math.toDegrees(phaseVoltage(bus.get3PhaseVotlages(), phase).getArgument()));
	}

	private static List<List<Integer>> phaseConnectivityGraph(DStabNetwork3Phase distNet) {
		int nodeCount = distNet.getNoBus() * 3;
		List<List<Integer>> graph = new ArrayList<>(nodeCount);
		for(int i = 0; i < nodeCount; i++) {
			graph.add(new ArrayList<>());
		}
		for(Object branchObj : distNet.getBranchList()) {
			Branch branch = (Branch) branchObj;
			if(branch.isActive()) {
				DStab3PBranch branch3p = (DStab3PBranch) branch;
				int from = branch.getFromBus().getSortNumber();
				int to = branch.getToBus().getSortNumber();
				addPhaseEdges(graph, from, to, branch3p.getYftabc());
				addPhaseEdges(graph, to, from, branch3p.getYtfabc());
			}
		}
		return graph;
	}

	private static List<ComponentAudit> phaseComponentAudits(DStabNetwork3Phase distNet,
			List<List<Integer>> graph) {
		boolean[] seen = new boolean[graph.size()];
		List<ComponentAudit> audits = new ArrayList<>();
		for(BaseAclfBus<?, ?> bus : distNet.getBusList()) {
			if(!bus.isActive()) {
				continue;
			}
			for(int phase = 0; phase < 3; phase++) {
				int start = bus.getSortNumber() * 3 + phase;
				if(seen[start] || graph.get(start).isEmpty()) {
					continue;
				}
				ArrayDeque<Integer> queue = new ArrayDeque<>();
				List<Integer> component = new ArrayList<>();
				seen[start] = true;
				queue.add(start);
				while(!queue.isEmpty()) {
					int node = queue.remove();
					component.add(node);
					for(int next : graph.get(node)) {
						if(!seen[next]) {
							seen[next] = true;
							queue.add(next);
						}
					}
				}
				audits.add(componentAudit(distNet, component));
			}
		}
		return audits;
	}

	private static ComponentAudit componentAudit(DStabNetwork3Phase distNet, List<Integer> component) {
		Set<String> busIds = new HashSet<>();
		Set<Integer> nodeSet = new HashSet<>(component);
		boolean hasSwing = false;
		int loadBusCount = 0;
		int singlePhaseLoadCount = 0;
		int threePhaseLoadCount = 0;
		double minDiagAbs = Double.POSITIVE_INFINITY;
		String minDiagLabel = "";
		for(int node : component) {
			DStab3PBus bus = (DStab3PBus) busBySortNumber(distNet, node / 3);
			busIds.add(bus.getId());
			hasSwing = hasSwing || bus.isSwing();
			if(!bus.getSinglePhaseLoadList().isEmpty() || !bus.getThreePhaseLoadList().isEmpty()) {
				loadBusCount++;
				singlePhaseLoadCount += bus.getSinglePhaseLoadList().size();
				threePhaseLoadCount += bus.getThreePhaseLoadList().size();
			}
			double diagAbs = getPhaseValue(bus.getYiiAbcForPowerflow(), node % 3, node % 3).abs();
			if(diagAbs < minDiagAbs) {
				minDiagAbs = diagAbs;
				minDiagLabel = bus.getId() + "." + phaseLabel(node % 3);
			}
		}

		int branchCount = 0;
		int lineCount = 0;
		int xfrCount = 0;
		int explicitXfrCount = 0;
		int triplexLikeCount = 0;
		double maxOffDiagAbs = 0.0;
		String branchSamples = "";
		for(Object branchObj : distNet.getBranchList()) {
			DStab3PBranch branch = (DStab3PBranch) branchObj;
			if(!branch.isActive() || !branchTouchesComponent(branch, nodeSet)) {
				continue;
			}
			branchCount++;
			if(branch.isLine()) {
				lineCount++;
			}
			if(branch.isXfr()) {
				xfrCount++;
			}
			if(branch.hasExplicitYabc()) {
				explicitXfrCount++;
			}
			if(isTriplexLike(branch)) {
				triplexLikeCount++;
			}
			maxOffDiagAbs = Math.max(maxOffDiagAbs, branch.getYftabc().absMax());
			maxOffDiagAbs = Math.max(maxOffDiagAbs, branch.getYtfabc().absMax());
			if(branchSamples.length() < 320) {
				if(branchSamples.length() > 0) {
					branchSamples += "; ";
				}
				branchSamples += branch.getName() + "(" + branch.getFromBus().getId()
						+ "->" + branch.getToBus().getId()
						+ ",phase=" + branch.getPhaseCode()
						+ ",xfr=" + branch.isXfr()
						+ ",explicit=" + branch.hasExplicitYabc() + ")";
			}
		}

		return new ComponentAudit(component, busIds, hasSwing, loadBusCount,
				singlePhaseLoadCount, threePhaseLoadCount, minDiagAbs, minDiagLabel,
				branchCount, lineCount, xfrCount, explicitXfrCount, triplexLikeCount,
				maxOffDiagAbs, branchSamples);
	}

	private static void addPhaseEdges(List<List<Integer>> graph, int fromSort, int toSort, Complex3x3 y) {
		for(int fromPhase = 0; fromPhase < 3; fromPhase++) {
			for(int toPhase = 0; toPhase < 3; toPhase++) {
				Complex value = getPhaseValue(y, fromPhase, toPhase);
				if(value != null && value.abs() > 1.0e-12) {
					int fromNode = fromSort * 3 + fromPhase;
					int toNode = toSort * 3 + toPhase;
					graph.get(fromNode).add(toNode);
					graph.get(toNode).add(fromNode);
				}
			}
		}
	}

	private static boolean branchTouchesComponent(DStab3PBranch branch, Set<Integer> nodeSet) {
		int from = branch.getFromBus().getSortNumber();
		int to = branch.getToBus().getSortNumber();
		return blockTouchesComponent(branch.getYftabc(), from, to, nodeSet)
				|| blockTouchesComponent(branch.getYtfabc(), to, from, nodeSet);
	}

	private static boolean blockTouchesComponent(Complex3x3 y, int fromSort, int toSort, Set<Integer> nodeSet) {
		for(int fromPhase = 0; fromPhase < 3; fromPhase++) {
			for(int toPhase = 0; toPhase < 3; toPhase++) {
				Complex value = getPhaseValue(y, fromPhase, toPhase);
				if(value != null && value.abs() > 1.0e-12
						&& (nodeSet.contains(fromSort * 3 + fromPhase)
								|| nodeSet.contains(toSort * 3 + toPhase))) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isTriplexLike(DStab3PBranch branch) {
		String name = branch.getName() == null ? "" : branch.getName().toLowerCase();
		String from = branch.getFromBus().getId().toLowerCase();
		String to = branch.getToBus().getId().toLowerCase();
		return name.contains("triplex") || from.startsWith("x") || to.startsWith("x")
				|| branch.getPhaseCode() == PhaseCode.AB;
	}

	private static Complex getPhaseValue(Complex3x3 matrix, int row, int col) {
		if(row == 0 && col == 0) return matrix.aa;
		if(row == 0 && col == 1) return matrix.ab;
		if(row == 0 && col == 2) return matrix.ac;
		if(row == 1 && col == 0) return matrix.ba;
		if(row == 1 && col == 1) return matrix.bb;
		if(row == 1 && col == 2) return matrix.bc;
		if(row == 2 && col == 0) return matrix.ca;
		if(row == 2 && col == 1) return matrix.cb;
		return matrix.cc;
	}

	private static BaseAclfBus<?, ?> busBySortNumber(DStabNetwork3Phase distNet, int sortNumber) {
		for(BaseAclfBus<?, ?> bus : distNet.getBusList()) {
			if(bus.getSortNumber() == sortNumber) {
				return bus;
			}
		}
		throw new IllegalArgumentException("No bus for sort number " + sortNumber);
	}

	private static String csv(String value) {
		if(value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	public static final class VoltageReference {
		public final String caseName;
		public final String bus;
		public final int phase;
		public final double vmagPu;
		public final double angleDeg;

		public VoltageReference(String caseName, String bus, int phase, double vmagPu, double angleDeg) {
			this.caseName = caseName;
			this.bus = bus;
			this.phase = phase;
			this.vmagPu = vmagPu;
			this.angleDeg = angleDeg;
		}

		public String label() {
			return caseName + ":" + bus + "." + phase;
		}

		public Complex phasor() {
			double angleRad = Math.toRadians(this.angleDeg);
			return new Complex(this.vmagPu * Math.cos(angleRad), this.vmagPu * Math.sin(angleRad));
		}
	}

	public static final class ComparisonResult {
		public final double maxMagError;
		public final String maxMagLabel;
		public final double maxAngleError;
		public final String maxAngleLabel;

		public ComparisonResult(double maxMagError, String maxMagLabel,
				double maxAngleError, String maxAngleLabel) {
			this.maxMagError = maxMagError;
			this.maxMagLabel = maxMagLabel;
			this.maxAngleError = maxAngleError;
			this.maxAngleLabel = maxAngleLabel;
		}

		public String summary(String caseName) {
			return String.format("%s comparison: max |V| error %.6f pu at %s, max angle error %.6f deg at %s",
					caseName, this.maxMagError, this.maxMagLabel, this.maxAngleError, this.maxAngleLabel);
		}
	}

	public static final class YMatrixAudit {
		public final int busCount;
		public final int branchCount;
		public final long activeTransformerCount;
		public final long explicitYTransformerCount;
		public final List<ComponentAudit> components;

		private YMatrixAudit(int busCount, int branchCount, long activeTransformerCount,
				long explicitYTransformerCount, List<ComponentAudit> components) {
			this.busCount = busCount;
			this.branchCount = branchCount;
			this.activeTransformerCount = activeTransformerCount;
			this.explicitYTransformerCount = explicitYTransformerCount;
			this.components = List.copyOf(components);
		}

		public long floatingComponentCount() {
			return this.components.stream().filter(component -> !component.hasSwing).count();
		}

		public long loadedFloatingComponentCount() {
			return this.components.stream().filter(component -> !component.hasSwing && component.hasLoad()).count();
		}

		public long weakDiagComponentCount(double minDiagTolerance) {
			return this.components.stream().filter(component -> component.minDiagAbs < minDiagTolerance).count();
		}

		public String summary(String label) {
			return label + " Y audit: buses=" + this.busCount
					+ ", branches=" + this.branchCount
					+ ", active transformers=" + this.activeTransformerCount
					+ ", explicitY transformers=" + this.explicitYTransformerCount
					+ ", phase components=" + this.components.size()
					+ ", floating components=" + floatingComponentCount()
					+ ", loaded floating components=" + loadedFloatingComponentCount()
					+ ", weak-diag components=" + weakDiagComponentCount(1.0e-10);
		}
	}

	public static final class IslandDeactivationResult {
		public final int inactiveBusCount;
		public final int inactiveBranchCount;
		public final Set<String> inactiveBusIds;

		private IslandDeactivationResult(int inactiveBusCount, int inactiveBranchCount,
				Set<String> inactiveBusIds) {
			this.inactiveBusCount = inactiveBusCount;
			this.inactiveBranchCount = inactiveBranchCount;
			this.inactiveBusIds = Set.copyOf(inactiveBusIds);
		}

		public String summary(String label) {
			return label + " island deactivation: buses=" + this.inactiveBusCount
					+ ", branches=" + this.inactiveBranchCount;
		}
	}

	public static final class ComponentAudit {
		public final List<Integer> component;
		public final Set<String> busIds;
		public final boolean hasSwing;
		public final int loadBusCount;
		public final int singlePhaseLoadCount;
		public final int threePhaseLoadCount;
		public final double minDiagAbs;
		public final String minDiagLabel;
		public final int branchCount;
		public final int lineCount;
		public final int xfrCount;
		public final int explicitXfrCount;
		public final int triplexLikeCount;
		public final double maxOffDiagAbs;
		public final String branchSamples;

		private ComponentAudit(List<Integer> component, Set<String> busIds, boolean hasSwing,
				int loadBusCount, int singlePhaseLoadCount, int threePhaseLoadCount,
				double minDiagAbs, String minDiagLabel, int branchCount, int lineCount,
				int xfrCount, int explicitXfrCount, int triplexLikeCount, double maxOffDiagAbs,
				String branchSamples) {
			this.component = List.copyOf(component);
			this.busIds = Set.copyOf(busIds);
			this.hasSwing = hasSwing;
			this.loadBusCount = loadBusCount;
			this.singlePhaseLoadCount = singlePhaseLoadCount;
			this.threePhaseLoadCount = threePhaseLoadCount;
			this.minDiagAbs = minDiagAbs;
			this.minDiagLabel = minDiagLabel;
			this.branchCount = branchCount;
			this.lineCount = lineCount;
			this.xfrCount = xfrCount;
			this.explicitXfrCount = explicitXfrCount;
			this.triplexLikeCount = triplexLikeCount;
			this.maxOffDiagAbs = maxOffDiagAbs;
			this.branchSamples = branchSamples;
		}

		public boolean hasSwing() {
			return this.hasSwing;
		}

		public boolean hasLoad() {
			return this.loadBusCount > 0;
		}

		public double minDiagAbs() {
			return this.minDiagAbs;
		}

		public String summary(DStabNetwork3Phase distNet) {
			return "nodes=" + this.component.size()
					+ ", buses=" + this.busIds.size()
					+ ", swing=" + this.hasSwing
					+ ", loadBuses=" + this.loadBusCount
					+ ", loads1p=" + this.singlePhaseLoadCount
					+ ", loads3p=" + this.threePhaseLoadCount
					+ ", branches=" + this.branchCount
					+ ", lines=" + this.lineCount
					+ ", xfr=" + this.xfrCount
					+ ", explicitXfr=" + this.explicitXfrCount
					+ ", triplexLike=" + this.triplexLikeCount
					+ ", minDiag=" + this.minDiagAbs + "@" + this.minDiagLabel
					+ ", maxOffDiag=" + this.maxOffDiagAbs
					+ ", sampleNodes=[" + phaseComponentSample(distNet, this.component, 10)
					+ "], sampleBranches=[" + this.branchSamples + "]";
		}
	}
}
