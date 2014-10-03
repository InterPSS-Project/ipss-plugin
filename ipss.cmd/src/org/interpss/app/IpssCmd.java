package org.interpss.app;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import org.interpss.IpssCorePlugin;
import org.interpss.pssl.plugin.CmdRunner;
import org.interpss.pssl.plugin.IpssAdapter;
import org.interpss.pssl.simu.BaseDSL;

import com.interpss.common.exp.InterpssException;
import com.interpss.spring.CoreCommonSpringFactory;

public class IpssCmd {
	private static boolean outHelpInfo = false;
	//private static boolean nativeSolver = false;

	private static String inputFilename = null;
	private static IpssAdapter.FileFormat format = IpssAdapter.FileFormat.IEEE_ODM;
	private static IpssAdapter.PsseVersion psseVersion = IpssAdapter.PsseVersion.PSSE_30;
	
	private static String controlFilename = null;
	private static String outputFilename = null;
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		IpssCorePlugin.init();

		try {
			parseCmdInput(args);
			
			if (outHelpInfo)
				System.out.println(getHelpInfo());
			
			if (!checkCmdInput()) {
				if (!outHelpInfo)
					ipssLogger.severe("Not enough info to run the application");
				return;
			}
			
			BaseDSL.setMsgHub(CoreCommonSpringFactory.getIpssMsgHub());		
		
			/*
			if (nativeSolver) {
				ipssLogger.info("Using native sparse solver");
				IpssCorePlugin.setSparseEqnSolver(ISparseEquation.SolverType.Native);
				BaseDSL.sparseSolver = BaseDSL.SparseSolverType.Native;
			}
			else 
				ipssLogger.info("Using default Java sparse solver");			
			*/
			new CmdRunner()
					.inputFilename(inputFilename)
					.format(format)
					.version(psseVersion)
					.odmControlFilename(controlFilename)
					.outputFilename(outputFilename)
					.run();
			
		} catch (Exception e) {
			System.err.println(e.toString());
			e.printStackTrace();
		}

	}
	
	private static boolean checkCmdInput() {
		boolean ok = false;
		/*
		 * conditions:
		 *   - for default input file format ODM, min inputFilename needs to be specified
		 *   - for other input file format, inputFilename, format and control file need to be specified
		 */
		if (format == IpssAdapter.FileFormat.IEEE_ODM && inputFilename != null)
			ok = true;
		else if (inputFilename != null && controlFilename != null)
			ok = true;
			
		return ok;
	}
	
	private static void parseCmdInput(String[] args) throws InterpssException {
		for (int cnt = 0; cnt < args.length; cnt++) {
			if (HelpOptStr.equals(args[cnt])) {
				outHelpInfo = true;
			}
			/*
			else if (SolverOptStr.equals(args[cnt])) {
				if (Parm_Native.equals(args[++cnt]))
					nativeSolver = true;
				else 
					throw new InterpssException("Wrong sparse solver type");
			} 
			*/
			else if (InOptStr.equals(args[cnt])) {
				inputFilename = args[++cnt];
			} 
			else if (FormatOPtStr.equals(args[cnt])) {
				String str = args[++cnt];
				if (FMT_PSSE.equals(str))
					format = IpssAdapter.FileFormat.PSSE;
				else if (FMT_PSLF.equals(str))
					format = IpssAdapter.FileFormat.GE_PSLF;
				else if (FMT_UCTE.equals(str))
					format = IpssAdapter.FileFormat.UCTE;
				else if (FMT_BPA.equals(str))
					format = IpssAdapter.FileFormat.BPA;
				else if (FMT_PWD.equals(str))
					format = IpssAdapter.FileFormat.PWD;
				else if (FMT_ODM.equals(str))
					format = IpssAdapter.FileFormat.IEEE_ODM;
				else if (FMT_IEEECDF.equals(str))
					format = IpssAdapter.FileFormat.IEEECommonFormat;
				else
					throw new InterpssException("Wrong input data format");
			} 
			else if (VersionOptStr.equals(args[cnt])) {
				String str = args[++cnt];
				if (VER_PSSEV30.equals(str))
					psseVersion = IpssAdapter.PsseVersion.PSSE_30;
				else if (VER_PSSEV29.equals(str))
					psseVersion = IpssAdapter.PsseVersion.PSSE_29;
				else if (VER_PSSEV26.equals(str))
					psseVersion = IpssAdapter.PsseVersion.PSSE_26;
			} 
			else if (ControlOptStr.equals(args[cnt])) {
				controlFilename = args[++cnt];
			} 
			else if (OutOptStr.equals(args[cnt])) {
				outputFilename = args[++cnt];
			} 
		}

	}
	
	//private final static String SolverOptStr = "-s";
	private final static String InOptStr 		= "-i";
	private final static String FormatOPtStr 	= "-f";
	private final static String VersionOptStr 	= "-v";
	private final static String ControlOptStr 	= "-c";
	private final static String OutOptStr 		= "-o";
	private final static String HelpOptStr 		= "-h";

	//private final static String Parm_Native = "native";
	
	private final static String FMT_IEEECDF 	= "IEEE-CDF";
	private final static String FMT_PSSE 		= "PSSE";
	private final static String FMT_PSLF 		= "GE-PSLF";
	private final static String FMT_UCTE 		= "UCTE";
	private final static String FMT_BPA 		= "BPA";
	private final static String FMT_PWD 		= "PWD";
	private final static String FMT_ODM 		= "IEEE-ODM";
	
	private final static String VER_PSSEV30 	= "PSSEV30";
	private final static String VER_PSSEV29 	= "PSSEV29";
	private final static String VER_PSSEV26 	= "PSSEV26";

	private static String getHelpInfo() {
		return "java org.interpss.app.IpssCmd [-h] -i inputFile [-f IEEE-CDF|PSSE|GE-PSLF|UCTE|BPA|PWD|IEEE-ODM] [-v PSSEV30|PSSEV29|PSSEV26] [-c controlFile] [-o outputFile|Console] \n"
				+ "  -h for help info\n"
				+ "  -i input file, relative to the current dir or full path\n"
				+ "  -f input file format, default IEEE-CDF\n"
				+ "  -v input file version\n"
				+ "  -c ODM Xml file to control the run\n"
				+ "  -o simulation result output file\n";
	}	

}
