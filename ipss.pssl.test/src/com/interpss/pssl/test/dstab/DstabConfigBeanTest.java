package com.interpss.pssl.test.dstab;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.interpss.pssl.plugin.cmd.json.BaseJSONBean;
import org.interpss.pssl.plugin.cmd.json.DstabRunConfigBean;
import org.junit.Test;

public class DstabConfigBeanTest {
	
	@Test
	public void testBeanDeserialization() throws IOException{
		
		DstabRunConfigBean bean = new DstabRunConfigBean();
	 
	    System.out.println(bean.toString());
	    
	    assertTrue(bean.eventStartTimeSec == 1.0);
	    assertTrue(bean.monitoringGenAry.length==0);
	
	    DstabRunConfigBean bean2 =BaseJSONBean.toBean("testData/dstab/dstabConfigSample.json",DstabRunConfigBean.class);
	    
	    assertTrue(bean2.eventStartTimeSec == 1.0);
	    assertTrue(bean2.monitoringGenAry[0].equals("Bus1-Gen1"));
	    
	    
	
	    
	}
	
}
