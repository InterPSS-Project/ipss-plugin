package org.interpss.plugin.opf.constraint.dc;

import java.util.List;

import org.interpss.plugin.opf.OpfSolverFactory;
import org.interpss.plugin.opf.constraint.BaseConstraintCollector;
import org.interpss.plugin.opf.constraint.OpfConstraint;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.net.Bus;
import com.interpss.opf.cst.ConstraintFactory;
import com.interpss.opf.cst.OpfBusLimits;
import com.interpss.opf.cst.OpfConstraintType;
import com.interpss.opf.dep.BaseOpfNetwork;
import com.interpss.opf.dep.OpfGenBus;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;

public class GenMwOutputConstraintCollector extends BaseConstraintCollector{	
	
	public GenMwOutputConstraintCollector(BaseOpfNetwork opfNet, List<OpfConstraint> cstContainer) {
		super(opfNet,cstContainer);		
	}


	@Override
	public void collectConstraint() {		
		
		int genIndex = 0;
		for (Bus bus : opfNet.getBusList()) {
			
			AclfBus b = (AclfBus)bus;
			if (b.isGen()) {
				IntArrayList colNo = new IntArrayList();
				DoubleArrayList val = new DoubleArrayList();
				OpfConstraint cst = new OpfConstraint();	
				OpfGenBus genOPF = (OpfGenBus) bus;	
	    		OpfBusLimits con = genOPF.getConstraints();
	    		
				double ul = con.getPLimit().getMax();
				double ll = con.getPLimit().getMin();				
				
				int id = cstContainer.size();
				String des = "Gen MW upper limit @"+ b.getId();	
				colNo.add(genIndex);
				val.add(1);				
				cst = OpfSolverFactory.createOpfConstraint(id, des, ul, ll, OpfConstraintType.LESS_THAN, colNo, val);
				cstContainer.add(cst);
				
				des = "Gen MW upper limit @"+ b.getId();
				cst = OpfSolverFactory.createOpfConstraint(id+1, des, ul, ll, OpfConstraintType.LARGER_THAN, colNo, val);
				cstContainer.add(cst);					
				
				genIndex++;
			}
		}		
	}
	


	

	
}
