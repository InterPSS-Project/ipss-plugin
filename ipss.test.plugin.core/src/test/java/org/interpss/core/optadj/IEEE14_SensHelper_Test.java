
package org.interpss.core.optadj;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.plugin.optadj.algo.util.AclfNetSensHelper;
import org.interpss.plugin.optadj.algo.util.Sen2DMatrix;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;

public class IEEE14_SensHelper_Test extends CorePluginTestSetup {
	@Test
	public void test() throws InterpssException {
		AclfNetwork net = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14.ieee")
				.getAclfNet();
		
		AclfNetSensHelper senHelper = new AclfNetSensHelper(net);
		
		Sen2DMatrix gfs = senHelper.calGFS();
		
		//System.out.println("GFS Matrix: \n" + gfs.toString());

		assertEquals(-0.838, gfs.get(1, 0), 0.001);
		assertEquals(-0.002, gfs.get(1, 19), 0.001);
		
		assertEquals(-0.644, gfs.get(13, 0), 0.001);
		assertEquals(-0.397, gfs.get(13, 19), 0.001);
		
		Set<String> busIdSet = new HashSet<>(Arrays.asList("Bus1", "Bus2", "Bus3", "Bus6", "Bus8"));
		
		gfs = senHelper.calGFS(busIdSet);
		
		//System.out.println("GFS Matrix: \n" + gfs.toString());
		
		int no = net.getBus("Bus2").getSortNumber();
		assertEquals(-0.838, gfs.get(no, 0), 0.001);  // Bus2
		assertEquals(-0.002, gfs.get(no, 19), 0.001);
		
		no = net.getBus("Bus8").getSortNumber();
		assertEquals(-0.657, gfs.get(no, 0), 0.001);  // Bus8
		assertEquals(-0.079, gfs.get(no, 19), 0.001);
	}
}
