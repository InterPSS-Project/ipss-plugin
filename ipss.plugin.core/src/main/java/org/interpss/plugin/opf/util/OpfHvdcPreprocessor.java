package org.interpss.plugin.opf.util;

import org.apache.commons.math3.complex.Complex;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.net.Branch;
import com.interpss.opf.OpfBus;
import com.interpss.opf.OpfNetwork;

public final class OpfHvdcPreprocessor {
	private OpfHvdcPreprocessor() {
	}

	public static void preprocess(OpfNetwork network) {
		double baseMva = network.getBaseKva() * 0.001;
		for (Branch branch : network.getSpecialBranchList()) {
			if (!branch.isActive() || !(branch.getExtensionObject() instanceof MatpowerDcLineData data)) {
				continue;
			}
			addTerminalLoad((OpfBus)branch.getFromBus(), branch.getId() + "_matpower_from",
					"MATPOWER DC line " + data.index() + " from terminal",
					new Complex(data.fromPmw() / baseMva, data.fromQmvar() / baseMva));
			addTerminalLoad((OpfBus)branch.getToBus(), branch.getId() + "_matpower_to",
					"MATPOWER DC line " + data.index() + " to terminal",
					new Complex(data.toPmw() / baseMva, data.toQmvar() / baseMva));
			branch.setStatus(false);
		}
	}

	private static void addTerminalLoad(OpfBus bus, String id, String name, Complex loadPower) {
		if (bus.getContributeLoadList().stream().anyMatch(load -> id.equals(load.getId()))) {
			return;
		}
		AclfLoad load = CoreObjectFactory.createAclfLoad(id);
		load.setName(name);
		load.setLoadCP(loadPower);
		load.setCode(AclfLoadCode.CONST_P);
		bus.getContributeLoadList().add(load);
		bus.setLoadCode(AclfLoadCode.CONST_P);
	}
}
