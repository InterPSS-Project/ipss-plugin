package org.interpss.threePhase.odm;

import org.ieee.odm.model.dstab.DStabModelParser;

import com.interpss.common.msg.IPSSMsgHub;

public class ODM3PhaseDStabParserMapper extends AbstractODM3PhaseDStabParserMapper<DStabModelParser> {

	/**
	 * constructor
	 *
	 * @param msg
	 */
	public ODM3PhaseDStabParserMapper(IPSSMsgHub msg) {
		this.msg = msg;
	}
}
