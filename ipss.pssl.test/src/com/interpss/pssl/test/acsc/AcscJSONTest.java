package com.interpss.pssl.test.acsc;

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.ieee.odm.adapter.IODMAdapter.NetType;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.pssl.plugin.IpssAdapter.FileImportDSL;
import org.interpss.pssl.plugin.cmd.AcscDslRunner;
import org.interpss.pssl.plugin.cmd.json.AcscRunConfigBean;
import org.interpss.pssl.plugin.cmd.json.BaseJSONBean;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.fault.SimpleFaultType;
import com.interpss.core.datatype.IFaultResult;
import com.interpss.pssl.test.BaseTestSetup;

public class AcscJSONTest extends BaseTestSetup {
	
	@Test
	public void ieee9_acsc_json_test() throws InterpssException, IOException{
		
		AcscRunConfigBean acscBean = BaseJSONBean.toBean("testData/acsc/ieee9_acsc.json", AcscRunConfigBean.class);
		
		FileImportDSL inDsl =  new FileImportDSL();
		AcscNetwork acNet = inDsl.setFormat(acscBean.runAclfConfig.format)
			 .setPsseVersion(acscBean.runAclfConfig.version)
		     .load(NetType.AcscNet, new String[]{acscBean.runAclfConfig.aclfCaseFileName,
				acscBean.seqFileName})
		      .getImportedObj();	
		
		IFaultResult scResults = new AcscDslRunner(acNet).run(acscBean);
		
		// output short circuit result
		
		// require the base votlage of the fault point
		double baseV = acscBean.type == SimpleFaultType.BUS_FAULT? acNet.getBus(acscBean.faultBusId).getBaseVoltage():
			                                  acNet.getBus(acscBean.faultBranchFromId).getBaseVoltage();
			
		Complex3x1 v012 = scResults.getBusVoltage_012(acNet.getBus("Bus1"));
		/*
		 *    bus_id, Vpu(1,2,0): Bus1
              0.794920.7949 + j-0.00531
              0.00000.0000 + j0.0000
              0.00000.0000 + j0.0000
		 */
		assertTrue(v012.a_0.abs()==0.0);
		assertTrue(v012.c_2.abs()==0.0);
		assertTrue(Math.abs(v012.b_1.getReal()-0.79492 )< 5.0E-5);
		
		//FileUtil.write2File(acscBean.acscOutputFileName, scResults.toString(baseV).getBytes());
		System.out.println(scResults.toString(baseV));
		ipssLogger.info("Ouput written to " + acscBean.acscOutputFileName);
		
		
		
	}

}
