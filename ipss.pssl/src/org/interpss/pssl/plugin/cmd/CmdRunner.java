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
import static org.interpss.pssl.plugin.IpssAdapter.importNet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.ieee.odm.model.ext.ipss.IpssScenarioHelper;
import org.ieee.odm.schema.IpssAclfAlgorithmXmlType;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.plugin.IpssAdapter.FileImportDSL;
import org.interpss.pssl.plugin.cmd.json.AclfRunConfigBean;
import org.interpss.pssl.plugin.cmd.json.BaseJSONBean;
import org.interpss.util.FileUtil;

import com.google.gson.Gson;
import com.interpss.SimuObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.StringUtil;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.simu.SimuContext;

/**
 * Main class for implement commend line version.
 * 
 * 
 * @author mzhou
 *
 */
public class CmdRunner {
	/**
	 * defefault output dir
	 */
	public static String OutputDir = "output";
	/**
	 * file separator
	 */
	public static String SysSeparator = System.getProperty("file.separator");
	
	private String inputFilename = null;
	private IpssAdapter.FileFormat format = IpssAdapter.FileFormat.IEEE_ODM;
	private IpssAdapter.PsseVersion version = IpssAdapter.PsseVersion.PSSE_30;
	private String controlFilename = null;
	private String outputFilename = null;
	
	/**
	 * default constructor
	 */
	public CmdRunner() {
	}
	
	/**
	 * set input file name
	 * 
	 * @param inputFilename
	 * @return
	 */
	public CmdRunner inputFilename(String inputFilename) {
		this.inputFilename = inputFilename;
		return this;
	}

	/**
	 * set input file format
	 * 
	 * @param format
	 * @return
	 */
	public CmdRunner format(IpssAdapter.FileFormat format) {
		this.format = format;
		return this;
	}

	/**
	 * set input file version, applies to PSS/E
	 * 
	 * @param psseVersion
	 * @return
	 */
	public CmdRunner version(IpssAdapter.PsseVersion psseVersion) {
		this.version = psseVersion;
		return this;
	}

	/**
	 * set ODM control file name
	 * 
	 * @param controlFilename
	 * @return
	 */
	public CmdRunner controlFilename(String controlFilename) {
		this.controlFilename = controlFilename;
		return this;
	}

	/**
	 * set output file name
	 * 
	 * @param outputFilename
	 * @return
	 */
	public CmdRunner outputFilename(String outputFilename) {
		this.outputFilename = outputFilename;
		return this;
	}
	
	/**
	 * run the case
	 * 
	 * @return
	 * @throws FileNotFoundException
	 * @throws InterpssException
	 */
	public SimuContext run() throws FileNotFoundException, IOException, InterpssException {
		prepareForRun();
		
		FileImportDSL inDsl = importNet(this.inputFilename)
				.setFormat(this.format)
				.setPsseVersion(this.version)
				.load();	

		if (this.format == IpssAdapter.FileFormat.IEEE_ODM && 
				!inDsl.getOdmParser().isTransmissionLoadflow()) {
			throw new InterpssException("Not implemented");
		}
		else
			return runAclf(inDsl);
	}
	
	private SimuContext runAclf(FileImportDSL inDsl) throws FileNotFoundException, IOException, InterpssException {
		AclfNetwork net = inDsl.getImportedObj();	
		
		AclfRunConfigBean algoBean = null;
		IpssAclfAlgorithmXmlType algoXml = null;
		
		if (inDsl.getOdmParser().getStudyScenario() != null) {
			// the run configure info has been defined in the ODM Parser object
			algoXml = new IpssScenarioHelper(inDsl.getOdmParser())
						.getAclfAnalysis()
						.getAclfAlgo();
		}
		else if (this.controlFilename != null) {
			// the run configure info is defined in a control file in ODM format or in JSON format
			if (this.controlFilename.toLowerCase().endsWith(".xml"))
				algoXml = new IpssScenarioHelper(this.controlFilename)
            			.getAclfAnalysis()
            			.getAclfAlgo();
			else if (this.controlFilename.toLowerCase().endsWith(".json")) {
				algoBean = BaseJSONBean.toBean(this.controlFilename, AclfRunConfigBean.class);
			}
		}
		
		if (algoXml != null) {
			new AclfDslRunner(net)
		      		.runAclf(algoXml);
		}
		else if (algoBean != null) {
			new AclfDslRunner(net)
		      		.runAclf(algoBean);
		}
		else
			throw new InterpssException("Input error: Aclf algorithm not defined");
		
		FileUtil.write2File(outputFilename, aclfResultSummary.apply(net).toString().getBytes());
		ipssLogger.info("Ouput written to " + this.outputFilename);

		return SimuObjectFactory.createSimuCtxTypeAclfNet(net);
	}
	
	private void prepareForRun() {
		// set output file if necessary
		if (this.outputFilename == null) {
			String str = StringUtil.getFileNameNoExt(this.inputFilename);
			this.outputFilename = OutputDir + SysSeparator + str + ".txt";
		}
	}
}
