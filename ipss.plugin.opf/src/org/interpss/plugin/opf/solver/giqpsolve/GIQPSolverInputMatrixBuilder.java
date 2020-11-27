package org.interpss.plugin.opf.solver.giqpsolve;

import java.util.List;

import org.interpss.plugin.opf.constraint.OpfConstraint;

import com.interpss.opf.cst.OpfConstraintType;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;
import cern.colt.matrix.impl.SparseDoubleMatrix1D;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;

public class GIQPSolverInputMatrixBuilder {
	
	
	private List<OpfConstraint> cstContainer = null;
	public GIQPSolverInputMatrixBuilder(List<OpfConstraint> cstContainer){
		this.cstContainer = cstContainer;		
	}

	public void buildCeqAndBiq(SparseDoubleMatrix2D Ceq,SparseDoubleMatrix1D beq,
			int startIdx, int endIdx){				
		for( int i=startIdx;i< endIdx; i++){
			OpfConstraint con = cstContainer.get(i);
					
			IntArrayList rowIdx = con.getColNo(); 
			//IntArrayList colIdx = new IntArrayList();
			DoubleArrayList val = con.getVal();			
			//row.getNonZeros(rowIdx, val);			
			for (int j=0; j<rowIdx.size();j++){
				Ceq.set(i, rowIdx.get(j), val.get(j));
			}			
			double rh = con.getLowerLimit();
			beq.set(i,rh);
		}	
		
	}
	public void buildCiqAndBiq(SparseDoubleMatrix2D Ciq,SparseDoubleMatrix1D biq,
			int startIdx, int endIdx){
		int cnt =0;
		for( OpfConstraint con : cstContainer ) {
					
			IntArrayList idx = con.getColNo(); 
			DoubleArrayList val = con.getVal();
			
			OpfConstraintType type = con.getCstType();
			if(type.equals(OpfConstraintType.LARGER_THAN)){
				for (int j=0; j<idx.size();j++){
					Ciq.set(cnt, idx.get(j), val.get(j));
				}
				double rh = con.getLowerLimit();
				biq.set(cnt, rh);
				cnt++;
			}else if(type.equals(OpfConstraintType.LESS_THAN)){
				for (int j=0; j<idx.size();j++){					
					Ciq.set(cnt, idx.get(j), -val.get(j));
				}
				double rh = con.getUpperLimit();
				biq.set(cnt, -rh);	
				cnt++;
			}
			
		}
	}
}
