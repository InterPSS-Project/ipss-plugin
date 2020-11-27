package org.interpss.plugin.opf.constraint.dc;

import java.util.List;

import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.opf.OpfSolverFactory;
import org.interpss.plugin.opf.constraint.BaseConstraintCollector;
import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.plugin.opf.util.OpfDataHelper;

import com.interpss.opf.OpfBus;
import com.interpss.opf.OpfNetwork;
import com.interpss.opf.datatype.OpfConstraintType;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;

public class BusMinAngleConstraintCollector extends BaseConstraintCollector{	
	private LimitType angleLimit = null;
	
	public BusMinAngleConstraintCollector(OpfNetwork opfNet,
			List<OpfConstraint> cstContainer, LimitType angleLimit) {
		super(opfNet, cstContainer);	
		this.angleLimit = angleLimit;
	}	

	@Override
	public void collectConstraint() {
		
		int swingBusIdx = OpfDataHelper.getSwingBusIndex(opfNet);
		for(OpfBus bus: opfNet.getBusList()){	
			IntArrayList colNo = new IntArrayList();
			DoubleArrayList val = new DoubleArrayList();			
			int sortNumber = bus.getSortNumber();			
			if(sortNumber ==swingBusIdx){
				int id = cstContainer.size();
				String des = "Swing bus @"+ bus.getId();	
				colNo.add(numOfGen+sortNumber);
				val.add(1);				
				OpfConstraint cst1 = OpfSolverFactory.createOpfConstraint(id, des, 0, -1000, OpfConstraintType.LESS_THAN, colNo, val);
				cstContainer.add(cst1);
				
				des = "Bus angle lower limit @"+ bus.getId();
				OpfConstraint cst2 = OpfSolverFactory.createOpfConstraint(id+1, des, 1000, 0, OpfConstraintType.LARGER_THAN,  colNo, val);
				cstContainer.add(cst2);				
			}else{
				int id = cstContainer.size();
				String des = "Bus angle upper limit @"+ bus.getId();			
				colNo.add(numOfGen+sortNumber);
				val.add(1);	
				OpfConstraint cst = OpfSolverFactory.createOpfConstraint(id, des, angleLimit.getMax(), -1000, OpfConstraintType.LESS_THAN,  colNo, val);
				cstContainer.add(cst);
				
				des = "Bus angle lower limit @"+ bus.getId();
				cst = OpfSolverFactory.createOpfConstraint(id+1, des, 1000, angleLimit.getMin(), OpfConstraintType.LARGER_THAN,  colNo, val);
				cstContainer.add(cst);	
			}
			
		}
		
	}

}
