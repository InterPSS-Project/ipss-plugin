package org.interpss.plugin.opf.constraint;

import com.interpss.opf.cst.impl.BaseOpfConstraintImpl;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;

public class OpfConstraint extends BaseOpfConstraintImpl {
	private IntArrayList colNo;
	private DoubleArrayList val;
	/*
	public OpfConstraint (int id, String des, double ul, double ll, 
			OpfConstraintType type,IntArrayList colNo,DoubleArrayList val){
		setDesc(des);
		setId(id);
		setLowerLimit(ll);
		setUpperLimit(ul);
		setColNo(colNo);
		setVal(val);
	    setCstType(type);
	}
	*/
	
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
