package org.interpss.QA.test.compare;


import static org.junit.Assert.assertTrue;

import org.interpss.QA.compare.aclf.AclfNetModelComparator;
import org.interpss.QA.test.QATestSetup;
import org.interpss.datamodel.bean.aclf.AclfNetBean;
import org.interpss.datamodel.util.INetBeanComparator;
import org.interpss.mapper.bean.aclf.AclfNet2BeanMapper;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.simu.util.sample.SampleTestingCases;

/**
 * There are two way two ways to compare AclfNetwork. The approach in 
 * test() is recommended.
 * 
 * 
 * @author mzhou
 *
 */
public class AclfNetCompareTest extends QATestSetup {
	@Test    // recommended approach
	public void test() throws InterpssException {
  		AclfNetwork net1 = CoreObjectFactory.createAclfNetwork();
		SampleTestingCases.load_LF_5BusSystem(net1);
		AclfNetBean netBean1 = new AclfNet2BeanMapper().map2Model(net1);
		
		AclfNetwork net2 = CoreObjectFactory.createAclfNetwork();
		SampleTestingCases.load_LF_5BusSystem(net2);
		//net2.getBus("1").setVoltageMag(0.95);
		AclfNetBean netBean2 = new AclfNet2BeanMapper().map2Model(net2);
		
		// compare output msg written to the console
		assertTrue(netBean1.compareTo(netBean2) == 0);
		
		// compare output msg written to the netBean1.msgList
		netBean1.setCompareLog(INetBeanComparator.CompareLog.MsgList);
		netBean1.compareTo(netBean2);
		//System.out.println(netBean1.getMsgList());
		assertTrue(netBean1.getMsgList().size() == 0);
	}

	@Test
	public void test1() {
  		AclfNetwork net1 = CoreObjectFactory.createAclfNetwork();
		SampleTestingCases.load_LF_5BusSystem(net1);

		AclfNetwork net2 = CoreObjectFactory.createAclfNetwork();
		SampleTestingCases.load_LF_5BusSystem(net2);
		
		AclfNetModelComparator comparator = new AclfNetModelComparator();
	
		assertTrue(comparator.compare(net1, net2));
		//if (!comparator.compare(net1, net2))
		//	System.out.println(comparator.getMsg());
		
		net2.getBusList().get(0).setBaseVoltage(1.0);
		assertTrue(!comparator.compare(net1, net2));
		//if (!comparator.compare(net1, net2))
		//	System.out.println(comparator.getMsg());
	}
}
