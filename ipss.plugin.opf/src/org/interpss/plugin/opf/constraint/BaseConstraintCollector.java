package org.interpss.plugin.opf.constraint;

import java.util.List;

import org.interpss.plugin.opf.util.OpfDataHelper;

import com.interpss.opf.OpfNetwork;

public abstract class BaseConstraintCollector implements IConstraintCollector {
	protected OpfNetwork opfNet = null;		
	public final static double DEFAULT_BIJ = 10000; 
	protected int numOfVar = 0;
	protected int numOfBus = 0;
	protected int numOfBranch = 0;
	protected int numOfGen = 0;	
	protected List<OpfConstraint> cstContainer;	
	
	
	public BaseConstraintCollector(OpfNetwork opfNet, List<OpfConstraint> cstContainer){
		this.opfNet = opfNet;
		this.cstContainer = cstContainer;		
		this.numOfBus = this.opfNet.getNoActiveBus();
		this.numOfGen = OpfDataHelper.getNoOfGen(this.opfNet);
		this.numOfBranch = this.opfNet.getNoActiveBranch();		
		this.numOfVar = numOfGen + numOfBus;
	}		
}
