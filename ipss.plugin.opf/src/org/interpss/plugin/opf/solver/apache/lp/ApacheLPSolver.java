package org.interpss.plugin.opf.solver.apache.lp;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.math3.exception.MathIllegalStateException;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.linear.SparseRealVector;
import org.apache.commons.math3.optimization.GoalType;
import org.apache.commons.math3.optimization.PointValuePair;
import org.apache.commons.math3.optimization.linear.LinearConstraint;
import org.apache.commons.math3.optimization.linear.LinearObjectiveFunction;
import org.apache.commons.math3.optimization.linear.Relationship;
import org.apache.commons.math3.optimization.linear.SimplexSolver;
import org.interpss.plugin.opf.common.OPFLogger;
import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.plugin.opf.constraint.dc.ActivePowerEqnConstraintCollector;
import org.interpss.plugin.opf.constraint.dc.BusMinAngleConstraintCollector;
import org.interpss.plugin.opf.constraint.dc.GenMwOutputConstraintCollector;
import org.interpss.plugin.opf.constraint.dc.LineMwFlowConstraintCollector;
import org.interpss.plugin.opf.objectiveFunction.ApacheLpsolveSolverObjectiveFunctionCollector;
import org.interpss.plugin.opf.solver.AbstractOpfSolver;

import com.interpss.opf.dep.OpfNetwork;

public class ApacheLPSolver extends AbstractOpfSolver{

	private Collection<LinearConstraint> constraintCollection = null;
	private LinearObjectiveFunction objFunc = null;
	private SimplexSolver solver = null;
	public ApacheLPSolver(OpfNetwork opfNet, constraintHandleType constType) {
		super(opfNet, constType);
		this.constraintCollection = new ArrayList<LinearConstraint>();
		this.numOfVar = numOfGen + numOfBus;
		this.solver = new SimplexSolver();
	}

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

		ApacheLpsolveSolverObjectiveFunctionCollector objBuilder = new ApacheLpsolveSolverObjectiveFunctionCollector(
				opfNet);
		SparseRealVector objVec = objBuilder.processGenCostFunction();
		
		objFunc =  new LinearObjectiveFunction(objVec, 0);
		
		// add the additional constraints to constraint container
		objBuilder.genCostFunctionRefinement(cstContainer);
		
		
		new ApacheLpsolveSolverInputBuilder(cstContainer, constraintCollection, numOfVar + numOfGen)
					.buildInput();
		
	}

	
	@Override
	public boolean solve() {
		
		this.build(cstContainer);
		try {			
	        //debug(constraintCollection, objFunc);
	        PointValuePair x = solver.optimize(objFunc, constraintCollection, GoalType.MINIMIZE, false);

	        optimX = x.getPoint();
	        this.attachedResult();
			isSolved = true;
		} catch (MathIllegalStateException e) {
			isSolved = false;
			OPFLogger.getLogger().severe(e.toString());
		}
		return isSolved;
	}	
	
	@Override
	public void debug(String file) {
		OPFLogger.getLogger().info("Running DCOPF debug mode for Apache LP solver...");
		if(objFunc ==null){
			this.build(cstContainer);
		}
		
		try {
			outputMatrix( file);
			OPFLogger.getLogger().info("Output file for debug purpose has been saved to: "+file);
		} catch (Exception e) {
			OPFLogger.getLogger().severe(e.toString());
		}	
		
	}
	
	private void outputMatrix(String file) throws Exception{
		RealVector f =  objFunc.getCoefficients();			
		
		Array2DRowRealMatrix Aeq = new Array2DRowRealMatrix(numOfBus, numOfVar + numOfGen);
		ArrayRealVector beq = new ArrayRealVector(numOfBus);
		Array2DRowRealMatrix Aiq = new Array2DRowRealMatrix(constraintCollection.size()- numOfBus, numOfVar + numOfGen);
		ArrayRealVector biq = new ArrayRealVector(constraintCollection.size()- numOfBus);
		int eqcnt = 0;
		int iqcnt = 0;
		
		// this syntax is more easy to read
		for ( LinearConstraint con : constraintCollection) {
			RealVector coe = con.getCoefficients();
			if(con.getRelationship().equals(Relationship.EQ)){
				Aeq.setRowVector(eqcnt, coe);
				beq.setEntry(eqcnt++, con.getValue());
			}else if(con.getRelationship().equals(Relationship.LEQ)){
				Aiq.setRowVector(iqcnt, coe);
				biq.setEntry(iqcnt++, con.getValue());
			}else {
				OPFLogger.getLogger().severe("Relationship laggerthan needs to be convertted to lessthan."+
			"for the "+ iqcnt+" inequality constraint.");
			}			
		}	

		writeMatlabInputFile(file,f,Aeq,beq,Aiq,biq);	
	}
	

	private void writeMatlabInputFile(String file, RealVector f,Array2DRowRealMatrix Aeq,
			ArrayRealVector beq,Array2DRowRealMatrix Aiq, ArrayRealVector biq) throws IOException{
		
		FileWriter fstream = new FileWriter(file);
		BufferedWriter out = new BufferedWriter(fstream);		
		try {
			helper.writeVector(out, f, "f=[ ");
			helper.writeMatrix(out, Aeq, "Aeq=[");
			helper.writeVector(out, beq, "beq=[ ");
			helper.writeMatrix(out, Aiq, "Aiq=[");
			helper.writeVector(out, biq, "biq=[ ");			
			String linprog ="x = linprog(f,Aiq,biq,Aeq,beq,[],[]);";
			out.append(linprog);
		} catch (Exception e) {	
			OPFLogger.getLogger().severe(e.toString());
			e.printStackTrace();
		}		  
		out.close();		
	}

	@Override
	public void calLMP() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public long getIteration() {		
		return solver.getIterations();
	}

	@Override
	public double[] getSolution() {		
		return this.optimX;
	}

	@Override
	public double getObjectiveFunctionValue() {		
		return objFunc.getValue(optimX);
	}

	@Override
	public void printInputData(String fileName) {
		if(objFunc ==null){
			this.build(cstContainer);
		}
		FileWriter fstream= null;
		try {
			fstream = new FileWriter(fileName);
			BufferedWriter out = new BufferedWriter(fstream);
	        RealVector f =  objFunc.getCoefficients();	
	        out.write("Objective funtion: ");
	        out.append("\n");
	        out.append("( ");
	        out.append("{ ");
			for (int i= 0; i<f.getDimension(); i++){
				out.append(Double.toString(f.getEntry(i)));
				out.append(", ");
			}
			out.append("}, ");
			out.append(Double.toString(objFunc.getConstantTerm()));
			out.append(");\n ");
			
			Iterator<LinearConstraint> it = constraintCollection.iterator();
			out.append("Linear constraints: ");
			out.append("\n");
			
	        while (it.hasNext()){
	        	out.append("( ");
		        out.append("{ ");
	        	LinearConstraint con = it.next();
	        	RealVector vec = con.getCoefficients();
	        	for (int i= 0; i< vec.getDimension();i++){
	    			out.append(Double.toString(vec.getEntry(i)));
	    			out.append(", ");
	    		}
	    		out.append("}, ");
	    		
	    		out.append(con.getRelationship().toString());
	    		out.append(" , ");
	    		out.append(Double.toString(con.getValue()));
				out.append(");\n ");	
	        }
	        out.close();
	        OPFLogger.getLogger().info("Input data saved to: "+fileName);
		} catch (IOException e) {
			OPFLogger.getLogger().severe(e.toString());
		}
		
		
	}
	
	public static void debug(Collection<LinearConstraint> constraints, LinearObjectiveFunction objFunc) {
        System.out.println("objFunc: " + objFunc.getCoefficients().toString() + " " +
	                        objFunc.getCoefficients().getClass().getSimpleName());
        for (LinearConstraint c : constraints)
        	System.out.println("constraints: " + c.getCoefficients().toString()+ " " +
                    objFunc.getCoefficients().getClass().getSimpleName());
    }
	
	public void printHardCodedData(String fileName) {
		if(objFunc ==null){
			this.build(cstContainer);
		}
		FileWriter fstream= null;
		try {
			fstream = new FileWriter(fileName);
			BufferedWriter out = new BufferedWriter(fstream);
	        RealVector f =  objFunc.getCoefficients();	
	        out.write("LinearObjectiveFunction f = new LinearObjectiveFunction(new double[] ");        
	        out.append("{ ");
			for (int i= 0; i<f.getDimension(); i++){
				out.append(Double.toString(f.getEntry(i)));
				out.append(", ");
			}
			out.append("}, ");
			out.append(Double.toString(objFunc.getConstantTerm()));
			out.append(");\n ");
			
			Iterator<LinearConstraint> it = constraintCollection.iterator();
			out.append("Collection<LinearConstraint> constraints = new ArrayList<LinearConstraint>(); ");
			out.append("\n");
			
	        while (it.hasNext()){
	        	out.append("constraints.add(new LinearConstraint(new double[] ");
		        out.append("{ ");
	        	LinearConstraint con = it.next();
	        	RealVector vec = con.getCoefficients();
	        	for (int i= 0; i< vec.getDimension();i++){
	    			out.append(Double.toString(vec.getEntry(i)));
	    			out.append(", ");
	    		}
	    		out.append("}, Relationship.");
	    		String rela = con.getRelationship().toString();
	    		if (rela.equals("=")){
	    			out.append("EQ");
	    		}else {
	    			out.append("LEQ");
	    		}
	    		out.append(" , ");
	    		out.append(Double.toString(con.getValue()));
				out.append("));\n ");	
	        }
	        out.close();
	        OPFLogger.getLogger().info("Hard-coded data saved to: "+fileName);
		} catch (IOException e) {
			OPFLogger.getLogger().severe(e.toString());
		}
		
		
	}
}
