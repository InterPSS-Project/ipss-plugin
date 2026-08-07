package org.interpss.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;
import org.interpss.display.impl.AclfOut_PSSE;
import org.interpss.numeric.datatype.ComplexFunc;
import org.slf4j.Logger;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import com.interpss.core.aclf.hvdc.VSCConverter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.net.Branch;

public class QAUtil {
	// add a logger
	private static final Logger log = org.slf4j.LoggerFactory.getLogger(QAUtil.class);

	public static double getMaxBusVoltageMagDiff (AclfNetwork net, AclfNetwork copyNet) {
		double maxDiff = 0;
		String maxDiffBusId = "";
		for(AclfBus bus: net.getBusList()) {
			if(bus.isActive()) {
				double vm = bus.getVoltageMag();
				AclfBus copyBus = copyNet.getBus(bus.getId());
				double vmCopy = copyBus.getVoltageMag();
				double vmdiff = vm - vmCopy;
				if(Math.abs(vmdiff) > maxDiff) {
					maxDiff = Math.abs(vmdiff);
					maxDiffBusId = bus.getId();
				}
			}
		}
		System.out.println("Max bus voltage magnitude difference: " + maxDiff
				+ " (Bus ID: " + maxDiffBusId + ")");
		return maxDiff;
	}

	public static double getMaxBusVoltageDiff (AclfNetwork net, AclfNetwork copyNet) {
		return getMaxBusVoltageDiff(net, copyNet, false);
	}
	
	public static double getMaxBusVoltageDiff (AclfNetwork net, AclfNetwork copyNet, boolean genBusOnly) {
		double maxDiff = 0;
		String maxDiffBusId = "";
		for(AclfBus bus: net.getBusList()) {
				if(bus.isActive() && (!genBusOnly || bus.isGen())) {
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

	/**
	 * Compare bus-voltage phasors after removing the single uniform angle offset
	 * between two solutions. Absolute voltage angle is arbitrary; the rotation is
	 * chosen by the least-squares phasor fit over all active buses. Voltage
	 * magnitude and relative-angle differences are preserved.
	 */
	public static double getMaxBusVoltageDiffAngleAligned(
			AclfNetwork net, AclfNetwork referenceNet) {
		double correlationReal = 0.0;
		double correlationImaginary = 0.0;
		for (AclfBus bus : net.getBusList()) {
			AclfBus referenceBus = referenceNet.getBus(bus.getId());
			if (!bus.isActive() || referenceBus == null || !referenceBus.isActive()) {
				continue;
			}
			Complex voltage = bus.getVoltage();
			Complex referenceVoltage = referenceBus.getVoltage();
			correlationReal += referenceVoltage.getReal() * voltage.getReal()
					+ referenceVoltage.getImaginary() * voltage.getImaginary();
			correlationImaginary += referenceVoltage.getImaginary() * voltage.getReal()
					- referenceVoltage.getReal() * voltage.getImaginary();
		}

		double angle = Math.atan2(correlationImaginary, correlationReal);
		Complex rotation = new Complex(Math.cos(angle), Math.sin(angle));
		double maxDiff = 0.0;
		String maxDiffBusId = "";
		for (AclfBus bus : net.getBusList()) {
			AclfBus referenceBus = referenceNet.getBus(bus.getId());
			if (!bus.isActive() || referenceBus == null || !referenceBus.isActive()) {
				continue;
			}
			double diff = bus.getVoltage().multiply(rotation)
					.subtract(referenceBus.getVoltage()).abs();
			if (diff > maxDiff) {
				maxDiff = diff;
				maxDiffBusId = bus.getId();
			}
		}
		System.out.println("Max angle-aligned bus voltage difference: " + maxDiff
				+ " (Bus ID: " + maxDiffBusId + ", reference shift: "
				+ Math.toDegrees(angle) + " deg)");
		return maxDiff;
	}

	/**
	 * Compare bus-voltage phasors after independently removing the uniform angle
	 * offset of each disconnected AC island. HVDC links do not constrain the AC
	 * phase reference between their terminal islands.
	 */
	public static double getMaxBusVoltageDiffAngleAlignedByIsland(
			AclfNetwork net, AclfNetwork referenceNet) {
		Set<String> visited = new HashSet<>();
		double maxDiff = 0.0;
		String maxDiffBusId = "";
		double maxDiffIslandShift = 0.0;

		for (AclfBus seed : net.getBusList()) {
			AclfBus referenceSeed = referenceNet.getBus(seed.getId());
			if (!seed.isActive() || referenceSeed == null || !referenceSeed.isActive()
					|| visited.contains(seed.getId())) {
				continue;
			}

			List<AclfBus> island = new ArrayList<>();
			ArrayDeque<AclfBus> queue = new ArrayDeque<>();
			queue.add(seed);
			visited.add(seed.getId());
			while (!queue.isEmpty()) {
				AclfBus bus = queue.removeFirst();
				island.add(bus);
				for (Branch branch : bus.getBranchIterable()) {
					if (!(branch instanceof AclfBranch) || !branch.isActive()) {
						continue;
					}
					AclfBus oppositeBus = (AclfBus) branch.getOppositeBus(bus);
					AclfBus referenceBus = referenceNet.getBus(oppositeBus.getId());
					if (oppositeBus.isActive() && referenceBus != null
							&& referenceBus.isActive() && visited.add(oppositeBus.getId())) {
						queue.addLast(oppositeBus);
					}
				}
			}

			double correlationReal = 0.0;
			double correlationImaginary = 0.0;
			for (AclfBus bus : island) {
				Complex voltage = bus.getVoltage();
				Complex referenceVoltage = referenceNet.getBus(bus.getId()).getVoltage();
				correlationReal += referenceVoltage.getReal() * voltage.getReal()
						+ referenceVoltage.getImaginary() * voltage.getImaginary();
				correlationImaginary += referenceVoltage.getImaginary() * voltage.getReal()
						- referenceVoltage.getReal() * voltage.getImaginary();
			}

			double angle = Math.atan2(correlationImaginary, correlationReal);
			Complex rotation = new Complex(Math.cos(angle), Math.sin(angle));
			for (AclfBus bus : island) {
				double diff = bus.getVoltage().multiply(rotation)
						.subtract(referenceNet.getBus(bus.getId()).getVoltage()).abs();
				if (diff > maxDiff) {
					maxDiff = diff;
					maxDiffBusId = bus.getId();
					maxDiffIslandShift = angle;
				}
			}
		}

		System.out.println("Max island-aligned bus voltage difference: " + maxDiff
				+ " (Bus ID: " + maxDiffBusId + ", island reference shift: "
				+ Math.toDegrees(maxDiffIslandShift) + " deg)");
		return maxDiff;
	}

	/**
	 * Return the Type-3 (swing) bus IDs for every active AC island. The list has
	 * one entry per island, including an empty set when an island has no Type-3
	 * bus. HVDC links are not treated as AC island connections.
	 */
	public static List<Set<String>> getActiveAcIslandType3BusIds(AclfNetwork net) {
		List<Set<String>> result = new ArrayList<>();
		for (List<AclfBus> island : findActiveAcIslands(net)) {
			Set<String> type3BusIds = new LinkedHashSet<>();
			for (AclfBus bus : island) {
				if (bus.isSwing()) {
					type3BusIds.add(bus.getId());
				}
			}
			result.add(type3BusIds);
		}
		return result;
	}

	/**
	 * Compare voltage angles after anchoring each active AC island at its Type-3
	 * bus. The two networks must have identical active-island membership and
	 * identical Type-3 assignments. The returned value is the largest remaining
	 * within-island angle difference in degrees.
	 */
	public static double getMaxBusVoltageAngleDiffAtType3ReferenceDeg(
			AclfNetwork net, AclfNetwork referenceNet) {
		List<List<AclfBus>> islands = findActiveAcIslands(net);
		List<List<AclfBus>> referenceIslands = findActiveAcIslands(referenceNet);
		Set<Set<String>> islandBusIds = islandBusIdSets(islands);
		Set<Set<String>> referenceIslandBusIds = islandBusIdSets(referenceIslands);
		if (!islandBusIds.equals(referenceIslandBusIds)) {
			throw new IllegalArgumentException(
					"Active AC island membership differs between the two networks");
		}

		double maxDiffDeg = 0.0;
		String maxDiffBusId = "";
		String maxDiffReferenceBusId = "";
		for (List<AclfBus> referenceIsland : referenceIslands) {
			Set<String> islandIds = new HashSet<>();
			Set<String> referenceType3Ids = new HashSet<>();
			for (AclfBus referenceBus : referenceIsland) {
				islandIds.add(referenceBus.getId());
				if (referenceBus.isSwing()) {
					referenceType3Ids.add(referenceBus.getId());
				}
			}
			Set<String> type3Ids = new HashSet<>();
			for (String busId : islandIds) {
				if (net.getBus(busId).isSwing()) {
					type3Ids.add(busId);
				}
			}
			if (!type3Ids.equals(referenceType3Ids)) {
				throw new IllegalArgumentException("Type-3 bus assignments differ for AC island "
						+ islandIds + ": actual=" + type3Ids
						+ ", reference=" + referenceType3Ids);
			}
			if (referenceType3Ids.isEmpty()) {
				throw new IllegalArgumentException(
						"Active AC island has no Type-3 reference bus: " + islandIds);
			}

			String referenceBusId = referenceType3Ids.stream().sorted().findFirst().get();
			double referenceShift = referenceNet.getBus(referenceBusId).getVoltageAng()
					- net.getBus(referenceBusId).getVoltageAng();
			for (String busId : islandIds) {
				double angleDiff = net.getBus(busId).getVoltageAng() + referenceShift
						- referenceNet.getBus(busId).getVoltageAng();
				double angleDiffDeg = Math.toDegrees(Math.abs(
						Math.atan2(Math.sin(angleDiff), Math.cos(angleDiff))));
				if (angleDiffDeg > maxDiffDeg) {
					maxDiffDeg = angleDiffDeg;
					maxDiffBusId = busId;
					maxDiffReferenceBusId = referenceBusId;
				}
			}
		}

		System.out.println("Max Type-3-anchored bus voltage angle difference: "
				+ maxDiffDeg + " deg (Bus ID: " + maxDiffBusId
				+ ", Type-3 reference: " + maxDiffReferenceBusId + ")");
		return maxDiffDeg;
	}

	private static List<List<AclfBus>> findActiveAcIslands(AclfNetwork net) {
		List<List<AclfBus>> islands = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		for (AclfBus seed : net.getBusList()) {
			if (!seed.isActive() || !visited.add(seed.getId())) {
				continue;
			}
			List<AclfBus> island = new ArrayList<>();
			ArrayDeque<AclfBus> queue = new ArrayDeque<>();
			queue.add(seed);
			while (!queue.isEmpty()) {
				AclfBus bus = queue.removeFirst();
				island.add(bus);
				for (Branch branch : bus.getBranchIterable()) {
					if (!(branch instanceof AclfBranch) || !branch.isActive()) {
						continue;
					}
					AclfBus oppositeBus = (AclfBus) branch.getOppositeBus(bus);
					if (oppositeBus.isActive() && visited.add(oppositeBus.getId())) {
						queue.addLast(oppositeBus);
					}
				}
			}
			islands.add(island);
		}
		return islands;
	}

	private static Set<Set<String>> islandBusIdSets(List<List<AclfBus>> islands) {
		Set<Set<String>> result = new HashSet<>();
		for (List<AclfBus> island : islands) {
			Set<String> busIds = new HashSet<>();
			for (AclfBus bus : island) {
				busIds.add(bus.getId());
			}
			result.add(busIds);
		}
		return result;
	}

	public static Complex getMaxBranchFlowDiff (AclfNetwork net, AclfNetwork copyNet, double zeroZBranchTreshold) {
		Complex maxDiff = new Complex(0,0);
		String maxDiffBranchId = "";
		for(AclfBranch branch: net.getBranchList()) {
			AclfBranch copyBranch = copyNet.getBranch(branch.getId());
			if(branch.isActive() && copyBranch != null && branch.getAdjustedZ().abs() > zeroZBranchTreshold) { // check if the branch is active and has a non-zero impedance
				Complex flow = branch.powerFrom2To();
				Complex flowPSSE = copyBranch.powerFrom2To();
				Complex flowDiff = flow.subtract(flowPSSE);
				double flowDiffAbs = flowDiff.abs();
				if(flowDiffAbs > maxDiff.abs()) {
					maxDiff = flowDiff;
					maxDiffBranchId = branch.getId();
					/*
					if (maxDiffBranchId.equals("Bus7366->Bus7400(1)")) {
						System.out.println("Branch " + maxDiffBranchId + " has a flow difference of " + maxDiff);
						System.out.println("Flow from 2 to: " + flow);
						System.out.println("PSSE Flow from 2 to: " + flowPSSE);
					}
					*/
				}
			}
		}
		System.out.println("Max branch flow difference: " + ComplexFunc.toStr(maxDiff) + " (Branch ID: " + maxDiffBranchId + ")");
		return maxDiff;
	}

    public static double getMaxGenPOutputDiff (AclfNetwork net, AclfNetwork copyNet) {
		double maxDiff = 0;
		String maxDiffGenId = "";
		AclfGenCode maxDiffGenType = AclfGenCode.NON_GEN;
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
								maxDiffGenType = bus.getGenCode();
							}
						}
					}
				}
			}
		}
		System.out.println("Max generator output difference: " + maxDiff + " (Generator ID: " + maxDiffGenId + 
				 			" Type: " + maxDiffGenType + ")");
		return maxDiff;
	}



    public static void saveCompareResults(AclfNetwork net, AclfNetwork copyNet, String busCompareFile, String branchCompareFile, String genCompareFile) {
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

	public static void saveBusVoltCompareResults(AclfNetwork net, AclfNetwork copyNet, String busCompareFile, String busNumberJsonFile) {
		Set<String> includedBusIds = loadBusIdsFromJson(busNumberJsonFile);
        StringBuffer busResultCompare = new StringBuffer();
		busResultCompare.append("BusId,AreaId,Vm,VmPSSE,VmDiff,Va,VaPSSE,VaDiff,VmDiffPercent,VaDiffPercent\n");
		for(AclfBus bus: net.getBusList()) {
			if(bus.isActive() && includedBusIds.contains(bus.getId())) {
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
			System.err.println("Error writing bus comparison results to file: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private static Set<String> loadBusIdsFromJson(String busNumberJsonFile) {
		Set<String> busIds = new HashSet<>();
		try (Reader reader = new FileReader(busNumberJsonFile)) {
			JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
			JsonArray busNumbers = jsonObject.getAsJsonArray("Bus Number");
			if (busNumbers == null) {
				throw new IllegalArgumentException("Missing 'Bus Number' array in " + busNumberJsonFile);
			}

			for (JsonElement busNumber : busNumbers) {
				String value = busNumber.getAsString().trim();
				busIds.add(value.startsWith("Bus") ? value : "Bus" + value);
			}
		} catch (IOException e) {
			throw new RuntimeException("Error reading bus number JSON file: " + busNumberJsonFile, e);
		}

		return busIds;
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
				for( Branch bra : bus.getBranchIterable()){
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
			for( Branch bra : bus.getBranchIterable()){
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
		for(Branch bra:b.getBranchIterable()){
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
			//System.out.println("Processing HVDC Branch: " + bra.getId());
			if (bra instanceof HvdcLine2TLCC) {
				HvdcLine2TLCC<AclfBus> hvdcBranch = (HvdcLine2TLCC<AclfBus>) bra;
				hvdcBranch.initLoadflow();
				//add equivalent load to the from bus based on the power into the converter
				//hvdcBranch.getFromBus().
				Complex s = hvdcBranch.powerIntoConverter(hvdcBranch.getFromBusId());
				AclfBus bus = net.getBus(hvdcBranch.getFromBusId());
				// if(bus.getId().equals("Bus667661")){
				// 	System.out.println("HVDC From Bus Power: " + s);
				// }

				if(bus != null){
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
				if(busTo != null){
					// create a load object with the total power at the boundary bus
					AclfLoad loadTo = CoreObjectFactory.createAclfLoad(hvdcBranch.getId() + "_" + hvdcBranch.getToBusId());
					busTo.getContributeLoadList().add(loadTo);
					loadTo.setLoadCP(sTo);
					loadTo.setCode(AclfLoadCode.CONST_P); // set load code to CONST_P
					busTo.setLoadCode(AclfLoadCode.CONST_P);
				}
				//turn off the hvdc branch
				bra.setStatus(false);

			} else if (bra instanceof HvdcLine2TVSC && bra.isActive()) {
				HvdcLine2TVSC<AclfBus> hvdcBranch = (HvdcLine2TVSC<AclfBus>) bra;
				//turn off the hvdc branch
				bra.setStatus(false);

				//from bus
				AclfBus busFrom = net.getBus(hvdcBranch.getFromBusId());
				if(busFrom != null && busFrom.isActive()){
					//hvdcBranch.initLoadflow();
					// create a load object with the total power at the boundary bus
					AclfLoad loadFrom = CoreObjectFactory.createAclfLoad(hvdcBranch.getId() + "_" + hvdcBranch.getFromBusId());
					busFrom.getContributeLoadList().add(loadFrom);
					loadFrom.setLoadCP(busFrom.mismatch(AclfMethodType.NR));
					loadFrom.setCode(AclfLoadCode.CONST_P); // set load code to CONST_P
					busFrom.setLoadCode(AclfLoadCode.CONST_P);
				}
				//to bus
				AclfBus busTo = net.getBus(hvdcBranch.getToBusId());
				if(busTo != null && busTo.isActive()){
					// create a load object with the total power at the boundary bus
					AclfLoad loadTo = CoreObjectFactory.createAclfLoad(hvdcBranch.getId() + "_" + hvdcBranch.getToBusId());
					busTo.getContributeLoadList().add(loadTo);
					loadTo.setLoadCP(busTo.mismatch(AclfMethodType.NR));
					loadTo.setCode(AclfLoadCode.CONST_P); // set load code to CONST_P
					busTo.setLoadCode(AclfLoadCode.CONST_P);
				}
				
			}
		}

		return net;
	}

	public static AclfNetwork equivLCCHVDC(AclfNetwork net) {

		// calculate the hvdc branch power
		for (Branch bra : net.getSpecialBranchList()) {
			if (!bra.isActive()) continue;
			System.out.println("Processing HVDC Branch: " + bra.getId());
			if (bra instanceof HvdcLine2TLCC) {
				HvdcLine2TLCC<AclfBus> hvdcBranch = (HvdcLine2TLCC<AclfBus>) bra;
				hvdcBranch.initLoadflow();
				//add equivalent load to the from bus based on the power into the converter
				//hvdcBranch.getFromBus().
				Complex s = hvdcBranch.powerIntoConverter(hvdcBranch.getFromBusId());
				AclfBus bus = net.getBus(hvdcBranch.getFromBusId());
				if(bus.getId().equals("Bus667661")){
					System.out.println("HVDC From Bus Power: " + s);
				}

				if(bus != null){
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
				if(busTo != null){
					// create a load object with the total power at the boundary bus
					AclfLoad loadTo = CoreObjectFactory.createAclfLoad(hvdcBranch.getId() + "_" + hvdcBranch.getToBusId());
					busTo.getContributeLoadList().add(loadTo);
					loadTo.setLoadCP(sTo);
					loadTo.setCode(AclfLoadCode.CONST_P); // set load code to CONST_P
					busTo.setLoadCode(AclfLoadCode.CONST_P);
				}
				//turn off the hvdc branch
				bra.setStatus(false);

			} 
		}

		return net;
	}

	public static AclfNetwork equivVSCHVDC(AclfNetwork net) {

		// calculate the hvdc branch power
		for (Branch bra : net.getSpecialBranchList()) {
			if (!bra.isActive()) continue;
			if (bra instanceof HvdcLine2TVSC && bra.isActive()) {
				HvdcLine2TVSC<AclfBus> hvdcBranch = (HvdcLine2TVSC<AclfBus>) bra;
				//turn off the hvdc branch
				bra.setStatus(false);
				// To consider the VSC-HVDC control modes on both terminals.
				processVscConverter(net, hvdcBranch.getId(), hvdcBranch.getRecConverter());
				processVscConverter(net, hvdcBranch.getId(), hvdcBranch.getInvConverter());
			}
		}

		return net;
	}

	private static void processVscConverter(AclfNetwork net, String branchId, VSCConverter<AclfBus> converter) {
		if (converter == null) {
			return;
		}

		AclfBus bus = converter.getBus();
		if (bus == null || !bus.isActive()) {
			return;
		}

		double acSet = converter.getAcSetPoint();
		switch (converter.getAcControlMode()) {
			case AC_POWER_FACTOR:
			case AC_REACTIVE_POWER:
				addEquivalentLoad(bus, branchId + "_" + bus.getId(),
						createEquivalentLoadPower(net.getBaseMva(), converter));
				break;

			case AC_VOLTAGE: {
				double vm = bus.getVoltageMag();
				if (Math.abs(acSet - vm) > 0.001) {
					log.warn(String.format(
							"Warning: The voltage setpoint of VSC-HVDC %s is different from the terminal bus voltage. Setpoint: %f, Bus Voltage: %f. Set to terminal voltage for equivalence. Please check the original data and update the setpoint if needed.",
							converter.getId(), acSet, vm));
				}
				bus.setGenCode(AclfGenCode.GEN_PV);
				bus.setGenP(-converter.powerIntoConverter().getReal());
				bus.setDesiredVoltMag(vm); // Set the desired voltage magnitude to the current bus voltage
				break;
			}

			case AC_FREQ:
				throw new UnsupportedOperationException("AC frequency control is not supported yet!");
		}
	}

	private static Complex createEquivalentLoadPower(double baseMva, VSCConverter<AclfBus> converter) {
		Complex converterPower = converter.powerIntoConverter();
		double activePower = converterPower.getReal();
		double reactivePower;

		switch (converter.getAcControlMode()) {
			case AC_POWER_FACTOR:
				reactivePower = activePower * Math.tan(Math.acos(converter.getAcSetPoint()));
				break;

			case AC_REACTIVE_POWER:
				reactivePower = converter.getAcSetPoint() / baseMva;
				break;

			default:
				reactivePower = converterPower.getImaginary();
		}

		return new Complex(activePower, reactivePower);
	}

	private static void addEquivalentLoad(AclfBus bus, String loadId, Complex loadPower) {
		AclfLoad load = CoreObjectFactory.createAclfLoad(loadId);
		bus.getContributeLoadList().add(load);
		load.setLoadCP(loadPower);
		load.setCode(AclfLoadCode.CONST_P); // set load code to CONST_P
		bus.setLoadCode(AclfLoadCode.CONST_P);
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
			if(bus.isActive() && bus.isStaticVarCompensator()&& bus.getFirstStaticVarCompensator(true).isControlStatus()){

				// if (bus.getId().equals("Bus345123")) {
				// 	System.out.println(bus.toString());
				// }
				bus.setGenCode(AclfGenCode.GEN_PV);
				Complex  mis = bus.mismatch(AclfMethodType.NR);
				if(Math.abs(mis.getImaginary())>0.001 ){
					StaticVarCompensator svc = bus.getFirstStaticVarCompensator(true);
					
					double vm = bus.getVoltageMag();
					double b = -mis.getImaginary()/(vm*vm);
					// make sure b is within the Bmin and Bmax
					if(b < svc.getBLimit(false).getMin()) b = svc.getBLimit(false).getMin();
					else if (b > svc.getBLimit(false).getMax()) b = svc.getBLimit(false).getMax();
					// set the initial B value
					// svc.setBInit(-b);
					if (b != 0) bus.setShuntY(bus.getShuntY().add(new Complex(0, b)));
					log.info(String.format("SVC %s at Bus %s is merged into bus shuntY with B = %f pu", svc.getId(), bus.getId(), svc.getBInit()) );
					// remove the SVC from the bus
					svc.setControlStatus(false);
					
				}	
			}
		}
		
		return net;
	}

	/**
	 * Compare the InterPSS Y-matrix CSV output with a PSS/E Ybus text file.
	 * The PSS/E file format is: fromBusNum, toBusNum, real, imag (one entry per line).
	 * Lines after "ZERO IMPEDANCE LINE CONNECTED BUSES (BUS IN MATRIX LISTED FIRST):" are ignored.
	 * The PSS/E file is used as the base; only entries present in the PSS/E file are checked.
	 * Bus numbers > 1,000,000 in the PSS/E file are 3-winding transformer star buses,
	 * matched in the CSV by finding a row where one bus starts with "3WNDTR_" and the
	 * other bus matches the regular bus.
	 *
	 * @param csvFilePath      path to the InterPSS Y-matrix CSV file
	 * @param psseFilePath     path to the PSS/E Ybus text file
	 * @param tolerance        comparison tolerance
	 * @return comparison result as string
	 */
	public static String compareYMatrix(String csvFilePath, String psseFilePath, double tolerance) {
		StringBuilder sb = new StringBuilder();
		sb.append("Mismatch,fromBusNum,toBusNum,CSV_re,CSV_im,PSSE_re,PSSE_im,absDiff\n");

		int matchCount = 0;
		int mismatchCount = 0;
		int notFoundCount = 0;
		int star3WCount = 0;

		// Load CSV into a lookup map: "fromBusId|toBusId" -> Complex(re, im)
		Map<String, Complex> csvMap = new HashMap<>();
		// For 3WNDTR matching: regularBusId -> list of [starBusId, re, im]
		Map<String, List<String[]>> csvStarBusMap = new HashMap<>();

		try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
			String line = br.readLine(); // skip header row
			while ((line = br.readLine()) != null) {
				String[] parts = line.trim().split(",");
				if (parts.length < 4) continue;
				String fromId = parts[0].trim();
				String toId   = parts[1].trim();
				double re = Double.parseDouble(parts[2].trim());
				double im = Double.parseDouble(parts[3].trim());
				csvMap.put(fromId + "|" + toId, new Complex(re, im));
				// Build reverse lookup for rows involving a 3-winding star bus
				if (fromId.startsWith("3WNDTR_") || toId.startsWith("3WNDTR_")) {
					String regularId = fromId.startsWith("3WNDTR_") ? toId : fromId;
					String starId    = fromId.startsWith("3WNDTR_") ? fromId : toId;
					csvStarBusMap.computeIfAbsent(regularId, k -> new ArrayList<>())
						.add(new String[]{starId, String.valueOf(re), String.valueOf(im)});
				}
			}
		} catch (Exception e) {
			sb.append("Error reading CSV: ").append(e.getMessage()).append("\n");
			return sb.toString();
		}

		// Parse PSS/E file as base and compare
		try (BufferedReader br = new BufferedReader(new FileReader(psseFilePath))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (line.contains("ZERO IMPEDANCE LINE CONNECTED BUSES")) break;
				line = line.trim();
				if (line.isEmpty()) continue;
				String[] parts = line.split(",");
				if (parts.length != 4) continue;
				try {
					int fromBusNum = Integer.parseInt(parts[0].trim());
					int toBusNum   = Integer.parseInt(parts[1].trim());
					double psseRe  = Double.parseDouble(parts[2].trim());
					double psseIm  = Double.parseDouble(parts[3].trim());

					boolean fromIsStar = fromBusNum > 1_000_000;
					boolean toIsStar   = toBusNum   > 1_000_000;

					Complex csvValue = null;
					if (!fromIsStar && !toIsStar) {
						// Both regular buses
						String fromId = "Bus" + fromBusNum;
						String toId   = "Bus" + toBusNum;
						csvValue = csvMap.get(fromId + "|" + toId);
						if (csvValue == null)
							csvValue = csvMap.get(toId + "|" + fromId);
					} else {
						// One is a 3-winding star bus — match via the regular bus
						star3WCount++;
						String regularId = fromIsStar ? "Bus" + toBusNum : "Bus" + fromBusNum;
						List<String[]> starEntries = csvStarBusMap.get(regularId);
						if (starEntries != null && starEntries.size() == 1) {
							String[] entry = starEntries.get(0);
							csvValue = new Complex(Double.parseDouble(entry[1]), Double.parseDouble(entry[2]));
						} else if (starEntries != null && starEntries.size() > 1) {
							sb.append("AmbiguousStarBus,").append(fromBusNum).append(",").append(toBusNum)
							  .append(",multiple 3WNDTR entries for ").append(regularId).append("\n");
							notFoundCount++;
							continue;
						}
					}

					if (csvValue == null) {
						sb.append("NotFound,").append(fromBusNum).append(",").append(toBusNum).append("\n");
						notFoundCount++;
					} else {
						double diff = csvValue.subtract(new Complex(psseRe, psseIm)).abs();
						if (Math.abs(csvValue.getReal() - psseRe) > tolerance
								|| Math.abs(csvValue.getImaginary() - psseIm) > tolerance) {
							sb.append("Mismatch,").append(fromBusNum).append(",").append(toBusNum).append(",")
							  .append(csvValue.getReal()).append(",").append(csvValue.getImaginary()).append(",")
							  .append(psseRe).append(",").append(psseIm).append(",").append(diff).append("\n");
							mismatchCount++;
						} else {
							matchCount++;
						}
					}
				} catch (NumberFormatException e) {
					// skip non-data lines (title rows etc.)
				}
			}
		} catch (Exception e) {
			sb.append("Error reading PSS/E file: ").append(e.getMessage()).append("\n");
		}

		sb.append("Total matches: ").append(matchCount)
		  .append(", mismatches: ").append(mismatchCount)
		  .append(", not found: ").append(notFoundCount)
		  .append(", 3W star bus entries: ").append(star3WCount).append("\n");
		return sb.toString();
	}

	public static void printBusConnections(AclfNetwork net, String busId) {
		AclfBus bus = net.getBus(busId);
		if (bus == null) {
			log.warn("Bus " + busId + " not found in the network.");
			return;
		}
		log.info("Connections for Bus " + busId + ":");
		for (Branch bra : bus.getBranchIterable()) {
			if (bra.isActive()) {
				System.out.println("Connected Branch: " + bra.getId() + ", From: " + bra.getFromBus().getId() + ", To: " + bra.getToBus().getId());
			}
		}
	}


}
