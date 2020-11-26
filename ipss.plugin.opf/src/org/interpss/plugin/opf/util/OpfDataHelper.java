package org.interpss.plugin.opf.util;

import java.io.BufferedWriter;
import java.io.IOException;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.interpss.plugin.opf.common.OPFLogger;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.opf.OpfBranch;
import com.interpss.opf.OpfBus;
import com.interpss.opf.OpfNetwork;

import cern.colt.matrix.impl.SparseDoubleMatrix1D;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;
import lpsolve.LpSolve;
import lpsolve.LpSolveException;

public class OpfDataHelper {

	public static int getNoOfGen(OpfNetwork net) {
		int numOfGen = 0;
		for (Bus b : net.getBusList()) {
			AclfBus acbus = (AclfBus) b;
			if (acbus.isGen()) {
				numOfGen++;
			}
		}
		return numOfGen;
	}

	public static double round(double value, int places) {
	    if (places < 0) throw new IllegalArgumentException();

	    long factor = (long) Math.pow(10, places);
	    value = value * factor;
	    long tmp = Math.round(value);
	    return (double) tmp / factor;
	}	
	
	public static SparseDoubleMatrix2D getBusAdmittance(OpfNetwork opfNet) {
		int numOfBus = opfNet.getNoActiveBus();
		SparseDoubleMatrix2D busAdm = new SparseDoubleMatrix2D(numOfBus,
				numOfBus);
		for (Bus b : opfNet.getBusList()) {
			int i = b.getSortNumber();
			double Bii = 0;
			for (Branch bra : b.getBranchList()) {
				//if (bra.isAclfBranch()) {
					AclfBranch aclfBranch = (AclfBranch) bra;
					Bus busj = bra.getToBus().getId().equals(b.getId()) ? bra
							.getFromBus() : bra.getToBus();
					int j = busj.getSortNumber();
					double Bij = 1.0 / aclfBranch.getZ().getImaginary();// aclfBranch.b1ft();
					double Bij_exit = busAdm.get(i, j);
					busAdm.set(i, j, Bij_exit-Bij);
					Bii += Bij;
				//}
			}
			busAdm.set(i, i, Bii);		}
		
		return busAdm;
	}
	
	public static double getYij(Array2DRowRealMatrix Y, int i, int j){
		double yij = Y.getEntry(i, j);
		return yij;		
	}
	

	private static int[] getNonSwingBusRows(OpfNetwork opfNet) {
		int[] NonSwingBusRows = new int[opfNet.getNoActiveBus() - 1];
		int idx = 0;
		for (int busIndex = 0; busIndex < opfNet.getNoActiveBus(); busIndex++) {
			Bus b = opfNet.getBusList().get(busIndex);
			AclfBus bus = (AclfBus) b;
			if (!bus.isSwing()) {
				// swingSortNum=bus.getSortNumber();
				NonSwingBusRows[idx] = busIndex;
				idx++;
			}
		}
		return NonSwingBusRows;
	}
	
	public static ArrayRealVector combineVector(ArrayRealVector vec1, ArrayRealVector vec2){
		int size1 = vec1.getDimension();
		int size2 = vec2.getDimension();
		ArrayRealVector vec = new ArrayRealVector(2*size1+size2);
		vec.setSubVector(0, vec1);
		vec.setSubVector(size1, vec1);
		vec.setSubVector(2*size1, vec2);
		return vec;
	}
	
	public static Array2DRowRealMatrix FormCiq(Array2DRowRealMatrix mat_lfc,
			Array2DRowRealMatrix mat_genc){
		int rowLfc = mat_lfc.getRowDimension();
		int colLfc = mat_lfc.getColumnDimension();
		int rowGenc = mat_genc.getRowDimension();
		int colGenc = mat_genc.getColumnDimension();
		int rowCiq = rowLfc + rowGenc;
		int colCiq = 2 * (colLfc + colGenc);
		Array2DRowRealMatrix Ciq = new Array2DRowRealMatrix(rowCiq, colCiq);
		Ciq.setSubMatrix(mat_lfc.getData(), rowCiq - rowLfc, 0);
		Ciq.setSubMatrix(mat_lfc.scalarMultiply(-1).getData(), rowCiq - rowLfc,  colLfc);
		Ciq.setSubMatrix(mat_genc.getData(), 0, 2 * colLfc );
		Ciq.setSubMatrix(mat_genc.scalarMultiply(-1).getData(), 0, 2 * colLfc + colGenc);
		
		return Ciq;
	}
	

	public static int getSwingBusIndex(OpfNetwork aclfNet) {
		// assume there is one swing bus in the system
		for (Bus b : aclfNet.getBusList()) {
			if (((OpfBus) b).isSwing()) {
				return b.getSortNumber();
			}
		}
		OPFLogger.getLogger().severe("No swing bus found in the system");
		return 0;
	}

	// angel difference weight matrix
	public static Array2DRowRealMatrix formAngleDiffWeightMatrix(OpfNetwork opfNet) {
		int numOfBus = opfNet.getNoActiveBus();
		Array2DRowRealMatrix angleDiffWeight = new Array2DRowRealMatrix(
				numOfBus, numOfBus);

		for (Bus busi : opfNet.getBusList()) {
			// double ADWij = 0;
			// double ADWii = 0;
			int i = busi.getSortNumber();
			for (Branch bra : busi.getFromBranchList()) {
				Bus busj = bra.getToBus();
				int j = busj.getSortNumber();
				double ADWij = -1;
				// ADWii++;
				angleDiffWeight.setEntry(i, j, ADWij);
				angleDiffWeight.setEntry(j, i, ADWij);

			}
			angleDiffWeight.setEntry(i, i, busi.getBranchList().size());
		}
		return (Array2DRowRealMatrix) angleDiffWeight.scalarMultiply(2 * opfNet
				.getAnglePenaltyFactor());
	}

	public static Array2DRowRealMatrix formReducedADWMatrix(OpfNetwork opfNet) {
		Array2DRowRealMatrix angleDiffWeight = formAngleDiffWeightMatrix(opfNet);
		try {
			int[] selectedRows = getNonSwingBusRows(opfNet);
			return (Array2DRowRealMatrix) angleDiffWeight.getSubMatrix(
					selectedRows, selectedRows);
		} catch (Exception e) {			
			OPFLogger.getLogger().severe(e.toString());
			e.printStackTrace();
		}
		return null;
	}
	
	public static void addLpConstraint(LpSolve lpsolver,Array2DRowRealMatrix Ceq, ArrayRealVector beq, int type){
		int colCeq = Ceq.getColumnDimension();
		int rowCeq = Ceq.getRowDimension();
		int j = 0;
		int[] colNo = new int[colCeq];
		double[] row = new double[colCeq];
		//int cnt = 1;
		double rh =0;
		for (int i=0; i<rowCeq; i++){
			j = 0;
			for(int ii = 0; ii<colCeq; ii++){
				double val = Ceq.getEntry(i, ii);
				if(val!=0){
					colNo[j] = ii+1;
					row[j++] = val;
				}
			}
			rh = beq.getEntry(i);
			try {
				lpsolver.addConstraintex( j, row, colNo, type, rh);
			} catch (LpSolveException e) {				
				e.printStackTrace();
			}
		}
	}
	
	public static void writeMatrix(BufferedWriter out, Array2DRowRealMatrix mat,String title) throws Exception, IOException{
		out.write(title);
		for (int i= 0; i< mat.getRowDimension();i++){
			for(int j=0; j<mat.getColumnDimension();j++){
				out.append(Double.toString(mat.getEntry(i, j)));
				out.append(" ");
			}
			out.append("; \n");			
		}
		out.append("];");
		out.append("\n");
		
	}
	
	public static void writeMatrix(BufferedWriter out, SparseDoubleMatrix2D mat,String title) throws Exception, IOException{
		out.write(title);
		for (int i= 0; i< mat.rows();i++){
			for(int j=0; j<mat.columns();j++){
				out.append(Double.toString(mat.get(i, j)));
				out.append(" ");
			}
			out.append("; \n");			
		}
		out.append("];");
		out.append("\n");
		
	}
	
	public static void writeVector(BufferedWriter out, ArrayRealVector vec,String title) throws Exception, IOException{
		out.write(title);
		for (int i= 0; i< vec.getDimension();i++){
			out.append(Double.toString(vec.getEntry(i)));
			out.append("; ");
		}
		out.append("];");
		out.append("\n");		
	}
	
	public static void writeVector(BufferedWriter out, RealVector vec,String title) throws Exception, IOException{
		out.write(title);
		for (int i= 0; i< vec.getDimension();i++){
			out.append(Double.toString(vec.getEntry(i)));
			out.append("; ");
		}
		out.append("];");
		out.append("\n");		
	}
	public void writeVector(BufferedWriter out, SparseDoubleMatrix1D vec,String title) throws Exception, IOException{
		out.write(title);
		for (int i= 0; i< vec.size();i++){
			out.append(Double.toString(vec.get(i)));
			out.append("; ");
		}
		out.append("];");
		out.append("\n");		
	}
}
