package org.interpss.plugin.opf.objectiveFunction;

import com.interpss.opf.OpfNetwork;

public class BaseObjectiveFunctionCollector {
	
	protected OpfNetwork opfNet = null;	
	protected int numOfGen = 0;	
	protected int numOfBus = 0;	
	protected int numOfVar = 0;
	
	public BaseObjectiveFunctionCollector(OpfNetwork opfNet){
		this.opfNet = opfNet;		
		this.numOfGen = this.opfNet.getNoOpfGen();	
		numOfBus = opfNet.getNoActiveBus();
		this.numOfVar = numOfGen + numOfBus;
		
	}
	

}
