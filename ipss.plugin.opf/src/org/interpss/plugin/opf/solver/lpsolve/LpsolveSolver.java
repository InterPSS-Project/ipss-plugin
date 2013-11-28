package org.interpss.plugin.opf.solver.lpsolve;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import lpsolve.LpSolve;
import lpsolve.LpSolveException;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.interpss.plugin.opf.common.OPFLogger;
import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.plugin.opf.constraint.dc.ActivePowerEqnConstraintCollector;
import org.interpss.plugin.opf.constraint.dc.BusMinAngleConstraintCollector;
import org.interpss.plugin.opf.constraint.dc.GenMwOutputConstraintCollector;
import org.interpss.plugin.opf.constraint.dc.LineMwFlowConstraintCollector;
import org.interpss.plugin.opf.objectiveFunction.LpsolveSolverObjectiveFunctionCollector;
import org.interpss.plugin.opf.solver.AbstractOpfSolver;
import org.interpss.plugin.opf.util.OpfDataHelper;

import com.interpss.core.net.Bus;
import com.interpss.opf.OpfBus;
import com.interpss.opf.OpfNetwork;

public class LpsolveSolver extends AbstractOpfSolver {

	private LpSolve lpsolver;

	public LpsolveSolver(OpfNetwork opfNet, constraintHandleType constType) {
		super(opfNet, constType);
		this.numOfVar = numOfGen + numOfBus;
		try {
			lpsolver = LpSolve.makeLp(0, numOfVar + numOfGen);
		} catch (LpSolveException e) {
			e.printStackTrace();
		}
		this.setVarName(lpsolver);
	}

	/*
	 * Build sequence: objective function to be built at last
	 */

	@Override
	public void build(List<OpfConstraint> cstContainer) {		

		new ActivePowerEqnConstraintCollector(opfNet, cstContainer)
				.collectConstraint();

		new LineMwFlowConstraintCollector(opfNet, cstContainer)
				.collectConstraint();

		new GenMwOutputConstraintCollector(opfNet, cstContainer)
				.collectConstraint();

		new BusMinAngleConstraintCollector(opfNet, cstContainer, BusAngleLimit)
				.collectConstraint();

		lpsolver.setAddRowmode(true);

		new LpsolveSolverInputBuilder(cstContainer)
				.buildInput(lpsolver);

		LpsolveSolverObjectiveFunctionCollector objBuilder = new LpsolveSolverObjectiveFunctionCollector(
				opfNet);
		try {
			objBuilder.processGenCostFunction(lpsolver);
			//int refineNum = 2;
			objBuilder.genCostFunctionRefinement(lpsolver);
		} catch (LpSolveException e) {
			OPFLogger.getLogger().severe(e.toString());
			e.printStackTrace();
		}	

		lpsolver.setAddRowmode(false);				
		
	}
	
	
	
	@Override
	public boolean solve() {
		OPFLogger.getLogger().info("Running DC Optimal Power Flow Using LP solver....");		
		Long startTime = System.currentTimeMillis();
		this.build(cstContainer);
		int ret = 0;
		lpsolver.setMinim();
		lpsolver.setVerbose(LpSolve.IMPORTANT);

		try {
			ret = lpsolver.solve();
		} catch (LpSolveException e) {
			OPFLogger.getLogger().severe(e.toString());
		}
		
		if (ret == LpSolve.OPTIMAL)
			ret = 0;
		else
            OPFLogger.getLogger().severe(this.retriveSolutionInfo(ret));
		
		if (ret == 0) {
			this.isSolved = true;
			Long endTime = System.currentTimeMillis();
			Long duration = endTime - startTime;	
			OPFLogger.getLogger().info("Optimization terminated.");	
			OPFLogger.getLogger().info("Converged in " + OpfDataHelper.round(duration, 3) +" milliseconds.");			
			try {
				ofv = lpsolver.getObjective(); // in $/(pu*h)				
				int xsize = lpsolver.getNcolumns();
				optimX = new double[xsize];
				lpsolver.getVariables(optimX);
				// attach result to network
				this.attachedResult();
				this.calLMP();				
			} catch (LpSolveException e) {
				OPFLogger.getLogger().severe(e.toString());
			}
		}
		return isSolved;
	}

	public void printShadowPrice() {
		lpsolver.printDuals();
	}

	@Override
	public void calLMP() {
		int cnt = 1;
		int numOfConstraint = lpsolver.getNorigRows();
		int numOfVar = lpsolver.getNcolumns();
		double[] shadowPrice = new double[numOfConstraint + numOfVar + 1];
		try {
			lpsolver.getDualSolution(shadowPrice);
		} catch (LpSolveException e) {			
			OPFLogger.getLogger().severe(e.toString());
		}
		
		double baseMVA = opfNet.getBaseKva() / 1000.0;
		double lmp = 0;
		for (Bus b : opfNet.getBusList()) {
			OpfBus bus1 = (OpfBus) b;
			lmp = Math.abs(shadowPrice[cnt++]);
			bus1.setLMP(lmp/baseMVA );
		}
	}

	@Override
	public long getIteration() {
		return lpsolver.getTotalIter();
	}

	@Override
	public double[] getSolution() {
		return this.optimX;
	}

	@Override
	public double getObjectiveFunctionValue() {
		// ofv is in $/(pu*h)
		//double baseKva = opfNet.getBaseKva()/1000; // in MW
		return ofv;
	}

	@Override
	public void printInputData(String fileName) {
		try {
			lpsolver.writeLp(fileName);
			OPFLogger.getLogger().info("The input data model has been saved to: "+fileName);
		} catch (LpSolveException e) {
			OPFLogger.getLogger().severe(e.toString());
			//e.printStackTrace();
		}

	}

	private String retriveSolutionInfo(int ret) {
		String s = "";
		if (ret == 1)
			s = "The solution is not guaranteed the most optimal one!";
		else if (ret == 2)
			s = "The model is infeasible!";
		else if (ret == 3)
			s = "The model is unbounded!";
		else if (ret == 4)
			s = "The model is degenerative! ";
		else if (ret == 5)
			s = "Numerical failure encountered!";
		else if (ret == 6)
			s = "It is aborted by user!";
		else if (ret == 7)
			s = "Time out occurs during solution.";
		else if (ret == 9)
			s = "The model could be solved by presolve!";
		else if (ret == 10)
			s = "The B&B routine failed!";
		else if (ret == 11)
			s = "The B&B was stopped!";
		else if (ret == 12)
			s = "A feasible B&B solution was found!";
		else if (ret == 13)
			s = "No feasible B&B solution found!";
		return s;
	}

	
	public void destroySolver() {
		lpsolver.deleteLp();
	}	

	public void printDual() {
		lpsolver.printDuals();
	}

	private void setVarName(LpSolve lpsolver) {
		int busIdx = 1;
		int genIndex = 1;
		try {
			for (Bus b : opfNet.getBusList()) {
				lpsolver.setColName( busIdx + this.numOfGen, "x" + (b.getSortNumber() + 1));
				if(opfNet.isOpfGenBus(b)){
					lpsolver.setColName(genIndex, "Pg" + (b.getSortNumber()+1));
					lpsolver.setColName(genIndex + this.numOfVar, "y" + (b.getSortNumber()+1));
					genIndex++;
				}				
				busIdx++;
			}
		} catch (LpSolveException e) {			
			OPFLogger.getLogger().severe(e.toString());
		}

	}

	@Override
	public void debug(String file) {
		OPFLogger.getLogger().info("Running DCOPF debug mode for LP solver...");
		this.build(cstContainer);
		try {
			outputMatrix( file);
			OPFLogger.getLogger().info("Output file for debug purpose has been saved to: "+file);
		} catch (Exception e) {
			OPFLogger.getLogger().severe(e.toString());
			//e.printStackTrace();
		}	
		
	}
	
	public void outputMatrix(String file) throws Exception{
		int row = lpsolver.getNrows()+1;
		int col = lpsolver.getNcolumns()+1;		
		ArrayRealVector f = new ArrayRealVector(col-1);
		for (int j =1; j<col; j++){
			f.setEntry(j-1, lpsolver.getMat(0, j));
		}
		Array2DRowRealMatrix Aeq = new Array2DRowRealMatrix(numOfBus, col-1);
		ArrayRealVector beq = new ArrayRealVector(numOfBus);
		
		for (int i =1;i<numOfBus+1;i++){
			for (int j =1; j<col; j++){
				Aeq.setEntry(i-1, j-1, lpsolver.getMat(i, j));
			}
			beq.setEntry(i-1, lpsolver.getRh(i));
		}
		
		Array2DRowRealMatrix Aiq = new Array2DRowRealMatrix(row - numOfBus-1, col-1);
		ArrayRealVector biq = new ArrayRealVector(row - numOfBus-1);
		
		int cnt = 0;
		for (int i =numOfBus +1;i<row ;i++){
			for (int j =1; j<col; j++){
				Aiq.setEntry(cnt, j-1, lpsolver.getMat(i, j));
			}
			biq.setEntry(cnt, lpsolver.getRh(i));
			cnt++;
		}
		
		ArrayRealVector lb = new ArrayRealVector(col-1);
		for (int i=1; i<col-1;i++){
			lb.setEntry(i-1, lpsolver.getLowbo(i));
		}
		
		ArrayRealVector ub = new ArrayRealVector(col-1);
		for (int i=1; i<col;i++){
			ub.setEntry(i-1, lpsolver.getUpbo(i));
		}
		
		writeMatlabInputFile(file,f,Aeq,beq,Aiq,biq,ub,lb);	
	}
	

	private void writeMatlabInputFile(String file, ArrayRealVector f,Array2DRowRealMatrix Aeq,
			ArrayRealVector beq,Array2DRowRealMatrix Aiq, ArrayRealVector biq,
			ArrayRealVector ub,ArrayRealVector lb) throws IOException{
		
		FileWriter fstream = new FileWriter(file);
		BufferedWriter out = new BufferedWriter(fstream);		
		try {
			helper.writeVector(out, f, "f=[ ");
			helper.writeMatrix(out, Aeq, "Aeq=[");
			helper.writeVector(out, beq, "beq=[ ");
			helper.writeMatrix(out, Aiq, "Aiq=[");
			helper.writeVector(out, biq, "biq=[ ");
			helper.writeVector(out, lb, "lb=[ ");
			helper.writeVector(out, ub, "ub=[ ");
			String linprog ="x = linprog(f,Aiq,biq,Aeq,beq,lb,ub);";
			out.append(linprog);
		} catch (Exception e) {	
			OPFLogger.getLogger().severe(e.toString());
			//e.printStackTrace();
		}
		  
		out.close();
		
	}
	

}
