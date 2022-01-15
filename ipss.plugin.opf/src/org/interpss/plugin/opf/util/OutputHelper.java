package org.interpss.plugin.opf.util;

import java.util.ArrayList;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.opf.OpfBranch;
import com.interpss.opf.OpfBus;
import com.interpss.opf.OpfGen;
import com.interpss.opf.OpfNetwork;

public class OutputHelper {
	private OpfNetwork net = null;
	private double totalGenCap = 0.0;
	private double totalOnlineGen = 0.0;
	private double totalActualGen = 0;
	private double totalLoad = 0;
	private double totalLoss = 0;
	private double maxLMP = 0;
	private String maxLMPBus = "";
	private double minLMP = 10000000;
	private String minLMPBus = " ";
	private double maxAngle = 0;
	private String maxAngleBus = "";
	private double minAngle = 0;
	private String minAngleBus = "";
	
	private int numOfGen = 0;
	private int numOfLoad = 0;	
	private int numOfArea = 1;
	
	private int numOfPS = 0;
	
	private ArrayList<AclfBranch> bindingBranchList = new ArrayList<AclfBranch>();
	
	public OutputHelper(OpfNetwork net){
		this.net = net;
		this.walkThroughNetwork();
	}
	
	public void walkThroughNetwork(){
		numOfArea = net.getAreaMap().size();
		for (Bus b: net.getBusList()){			
			OpfBus bus = (OpfBus)b;
			double lmp = bus.getLMP();
			if(lmp > maxLMP){
				maxLMP = lmp;
				maxLMPBus = bus.getId();
			}
			if (lmp<minLMP){
				minLMP = lmp;
				minLMPBus = bus.getId();
			}
			double ang = bus.getVoltageAng();
			if(ang >= maxAngle){
				maxAngle = ang;
				maxAngleBus = bus.getId();
			}
			if(ang <= minAngle){
				minAngle = ang;
				minAngleBus = bus.getId();
			}
			
			if(bus.isOpfGen()){
				numOfGen++;
				OpfGen opfGen = bus.getOpfGen();
				double pmax = opfGen.getOpfLimits().getPLimit().getMax();
				totalGenCap = totalGenCap + pmax;
				double p = bus.getGenP();
				totalActualGen = totalActualGen + p;
			}
			
			if(bus.isLoad()){
				numOfLoad++;
				double pload = bus.getLoadP();
				totalLoad = totalLoad + pload;
			}				
		}
	
		totalLoss = totalActualGen - totalLoad;
		
		for (Branch bra: net.getBranchList()){
			AclfBranch branch = (AclfBranch) bra;
			if (branch.isPSXfr()){
				numOfPS ++;
			}
			OpfBranch opfBra = (OpfBranch) branch;
			double rating = opfBra.getRatingMw1();
			// TODO: direction ?
			double flow = opfBra.dcPowerFrom2To();
			if( Math.abs(Math.abs(flow) - Math.abs(rating)) < 0.001 ){
				bindingBranchList.add(branch);
			}
			
		}
		
	}

	public ArrayList<AclfBranch> getConstrainedBranchList(){
		return this.bindingBranchList;
	}
	public double getTotalGenCapacity(){
		return this.totalGenCap;
	}
	public double getTotalOnlineGen(){
		// TODO: for now totalOnlineGen = totalGenCapacity
		return this.totalGenCap;
	}
	public double getTotalActualGen(){
		return this.totalActualGen;
	}
	public double getTotalLoad(){
		return this.totalLoad;
	}
	public double getTotalLoss(){		
		return this.totalLoss;
	}
	public double getMaxLMP(){
		return this.maxLMP;
	}
	public String getMaxLMPBus(){
		return this.maxLMPBus;
	}
	public double getMinLMP(){
		return this.minLMP;
	}
	public String getMinLMPBus(){
		return this.minLMPBus;
	}
	public double getMaxBusAngle(){
		return this.maxAngle;
	}
	public String getMaxBusAngleBus(){
		return this.maxAngleBus;
	}
	public double getMinBusAngle(){
		return this.minAngle;
	}
	public String getMinBusAngleBus(){
		return this.minAngleBus;
	}
	
	public int getNumOfGenerator(){
		return this.numOfGen;
	}
	public int getNumOfLoad(){
		return this.numOfLoad;
	}
	public int getNumOfArea(){
		return this.numOfArea;
	}
	public int getNumOfPhaseShifter(){
		return this.numOfPS;
	}
}
