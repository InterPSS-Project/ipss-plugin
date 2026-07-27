package org.interpss.nbreaker;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.net.BranchBusSide;
import com.interpss.core.net.NameTag;
import com.interpss.core.net.Substation;
import com.interpss.core.net.nb.NBEquipConnection;
import com.interpss.core.net.nb.NBNode;

public class SubstationBusSplitMergeHelper {
	private static final Logger logger = LoggerFactory.getLogger(SubstationBusSplitMergeHelper.class);
	
	private Substation substation;

	public SubstationBusSplitMergeHelper(Substation substation) {
		this.substation = substation;
	}

	/**
	 * Split a bus into two buses by moving equipments of groupN2 to the newly create bus.
	 * 
	 * @param groupN1 the group number of the bus to split
	 * @param groupN2 the group number of the bus to move equipment to
	 * @return
	 */
	public boolean splitBus(int groupN1, int groupN2) {
		List<NBEquipConnection> group1 = this.getEquipByGroup(groupN1);
		System.out.println("Group 1 equip count: " + group1.size());

		List<NBEquipConnection> group2 = this.getEquipByGroup(groupN2);
		System.out.println("Group 2 equip count: " + group2.size());

		BaseAclfNetwork<?, ?> aclfNet = (BaseAclfNetwork<?, ?>) this.substation.getNetwork();
		AclfBus bus1 = this.getBusByGroup(1);
		AclfBus bus1Split = CoreObjectFactory.createAclfBus(bus1.getId() + "_split", aclfNet).get();
		bus1Split.setName(bus1.getName() + " Split");
		bus1Split.setBaseVoltage(bus1.getBaseVoltage());
		bus1Split.setVoltage(bus1.getVoltage());
		bus1Split.setSubstation(this.substation);
		logger.info("Split bus: " + bus1Split.getId()
				+ " name=" + bus1Split.getName()
				+ " Vbase=" + bus1Split.getBaseVoltage()
				+ " V=" + bus1Split.getVoltage()
				+ " (from " + bus1.getId() + ")");

		this.moveEquipToBus(group2, bus1, bus1Split);
		// Retarget all group-N2 nodes (incl. bus-bar nodes with no terminals)
		for (NBNode node : this.substation.getNbNodeList()) {
			if (node.getIntFlag() == groupN2) {
				node.setBus(bus1Split);
			}
		}
		logger.info("After move group2 -> " + bus1Split.getId()
				+ ": bus1 gens=" + bus1.getContributeGenList().size()
				+ " loads=" + bus1.getContributeLoadList().size()
				+ " branches=" + bus1.getBranchList().size()
				+ "; split gens=" + bus1Split.getContributeGenList().size()
				+ " loads=" + bus1Split.getContributeLoadList().size()
				+ " branches=" + bus1Split.getBranchList().size());

		return true;
	}

	/**
	 * Merge {@code toMerge} onto {@code retained}: move equipment and NB node
	 * bus refs, then remove {@code toMerge} from the network.
	 *
	 * @param retained bus kept after merge (e.g. Bus2)
	 * @param toMerge  bus to absorb and delete (e.g. Bus2_split)
	 * @return true if merge and remove succeeded
	 */
	public boolean mergeBus(AclfBus retained, AclfBus toMerge) {
		if (retained == null || toMerge == null || retained == toMerge) {
			logger.error("mergeBus: invalid retained/toMerge buses");
			return false;
		}

		List<NBEquipConnection> terms = this.substation.getNbEquipConnectList().stream()
				.filter(term -> term.getBnNode() != null && term.getBnNode().getBus() == toMerge)
				.collect(Collectors.toList());
		this.moveEquipToBus(terms, toMerge, retained);

		for (NBNode node : this.substation.getNbNodeList()) {
			if (node.getBus() == toMerge) {
				node.setBus(retained);
			}
		}

		this.substation.getBusList().remove(toMerge);
		BaseAclfNetwork<?, ?> aclfNet = (BaseAclfNetwork<?, ?>) this.substation.getNetwork();
		String toMergeId = toMerge.getId();
		if (!aclfNet.removeBus(toMergeId)) {
			logger.error("mergeBus: failed to remove bus " + toMergeId
					+ " (branches still connected?)");
			return false;
		}
		logger.info("Merged " + toMerge.getId() + " into " + retained.getId()
				+ "; gens=" + retained.getContributeGenList().size()
				+ " loads=" + retained.getContributeLoadList().size()
				+ " branches=" + retained.getBranchList().size());
		return true;
	}

	/**
	 * Equip terminals whose {@link NBNode#getIntFlag()} equals {@code group}
	 * (after {@link #topoAnalysis()}).
	 */
	public List<NBEquipConnection> getEquipByGroup(int group) {
		return this.substation.getNbEquipConnectList().stream()
				.filter(term -> term.getBnNode() != null && term.getBnNode().getIntFlag() == group)
				.collect(Collectors.toList());
	}

	/**
	 * Electrical bus linked from any NBNode in the topology {@code group}
	 * (after {@link #topoAnalysis()}).
	 */
	public AclfBus getBusByGroup(int group) {
		return this.substation.getNbNodeList().stream()
				.filter(n -> n.getIntFlag() == group && n.getBus() != null)
				.map(n -> (AclfBus) n.getBus())
				.findFirst()
				.orElse(null);
	}

	/**
	 * Move bus-branch equipment referenced by {@code terms} from {@code fromBus}
	 * onto {@code toBus}, and retarget matching NB overlay bus refs / node bus links.
	 */
	public void moveEquipToBus(List<NBEquipConnection> terms, AclfBus fromBus, AclfBus toBus) {
		if (terms == null || fromBus == null || toBus == null) {
			return;
		}
		AclfGenCode fromGenCode = fromBus.getGenCode();
		AclfLoadCode fromLoadCode = fromBus.getLoadCode();
		boolean movedGen = false;
		boolean movedLoad = false;

		for (NBEquipConnection term : terms) {
			NameTag equip = term.getEquip();
			if (equip == null) {
				continue;
			}
			switch (term.getEquipType()) {
				case MACHINE -> {
					if (equip instanceof AclfGen gen && gen.getParentBus() == fromBus) {
						gen.setParentBus(toBus);
						movedGen = true;
					}
				}
				case LOAD -> {
					if (equip instanceof AclfLoad load && load.getParentBus() == fromBus) {
						load.setParentBus(toBus);
						movedLoad = true;
					}
				}
				case ACLF_BRANCH -> {
					if (equip instanceof AclfBranch branch) {
						if (branch.getFromBus() == fromBus) {
							branch.reconnect(toBus, BranchBusSide.FROM_SIDE, false);
						} else if (branch.getToBus() == fromBus) {
							branch.reconnect(toBus, BranchBusSide.TO_SIDE, false);
						}
					}
				}
				default -> {
					// other terminal types not needed for the IEEE14 bus-split sample
					logger.warn("Unsupported equip type: " + term.getEquipType());
				}
			}

			if (term.getFromBus() == fromBus) {
				term.setFromBus(toBus);
			}
			if (term.getToBus() == fromBus) {
				term.setToBus(toBus);
			}
			if (term.getTertBus() == fromBus) {
				term.setTertBus(toBus);
			}

			NBNode node = term.getBnNode();
			if (node != null && node.getBus() == fromBus) {
				node.setBus(toBus);
			}
		}

		if (movedGen) {
			if (toBus.getGenCode() == AclfGenCode.NON_GEN) {
				toBus.setGenCode(fromGenCode);
			}
			if (fromBus.getContributeGenList().isEmpty()) {
				fromBus.setGenCode(AclfGenCode.NON_GEN);
			}
			fromBus.initContributeGen(false);
			toBus.initContributeGen(false);
		}
		if (movedLoad) {
			if (toBus.getLoadCode() == AclfLoadCode.NON_LOAD) {
				toBus.setLoadCode(fromLoadCode);
			}
			if (fromBus.getContributeLoadList().isEmpty()) {
				fromBus.setLoadCode(AclfLoadCode.NON_LOAD);
			}
			fromBus.initContributeLoad(false);
			toBus.initContributeLoad(false);
		}
	}

	/**
	 * Print {@code NBEquipConnection} terminals for each topology group
	 * (node {@code intFlag} after {@link #topoAnalysis()}).
	 */
	public void printEquipByGroup(int groupNo) {
		System.out.println();
		System.out.println("EquipConnection by topo group:");
		for (int g = 1; g <= groupNo; g++) {
			System.out.println("  Group " + g + ":");
			List<NBEquipConnection> terms = this.getEquipByGroup(g);
			if (terms.isEmpty()) {
				System.out.println("    (none)");
				continue;
			}
			for (NBEquipConnection term : terms) {
				NBNode node = term.getBnNode();
				NameTag equip = term.getEquip();
				String equipId = equip != null ? equip.getId() : "?";
				String equipName = equip != null ? equip.getName() : "";
				System.out.println("    " + term.getEquipType()
						+ " id=" + equipId
						+ (equipName == null || equipName.isBlank() ? "" : " name=" + equipName.trim())
						+ " @ node=" + (node != null ? node.getName() : "?"));
			}
		}
	}

}
