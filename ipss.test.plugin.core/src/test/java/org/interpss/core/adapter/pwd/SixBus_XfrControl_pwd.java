package org.interpss.core.adapter.pwd;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;

public class SixBus_XfrControl_pwd extends CorePluginTestSetup {
	@Test
	public void aclf() throws Exception {
		IpssCorePlugin.init();
        //IpssCorePlugin.setSparseEqnSolver(SolverType.Native);


		AclfNetwork net = IpssAdapter.importAclfNet("testData/pwd/SixBus_XfrControl.aux")
					.setFormat(IpssAdapter.FileFormat.PWD)
					//.load(true, "output/odm.xml")
					.load()
					.getImportedObj();
  		//System.out.println(net.net2String());
  		
  		AclfBranch branch = net.getBranch("Bus1->Bus3(1)");
  		assertTrue(branch != null);
  		assertTrue(branch.getTapControl() != null);
  		assertEquals(1.0450, branch.getTapControl().getControlSpec(), 0.0001);
  		
  		branch = net.getBranch("Bus5->Bus6(T9)");
  		assertTrue(branch != null);
  		assertTrue(branch.getPSXfrPControl() != null);
  		assertEquals(-0.75, branch.getPSXfrPControl().getControlSpec(), 0.0001);
  		
  		
	}
}

