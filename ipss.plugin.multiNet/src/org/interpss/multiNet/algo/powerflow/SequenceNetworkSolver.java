package org.interpss.multiNet.algo.powerflow;

import java.util.Hashtable;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;

import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.BaseAcscBus;
import com.interpss.core.acsc.BaseAcscNetwork;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.algo.sc.ScBusModelType;
import com.interpss.core.sparse.ISparseEqnSolver;
import com.interpss.core.sparse.SparseEqnSolverFactory;
import com.interpss.core.sparse.impl.SparseEqnComplexImpl;

/**
 * Sequence Network Helper is to solve the negative and zeor
 * sequence networks, which, together with the traditional positive
 * sequence transient stability simulation, realizes the three-sequence
 * transient stability simulation. 
 * 
 * @author Qiuhua Huang
 * School of Electrical, Computer and Energy Engineering
 * Ira A. Fulton Schools of Engineering
 * Arizona State University
 * Email: qhuang24@asu.edu
 *
 */
public class SequenceNetworkSolver {
	
	private BaseAcscNetwork<? extends BaseAcscBus<?,?>, ? extends AcscBranch> net = null;
	private ISparseEqnComplex zeroSeqYMatrix = null; 
	private ISparseEqnComplex negSeqYMatrix  = null;
	private Hashtable<String,Complex3x1>  seqVoltTable =null;
	
	private ISparseEqnSolver zeroYSolver=null;
	private ISparseEqnSolver negYSolver=null;
    
	private String[] monitorBusAry =null;
	
	/**
	 * 
	 * @param net
	 * @param monitorBusAry
	 */
	public SequenceNetworkSolver(BaseAcscNetwork<? extends BaseAcscBus<?,?>, ? extends AcscBranch> net,String[] monitorBusAry){
		this.net =net;
		this.monitorBusAry = monitorBusAry;
		
		SparseEqnSolverFactory factory = new SparseEqnSolverFactory();
		
		zeroSeqYMatrix = net.formScYMatrix(SequenceCode.ZERO, ScBusModelType.LOADFLOW_VOLT, false);
		zeroYSolver = factory.createSparseEqnComplexSolver(zeroSeqYMatrix);
		
		negSeqYMatrix =  net.formScYMatrix(SequenceCode.NEGATIVE, ScBusModelType.LOADFLOW_VOLT, false);
		negYSolver = factory.createSparseEqnComplexSolver(negSeqYMatrix);
		
		//LU factorize the YMaxtri, prepare it for calculating Z matrix;
		try {
			zeroYSolver.factorization();// tolearance is not used actually.
			negYSolver.factorization();// tolearance is not used actually.
		} catch (IpssNumericException e) {
			
			e.printStackTrace();
		} 
		
		
		
		seqVoltTable = new Hashtable<>();
		for(String id: monitorBusAry){
			
			seqVoltTable.put(id, net.getBus(id).getThreeSeqVoltage());
		}
		
		
	}
	
	public Hashtable<String,Complex3x1> getSeqVoltTable(){
		return seqVoltTable;
	}
	
	
	public  Hashtable<String,Complex3x1> solveNegZeroSeqNetwork(Hashtable<String, Complex3x1> threeSeqCurInjTable){
		
		// negative sequence
		((SparseEqnComplexImpl)negSeqYMatrix).setB2Zero();
		for(String busId: this.monitorBusAry){
			int sortNum = net.getBus(busId).getSortNumber();
			Complex i2 =threeSeqCurInjTable.get(busId).c_2;
		    negSeqYMatrix.setBi(i2, sortNum);
		   // System.out.println(busId+","+net.getBus(busId).getName()+", sort: "+sortNum+","+i2);
		}
		try {
			negYSolver.solveEqn();
		} catch (IpssNumericException e) {
			e.printStackTrace();
		}
		
		for(String busId:monitorBusAry){
			int busSortNum = net.getBus(busId).getSortNumber();
			seqVoltTable.get(busId).c_2 = negSeqYMatrix.getX(busSortNum);
		}
		
		
		//zero sequence
		
		((SparseEqnComplexImpl)zeroSeqYMatrix).setB2Zero();
		for(String busId: this.monitorBusAry){
		   zeroSeqYMatrix.setBi(threeSeqCurInjTable.get(busId).a_0, net.getBus(busId).getSortNumber());
		}
		try {
			zeroYSolver.solveEqn();
		} catch (IpssNumericException e) {
			e.printStackTrace();
		}
		
		for(String busId:monitorBusAry){
			int busSortNum = net.getBus(busId).getSortNumber();
			seqVoltTable.get(busId).a_0 = zeroSeqYMatrix.getX(busSortNum);
		}
		
		
		return seqVoltTable;
		
		
	}
	
	/**
	 * 
	 * 
	 * @param zeroSeqCurInjTable
	 * @return
	 */
	public Hashtable<String, Complex> calcZeroSeqVolt(Hashtable<String, Complex> zeroSeqCurInjTable){
		
		Hashtable<String, Complex> zeroSeqVoltHashtable = new Hashtable<>();
		((SparseEqnComplexImpl)zeroSeqYMatrix).setB2Zero();
		
		for(String busId: zeroSeqCurInjTable.keySet()){
		   zeroSeqYMatrix.setBi(zeroSeqCurInjTable.get(busId), net.getBus(busId).getSortNumber());
		}
		try {
			zeroYSolver.solveEqn();
		} catch (IpssNumericException e) {
			e.printStackTrace();
		}
		
		for(String busId:monitorBusAry){
			int busSortNum = net.getBus(busId).getSortNumber();
		   zeroSeqVoltHashtable.put(busId,zeroSeqYMatrix.getX(busSortNum));
		}
		
		return zeroSeqVoltHashtable;
	}
	
	/**
	 * 
	 * @param negSeqCurInjTable
	 * @return
	 */
	public Hashtable<String, Complex> calcNegativeSeqVolt(Hashtable<String, Complex> negSeqCurInjTable){
		
		Hashtable<String, Complex> negSeqVoltHashtable = new Hashtable<>();
		((SparseEqnComplexImpl)negSeqYMatrix).setB2Zero();
		for(String busId: negSeqCurInjTable.keySet()){
			int sortNum = net.getBus(busId).getSortNumber();
			Complex i2 =negSeqCurInjTable.get(busId);
		    negSeqYMatrix.setBi(i2, sortNum);
		   // System.out.println(busId+","+net.getBus(busId).getName()+", sort: "+sortNum+","+i2);
		}
		try {
			negYSolver.solveEqn();
		} catch (IpssNumericException e) {
			e.printStackTrace();
		}
		
		for(String busId:monitorBusAry){
			int busSortNum = net.getBus(busId).getSortNumber();
		   negSeqVoltHashtable.put(busId,negSeqYMatrix.getX(busSortNum));
		}
		
		return negSeqVoltHashtable;
	}


}
