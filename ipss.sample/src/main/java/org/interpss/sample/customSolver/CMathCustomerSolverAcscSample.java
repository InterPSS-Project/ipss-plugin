package org.interpss.sample.customSolver;

import org.apache.commons.math3.complex.Complex;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AcscOutFunc;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.sample.customSolver.impl.CMathSparseEqnComplex;
import org.interpss.sample.customSolver.impl.CMathSparseEqnComplexMatrix3x3;
import org.interpss.sample.customSolver.solver.CMathSquareMatrixEqnComplexSolver;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.core.acsc.fault.SimpleFaultCode;
import com.interpss.core.algo.sc.SimpleFaultAlgorithm;
import com.interpss.core.sparse.SparseEqnObjectFactory;
import com.interpss.core.sparse.solver.SparseEqnSolverFactory;
import com.interpss.simu.util.sample.SampleAcscTestingCases;

public class CMathCustomerSolverAcscSample {

	public static void main(String[] args)  throws InterpssException {
		IpssCorePlugin.init();
		
		unitVoltTest();
	}
	
	public static void unitVoltTest() throws InterpssException {
  		AcscNetwork faultNet = CoreObjectFactory.createAcscNetwork();
  		SampleAcscTestingCases.load_SC_5BusSystem(faultNet);
		
		//System.out.println(faultNet.net2String());
  		
 		/*
		SparseEqnSolverFactory.setComplexSolverCreator(
				(ISparseEqnComplex eqn) -> new CSJSquareMatrixEqnComplexSolver(eqn));
		
		SparseEqnObjectFactory.setComplexEqnCreator(
				(Integer n) -> new CSJSparseEqnComplexImpl(n));
		
		SparseEqnObjectFactory.setComplextMatrix3x3EqnCreator(
				(Integer n) -> new CSJSparseEqnComplexMatrix3x3Impl(n));
  		*/
 
		SparseEqnSolverFactory.setComplexSolverCreator(
				(ISparseEqnComplex eqn) -> new CMathSquareMatrixEqnComplexSolver(eqn));
		
		SparseEqnObjectFactory.setComplexEqnCreator(
				(Integer n) -> new CMathSparseEqnComplex(n));
		
		SparseEqnObjectFactory.setComplextMatrix3x3EqnCreator(
				(Integer n) -> new CMathSparseEqnComplexMatrix3x3(n));
	
	  	SimpleFaultAlgorithm algo = CoreObjectFactory.createSimpleFaultAlgorithm(faultNet);
  		AcscBusFault fault = CoreObjectFactory.createAcscBusFault("2", algo, true /* cacheBusScVolt */);
		fault.setFaultCode(SimpleFaultCode.GROUND_3P);
		fault.setZLGFault(new Complex(0.0, 0.0));
		fault.setZLLFault(new Complex(0.0, 0.0));
		
	  	algo.calBusFault(fault);
		System.out.println("//////////////////////////////////////////////////////");		
		System.out.println("----------- Fault using UnitVolt ---------------------");		
		System.out.println(AcscOutFunc.faultResult2String(faultNet, algo));		
	}
}
