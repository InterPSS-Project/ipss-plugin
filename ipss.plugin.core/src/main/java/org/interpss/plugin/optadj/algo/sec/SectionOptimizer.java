package org.interpss.plugin.optadj.algo.sec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.optim.linear.Relationship;
import org.ejml.data.DMatrixSparseCSC;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfNetwork;

import org.interpss.plugin.optadj.algo.bean.PowerSystemSection;
import org.interpss.plugin.optadj.optimizer.GenStateOptimizer;
import org.interpss.plugin.optadj.optimizer.bean.GenConstrainData;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;
import org.interpss.plugin.optadj.algo.util.AclfNetSensSparseHelper;

/** 

* @author  Donghao.F 

* @date 2026 Jan 6 11:22:49 

* 

*/
public class SectionOptimizer {
	private AclfNetwork net;
	private List<PowerSystemSection> sections;
	private Map<String,Integer> generatorIndexMap;
	// Reverse mapping: index -> ID
	private List<String> generatorIndexToId;

	Map<String, AclfGen> generatorMap;
	
	Predicate<AclfGen> genPre = gen -> true;
	/**
	 * Constructor
	 * 
	 * @param network network model
	 * @param sections section list
	 */
	public SectionOptimizer(AclfNetwork network, List<PowerSystemSection> sections) {
		this.net = network;
		this.sections = sections;
	}

	/**
	 * Run GenStateOptimizer
	 * 
	 * @return optimized generator and section constraint adjustments
	 */
	public Map<String, Double> optimize() {
		
		generatorMap = net.getAclfGenNameLookupTable().entrySet().stream()
				.filter(entry -> genPre.test(entry.getValue()))
				.collect(Collectors.toMap(entry -> entry.getKey(), entry -> entry.getValue()));
		
		

		Set<String> busSet = new HashSet<>(); // use Set for automatic deduplication

		generatorMap.forEach((k, gen) -> {
			// collect only generators included in genNameSet
			AclfBus bus = (AclfBus) gen.getParentBus();
			if (bus != null) {
				busSet.add(bus.getId()); // collect bus IDs with deduplication
			}
		});

		
		Set<String> allowedBranches = sections.stream()
		    .flatMap(sec -> sec.getBranchCoefficients().keySet().stream())
		    .collect(Collectors.toSet());

		
		DMatrixSparseCSC sen = new AclfNetSensSparseHelper(net).calSenSortNumber(busSet, allowedBranches);
		this.sections.forEach(sec -> {
			sec.calculate(net,generatorMap, sen);
		});
	    // 1. Create optimizer
	    GenStateOptimizer optimizer = new GenStateOptimizer();
	    
	    // 2. Build generator index map
	    buildGeneratorIndexMap(); // build bidirectional mapping
	    
	    // 3. Build constraints
	    buildGeneratorConstraints(optimizer);
	    buildSectionConstraints(optimizer);
	    // 4. Run optimization
	    if (!optimizer.optimize()) {
	        throw new RuntimeException("Optimization solve failed");
	    }
	    System.out.println("isAllControl:" + optimizer.isAllControl());
//	    System.out.println("==================== Generator Sensitivity Comparison ====================");
//	    System.out.printf("%-20s %-12s %-12s %-15s %-15s %-15s\n", 
//	                      "Generator", "Current Power", "Max Power", "True Sensitivity", "False Sensitivity", "Diff");
//	    System.out.println("--------------------------------------------------------------------------------");
//
//	    generatorMap.forEach((k, v) -> {
//	        Integer index = generatorIndexMap.get(k);
//	        String genName = k;
//	        double currentPower = v.isActive() ? v.getGen().getReal() : 0;;
//	        double maxPower = v.getPGenLimit().getMax();
//	        double trueSen = optimizer.getSen(index, 0);
//	        double falseSen = sen.get(v.getParentBus().getSortNumber(), net.getBranch(sections.get(0).getBranchIds().get(0)).getSortNumber());
//	        double diff = trueSen - falseSen;
//	        
//	        System.out.printf("%-20s %-12.2f %-12.2f %-15.6f %-15.6f %-15.6f\n", 
//	                          genName, currentPower, maxPower, trueSen, falseSen, diff);
//	    });
//
//	    System.out.println("==================== Comparison End ====================");
	    
	    
	    // 5. Convert optimization result to a map
	    Map<String, Double> resultMap = new LinkedHashMap<>();
	    double[] optimalPoints = optimizer.getCachedDGenP();
//	    optimizer.printAllDSecP();
	    // 6. Map index back to generator ID
		for (int i = 0; i < generatorIndexToId.size(); i++) {
			String generatorId = generatorIndexToId.get(i);
			double optimalValue = optimalPoints[i];
			if (Math.abs(optimalValue) > 0)
				resultMap.put(generatorId, optimalValue);

		}
	    
	    return resultMap;
	}
	
	public void updateNet(Map<String, Double> resultMap) {
	    final double EPSILON = 1e-3;
	    
	    resultMap.forEach((k, v) -> {
	        AclfGen gen = generatorMap.get(k);
	        Complex currentGen = gen.getGen();
	        
	        double currentReal = currentGen.getReal();
	        double currentImag = currentGen.getImaginary();
	        boolean isCurrentlyActive = gen.isActive();
	        
	        // Preserve original Q/P ratio
	        double ratio = (Math.abs(currentReal) > EPSILON) ? currentImag / currentReal : 0.0;
	        
	        // Active power
	        double newReal = isCurrentlyActive ? currentReal + v : v;
	        boolean isShutdown = Math.abs(newReal) <= EPSILON;
	        
	        // Reactive power follows the original Q/P ratio unless shut down
	        double newImag = isShutdown ? 0.0 : newReal * ratio;
	        
	        gen.setGen(new Complex(newReal, newImag));
	        gen.setStatus(!isShutdown);
	        if (gen.getCode() != AclfGenCode.NON_GEN)
				gen.getParentBus().setGenCode(gen.getCode());
	    });
	}

	/**
	 * Build generator constraints
	 */
	private void buildGeneratorConstraints(GenStateOptimizer optimizer) {

		// iterate over all generators in the map
		for (AclfGen generator : generatorMap.values()) {

			
				int index = generatorIndexMap.get(generator.getName());
				// apply active-power limits
				double p = generator.isActive() ? generator.getGen().getReal() : 0;
				if (generator.getPGenLimit() != null) {
					optimizer.adConstraint(new GenConstrainData(p, Relationship.LEQ,
							Math.max(p, generator.getPGenLimit().getMax()), index));
					optimizer.adConstraint(new GenConstrainData(p, Relationship.GEQ,
							Math.min(p, generator.getPGenLimit().getMin()), index));
				} else {
					optimizer.adConstraint(new GenConstrainData(p, Relationship.LEQ, Double.MAX_VALUE, index));
					optimizer.adConstraint(new GenConstrainData(p, Relationship.GEQ, 0, index));
				}
			
		}
	}
	
	/**
	 * Build generator index map
	 * @param network network model
	 * @return generator ID to index map
	 */
	private void buildGeneratorIndexMap() {
		generatorIndexMap = new HashMap<>();
		generatorIndexToId = new ArrayList<String>();
		// method 1: iterate values directly and record indices
		int index = 0;
		for (AclfGen generator : generatorMap.values()) {
			
				generatorIndexMap.put(generator.getName(), index);
				generatorIndexToId.add(generator.getName());
				index++;
			
		}
		System.out.println("genSize" + index);
	}

	/**
	 * Build section constraints
	 */
	private void buildSectionConstraints(GenStateOptimizer optimizer) {

		int generatorCount = generatorIndexMap.size();

		for (int i = 0; i < sections.size(); i++) {
			PowerSystemSection section = sections.get(i);

			// pre-initialize sensitivity array
			double[] senArray = new double[generatorCount];

			// fill array using generator index map
			section.getGeneratorSensitivities().forEach((genId, sensitivity) -> {
				Integer idx = generatorIndexMap.get(genId);
				if (idx != null) {
					senArray[idx] = sensitivity;
				}
			});

			// original upper/lower section constraints

			// signs are opposite for upper and lower bounds
			optimizer.adConstraint(new SectionConstrainData(section.getCurrentPower(), Relationship.LEQ,
					section.getUpper(), senArray));

			optimizer.adConstraint(new SectionConstrainData(section.getCurrentPower(), Relationship.GEQ,
					section.getLower(), senArray));

		}
	}

	public void setGenPre(Predicate<AclfGen> genPre) {
		this.genPre = genPre;
	}

	
	
}
