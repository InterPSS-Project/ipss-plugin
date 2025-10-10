package org.interpss.util;

import java.io.FileWriter;
import java.io.IOException;

import org.apache.commons.math3.complex.Complex;
import org.interpss.display.impl.AclfOut_PSSE;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.facts.StaticVarCompensator;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLine2TVSC;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.net.Branch;

public class QAUtil {

	public static double getMaxBusVoltageMagDiff (AclfNetwork net, AclfNetwork copyNet) {
		double maxDiff = 0;
		for(AclfBus bus: net.getBusList()) {
			if(bus.isActive()) {
				double vm = bus.getVoltageMag();
				AclfBus copyBus = copyNet.getBus(bus.getId());
				double vmCopy = copyBus.getVoltageMag();
				double vmdiff = vm - vmCopy;
				if(Math.abs(vmdiff) > maxDiff) {
					maxDiff = Math.abs(vmdiff);
				}
			}
		}
		return maxDiff;
	}

	public static double getMaxBusVoltageDiff (AclfNetwork net, AclfNetwork copyNet) {
		double maxDiff = 0;
		String maxDiffBusId = "";
		for(AclfBus bus: net.getBusList()) {
			if(bus.isActive()) {
				Complex v = bus.getVoltage();
				AclfBus copyBus = copyNet.getBus(bus.getId());
				Complex vCopy = copyBus.getVoltage();
				Complex vDiff = v.subtract(vCopy);
				if(vDiff.abs() > maxDiff) {
					maxDiff = vDiff.abs();
					maxDiffBusId = bus.getId();
				}
			}
		}
		System.out.println("Max bus voltage difference: " + maxDiff + " (Bus ID: " + maxDiffBusId + ")");
		return maxDiff;
	}

	public static double getMaxBranchFlowDiff (AclfNetwork net, AclfNetwork copyNet, double zeroZBranchTreshold) {
		double maxDiff = 0;
		String maxDiffBranchId = "";
		for(AclfBranch branch: net.getBranchList()) {
			if(branch.isActive() && copyNet.getBranch(branch.getId()) != null && branch.getAdjustedZ().abs() > zeroZBranchTreshold) { // check if the branch is active and has a non-zero impedance
				Complex flow = branch.powerFrom2To();
				Complex flowPSSE = copyNet.getBranch(branch.getId()).powerFrom2To();
				Complex flowDiff = flow.subtract(flowPSSE);
				double flowDiffAbs = flowDiff.abs();
				if(flowDiffAbs > maxDiff) {
					maxDiff = flowDiffAbs;
					maxDiffBranchId = branch.getId();
				}
			}
		}
		System.out.println("Max branch flow difference: " + maxDiff + " (Branch ID: " + maxDiffBranchId + ")");
		return maxDiff;
	}

    public static double getMaxGenPOutputDiff (AclfNetwork net, AclfNetwork copyNet) {
		double maxDiff = 0;
		String maxDiffGenId = "";
		for(AclfBus bus: net.getBusList()) {
			if(bus.isActive() && bus.getContributeGenList() != null) {
				for (AclfGen gen : bus.getContributeGenList()) {
					if (gen.isActive()) {
						double p = gen.getGen().getReal();
						double q = gen.getGen().getImaginary();
						AclfGen copyGen = copyNet.getBus(bus.getId()).getContributeGen(gen.getId());
						if (copyGen != null) {
							double pCopy = copyGen.getGen().getReal();
							double qCopy = copyGen.getGen().getImaginary();
							double pDiff = p - pCopy;
							double qDiff = q - qCopy;
							double pDiffPercent = Math.abs(pDiff); // Assuming P is the main focus
							if (pDiffPercent > maxDiff) {
								maxDiff = pDiffPercent;
								maxDiffGenId = bus.getId() + "-" + gen.getId();
							}
						}
					}
				}
			}
		}
		System.out.println("Max generator output difference: " + maxDiff + " (Generator ID: " + maxDiffGenId + ")");
		return maxDiff;
	}



    public static void printResults(AclfNetwork net, AclfNetwork copyNet, String busCompareFile, String branchCompareFile, String genCompareFile) {
        StringBuffer busResultCompare = new StringBuffer();
		busResultCompare.append("BusId,AreaId,Vm,VmPSSE,VmDiff,Va,VaPSSE,VaDiff,VmDiffPercent,VaDiffPercent\n");
		for(AclfBus bus: net.getBusList()) {
			if(bus.isActive()) {
				double vm = bus.getVoltageMag();
				double va = bus.getVoltageAng()*180/Math.PI; // convert to degree
				//System.out.println(bus.getId() + ", Vm: " + vm + ",	
				AclfBus copyBus = copyNet.getBus(bus.getId());
				double vmCopy = copyBus.getVoltageMag();
				double vaCopy = copyBus.getVoltageAng()*180/Math.PI; // convert to degree
				double vmdiff = vm - vmCopy;
				double vadiff = va - vaCopy;
				double vmdiffPercent = vmdiff/vmCopy*100;
				double vadiffPercent = vadiff/vaCopy*100;
				String areaId = bus.getAreaId();
				busResultCompare.append(bus.getId()+","+areaId+","+vm+","+vmCopy+","+vmdiff+","+va+","+vaCopy+","+vadiff+","+vmdiffPercent+","+vadiffPercent+"\n");
			}
		}

		try (FileWriter writer = new FileWriter(busCompareFile)) {
			writer.write(busResultCompare.toString());
			writer.flush();
			System.out.println("Bus comparison results are saved to: " + busCompareFile);
		} catch (IOException e) {
			System.err.println("Error writing load flow comparison results to file: " + e.getMessage());
			e.printStackTrace();
		}
		
		// branch flow results
		StringBuffer branchResultCompare = new StringBuffer();
		branchResultCompare.append("BranchId,AreaId,FromBusId,ToBusId,P_IPSS,P_PSSE, P_Diff,Q_IPSS,Q_PSSE,Q_Diff,FlowDiffPercent\n");
		for(AclfBranch branch: net.getBranchList()) {
			if(branch.getId().equals("3WNDTR_43123_48518_48516_1->Bus48518(1)")){
				System.out.println(branch.toString());
			}
			if(branch.isActive() && copyNet.getBranch(branch.getId()) != null && branch.getZ().abs() > 0.001) { // check if the branch is active and has a non-zero impedance
				Complex flow = branch.powerFrom2To();
				Complex flowPSSE = copyNet.getBranch(branch.getId()).powerFrom2To();
				Complex flowDiff = flow.subtract(flowPSSE);
				double flowDiffPercent = flowDiff.abs()/flowPSSE.abs()*100;
				String areaId = branch.getFromBus().getAreaId();
				branchResultCompare.append(branch.getId()+","+areaId+","+branch.getFromBus().getId()+","+branch.getToBus().getId()+","+flow.getReal()+","+flowPSSE.getReal()+","+flowDiff.getReal()+","+flow.getImaginary()+","+flowPSSE.getImaginary()+","+flowDiff.getImaginary()+","+flowDiffPercent+"\n");
			}
		}
	

		try (FileWriter writer = new FileWriter(branchCompareFile)) {
			writer.write(branchResultCompare.toString());
			writer.flush();
			System.out.println("Branch comparison results are saved to: " + branchCompareFile);
		} catch (IOException e) {
			System.err.println("Error writing branch comparison results to file: " + e.getMessage());
			e.printStackTrace();
		}



		StringBuffer genResultCompare = new StringBuffer();
		genResultCompare.append("BusId,genId,genType,P_IPSS,P_PSSE,P_Diff,pDiffPercent,Q_IPSS,Q_PSSE,Q_Diff,qDiffPercent\n");
		for(AclfBus bus: net.getBusList()) {
			if(bus.isActive() && bus.getContributeGenList() != null) {
				for (AclfGen gen : bus.getContributeGenList()) {
					if (gen.isActive()) {
						double p = gen.getGen().getReal();
						double q = gen.getGen().getImaginary();
						AclfGen copyGen = copyNet.getBus(bus.getId()).getContributeGen(gen.getId());
						if (copyGen != null) {
							double pCopy = copyGen.getGen().getReal();
							double qCopy = copyGen.getGen().getImaginary();
							double pDiff = p - pCopy;
							double qDiff = q - qCopy;
							double pDiffPercent = (pDiff / pCopy) * 100; // Assuming P is the main focus
							double qDiffPercent = (qDiff / qCopy) * 100;
							genResultCompare.append(bus.getId() + "," + gen.getId() + "," + bus.getGenCode() + "," + p + "," + pCopy + "," + pDiff + "," + pDiffPercent + "," + q + "," + qCopy + "," + qDiff + "," + qDiffPercent + "\n");
						}
						else{
							genResultCompare.append(bus.getId() + "," + gen.getId() + "," + bus.getGenCode() + "," + p + "," + 0 + "," + p + "," + 100 + "," + q + "," + 0 + "," + q + "," + 100 + "\n");
						}
					}
				}
				
			}
		}
	

		try (FileWriter writer = new FileWriter(genCompareFile)) {
			writer.write(genResultCompare.toString());
			writer.flush();
			System.out.println("Generator comparison results are saved to: " + genCompareFile);
		} catch (IOException e) {
			System.err.println("Error writing generator comparison results to file: " + e.getMessage());
			e.printStackTrace();
		}

    }

	public static void checkAllBusMismatch(AclfNetwork net, double smallZBranchThreshold, double mismatchTreshold) {

		for (AclfBus bus : net.getBusList()) {
			// if(bus.getId().equals("Bus30731")) {
			// 	System.out.println(bus.toString());

			// }	
			Complex  mis = bus.mismatch(AclfMethodType.NR);
			if(bus.isActive() //&& bus.getId().equals("Bus14001")
					&& mis.abs()>mismatchTreshold && !isSmallZBranchConnected(bus.getId(),net,smallZBranchThreshold)){
				System.out.println(bus.getId()+" , mismatch = " +mis.toString());
				System.out.println(bus.getId()+"\n"+ AclfOut_PSSE.busResults(bus,100000)+"\n");
				for( Branch bra : bus.getBranchList()){
					//check if the branch is active and is a special branch like HVDC, VSC-HVDC, or 3W transformer
					if (bra.isActive() && !(bra instanceof AclfBranch)) {
		
						if (bra instanceof HvdcLine2TLCC){
							HvdcLine2TLCC<AclfBus> hvdcBranch = (HvdcLine2TLCC<AclfBus>) bra;
							System.out.println(String.format("Connected LCC HVDC Branch %s, power: %s",hvdcBranch.getId(),hvdcBranch.powerIntoConverter(bus.getId())));

						} else if (bra instanceof  HvdcLine2TVSC) {
							HvdcLine2TVSC<AclfBus> hvdcBranch = (HvdcLine2TVSC<AclfBus>) bra;

							System.out.println(String.format("Connected VSC HVDC Branch %s, power: %s",hvdcBranch.getId(),hvdcBranch.powerIntoConverter(bus.getId())));
						} else {
							System.out.println("Connected Special Branch: " + bra.getId() );
						}
					}
					
				}
				System.out.println(bus.toString());
		
			}
		}
	}

	public static void checkBusMismatch(AclfNetwork net, String busId, double smallZBranchThreshold, double mismatchTreshold) {

		AclfBus bus =  net.getBus(busId);
		if (bus == null) {
			System.out.println("Bus " + busId + " not found in the network.");
			return;
		}
		
		Complex  mis = bus.mismatch(AclfMethodType.NR);
		if(bus.isActive() //&& bus.getId().equals("Bus14001")
				&& mis.abs()>mismatchTreshold && !isSmallZBranchConnected(bus.getId(),net,smallZBranchThreshold)){
			System.out.println(bus.getId()+" , mismatch = " +mis.toString());
			System.out.println(bus.getId()+"\n"+ AclfOut_PSSE.busResults(bus,100000)+"\n");
			for( Branch bra : bus.getBranchList()){
				//check if the branch is active and is a special branch like HVDC, VSC-HVDC, or 3W transformer
				if (bra.isActive() && !(bra instanceof AclfBranch)) {
	
					if (bra instanceof HvdcLine2TLCC){
						HvdcLine2TLCC<AclfBus> hvdcBranch = (HvdcLine2TLCC<AclfBus>) bra;
						System.out.println(String.format("Connected LCC HVDC Branch %s, power: %s",hvdcBranch.getId(),hvdcBranch.powerIntoConverter(bus.getId())));

					} else if (bra instanceof  HvdcLine2TVSC) {
						HvdcLine2TVSC<AclfBus> hvdcBranch = (HvdcLine2TVSC<AclfBus>) bra;

						System.out.println(String.format("Connected VSC HVDC Branch %s, power: %s",hvdcBranch.getId(),hvdcBranch.powerIntoConverter(bus.getId())));
					} else {
						System.out.println("Connected Special Branch: " + bra.getId() );
					}
				}
				
			}
			System.out.println(bus.toString());
	
		}
		
	}

	private static boolean isSmallZBranchConnected(String busId, AclfNetwork net, double smallZ) {
		AclfBus b = net.getBus(busId); // Uncommented to get the bus object
		for(Branch bra:b.getBranchList()){
			if(bra.isActive() && bra instanceof AclfBranch) { // Changed 'and' to '&&'
				AclfBranch branch = (AclfBranch) bra; // Cast to AclfBranch to access getZ()
				if(branch.getZ().abs()<smallZ) {
					return true;
				}
			}
		}
		return false;
	}
public static AclfNetwork equivHVDC(AclfNetwork net) {

		// calculate the hvdc branch power
		for (Branch bra : net.getSpecialBranchList()) {
			if (!bra.isActive()) continue;
			if (bra instanceof HvdcLine2TLCC) {
				HvdcLine2TLCC<AclfBus> hvdcBranch = (HvdcLine2TLCC<AclfBus>) bra;
				hvdcBranch.calculateLoadflow();
				//add equivalent load to the from bus based on the power into the converter
				//hvdcBranch.getFromBus().
				Complex s = hvdcBranch.powerIntoConverter(hvdcBranch.getFromBusId());
				AclfBus bus = net.getBus(hvdcBranch.getFromBusId());
				if(bus != null && bus.isActive()){
					// create a load object with the total power at the boundary bus
					AclfLoad load = CoreObjectFactory.createAclfLoad(hvdcBranch.getId() + "_" + hvdcBranch.getFromBusId());
					bus.getContributeLoadList().add(load);
					load.setLoadCP(s);
					load.setCode(AclfLoadCode.CONST_P); // set load code to CONST_P
					bus.setLoadCode(AclfLoadCode.CONST_P);
				}

				//to bus
				Complex sTo = hvdcBranch.powerIntoConverter(hvdcBranch.getToBusId());
				AclfBus busTo = net.getBus(hvdcBranch.getToBusId());
				if(busTo != null && busTo.isActive()){
					// create a load object with the total power at the boundary bus
					AclfLoad loadTo = CoreObjectFactory.createAclfLoad(hvdcBranch.getId() + "_" + hvdcBranch.getToBusId());
					busTo.getContributeLoadList().add(loadTo);
					loadTo.setLoadCP(sTo);
					loadTo.setCode(AclfLoadCode.CONST_P); // set load code to CONST_P
					busTo.setLoadCode(AclfLoadCode.CONST_P);
				}
				//turn off the hvdc branch
				bra.setStatus(false);

			} else if (bra instanceof HvdcLine2TVSC) {
				HvdcLine2TVSC<AclfBus> hvdcBranch = (HvdcLine2TVSC<AclfBus>) bra;
				hvdcBranch.calculateLoadflow();
			}
		}

		return net;
	}

	public static AclfNetwork changeSWShuntToFixedShunt(AclfNetwork net) {
		for (AclfBus bus : net.getBusList()) {
			if (bus.isActive() && bus.isCapacitor()) {
				if (bus.isSwitchedShunt()) {
						// Get the total B from all switched shunts in service
						double b = bus.getSwitchedShuntList().stream()
									.filter(switchShunt->switchShunt.isControlStatus())
									.mapToDouble(switchShunt->switchShunt.getBActual())
									.sum();
						//remove the switched shunt
						bus.getSwitchedShuntList().clear();
		
						//add a fixed shunt to the bus
						if (b != 0) bus.setShuntY(bus.getShuntY().add(new Complex(0, b)));
				}
			}
		}
		
		return net;
	}

	public static AclfNetwork changeSVCToFixedShunt(AclfNetwork net) {
		for(AclfBus bus: net.getBusList()){
			if(bus.isActive() && bus.isStaticVarCompensator()&& bus.getFirstStaticVarCompensator().isControlStatus()){

				// if (bus.getId().equals("Bus345123")) {
				// 	System.out.println(bus.toString());
				// }
				bus.setGenCode(AclfGenCode.GEN_PV);
				Complex  mis = bus.mismatch(AclfMethodType.NR);
				if(Math.abs(mis.getImaginary())>0.001 ){
					StaticVarCompensator svc = bus.getFirstStaticVarCompensator();
					
					double vm = bus.getVoltageMag();
					double b = -mis.getImaginary()/(vm*vm);
					// make sure b is within the Bmin and Bmax
					if(b < svc.getBLimit(false).getMin()) b = svc.getBLimit(false).getMin();
					else if (b > svc.getBLimit(false).getMax()) b = svc.getBLimit(false).getMax();
					// set the initial B value
					// svc.setBInit(-b);
					if (b != 0) bus.setShuntY(bus.getShuntY().add(new Complex(0, b)));
					System.out.println(String.format("SVC %s at Bus %s is merged into bus shuntY with B = %f pu", svc.getId(), bus.getId(), svc.getBInit()) );
					// remove the SVC from the bus
					svc.setControlStatus(false);
					
				}	
			}
		}
		
		return net;
	}


}
