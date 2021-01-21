package org.interpss.QA.compare.aclf;

import org.interpss.QA.compare.DataComparatorAdapter;
import org.interpss.numeric.sparse.ISparseEqnDouble;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.JacobianMatrixType;

/**
 * AclfNetwork data comparator
 * 
 * @author mzhou
 *
 */
public class AclfNetDataComparator extends DataComparatorAdapter<BaseAclfNetwork<?,?>, BaseAclfNetwork<?,?>> {
	boolean compareB1Matrix = false;
	
	@Override public boolean compare(BaseAclfNetwork<?,?> baseNet, BaseAclfNetwork<?,?> net) {
		boolean ok = true;
		if (!NumericUtil.equals(baseNet.getBaseKva(), net.getBaseKva())) {
			this.msg += "\nnet.baseKva not equal, " + baseNet.getBaseKva() + "(base), " + net.getBaseKva(); ok = false; }

		if (baseNet.isBusNumberArranged() != net.isBusNumberArranged()) {
			this.msg += "\nnet.busNumberArranged not equal, " + baseNet.isBusNumberArranged() + "(base), " + net.isBusNumberArranged(); ok = false;
		}
		
		if (baseNet.getBusList().size() != net.getBusList().size()) {
			this.msg += "\nnet.busList.size not equal, " + baseNet.getBusList().size() + "(base), " + net.getBusList().size(); ok = false; 	}

		if (baseNet.getNoActiveBus() != net.getNoActiveBus()) {
			this.msg += "\nnet.noActiveBus not equal, " + baseNet.getNoActiveBus() + "(base), " + net.getNoActiveBus(); ok = false; }
		
		if (baseNet.getBranchList().size() != net.getBranchList().size()) {
			this.msg += "\nnet.branchList.size not equal, " + baseNet.getBranchList().size() + "(base), " + net.getBranchList().size(); ok = false; }

		if (baseNet.getNoActiveBranch() != net.getNoActiveBranch()) {
			this.msg += "\nnet.noActiveBranch not equal, " + baseNet.getNoActiveBranch() + "(base), " + net.getNoActiveBranch(); ok = false; }
	
		if (this.compareB1Matrix) {
			System.out.println("Compare B1 matrix ...");
			try {
				if (!compareBMatrix(baseNet, net))
					ok = false;
			} catch (InterpssException e) {
				e.printStackTrace();
			}
			System.out.println("Compare B1 matrix completed");
		}
		
		this.msg += "\n";
		return ok;
	}			
	
	private boolean compareBMatrix(BaseAclfNetwork<?,?> baseNet, BaseAclfNetwork<?,?> net) throws InterpssException {
		boolean ok = true;
	
		//baseNet.accept(CoreObjectFactory.createBusNoArrangeVisitor());	  
		//net.accept(CoreObjectFactory.createBusNoArrangeVisitor());
		baseNet.arrangeBusNumber();	  
		net.arrangeBusNumber();	 
		ISparseEqnDouble baseB1 = baseNet.formB1Matrix(JacobianMatrixType.REDUCED_BMATRIX);
		ISparseEqnDouble b1 = net.formB1Matrix(JacobianMatrixType.REDUCED_BMATRIX);

		if (baseB1.getDimension() != b1.getDimension()) {
			this.msg += "\nb1.dimension not equal, " + baseB1.getDimension() + "(base), " + 
		                b1.getDimension();
			ok = false;
		}
		
		int cnt = 0;
		for (int i = 0; i < baseB1.getDimension(); i++) {
			for (int j = 0; j < baseB1.getDimension(); j++) {
				if (!NumericUtil.equals(baseB1.getAij(i, j), b1.getAij(i, j))) {
					this.msg += "\nb1.aij not equal, <" + i + ", " + j + "> " + baseB1.getAij(i, j) + "(base), " + 
							b1.getAij(i, j);
					ok = false;
					if (cnt++ > 100)
						return ok;
				}
			}
		}
		return ok;
	}
}
