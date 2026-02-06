
package org.interpss.plugin.optadj.optimizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.plugin.optadj.optimizer.bean.BaseConstrainData;
import org.interpss.plugin.optadj.optimizer.bean.GenConstrainData;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;
import org.ojalgo.optimisation.Expression;
import org.ojalgo.optimisation.ExpressionsBasedModel;
import org.ojalgo.optimisation.Optimisation;


/**
 * 
 * @author Donghao.F
 * 
 * @date 2024年5月27日 下午5:19:38
 * 
 * 
 * 
 */
public class GenStateOptimizer extends BaseStateOptimizer  {

	List<GenConstrainData> genConstrainDataList = new ArrayList<GenConstrainData>();
	
	List<SectionConstrainData> secConstrainDataList = new ArrayList<SectionConstrainData>();
	
	double senLimit = 0.01;

	int genSize;
	
	Optimisation.Result result;
	
	ExpressionsBasedModel model;
	
	private double interfaceFactor = 1e4;
	private double sectionFactor = 1e2;
	public GenStateOptimizer() {
		 model = new ExpressionsBasedModel();
		
	}

	public void adConstraint(BaseConstrainData data) {
		if (data instanceof GenConstrainData) {
			genConstrainDataList.add((GenConstrainData) data);
		} else {
			secConstrainDataList.add((SectionConstrainData) data);
		}
	}
	
	public void optimize() {
		//System.out.println("gen size:" + this.genConstrainDataList.size() + "," + "section size:"
		//		+ this.secConstrainDataList.size());
		 // Determine genSize
        
		Map<Integer, Double> weightMap = new HashMap<Integer, Double>();
		genConstrainDataList.forEach(data -> {
			if (data.getIndex() + 1 > genSize) {
				genSize = data.getIndex() + 1;
			}
			weightMap.put(data.getIndex(), data.getWeight());
		});
		
        // Create variables: dGenP (genSize), dSecP (secConstrainDataList.size()), |dGenP| (genSize), |sum(dGenP)| (1)
        int totalVars = genSize + secConstrainDataList.size() + genSize + 1;
        
        // Create all variables
		for (int i = 0; i < totalVars; i++) {
			model.addVariable("v" + i).weight(0);
		}
		// 1. 发电机约束
		for (GenConstrainData data : genConstrainDataList) {
			Expression constraint = model.getExpression("gen_constraint_" + data.getIndex());
			if (constraint == null) {
				constraint = model.addExpression("gen_constraint_" + data.getIndex());
				constraint.set(data.getIndex(), 1.0);
			}
            
			switch (data.getRelationship()) {
			case LEQ:
				constraint.upper(data.getLimit());
				break;
			case GEQ:
				constraint.lower(data.getLimit());
				break;
			default:
				break;
			}
        }
		
		// 2. 断面约束
        for (int i = 0; i < secConstrainDataList.size(); i++) {
            final SectionConstrainData data = secConstrainDataList.get(i);
            final double[] senArray = data.getSenArray();
			if (Arrays.stream(senArray).allMatch(value -> Math.abs(value) < senLimit)) {
				continue;
			}
            
            final Expression constraint = model.addExpression("section_constraint_" + i);
            
            
			for (int j = 0; j < Math.min(senArray.length, genSize); j++) {
				double sen = Math.abs(senArray[j]) < senLimit ? 0 : senArray[j];
				constraint.set(j, sen);
			}
            
            if (data.getRelationship() == Relationship.LEQ) {
                constraint.set(genSize + i, -1.0);
                constraint.upper(data.getLimit() - data.getValue());
            } else {
                constraint.set(genSize + i, 1.0);
                constraint.lower(data.getLimit() - data.getValue());
            }
        }
		
        
		// 确保dSecP >= 0
		for (int i = 0; i < secConstrainDataList.size(); i++) {
			final Expression constraint = model.addExpression("dSecP_nonneg_" + i);
			constraint.set(genSize + i, 1.0);
			constraint.lower(0.0);
			final SectionConstrainData data = secConstrainDataList.get(i);
			constraint.upper(Math.abs(data.getLimit() - data.getValue()));
		}
		
		 // 3. |xi| 约束
        for (int i = 0; i < genSize; i++) {
            // 3.1 xi + wi >= 0
            final Expression constraint1 = model.addExpression("abs_constraint1_" + i);
            constraint1.set(i, 1.0);
            constraint1.set(i + secConstrainDataList.size() + genSize, 1.0);
            constraint1.lower(0.0);

            // 3.2 xi - wi <= 0
            final Expression constraint2 = model.addExpression("abs_constraint2_" + i);
            constraint2.set(i, 1.0);
            constraint2.set(i + secConstrainDataList.size() + genSize, -1.0);
            constraint2.upper(0.0);

            // 3.3 wi >= 0
            final Expression constraint3 = model.addExpression("abs_constraint3_" + i);
            constraint3.set(i + secConstrainDataList.size() + genSize, 1.0);
            constraint3.lower(0.0);
        }
        
		// 4. |x1+x2+....| 约束
		// 4.1 x1+x2 ... +w >= 0
		final Expression sumConstraint1 = model.addExpression("sum_abs_constraint1");
		for (int i = 0; i < genSize; i++) {
			sumConstraint1.set(i, 1.0);
		}
		sumConstraint1.set(totalVars - 1, 1.0);
		sumConstraint1.lower(0.0);
		
		// 4.2 x1+x2 ... -w <= 0
        final Expression sumConstraint2 = model.addExpression("sum_abs_constraint2");
        for (int i = 0; i < genSize; i++) {
            sumConstraint2.set(i, 1.0);
        }
        sumConstraint2.set(totalVars - 1, -1.0);
        sumConstraint2.upper(0.0);

        // 4.3 w >= 0
        final Expression sumConstraint3 = model.addExpression("sum_abs_constraint3");
        sumConstraint3.set(totalVars - 1, 1.0);
        sumConstraint3.lower(0.0);

        // 设置目标函数: |x1+x2+..| + (|x1| + |x2| +...) + factor* (|xLn|...) + factor*(s1+s2+...)
        final Expression objective = model.addExpression("Objective").weight(1.0);
		for (int i = genSize; i < genSize + secConstrainDataList.size(); i++) {
			objective.set(i, sectionFactor);
		}
		for (int i = genSize + secConstrainDataList.size(); i < totalVars - 1; i++) {
			int index = i - (genSize + secConstrainDataList.size());
			objective.set(i, weightMap.get(index));
		}
        objective.set(totalVars - 1, interfaceFactor );
        
//		System.out.println("totalVars:" + totalVars + " ,  expressions :" + model.getExpressions().size());
        
        // 求解问题
        result = model.minimise();
        
        // 打印求解状态
//        System.out.println("Optimisation status: " + result.getState());
	}
	
	public double[] getPoint() {
        return result.toRawCopy1D();
    }

    public double getValue() {
        return result.getValue();
    }

	public double getSenLimit() {
		return senLimit;
	}

	public void setSenLimit(double senLimit) {
		this.senLimit = senLimit;
	}

	public void setInterfaceFactor(double interfaceFactor) {
		this.interfaceFactor = interfaceFactor;
	}

	public void setSectionFactor(double sectionFactor) {
		this.sectionFactor = sectionFactor;
	};
	
	
}

