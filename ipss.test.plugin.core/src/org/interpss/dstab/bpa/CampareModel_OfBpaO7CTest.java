package org.interpss.dstab.bpa;

import java.io.File;
import java.io.FileInputStream;

import org.ieee.odm.ODMObjectFactory;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.ieee.odm.model.dstab.DStabModelParser;
import org.interpss.dstab.DStabTestSetupBase;
import org.interpss.mapper.odm.ODMAclfParserMapper;
import org.interpss.mapper.odm.ODMDStabDataMapper;
import org.interpss.numeric.util.NumericUtil;
import org.junit.Test;

import com.interpss.SimuObjectFactory;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.tools.compare.IAclfBranchComparator;
import com.interpss.tools.compare.IAclfBusComparator;
import com.interpss.tools.compare.IAclfNetComparator;
import com.interpss.tools.compare.NetModelComparator;

public class CampareModel_OfBpaO7CTest extends DStabTestSetupBase {
	@Test
	public void compareTestCase() throws Exception {
		 AclfNetwork baseNet = null;

		File file = new File("testData/ieee_odm/07c_2010_OnlyMach_lf.xml");
		AclfModelParser aclfParser = ODMObjectFactory.createAclfModelParser();
		if (aclfParser.parse(new FileInputStream(file))) {
			//System.out.println(parser.toXmlDoc(false));

			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.ACLF_NETWORK);
			if (!new ODMAclfParserMapper().map2Model(aclfParser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			 baseNet=simuCtx.getAclfNet();
		}
		
		file = new File("testData/ieee_odm/07c_2010_OnlyMach.xml");
		DStabModelParser dstabParser = ODMObjectFactory.createDStabModelParser();
		if (dstabParser.parse(new FileInputStream(file))) {
			//System.out.println(parser.toXmlDoc(false));

			SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			if (!new ODMDStabDataMapper(msg)
						.map2Model(dstabParser, simuCtx)) {
				System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
				return;
			}
			
			DStabilityNetwork dstabNet = simuCtx.getDStabilityNet();
			
			System.out.println("LF Net as the base net for comparison");
			new NetModelComparator(baseNet)
					.compare(dstabNet, netCompRules, busCompRules, branchCompRules);

			System.out.println("DStab Net as the base net for comparison");
			new NetModelComparator(dstabNet)
				.compare(baseNet, netCompRules, busCompRules, branchCompRules);
		}
	}

	IAclfNetComparator netCompRules = new IAclfNetComparator() {
		@Override
		public boolean compare(AclfNetwork baseNet, AclfNetwork net) {
			boolean ok = true;
			if (baseNet.getNoBus() != net.getNoBus()) {
				IpssLogger.getLogger().warning("NoOfBus not the same: " + baseNet.getNoBus() + ", " + net.getNoBus());
				ok = false; 
			}
			if (baseNet.getNoBranch() != net.getNoBranch()) {
				IpssLogger.getLogger().warning("NoOfBranch not the same: " + baseNet.getNoBranch() + ", " + net.getNoBranch());
				ok = false; 
			}
			return ok;
		}
	}; 
	
	IAclfBusComparator busCompRules = new IAclfBusComparator(){
		@Override
		public boolean compare(AclfBus baseBus, AclfBus bus) {
			boolean ok = true;
			if (bus == null) {
				IpssLogger.getLogger().warning("AclfBus not found, " + baseBus.getId());
				ok = false; 
			}
			
			// compare base voltage
			if (!NumericUtil.equals(baseBus.getBaseVoltage(), bus.getBaseVoltage())) {
				IpssLogger.getLogger().warning("AclfBus base voltage not same, " + baseBus.getId());
				ok = false; 
			}

			// compare base yii
			/*
			if (!NumericUtil.equals(baseBus.yii(), bus.yii())) {
				IpssLogger.getLogger().warning("AclfBus yii not same, " + baseBus.getId());
				ok = false; 
			}
			*/
			return ok;
		}
	};
	
	IAclfBranchComparator branchCompRules = new IAclfBranchComparator(){
		@Override
		public boolean compare(AclfBranch baseBra, AclfBranch branch) {
			boolean ok = true;
			if (branch == null) {
				IpssLogger.getLogger().warning("AclfBranch not found, " + baseBra.getId());
				ok = false; 
			}
			return ok;
		}
	};
}
