package org.interpss.plugin.opf.solver.giqpsolve;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import org.interpss.plugin.opf.common.OPFLogger;
import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.plugin.opf.constraint.dc.ActivePowerEqnConstraintCollector;
import org.interpss.plugin.opf.constraint.dc.BusMinAngleConstraintCollector;
import org.interpss.plugin.opf.constraint.dc.GenMwOutputConstraintCollector;
import org.interpss.plugin.opf.constraint.dc.LineMwFlowConstraintCollector;
import org.interpss.plugin.opf.objectiveFunction.GIQPObjectiveFunctionCollector;
import org.interpss.plugin.opf.solver.AbstractOpfSolver;
import org.interpss.plugin.opf.util.OpfDataHelper;

import com.interpss.core.net.Bus;
import com.interpss.opf.BaseOpfBranch;
import com.interpss.opf.BaseOpfBus;
import com.interpss.opf.BaseOpfNetwork;

import cern.colt.matrix.impl.SparseDoubleMatrix1D;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;

public class GIQPSolver extends AbstractOpfSolver {
	
	QuadProgJ solver = null;	
	private SparseDoubleMatrix2D G = null;
	private SparseDoubleMatrix2D Ceq = null;
	private SparseDoubleMatrix2D Ciq = null;
	private SparseDoubleMatrix1D a = null;
	private SparseDoubleMatrix1D beq = null;
	private SparseDoubleMatrix1D biq = null;
	
	//Constructor
	public GIQPSolver(BaseOpfNetwork<BaseOpfBus<?>, BaseOpfBranch> opfNet, constraintHandleType constType) {
		super(opfNet, constType);		
		this.numOfVar = numOfGen + numOfBus;
		}

	// build order: Equality -> Inequality 
	@Override
	public void build(List<OpfConstraint> cstContainer) {		
		new ActivePowerEqnConstraintCollector(opfNet,cstContainer)
					.collectConstraint();
		
		new LineMwFlowConstraintCollector(opfNet,cstContainer)
					.collectConstraint();
		
		new GenMwOutputConstraintCollector(opfNet,cstContainer)
					.collectConstraint();
		
		
		new BusMinAngleConstraintCollector(opfNet,cstContainer,	BusAngleLimit)
					.collectConstraint();	
					
		GIQPSolverInputMatrixBuilder inputBuilder = new GIQPSolverInputMatrixBuilder(this.cstContainer);
		
		int startIdx = 0;
		int endIdx = this.numOfBus;
		int size = endIdx - startIdx ;
		Ceq = new SparseDoubleMatrix2D(size,this.numOfVar);
		beq = new SparseDoubleMatrix1D(size);
		
		inputBuilder.buildCeqAndBiq(Ceq, beq,startIdx, endIdx);		
		
		startIdx = this.numOfBus;
		endIdx = cstContainer.size();
		size = endIdx - startIdx ;
		Ciq = new SparseDoubleMatrix2D(size,this.numOfVar);
		biq = new SparseDoubleMatrix1D(size);
		inputBuilder.buildCiqAndBiq(Ciq, biq, startIdx, endIdx);	
		
		GIQPObjectiveFunctionCollector objBuilder = new GIQPObjectiveFunctionCollector(opfNet);
		G = objBuilder.buildG();
		a = objBuilder.buildA();	
		
		Algebra al = new Algebra();
		Ceq = (SparseDoubleMatrix2D) al.transpose(Ceq);
		Ciq = (SparseDoubleMatrix2D) al.transpose(Ciq);
		
		
	}
	@Override
	public boolean solve() {
		OPFLogger.getLogger().info("Running DC Optimal Power Flow Using QP solver....");
		Long startTime = System.currentTimeMillis();
		this.build(cstContainer);
		
		solver = new QuadProgJ(G,a,Ceq,beq,Ciq,biq);
		
		try{
			this.optimX = solver.getMinX();
			// attach result to network
			this.attachedResult();
			this.calLMP();			
			this.isSolved = true;
			Long endTime = System.currentTimeMillis();
			Long duration = endTime - startTime;	
			OPFLogger.getLogger().info("Optimization terminated.");	
			OPFLogger.getLogger().info("Converged in " + OpfDataHelper.round(duration, 3) +" milliseconds.");	
		}catch(Exception e){
			OPFLogger.getLogger().severe(e.toString());			
			return false;
		}	
		
		return this.isSolved;
	}
	  /**
	   * Computes and returns nx1 solution vector (x)
	   * @return doulbe[]
	   */
	public double[] getSolution(){
		return optimX;
	}
	public boolean IsSolutionFeasibleandOptimal(){
		return solver.getIsFeasibleAndOptimal();
	}
	  /**
	   * Computes and returns (q-meq)x1 Lagrangian multiplier vector for binding
	   * inequality constraints only
	   */
	public double[] getBindingIneqMultipliers(){
		return solver.getBindingIneqMultipliers();
	}
	  /**
	   * Computes and returns meqx1 Lagrangian multiplier vector for equality
	   * constraints only
	   * @return doulbe[]
	   */
	public double[] getEqMultipliers(){
		return solver.getEqMultipliers();
	}
	  /**
	   * Computes and returns the full miqx1 Lagrangian multiplier vector for both
	   * binding and un-binding inequality constraints (the multipliers for un-binding
	   * constraints are zeros)
	   */
	public double[] getIneqMultipiers(){
		return solver.getIneqMultipiers();
	}
	  /**
	   * Computes and returns the minimized function value (f(x*))
	   * @return double
	   */
	public double getObjectiveFunctionValue(){
		return solver.getMinF() + opfNet.getTotalFixedCost();
	}
	  /**
	   * Computes and returns qx1 Lagrangian multiplier vector (u)
	   * Note: the first meq elements of u is the Lagrangian multipliers
	   *       for equality constraints
	   * @return doulbe[]
	   */
	  public double[] getAllMultipliers() {
	    return solver.getAllMultipliers();
	  }
	  /**
	   * Returns the number of constraints that have been added to active set
	   * @return int
	   */
	  public int getNumConstraintsAdded() {
	    return solver.getNumConstraintsAdded();
	  }

	  /**
	   * Returns the number of constraints that have been dropped from active set
	   * @return int
	   */
	  public int getNumConstraintsDropped() {
	    return solver.getNumConstraintsDropped();
	  }
	  /**
	   * Returns the number of total binding constraints in the final active set.
	   * @return int
	   */
	  public int getTotalNumBC() {
	    return solver.getTotalNumBC();
	  }
	  /**
	   * Returns the number of binding constraints in the final active set.
	   * Note: The binding constraints here exclude the equality
	   *       constraints, because equality constraints are always binding.
	   * @return int
	   */
	  public int getNumBC() {
	    return solver.getNumBC();
	  }
	  /**
	   * Returns the original indices of binding constraints as the ith column in
	   * the constraint matrix C, including eq. constraints as the first meq
	   * elements.
	   * @return int[]
	   */
	  public int[] getActiveSet() {
	    return solver.getActiveSet();
	  }


	public void printInputData(){
		solver.printInputData();
	}
	public void printOutputSolution(){
		solver.printOutputSolution();
	}

	@Override
	public boolean isSolved() {		
		return this.isSolved;
	}
	@Override
	public void calLMP() {		
		// set  LMP to opfNet bus object				
		int cnt = 0;
		double baseMVA=opfNet.getBaseKva()/1000.0;
		for(Bus b: opfNet.getBusList()){				
			BaseOpfBus bus1=(BaseOpfBus) b;
			bus1.setLMP(getEqMultipliers()[cnt++]/baseMVA);
		}		
	}	
	@Override
	public long getIteration() {		
		return solver.getNumIterations();
	}

	@Override
	public void printInputData(String fileName) {				
	}
	

	@Override
	public void debug(String file) {
		OPFLogger.getLogger().info("Running DCOPF debug mode for QP solver...");
		this.build(cstContainer);
		try {
			writeMatlabInputFile(file,G,a,Ceq,beq,Ciq, biq);
			OPFLogger.getLogger().info("Output file for debug purpose has been saved to: "+file);
		} catch (IOException e) {
			OPFLogger.getLogger().severe(e.toString());
			e.printStackTrace();
		}
		
	}

	private void writeMatlabInputFile(String file, SparseDoubleMatrix2D G,SparseDoubleMatrix1D a,
			SparseDoubleMatrix2D Ceq,SparseDoubleMatrix1D beq,SparseDoubleMatrix2D Ciq,
			SparseDoubleMatrix1D biq		) throws IOException{
		
		FileWriter fstream = new FileWriter(file);
		BufferedWriter out = new BufferedWriter(fstream);		
		try {
			helper.writeMatrix(out, G, "G=[");
			helper.writeVector(out, a, "a=[ ");
			helper.writeMatrix(out, Ceq, "Ceq=[");
			helper.writeVector(out, beq, "beq=[ ");
			helper.writeMatrix(out, Ciq, "Ciq=[");
			helper.writeVector(out, biq, "biq=[ ");
			
			String linprog ="x = quadprog(G,a,Ciq,biq,Aeq,beq);";
			out.append(linprog);
		} catch (Exception e) {		
			OPFLogger.getLogger().severe(e.toString());
			e.printStackTrace();
		}
		  
		out.close();
		
	}
}
