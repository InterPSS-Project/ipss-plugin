package org.interpss.plugin.opf.objectiveFunction;

import org.interpss.plugin.opf.util.OpfDataHelper;

import com.interpss.opf.dep.BaseOpfNetwork;

public class BaseObjectiveFunctionCollector {
	
	protected BaseOpfNetwork opfNet = null;	
	protected int numOfGen = 0;	
	protected int numOfBus = 0;	
	protected int numOfVar = 0;
	protected OpfDataHelper helper = null;
	
	public BaseObjectiveFunctionCollector(BaseOpfNetwork opfNet){
		this.opfNet = opfNet;		
		this.helper = new OpfDataHelper();
		this.numOfGen = helper.getNoOfGen(this.opfNet);	
		numOfBus = opfNet.getNoActiveBus();
		this.numOfVar = numOfGen + numOfBus;
		
	}
	

}
