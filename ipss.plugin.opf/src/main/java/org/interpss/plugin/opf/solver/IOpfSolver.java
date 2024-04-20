package org.interpss.plugin.opf.solver;

import java.util.List;

import org.interpss.plugin.opf.constraint.OpfConstraint;


public interface IOpfSolver {
	/*public static enum networkType {AC, DC};
	public static enum solverType {LP, QP};*/
	public static enum constraintHandleType {AllIn, UserDefined};
	
	
	
	void build(List<OpfConstraint> cstContainer);
	boolean solve();
	
	/* out put the input file to a file in Matlab readable format 
	 *  The output file can be run in Matlab using linprog or quadprog
	*/
	void debug(String file);
	
	boolean isSolved();
	
	void calLMP();
	
	void attachedResult();
	
	long getIteration();
	
	double[] getSolution();
	
	double getObjectiveFunctionValue();
	
	//void printInputData();
	
	//void destroySolver();
	
	void printInputData(String fileName);	
	
	
	
	
	
	//boolean qpSolve();
	
	
	
	

	
	
	
	
	


}
