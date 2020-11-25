package org.interpss.plugin.opf.constraint.dc;

import java.util.List;

import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.opf.OpfSolverFactory;
import org.interpss.plugin.opf.constraint.BaseConstraintCollector;
import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.plugin.opf.util.OpfDataHelper;

import com.interpss.core.net.Bus;
import com.interpss.opf.BaseOpfNetwork;
import com.interpss.opf.cst.OpfConstraintType;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;

public class BusMinAngleConstraintCollector extends BaseConstraintCollector{	
	private LimitType angleLimit = null;
	
	public BusMinAngleConstraintCollector(BaseOpfNetwork opfNet,
			List<OpfConstraint> cstContainer, LimitType angleLimit) {
		super(opfNet, cstContainer);	
		this.angleLimit = angleLimit;
	}	

	@Override
	public void collectConstraint() {
		
		int swingBusIdx = OpfDataHelper.getSwingBusIndex(opfNet);
		for(Bus b: opfNet.getBusList()){	
			IntArrayList colNo = new IntArrayList();
			DoubleArrayList val = new DoubleArrayList();		
			int sortNumber = b.getSortNumber();			
			if(sortNumber ==swingBusIdx){
				OpfConstraint cst = new OpfConstraint();				
				int id = cstContainer.size();
				String des = "Swing bus @"+ b.getId();	
				colNo.add(numOfGen+sortNumber);
				val.add(1);				
				cst = OpfSolverFactory.createOpfConstraint(id, des, 0, -1000, OpfConstraintType.LESS_THAN, colNo, val);
				cstContainer.add(cst);
				
				des = "Bus angle lower limit @"+ b.getId();
				cst = OpfSolverFactory.createOpfConstraint(id+1, des, 1000, 0, OpfConstraintType.LARGER_THAN,  colNo, val);
				cstContainer.add(cst);				
			}else{
				OpfConstraint cst = new OpfConstraint();			
				int id = cstContainer.size();
				String des = "Bus angle upper limit @"+ b.getId();			
				colNo.add(numOfGen+sortNumber);
				val.add(1);	
				cst = OpfSolverFactory.createOpfConstraint(id, des, angleLimit.getMax(), -1000, OpfConstraintType.LESS_THAN,  colNo, val);
				cstContainer.add(cst);
				
				des = "Bus angle lower limit @"+ b.getId();
				cst = OpfSolverFactory.createOpfConstraint(id+1, des, 1000, angleLimit.getMin(), OpfConstraintType.LARGER_THAN,  colNo, val);
				cstContainer.add(cst);	
			}
			
		}
		
	}

}
