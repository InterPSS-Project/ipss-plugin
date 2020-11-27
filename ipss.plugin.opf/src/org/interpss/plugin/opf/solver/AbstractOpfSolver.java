package org.interpss.plugin.opf.solver;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import org.interpss.numeric.datatype.LimitType;
import org.interpss.plugin.opf.constraint.OpfConstraint;
import org.interpss.plugin.opf.util.OPFResultOutput;
import org.interpss.plugin.opf.util.OpfDataHelper;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.net.Bus;
import com.interpss.opf.OpfBus;
import com.interpss.opf.OpfNetwork;
import com.interpss.opf.algo.impl.BaseOpfAlgorithmImpl;

public abstract class AbstractOpfSolver extends BaseOpfAlgorithmImpl implements IOpfSolver {

	public final static LimitType BusAngleLimit = new LimitType(300, -300); 
	
	//protected OpfNetwork opfNet = null;
	protected double[] optimX = null;
	protected OpfDataHelper helper = null;
	protected IOpfSolver.constraintHandleType constType = null;
	protected boolean isSolved = false;
	protected double ofv = 0;
	protected List<OpfConstraint> cstContainer;
	protected int numOfVar = 0;
	protected int numOfBus = 0;
	protected int numOfBranch = 0;
	protected int numOfGen = 0;
	

	public AbstractOpfSolver(OpfNetwork opfNet, IOpfSolver.constraintHandleType constType) {
		this.setNetwork(opfNet);
		this.helper = new OpfDataHelper();
		this.constType = constType;
		this.formBusIndexTable();
		this.cstContainer = new ArrayList<OpfConstraint>();
		this.numOfBus = opfNet.getNoActiveBus();
		this.numOfGen = opfNet.getNoOpfGen();
		this.numOfBranch = opfNet.getNoActiveBranch();	
		
	}

	public boolean solve() {
		return isSolved;

	}
	
	public void writeOutputToFiel(String file) throws Exception{
		// TODO
		FileWriter fstream = new FileWriter(file);
		BufferedWriter out = new BufferedWriter(fstream);	
		out.write(OPFResultOutput.opfResultSummary(this.getNetwork()));
		out.close();
	}
	public boolean isSolved() {
		return isSolved;
	}

	public List<OpfConstraint> getConstraintContainer() {
		return this.cstContainer;
	}

	public void attachedResult() {
		double[] busAngle = null;

		double minF = this.getObjectiveFunctionValue();		
		this.getNetwork().setMinF(minF);
		// extract the angle value
		busAngle = new double[this.getNetwork().getNoActiveBus()];
		int noOfGen = this.getNetwork().getNoOpfGen();
		for (int k = noOfGen; k < noOfGen + this.getNetwork().getNoActiveBus() ; k++) {
			busAngle[k - noOfGen] = this.optimX[k]; // voltAngle in radians
		}

		// set gen P to opfNet bus object
		int genIndex = 0;
		for (Bus b : this.getNetwork().getBusList()) {
			OpfBus bus = (OpfBus)b;
			if (bus.isOpfGen()) {
				bus.setGenP(optimX[genIndex]);
				genIndex++;
			}
		}

		// set bus angle and LMP to opfNet bus object
		int idx = 0;

		for (Bus b : this.getNetwork().getBusList()) {
			AclfBus bus = (AclfBus) b;
			bus.setVoltageAng(busAngle[idx]);
			idx++;
		}		
	}

	// public void calLMP();

	private void formBusIndexTable() {
		int cnt = 0;
		for (OpfBus b : this.getNetwork().getBusList()) {
			b.setSortNumber(cnt++);
		}
	}

}
