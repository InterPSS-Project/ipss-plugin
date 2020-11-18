package org.interpss.plugin.opf.constraint;

import com.interpss.opf.cst.impl.BaseOpfConstraintImpl;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;

public class OpfConstraint extends BaseOpfConstraintImpl {
	private IntArrayList colNo;
	private DoubleArrayList val;
	
	public void setColNo(IntArrayList colNo){
		this.colNo = colNo;		
	}
	
	public IntArrayList getColNo(){
		return this.colNo;
	}
	
	public void setVal(DoubleArrayList val){
		this.val = val;
	}
	
	public DoubleArrayList getVal(){
		return this.val ;
	}
}
