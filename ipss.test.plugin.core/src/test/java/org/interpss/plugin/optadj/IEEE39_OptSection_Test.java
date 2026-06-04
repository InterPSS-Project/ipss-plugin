package org.interpss.plugin.optadj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.optadj.algo.bean.PowerSystemSection;
import org.interpss.plugin.optadj.algo.sec.SectionOptimizer;
import org.junit.jupiter.api.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

/**
 * Regression test for {@code IEEE39_OptSection_Sample}: iterative section power
 * optimization with {@link SectionOptimizer}.
 */
public class IEEE39_OptSection_Test extends CorePluginTestSetup {

	private static final double TARGET_REDUCTION_MW = 1.0;
	private static final int MAX_ITERATIONS = 20;
	private static final double POWER_TOLERANCE_MW = 0.01;

	static List<PowerSystemSection> createSectionList() {
		double factor = 0.90;
		List<PowerSystemSection> sections = new ArrayList<>(Arrays.asList(
				new PowerSystemSection.Builder()
						.setSectionName("L14-4")
						.addBranch("Bus14->Bus4(1)", 1)
						.upper((262.73 / 100) * factor)
						.build(),
				new PowerSystemSection.Builder()
						.setSectionName("L18-3")
						.addBranch("Bus18->Bus3(1)", 1)
						.upper((34.18 / 100) * factor)
						.build(),
				new PowerSystemSection.Builder()
						.setSectionName("L39-9")
						.addBranch("Bus39->Bus9(1)", 1)
						.upper((14.44 / 100) * factor)
						.build()));
		sections.addAll(buildSectionList());
		return sections;
	}

	private static List<PowerSystemSection> buildSectionList() {
		double factor = 1;

		return Arrays.asList(
				new PowerSystemSection.Builder()
						.setSectionName("Section 1")
						.addBranch("Bus26->Bus25(1)", -1)
						.addBranch("Bus18->Bus3(1)", -1)
						.addBranch("Bus4->Bus3(1)", -1)
						.addBranch("Bus39->Bus9(1)", 1)
						.upper((131.57 / 100) * factor)
						.build(),

				new PowerSystemSection.Builder()
						.setSectionName("Section 2")
						.addBranch("Bus39->Bus9(1)", 1)
						.addBranch("Bus4->Bus3(1)", -1)
						.addBranch("Bus14->Bus4(1)", 1)
						.addBranch("Bus12->Bus11(1)", 1)
						.addBranch("Bus11->Bus10(1)", -1)
						.upper((696.51 / 100) * factor)
						.build(),

				new PowerSystemSection.Builder()
						.setSectionName("Section 3")
						.addBranch("Bus11->Bus10(1)", -1)
						.addBranch("Bus12->Bus11(1)", 1)
						.addBranch("Bus14->Bus13(1)", -1)
						.addBranch("Bus19->Bus16(1)", 1)
						.addBranch("Bus22->Bus35(1)", -1)
						.upper((1744.2 / 100) * factor)
						.build(),

				new PowerSystemSection.Builder()
						.setSectionName("Section 4")
						.addBranch("Bus11->Bus10(1)", -1)
						.addBranch("Bus12->Bus11(1)", 1)
						.addBranch("Bus14->Bus13(1)", -1)
						.addBranch("Bus19->Bus16(1)", 1)
						.addBranch("Bus21->Bus16(1)", 1)
						.addBranch("Bus24->Bus16(1)", 1)
						.upper((1467.3 / 100) * 0.95)
						.build(),

				new PowerSystemSection.Builder()
						.setSectionName("Section 5")
						.addBranch("Bus29->Bus26(1)", 1)
						.addBranch("Bus28->Bus26(1)", 1)
						.upper((333.7 / 100) * factor)
						.build());
	}

	private static int countSectionsAboveOriginalTarget(List<PowerSystemSection> sections,
			List<Double> originalTargetsMw) {
		int count = 0;
		for (int i = 0; i < sections.size(); i++) {
			double currentMw = Math.abs(sections.get(i).getCurrentPower()) * 100;
			if (currentMw > originalTargetsMw.get(i) + POWER_TOLERANCE_MW) {
				count++;
			}
		}
		return count;
	}

	private static OptimizationResult iterateOptimization(AclfNetwork net, List<PowerSystemSection> sections)
			throws InterpssException {
		List<Double> originalTargets = sections.stream()
				.map(PowerSystemSection::getUpper)
				.collect(Collectors.toList());
		List<Double> originalTargetsMw = originalTargets.stream()
				.map(v -> v * 100)
				.collect(Collectors.toList());

		List<Double> controlTargets = new ArrayList<>();
		for (int i = 0; i < sections.size(); i++) {
			double controlMw = Math.max(0, originalTargetsMw.get(i) - TARGET_REDUCTION_MW);
			controlTargets.add(controlMw / 100);
			sections.get(i).setUpper(controlTargets.get(i));
		}

		int overLimitBeforeFirstOpt = 0;
		for (PowerSystemSection section : sections) {
			section.calculateCurrentPower(net);
		}
		overLimitBeforeFirstOpt = countSectionsAboveOriginalTarget(sections, originalTargetsMw);

		int iteration = 0;
		boolean targetReached = false;
		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);

		while (iteration < MAX_ITERATIONS && !targetReached) {
			iteration++;

			for (PowerSystemSection section : sections) {
				section.calculateCurrentPower(net);
			}

			boolean allBelowOriginal = true;
			for (int i = 0; i < sections.size(); i++) {
				double currentMw = Math.abs(sections.get(i).getCurrentPower()) * 100;
				if (currentMw > originalTargetsMw.get(i) + POWER_TOLERANCE_MW) {
					allBelowOriginal = false;
					break;
				}
			}

			if (allBelowOriginal) {
				targetReached = true;
				break;
			}

			SectionOptimizer optimizer = new SectionOptimizer(net, sections);
			Map<String, Double> adjustmentResult = optimizer.optmize();
			optimizer.updateNet(adjustmentResult);

			algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
			algo.loadflow();
		}

		for (PowerSystemSection section : sections) {
			section.calculateCurrentPower(net);
		}

		int overLimitAfter = countSectionsAboveOriginalTarget(sections, originalTargetsMw);
		String worstSection = worstOverLimitSectionName(sections, originalTargetsMw);
		double worstPowerMw = 0.0;
		for (int i = 0; i < sections.size(); i++) {
			if (sections.get(i).getSectionName().equals(worstSection)) {
				worstPowerMw = Math.abs(sections.get(i).getCurrentPower()) * 100;
				break;
			}
		}

		return new OptimizationResult(iteration, targetReached, overLimitBeforeFirstOpt, overLimitAfter,
				worstSection, worstPowerMw);
	}

	private static String worstOverLimitSectionName(List<PowerSystemSection> sections,
			List<Double> originalTargetsMw) {
		String worst = null;
		double maxExcess = -1.0;
		for (int i = 0; i < sections.size(); i++) {
			double currentMw = Math.abs(sections.get(i).getCurrentPower()) * 100;
			double excess = currentMw - originalTargetsMw.get(i);
			if (excess > maxExcess) {
				maxExcess = excess;
				worst = sections.get(i).getSectionName();
			}
		}
		return worst;
	}

	private record OptimizationResult(int iterations, boolean targetReached, int overLimitBeforeFirstOpt,
			int overLimitAfter, String worstSectionName, double worstSectionPowerMw) {
	}

	@Test
	void sectionOptimizerReducesOverLimitWithinMaxIterations() throws Exception {
		AclfNetwork net = IEEE39_TestCaseInfo.createTestCaseNetwork();

		LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		algo.loadflow();

		List<PowerSystemSection> sections = createSectionList();
		assertEquals(8, sections.size(), "Section count");

		OptimizationResult result = iterateOptimization(net, sections);

		assertTrue(result.overLimitBeforeFirstOpt() > 0,
				"Precondition: initial section powers should exceed original targets");
		assertTrue(result.overLimitAfter() < result.overLimitBeforeFirstOpt(),
				"Section optimizer should reduce over-limit section count");
		assertTrue(result.overLimitAfter() > 0,
				"Sample workflow still has residual over-limit sections at max iterations");

		// Regression anchors (IEEE39_OptSection_Sample).
		assertEquals(5, result.overLimitBeforeFirstOpt(), "Over-limit sections before first optimization");
		assertEquals(1, result.overLimitAfter(), "Over-limit sections after max iterations");
		assertEquals(20, result.iterations(), "Iterations executed");
		assertEquals(false, result.targetReached(), "Convergence within 20 iterations");
		assertEquals("L14-4", result.worstSectionName(), "Remaining over-limit section");
		assertTrue(result.worstSectionPowerMw() > 237.0 && result.worstSectionPowerMw() < 238.5,
				"Residual L14-4 section power (~237.7 MW)");
	}
}
