/*
  * @(#)CmdRunner.java   
  *
  * Copyright (C) 2006-2011 www.interpss.com
  *
  * This program is free software; you can redistribute it and/or
  * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
  * as published by the Free Software Foundation; either version 2.1
  * of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * @Author Mike Zhou
  * @Version 1.0
  * @Date 12/15/2011
  * 
  *   Revision History
  *   ================
  *
  */

package org.interpss.pssl.plugin.cmd;

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static org.interpss.CorePluginFunction.aclfResultSummary;
import static org.interpss.pssl.plugin.IpssAdapter.importAclfNet;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.interpss.pssl.plugin.IpssAdapter.FileImportDSL;
import org.interpss.pssl.plugin.cmd.json.AclfRunConfigBean;
import org.interpss.pssl.plugin.cmd.json.AcscRunConfigBean;
import org.interpss.pssl.plugin.cmd.json.BaseJSONBean;
import org.interpss.pssl.plugin.cmd.json.DstabRunConfigBean;
import org.interpss.util.FileUtil;

import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.StringUtil;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.acsc.AcscNetwork;
import com.interpss.core.acsc.fault.SimpleFaultType;
import com.interpss.core.datatype.IFaultResult;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.cache.StateMonitor;
import com.interpss.dstab.common.IDStabSimuOutputHandler;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;

/**
 * Main class for implement commend line runner PSSL.
 * 
 * 
 * @author mzhou
 *
 */
public class CmdRunner {
	public static enum RunType {Aclf, Acsc, DStab};
	
	/**
	 * default output dir
	 */
	public static String OutputDir = "output";
	
	/**
	 * file separator
	 */
	public static String SysSeparator = System.getProperty("file.separator");
	
	/**
	 * Cmd run type
	 */
	private RunType runType = RunType.Aclf;
	
	/**
	 * Cmd run control file
	 */
	private String controlFilename = null;
	
	/**
	 * Cmd run configure JSON bean 
	 */
	//private AclfRunConfigBean aclfBean;
	
	/**
	 * default constructor
	 */
	public CmdRunner() {
	}

	/**
	 * set the run type
	 * 
	 * @param run type
	 * @return
	 */
	public CmdRunner runType(RunType type) {
		this.runType = type;
		return this;
	}
	
	/**
	 * set control file name
	 * 
	 * @param controlFilename
	 * @return
	 */
	public CmdRunner controlFilename(String controlFilename) {
		this.controlFilename = controlFilename;
		return this;
	}

	/**
	 * run the case
	 * 
	 * @return
	 * @throws FileNotFoundException
	 * @throws InterpssException
	 */
	public SimuContext run() throws FileNotFoundException, IOException, InterpssException, ClassNotFoundException, IllegalAccessException, InstantiationException {
		if (this.runType == RunType.Aclf) {
			
			// load the Aclf run configure info stored in the control file
			AclfRunConfigBean aclfBean = loadAclfRunConfigInfo();
			
			// load the study case file
			FileImportDSL inDsl = importAclfNet(aclfBean.aclfCaseFileName)
					.setFormat(aclfBean.format)
					.setPsseVersion(aclfBean.version)
					.load();	

			// map ODM to InterPSS model object
			AclfNetwork net = inDsl.getImportedObj();	
		
			aclfBean.loadDslRunner()
					.setNetwork(net)
					.run(aclfBean);
			
			// output Loadflow result
			FileUtil.write2File(aclfBean.aclfOutputFileName, aclfResultSummary.apply(net).toString().getBytes());
			ipssLogger.info("Ouput written to " + aclfBean.aclfOutputFileName);

			return SimuObjectFactory.createSimuCtxTypeAclfNet(net);
			
		}
		else if(this.runType == RunType.Acsc) {
			
			// load the Acsc run configure info stored in the control file
			AcscRunConfigBean acscBean = loadAcscRunConfigInfo();
			
		    // import the file(s)
			FileImportDSL inDsl =  new FileImportDSL();
			inDsl.setFormat(acscBean.runAclfConfig.format)
				 .setPsseVersion(acscBean.runAclfConfig.version)
			     .load(new String[]{acscBean.runAclfConfig.aclfCaseFileName,
					acscBean.seqFileName});
			
			// map ODM to InterPSS model object
			AcscNetwork net = inDsl.getImportedObj();	
			
			IFaultResult scResults = acscBean.loadDslRunner()
											.setNetwork(net)
											.run(acscBean);
			
			// output short circuit result
			
			// require the base votlage of the fault point
			double baseV = acscBean.type == SimpleFaultType.BUS_FAULT? net.getBus(acscBean.faultBusId).getBaseVoltage():
				                                  net.getBus(acscBean.faultBranchFromId).getBaseVoltage();
				
			FileUtil.write2File(acscBean.acscOutputFileName, scResults.toString(baseV).getBytes());
			ipssLogger.info("Ouput written to " + acscBean.acscOutputFileName);
			
			// create a simuContext and return it
			SimuContext sc = SimuObjectFactory.createSimuCtxTypeAcscNet();
			sc.setAcscNet(net);
			return sc;
			
		}
		else if(this.runType == RunType.DStab) {
			
			DstabRunConfigBean dstabBean = loadDStabRunConfigInfo();
			
			  // import the file(s)
			FileImportDSL inDsl =  new FileImportDSL();
			inDsl.setFormat(dstabBean.acscConfigBean.runAclfConfig.format)
				 .setPsseVersion(dstabBean.acscConfigBean.runAclfConfig.version)
			     .load(new String[]{dstabBean.acscConfigBean.runAclfConfig.aclfCaseFileName,
			    		 dstabBean.acscConfigBean.seqFileName,
			    		 dstabBean.dynamicFileName});
			
			// map ODM to InterPSS model object
			DStabilityNetwork net = inDsl.getImportedObj();	
			
			/*
			IDStabSimuOutputHandler outputHdler = DslRunnerFactory.createDStabDslRunner()
					                                               .runDstab(dstabBean);
			*/		                                               
			IDStabSimuOutputHandler outputHdler = dstabBean.loadDslRunner()
			                                               .setNetwork(net)
			                                                .run(dstabBean);
			//output the result
	        
		
			
			if(!dstabBean.dstabOutputFileName.equals("")){
				FileUtil.write2File(dstabBean.dstabOutputFileName, outputHdler.toString().getBytes());
				ipssLogger.info("Ouput written to " + dstabBean.dstabOutputFileName);
			}
			
			
			SimuContext dstabSC = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
			dstabSC.setDStabilityNet(net);
			return dstabSC;
		}
		
		else {
			throw new InterpssException("Function Not implemented");
		}
		
	}
	
	private AclfRunConfigBean loadAclfRunConfigInfo() throws IOException {
		AclfRunConfigBean aclfBean = BaseJSONBean.toBean(this.controlFilename, AclfRunConfigBean.class);

		// set output file if necessary
		if (aclfBean.aclfOutputFileName == null) {
			String str = StringUtil.getFileNameNoExt(aclfBean.aclfCaseFileName);
			aclfBean.aclfOutputFileName = OutputDir + SysSeparator + str + ".txt";
		}
		
		return aclfBean;
	}
	
	private AcscRunConfigBean loadAcscRunConfigInfo() throws IOException {
		AcscRunConfigBean acscBean = BaseJSONBean.toBean(this.controlFilename, AcscRunConfigBean.class);

		// load Aclf config file if necessary
		if (acscBean.runAclf==true && acscBean.runAclfConfig == null ) {
			try {
				throw new InterpssException("Configuration conflict: runAclf = true, but Aclf Configuration is null!");
			} catch (InterpssException e) {
				e.printStackTrace();
			}
			
		}
		else if(acscBean.runAclf==true &&  acscBean.runAclfConfig.aclfCaseFileName.equals("")) {
			try {
				throw new InterpssException("Configuration conflict: runAclf = true, but Aclf case file is not defined in the JSON file!");
			} catch (InterpssException e) {
				e.printStackTrace();
			}
			
		}
		
		
		return acscBean;
	}
	
	
	private DstabRunConfigBean loadDStabRunConfigInfo() throws IOException {
		DstabRunConfigBean dstabBean = BaseJSONBean.toBean(this.controlFilename, DstabRunConfigBean.class);
		
		return dstabBean;
	}
	
}
