package org.interpss.dep.plugin.beanModel;

import org.interpss.dep.plugin.beanModel.AclfBeanMapperTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
	// aclf
	AclfBeanMapperTest.class,
	PSXfrPControlTest.class,
	SwitchedShuntTest.class,
	//XfrTapControlTest.class,
	
})
public class BeanModelMapperTestSuite {
}
