package org.interpss.nbreaker;

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

	List<NBSwitch> getNbSwitchList(boolean activeOnly) {
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
	
}
