
package org.interpss.plugin.optadj.optimizer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.math3.optim.linear.LinearConstraint;
import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.plugin.optadj.config.OptAdjConfigureInfo;
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
 * @date 2024��5��27�� ����5:19:38
 * 
 * 
 * 
 */
public class GenStateOptimizer {

	private Set<LinearConstraint> linearConstraintHashSet = new HashSet<LinearConstraint>();

	private List<GenConstrainData> genConstrainDataList = new ArrayList<GenConstrainData>();
	
	private List<SectionConstrainData> secConstrainDataList = new ArrayList<SectionConstrainData>();
	
	private double senLimit = 0.05;

	private int genSize;
	
	private Optimisation.Result result;
	
	private ExpressionsBasedModel model;

	public GenStateOptimizer() {
		 model = new ExpressionsBasedModel();
		
	}

	public List<GenConstrainData> getGenConstrainDataList() {
		return genConstrainDataList;
	}
	
	public List<SectionConstrainData> getSecConstrainDataList() {
		return secConstrainDataList;
	}

	public int getGenSize() {
		return genSize;
	}

	public void addConstraint(BaseConstrainData data) {
		if (data instanceof GenConstrainData) {
			genConstrainDataList.add((GenConstrainData) data);
		} else {
			secConstrainDataList.add((SectionConstrainData) data);
		}
	}

	public void optimize() {
		System.out.println("gen constrain size:" + this.genConstrainDataList.size() + ", " + 
							"section constrain size:"	+ this.secConstrainDataList.size());
		 // Determine genSize
        genConstrainDataList.forEach(data -> {
            if (data.getIndex() + 1 > genSize) {
                genSize = data.getIndex() + 1;
            }
        });
		
        // Create variables: dGenP (genSize), dSecP (secConstrainDataList.size()), |dGenP| (genSize), |sum(dGenP)| (1)
        int totalVars = genSize + secConstrainDataList.size() + genSize + 1;
        
        // Create all variables
		for (int i = 0; i < totalVars; i++) {
			model.addVariable("v" + i).weight(0);
		}
		
		for (GenConstrainData data : genConstrainDataList) {
            final Expression constraint = model.addExpression("gen_constraint_" + data.getIndex());
            constraint.set(data.getIndex(), 1.0);
            
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
		
		// 2. ����Լ��
        for (int i = 0; i < secConstrainDataList.size(); i++) {
            final SectionConstrainData data = secConstrainDataList.get(i);
            final Expression constraint = model.addExpression("section_constraint_" + i);
            
            final double[] senArray = data.getSenArray();
            for (int j = 0; j < Math.min(senArray.length, genSize); j++) {
                constraint.set(j, senArray[j]);
            }
            
            if (data.getRelationship() == Relationship.LEQ) {
                constraint.set(genSize + i, -1.0);
                constraint.upper(data.getLimit() - data.getValue());
            } else {
                constraint.set(genSize + i, 1.0);
                constraint.lower(data.getLimit() - data.getValue());
            }
        }
		
        
		// ȷ��dSecP >= 0
		for (int i = 0; i < secConstrainDataList.size(); i++) {
			final Expression constraint = model.addExpression("dSecP_nonneg_" + i);
			constraint.set(genSize + i, 1.0);
			constraint.lower(0.0);
		}
		
		 // 3. |xi| Լ��
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
        
		// 4. |x1+x2+....| Լ��
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

        // ����Ŀ�꺯��: |x1+x2+..| + 0.5*(|x1| + |x2| +...) + 10*(s1+s2+...)
        final Expression objective = model.addExpression("Objective").weight(1.0);
        for (int i = genSize; i < genSize + secConstrainDataList.size(); i++) {
            objective.set(i, 1.0/senLimit);
        }
        for (int i = genSize + secConstrainDataList.size(); i < totalVars - 1; i++) {
            objective.set(i, 0.5);
        }
        objective.set(totalVars - 1, 1.0);
        // �������
        result = model.minimise();
        
        // ��ӡ���״̬
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

	public void addConfigure(OptAdjConfigureInfo info) {
		info.getOptimizedUnitControlLimits().forEach(limit -> {
			if (limit.getPMax() != -1) {
				this.addConstraint(
						new GenConstrainData(limit.getOrigin(), Relationship.LEQ, limit.getPMax(), limit.getIndex()));
			}
			if (limit.getPMin() != -1) {
				this.addConstraint(
						new GenConstrainData(limit.getOrigin(), Relationship.GEQ, limit.getPMin(), limit.getIndex()));
			}
		});
		
	};
	
	
}

