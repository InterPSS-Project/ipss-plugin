package org.interpss.plugin.opf.constraint.dc;

import java.util.List;

import org.interpss.plugin.opf.constraint.BaseConstraintCollector;
import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.plugin.opf.constraint.OpfConstraint.cstType;
import org.interpss.plugin.opf.util.OpfDataHelper;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.net.Branch;
import com.interpss.opf.BaseOpfBranch;
import com.interpss.opf.BaseOpfNetwork;

public class LineMwFlowConstraintCollector extends BaseConstraintCollector {

	public LineMwFlowConstraintCollector(BaseOpfNetwork opfNet,
			List<OpfConstraint> cstContainer) {
		super(opfNet, cstContainer);
	}	

	@Override
	public void collectConstraint() {
		double bij = DEFAULT_BIJ;		
		
		for (Branch bra : opfNet.getBranchList()) {
			IntArrayList colNo = new IntArrayList();
			DoubleArrayList val = new DoubleArrayList();
			
			OpfConstraint cst = new OpfConstraint();
			//if (bra.isAclfBranch()) {
				
				AclfBranch aclfBra = (AclfBranch) bra;
				bij = (aclfBra.getZ().getImaginary() > 0.00001) ? 1 / aclfBra
						.getZ().getImaginary() : DEFAULT_BIJ; // in case x=0;
						
				
				bij = OpfDataHelper.round(bij,5);

				BaseOpfBranch opfBra = (BaseOpfBranch) bra;		
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

				cst = cst.setConstraint(id, des, ul, ll, cstType.lessThan,
						colNo, val);
				cstContainer.add(cst);
				
				cst = cst.setConstraint(id, des, ul, ll, cstType.largerThan,
						colNo, val);
				cstContainer.add(cst);	
				
			//}
		}

	}

}
