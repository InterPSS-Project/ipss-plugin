package org.interpss.core.dstab.cml;

import org.interpss.core.dstab.cml.block.DelayControlBlockTests;
import org.interpss.core.dstab.cml.block.FilterControlBlockTests;
import org.interpss.core.dstab.cml.block.IntegrationControlBlockTests;
import org.interpss.core.dstab.cml.block.PIControlBlockTests;
import org.interpss.core.dstab.cml.block.WashoutControlBlockTests;
import org.interpss.core.dstab.cml.controller.AnnotateParserTests;
import org.interpss.core.dstab.cml.controller.AnnotationExciterTests;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
	DelayControlBlockTests.class,
	FilterControlBlockTests.class,
	//GainBlockExtensionTests.class,
	IntegrationControlBlockTests.class,
	PIControlBlockTests.class,
	WashoutControlBlockTests.class,
	
	AnnotateParserTests.class,
	AnnotationExciterTests.class,
})
public class CMLTestSuite {
}
