
package org.interpss.plugin.optadj.optimizer;

import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.plugin.optadj.optimizer.bean.BaseConstrainData;
import org.interpss.plugin.optadj.optimizer.bean.GenConstrainData;
import org.interpss.plugin.optadj.optimizer.bean.SectionConstrainData;
import org.ojalgo.optimisation.Expression;

/**
 * 
 * @author Donghao.F
 * 
 * 
 * 
 * 
 */
public class ATCOptimizer extends BaseStateOptimizer {

	public void addConstraint(BaseConstrainData data) {
		if (data instanceof GenConstrainData) {
			deviceConstrainDataList.add((GenConstrainData) data);
		} else {
			secConstrainDataList.add((SectionConstrainData) data);
		}
	}

	@Override
	public void optimize() {
		System.out.println("gen constrain size:" + this.deviceConstrainDataList.size() + ", "
				+ "section constrain size:" + this.secConstrainDataList.size());
		// Determine genSize
		deviceConstrainDataList.forEach(data -> {
			if (data.getIndex() + 1 > genSize) {
				genSize = data.getIndex() + 1;
			}
		});

		// Create variables: dGenP, dSecP (secConstrainDataList.size();
		int totalVars = genSize ;

		// Create all variables
		for (int i = 0; i < totalVars; i++) {
			model.addVariable("v" + i).weight(0);
		}
		// 0. Device Constrain
		for (GenConstrainData data : deviceConstrainDataList) {
			final Expression constraint = model
					.addExpression("gen_constraint_" + data.getIndex() + "_" + data.getLimit());
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

		// 1. secConstrain
		for (int i = 0; i < secConstrainDataList.size(); i++) {
			final SectionConstrainData data = secConstrainDataList.get(i);
			final Expression constraint = model.addExpression("section_constraint_" + i);

			final double[] senArray = data.getSenArray();
			for (int j = 0; j < senArray.length; j++) {
				constraint.set(j, senArray[j]);
			}

			if (data.getRelationship() == Relationship.LEQ) {
				constraint.upper(data.getLimit() - data.getValue());
			} else {
				constraint.lower(data.getLimit() - data.getValue());
			}
		}


		// 2 g1+g2+g3.... == 0
		final Expression sumConstraint = model.addExpression("sum_constraint1");
		for (GenConstrainData data : deviceConstrainDataList) {
			sumConstraint.set(data.getIndex(), 1.0);
		}
		sumConstraint.upper(0);
		sumConstraint.lower(0);

		
		final Expression objective = model.addExpression("Objective").weight(1.0);
		for (GenConstrainData data : deviceConstrainDataList) {
				objective.set(data.getIndex(), data.getWeight());
			
		}
		
		objective.set(totalVars - 1, 1.0);
		result = model.maximise();

		System.out.println("Optimisation status: " + result.getState());

	}

}
