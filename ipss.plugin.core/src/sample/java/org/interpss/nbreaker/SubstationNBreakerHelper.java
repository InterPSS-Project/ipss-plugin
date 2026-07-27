package org.interpss.nbreaker;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

import com.interpss.core.net.Bus;
import com.interpss.core.net.NameTag;
import com.interpss.core.net.Substation;
import com.interpss.core.net.nb.NBEquipConnection;
import com.interpss.core.net.nb.NBModelEquipType;
import com.interpss.core.net.nb.NBNode;
import com.interpss.core.net.nb.NBSwitch;

public class SubstationNBreakerHelper {
	private Substation substation;

	public SubstationNBreakerHelper(Substation substation) {
		this.substation = substation;
	}

	public List<NBSwitch> getNbSwitchList(boolean activeOnly) {
		return this.substation.getNbSwitchList().stream()
				.filter(s -> !activeOnly || s.isActive())
				.collect(Collectors.toList());
	}

	public NBNode findNodeByName(String name) {
		return this.substation.getNbNodeList().stream()
				.filter(n -> name.equals(n.getName()))
				.findFirst()
				.orElse(null);
	}

	public NBSwitch findSwitchByName(String name) {
		return this.substation.getNbSwitchList().stream()
				.filter(s -> name.equals(s.getName()))
				.findFirst()
				.orElse(null);
	}

	public NBEquipConnection findEquip(NBModelEquipType type) {
		return this.substation.getNbEquipConnectList().stream()
				.filter(e -> e.getEquipType() == type)
				.findFirst()
				.orElse(null);
	}

	public NBEquipConnection findBranchEquip(String fromId, String toId) {
		return this.substation.getNbEquipConnectList().stream()
				.filter(e -> e.getEquipType() == NBModelEquipType.ACLF_BRANCH)
				.filter(e -> e.getFromBus() != null && e.getToBus() != null)
				.filter(e -> fromId.equals(e.getFromBus().getId()) && toId.equals(e.getToBus().getId()))
				.findFirst()
				.orElse(null);
	}

	/**
	 * Clear visit / component flags before a topology walk.
	 */
	private void clearTopoFlags() {
		for (NBNode node : this.substation.getNbNodeList()) {
			node.setIntFlag(0);
			node.setBooleanFlag(false);
		}
		for (NBSwitch sw : this.substation.getNbSwitchList()) {
			sw.setBooleanFlag(false);
		}
	}

	/**
	 * First NBNode with {@code booleanFlag == false}, or {@code null} if all visited.
	 */
	private NBNode findFirstUnvisitedNode() {
		return this.substation.getNbNodeList().stream()
				.filter(n -> !n.isBooleanFlag())
				.findFirst()
				.orElse(null);
	}

	public int topoAnalysis() {
		this.clearTopoFlags();

		// find the first unvisited node
		int groupNo = 0;
		while (this.findFirstUnvisitedNode() != null) {
			NBNode unvisitedNode = this.findFirstUnvisitedNode();
			//System.out.println("First unvisited node: " + unvisitedNode.getName());

			// mark the connected nodes from the unvisited node
			int n = this.markConnectedNode(unvisitedNode, ++groupNo);
			//System.out.println("Connected component size from unvisited node: " + n);
		}
		return groupNo;
	}

	/**
	 * BFS from {@code start} through closed switches ({@code currentStatus != 0}).
	 * Visited nodes and switches get {@code booleanFlag=true}; connected nodes get
	 * {@code intFlag=topoGroupNo}.
	 *
	 * @return number of nodes in the component
	 */
	public int markConnectedNode(NBNode start, int topoGroupNo) {
		if (start == null) {
			return 0;
		}
		Deque<NBNode> queue = new ArrayDeque<>();
		start.setBooleanFlag(true);
		start.setIntFlag(topoGroupNo);
		queue.add(start);
		int count = 1;

		while (!queue.isEmpty()) {
			NBNode node = queue.poll();
			for (NBSwitch sw : this.substation.getNbSwitchList()) {
				if (sw.isBooleanFlag() || sw.getCurrentStatus() == 0) {
					continue;
				}
				NBNode other = null;
				if (sw.getFromNBNode() == node) {
					other = sw.getToNBNode();
				} else if (sw.getToNBNode() == node) {
					other = sw.getFromNBNode();
				} else {
					continue;
				}
				sw.setBooleanFlag(true);
				if (other != null && !other.isBooleanFlag()) {
					other.setBooleanFlag(true);
					other.setIntFlag(topoGroupNo);
					queue.add(other);
					count++;
				}
			}
		}
		return count;
	}

	/**
	 * Print {@code Substation → AclfBus → NBNode → NBEquipConnection} (equip only; no bus refs on terminals).
	 */
	public void printSubstationTree() {
		System.out.println();
		System.out.println("Substation " + this.substation.getId() + " (" + this.substation.getName() + ")"
				+ " nodes=" + this.substation.getNbNodeList().size()
				+ " switches=" + this.substation.getNbSwitchList().size()
				+ "(" + this.getNbSwitchList(true).size() + " active)"
				+ " terminals=" + this.substation.getNbEquipConnectList().size());

		for (Bus bus : this.substation.getBusList()) {
			System.out.println("  Bus " + bus.getId() + " (" + bus.getName() + ")");
			for (NBNode node : this.substation.getNbNodeList()) {
				if (node.getBus() != bus) {
					continue;
				}
				System.out.println("    NBNode " + node.getId() + " " + node.getName());
				for (NBEquipConnection term : this.substation.getNbEquipConnectList()) {
					if (term.getBnNode() != node) {
						continue;
					}
					NameTag equip = term.getEquip();
					String equipId = equip != null ? equip.getId() : "?";
					String equipName = equip != null ? equip.getName() : "";
					System.out.println("      NBEquipConnection " + term.getEquipType()
							+ " id=" + equipId
							+ (equipName == null || equipName.isBlank() ? "" : " name=" + equipName.trim()));
				}
			}
		}
	}

	/** Print node/switch visit and component flags after a topology walk. */
	public void printTopoFlags() {
		System.out.println();
		System.out.println("Topo flags (node intFlag / booleanFlag; switch booleanFlag):");
		for (NBNode node : this.substation.getNbNodeList()) {
			System.out.println("  Node " + node.getName()
					+ " intFlag=" + node.getIntFlag()
					+ " visited=" + node.isBooleanFlag());
		}
		for (NBSwitch sw : this.substation.getNbSwitchList()) {
			System.out.println("  Switch " + sw.getName()
					+ " status=" + sw.getCurrentStatus()
					+ " visited=" + sw.isBooleanFlag());
		}
	}

}
