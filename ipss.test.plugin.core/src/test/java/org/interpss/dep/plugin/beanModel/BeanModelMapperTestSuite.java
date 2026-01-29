package org.interpss.dep.plugin.beanModel;

import org.interpss.dep.plugin.beanModel.AclfBeanMapperTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
	// aclf
	AclfBeanMapperTest.class,
	PSXfrPControlTest.class,
	SwitchedShuntTest.class,
	//XfrTapControlTest.class,
	
})
public class BeanModelMapperTestSuite {
}
