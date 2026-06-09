package org.interpss.optadj.ieee39.sparse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;

import org.interpss.optadj.ieee39.IEEE39_Sample_Data;
import org.interpss.plugin.optadj.algo.bean.PowerSystemSection;
import org.interpss.plugin.optadj.algo.sec.SectionOptimizer;

public class IEEE39_OptSection_Sample {

	public static void main(String args[]) throws Exception {
	    // Load network
	    AclfNetwork net = IEEE39_Sample_Data.createTestCaseNetwork();
	    
	    // Initial power flow
	    LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	    algo.loadflow();
	    //System.out.println(AclfOutFunc.loadFlowSummary(net));	

		// Create section list
	    List<PowerSystemSection> sections = createSectionList();

		// Iterative optimization
		iterateOptimization(net, sections, algo);
	}

	private static List<PowerSystemSection> createSectionList() {
		double factor = 0.90;
	    List<PowerSystemSection> sections = new ArrayList<>(Arrays.asList(
	            new PowerSystemSection.Builder()
						.setSectionName("L14-4")
						.addBranch("Bus14->Bus4(1)", 1)
						.upper((262.73 / 100) * factor )
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
	    
	    // Print final constraint power for each section
	    System.out.println("========== Section Constraint Power List ==========");
	    for (PowerSystemSection section : sections) {
	        System.out.printf("Section name: %-10s Constraint power: %.4f (MW)%n", 
	            section.getSectionName(), section.getUpper()*100);
	    }
	    System.out.println("=====================================");
	    System.out.println();

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
						.build()
	    );
	}

	/**
	 * Iterate optimization until all section powers are below the original target values.
	 * @param net power system model
	 * @param sections section list
	 * @param algo load flow algorithm
	 */
	private static void iterateOptimization(AclfNetwork net, List<PowerSystemSection> sections, LoadflowAlgorithm algo) 
	        throws InterpssException {
	    
	    // Optimization parameters
	    double targetReduction = 1.0; // Target reduction (MW), e.g. 236 -> 235
	    int maxIterations = 20;
	    int iteration = 0;
	    boolean targetReached = false;
	    
	    // Save original target values
	    List<Double> originalTargets = sections.stream()
	            .map(s -> s.getUpper())
	            .collect(Collectors.toList());
	    
	    // Create control targets used for optimization (slightly below original targets)
	    List<Double> controlTargets = new ArrayList<>();
	    for (int i = 0; i < sections.size(); i++) {
	        double originalMW = originalTargets.get(i) * 100;
	        double controlMW = originalMW - targetReduction;
	        // Ensure control target is not below 0
	        controlMW = Math.max(0, controlMW);
	        controlTargets.add(controlMW / 100); // Convert back to per-unit
	        sections.get(i).setUpper(controlTargets.get(i));
	    }
	    
	    System.out.println("\n========== Start Iterative Optimization ==========");
	    System.out.printf("Original target reduction: %.2f MW, max iterations: %d%n", targetReduction, maxIterations);
	    System.out.println("\nControl target settings:");
	    for (int i = 0; i < sections.size(); i++) {
	        System.out.printf("  %s: %.4f MW (original: %.4f MW)%n", 
	            sections.get(i).getSectionName(),
	            controlTargets.get(i) * 100,
	            originalTargets.get(i) * 100);
	    }
	    System.out.println();
	    
	    while (iteration < maxIterations && !targetReached) {
	        iteration++;
	        
	        // 1. Calculate current section power
	        for (PowerSystemSection section : sections) {
	            section.calculateCurrentPower(net);
	        }
	        
	        // 2. Print current status
	        System.out.printf("Iteration %d - current section power (MW):%n", iteration);
	        boolean allBelowOriginal = true;
	        
	        for (int i = 0; i < sections.size(); i++) {
	            PowerSystemSection section = sections.get(i);
	            double currentPower = Math.abs(section.getCurrentPower()) * 100;
	            double originalTargetMW = originalTargets.get(i) * 100;
	            double controlTargetMW = controlTargets.get(i) * 100;
	            double deviation = currentPower - originalTargetMW;
	            
	            // Check if below original target
	            boolean isBelowOriginal = currentPower <= originalTargetMW + 0.01; // Allow 0.01 MW numerical tolerance
	            if (!isBelowOriginal) {
	                allBelowOriginal = false;
	            }
	            
	            String status = isBelowOriginal ? "OK" : "OVER";
	            System.out.printf("  %s: %.4f (original target: %.4f, control target: %.4f, over limit: %.4f) %s%n", 
	                section.getSectionName(), 
	                currentPower, 
	                originalTargetMW, 
	                controlTargetMW,
	                Math.max(0, deviation),
	                status);
	        }
	        
	        // 3. Check whether all sections are below the original target
	        if (allBelowOriginal) {
	            targetReached = true;
	            System.out.printf("\nOK After iteration %d, goal reached: all section powers <= original target values%n", iteration);
	            break;
	        }
	        
	        // 4. If goal not reached, continue optimization
	        System.out.println("\nRunning section optimization...");
	        SectionOptimizer optimizer = new SectionOptimizer(net, sections);
	        Map<String, Double> adjustmentResult = optimizer.optmize();
	        
	        // 5. Apply optimization results
	        optimizer.updateNet(adjustmentResult);
	        
	        // 6. Recalculate power flow
	        algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
	        algo.loadflow();
	        
	        System.out.println();
	    }
	    
	    // Output final results
	    System.out.println("\n========== Optimization Complete ==========");
	    if (targetReached) {
	        System.out.printf("OK Success: after %d iterations, all section powers are at or below original target values%n", iteration);
	    } else {
	        System.out.printf("WARNING Reached max iterations %d; some section powers still exceed original targets%n", maxIterations);
	        // List sections still over limit
	        System.out.println("\nOver-limit section list:");
	        for (int i = 0; i < sections.size(); i++) {
	            PowerSystemSection section = sections.get(i);
	            double currentPower = Math.abs(section.getCurrentPower()) * 100;
	            double originalTargetMW = originalTargets.get(i) * 100;
	            if (currentPower > originalTargetMW) {
	                System.out.printf("  %s: %.4f MW > %.4f MW (over limit: %.4f MW)%n",
	                    section.getSectionName(), currentPower, originalTargetMW, currentPower - originalTargetMW);
	            }
	        }
	    }
	    
	    // Print final section power vs original target comparison
	    System.out.println("\nFinal section power vs original target:");
	    for (int i = 0; i < sections.size(); i++) {
	        PowerSystemSection section = sections.get(i);
	        double currentPower = Math.abs(section.getCurrentPower()) * 100;
	        double originalTarget = originalTargets.get(i) * 100;
	        double controlTarget = controlTargets.get(i) * 100;
	        String status = currentPower <= originalTarget ? "OK" : "OVER";
	        System.out.printf("  %s:%n", section.getSectionName());
	        System.out.printf("    Current power: %.4f MW%n", currentPower);
	        System.out.printf("    Original target: %.4f MW %s%n", originalTarget, status);
	        System.out.printf("    Control target: %.4f MW%n", controlTarget);
	        System.out.printf("    Over-limit amount: %.4f MW%n%n", Math.max(0, currentPower - originalTarget));
	    }
	    
	    // Output full power flow results
	    //System.out.println(AclfOutFunc.loadFlowSummary(net));
	}

}
