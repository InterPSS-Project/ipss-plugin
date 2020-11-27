package org.interpss.plugin.opf;

import org.interpss.plugin.opf.common.OPFException;
import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.plugin.opf.solver.IOpfSolver;
import org.interpss.plugin.opf.solver.apache.lp.ApacheLPSolver;
import org.interpss.plugin.opf.solver.giqpsolve.GIQPSolver;
import org.interpss.plugin.opf.solver.lpsolve.LpsolveSolver;

import com.interpss.opf.OpfNetwork;
import com.interpss.opf.datatype.OpfConstraintType;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;

public class OpfSolverFactory {
	public static OpfSolverFactory opfSolverFactory =null;
	static {
		opfSolverFactory = new OpfSolverFactory();
	}
	
	public static LpsolveSolver createLpsolveLPSolver(OpfNetwork opfnet,IOpfSolver.constraintHandleType type){
		LpsolveSolver solver = new LpsolveSolver(opfnet, type);
		return solver;
	}
	public static GIQPSolver createGIQPSolver(OpfNetwork opfnet,IOpfSolver.constraintHandleType type){
		GIQPSolver solver = new GIQPSolver(opfnet, type);
		return solver;
	}
	
	public static ApacheLPSolver createApacheLPSolver(OpfNetwork opfnet,IOpfSolver.constraintHandleType type){
		ApacheLPSolver solver = new ApacheLPSolver(opfnet, type);
		return solver;
	}
	
	public static OpfConstraint createOpfConstraint(int id, String des, double ul, double ll, 
			OpfConstraintType type, IntArrayList colNo, DoubleArrayList val){
		OpfConstraint cst = new OpfConstraint();	
		cst.setDesc(des);
		cst.setId(id);
		cst.setLowerLimit(ll);
		cst.setUpperLimit(ul);
		cst.setColNo(colNo);
		cst.setVal(val);
	    cst.setCstType(type);
		return cst;
	}
	
	public static IOpfSolver createOPFSolver(OPFSolverEnum solver,OpfNetwork opfnet,
			IOpfSolver.constraintHandleType type) throws OPFException {
		if ( solver == OPFSolverEnum.LpsolveLPSolver ) 
			return new LpsolveSolver(opfnet, type);
		else if ( solver == OPFSolverEnum.GIQPSolver )
			return new GIQPSolver(opfnet, type);
		else if ( solver == OPFSolverEnum.ApacheLPSolver )
			return new ApacheLPSolver(opfnet, type);
		throw new OPFException("Error - unsupported solver type");
	}

}
