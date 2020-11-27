package org.interpss.plugin.opf.constraint.dc;

import java.util.List;

import org.interpss.plugin.opf.OpfSolverFactory;
import org.interpss.plugin.opf.constraint.BaseConstraintCollector;
import org.interpss.plugin.opf.constraint.OpfConstraint;

import com.interpss.opf.OpfBus;
import com.interpss.opf.OpfGen;
import com.interpss.opf.OpfNetwork;
import com.interpss.opf.datatype.OpfBusLimits;
import com.interpss.opf.datatype.OpfConstraintType;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;

public class GenMwOutputConstraintCollector extends BaseConstraintCollector{	
	
	public GenMwOutputConstraintCollector(OpfNetwork opfNet, List<OpfConstraint> cstContainer) {
		super(opfNet,cstContainer);		
	}

	@Override
	public void collectConstraint() {		
		
		int genIndex = 0;
		for (OpfBus opfBus : opfNet.getBusList()) {
			//OpfBus opfBus = (OpfBus)bus;
			if (opfBus.isOpfGen()) {
				IntArrayList colNo = new IntArrayList();
				DoubleArrayList val = new DoubleArrayList();
				// OpfConstraint cst = new OpfConstraint();
				OpfGen genOPF = opfBus.getOpfGen();	
	    		OpfBusLimits limits = genOPF.getOpfLimits();
	    		
				double ul = limits.getPLimit().getMax();
				double ll = limits.getPLimit().getMin();				
				
				int id = cstContainer.size();
				String des = "Gen MW upper limit @"+ opfBus.getId();	
				colNo.add(genIndex);
				val.add(1);				
				OpfConstraint cst = OpfSolverFactory.createOpfConstraint(id, des, ul, ll, OpfConstraintType.LESS_THAN, colNo, val);
				cstContainer.add(cst);
				
				des = "Gen MW upper limit @"+ opfBus.getId();
				cst = OpfSolverFactory.createOpfConstraint(id+1, des, ul, ll, OpfConstraintType.LARGER_THAN, colNo, val);
				cstContainer.add(cst);					
				
				genIndex++;
			}
		}		
	}
}
