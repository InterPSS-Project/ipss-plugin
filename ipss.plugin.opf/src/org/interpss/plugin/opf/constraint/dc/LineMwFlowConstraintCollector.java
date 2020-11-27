package org.interpss.plugin.opf.constraint.dc;

import java.util.List;

import org.interpss.plugin.opf.OpfSolverFactory;
import org.interpss.plugin.opf.constraint.BaseConstraintCollector;
import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.plugin.opf.util.OpfDataHelper;

import com.interpss.opf.OpfBranch;
import com.interpss.opf.OpfNetwork;
import com.interpss.opf.datatype.OpfConstraintType;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;

public class LineMwFlowConstraintCollector extends BaseConstraintCollector {

	public LineMwFlowConstraintCollector(OpfNetwork opfNet,
			List<OpfConstraint> cstContainer) {
		super(opfNet, cstContainer);
	}	

	@Override
	public void collectConstraint() {
		double bij = DEFAULT_BIJ;		
		
		for (OpfBranch bra : opfNet.getBranchList()) {
			IntArrayList colNo = new IntArrayList();
			DoubleArrayList val = new DoubleArrayList();
			
			//OpfConstraint cst = new OpfConstraint();
			//if (bra.isAclfBranch()) {
				
			//AclfBranch aclfBra = (AclfBranch) bra;
			bij = (bra.getZ().getImaginary() > 0.00001) ? 1 / bra
					.getZ().getImaginary() : DEFAULT_BIJ; // in case x=0;
					
			
			bij = OpfDataHelper.round(bij,5);

			OpfBranch opfBra = (OpfBranch) bra;		
			double ratingMw = opfBra.getRatingMw1();				
			double ul = ratingMw;
			double ll = -ratingMw;				
			

			int fromBusIndex = bra.getFromBus().getSortNumber();
			int toBusIndex = bra.getToBus().getSortNumber();

			
			colNo.add(fromBusIndex + this.numOfGen);
			val.add(bij);
			
			colNo.add(toBusIndex + this.numOfGen);
			val.add(-bij);

			int id = cstContainer.size();
			String des = "Branch MW flow limit: " + bra.getFromPhysicalBusId()
					+ "-" + bra.getToPhysicalBusId() + "-" + bra.getCircuitNumber();

			OpfConstraint cst = OpfSolverFactory.createOpfConstraint(id, des, ul, ll, OpfConstraintType.LESS_THAN, colNo, val);
			cstContainer.add(cst);
			
			cst = OpfSolverFactory.createOpfConstraint(id, des, ul, ll, OpfConstraintType.LARGER_THAN, colNo, val);
			cstContainer.add(cst);	
		}
	}
}
