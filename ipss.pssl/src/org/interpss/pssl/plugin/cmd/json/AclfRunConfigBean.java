package org.interpss.pssl.plugin.cmd.json;

import org.ieee.odm.schema.LfMethodEnumType;
import org.interpss.pssl.plugin.IpssAdapter;

/**
 * Aclf Cmd run configuration file
 * 
 * @author Mike
 *
 */
public class AclfRunConfigBean extends BaseJSONBean {
	/**
	 * default constructor
	 */
	public AclfRunConfigBean() {
		// set the default AclfDslRunner class name
		this.dslRunnerClassName = "org.interpss.pssl.plugin.cmd.AclfDslRunner";
	}
	
	/**
	 *  input file name, mandatory
	 */  
	public String aclfCaseFileName = "name";

	/**
	 *  output file name, optional
	 */
	public String aclfOutputFileName = "name";

	/**
	 * input file format
	 */
	public IpssAdapter.FileFormat format = IpssAdapter.FileFormat.IEEECommonFormat;
	
	/**
	 * PSS/E file version
	 */
	public IpssAdapter.PsseVersion version = IpssAdapter.PsseVersion.PSSE_30;
	
	/**
	 * Loadflow method
	 */
	public LfMethodEnumType lfMethod = LfMethodEnumType.NR;
	
	/**
	 * max iterations for Loadflow
	 */
	public int maxIteration = 20;
	
	/**
	 * tolerance for Loadflow convergence in PU
	 */
	public double tolerance = 0.0001;
	
	/**
	 * If true, the non-divergent method will be used 
	 */
	public boolean nonDivergent = false;
	
	/**
	 * if set true, bus voltage will be initialized before Loadflow calculation
	 */
	public boolean initBusVoltage = false;
	
	/**
	 * acceleration factor for the GS method
	 */
	public double accFactor = 1.0;
}
