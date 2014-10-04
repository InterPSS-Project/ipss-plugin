package org.interpss.app;

import org.interpss.IpssCorePlugin;
import org.interpss.pssl.plugin.cmd.CmdRunner;
import org.interpss.pssl.simu.BaseDSL;

import com.interpss.common.exp.InterpssException;
import com.interpss.spring.CoreCommonSpringFactory;

public class IpssCmd {
	/**
	 * flag to control printing cmd help info 
	 */
	private static boolean outHelpInfo = false;

	/**
	 * Cmd run type 
	 */
	private static CmdRunner.RunType runType = CmdRunner.RunType.Aclf;
	
	/**
	 * Cdm run control file. It is in JSON format
	 */
	private static String controlFilename = null;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// init InterPSS runtime env
		IpssCorePlugin.init();
		BaseDSL.setMsgHub(CoreCommonSpringFactory.getIpssMsgHub());		

		try {
			parseCmdInput(args);
			
			if (outHelpInfo)
				System.out.println(getHelpInfo());
			
			new CmdRunner()
					.runType(runType)
					.controlFilename(controlFilename)
					.run();
			
		} catch (Exception e) {
			System.err.println(e.toString());
			e.printStackTrace();
		}

	}
	
	private static void parseCmdInput(String[] args) throws InterpssException {
		for (int cnt = 0; cnt < args.length; cnt++) {
			
			// parse help message output flag
			if (HelpOptStr.equals(args[cnt])) {
				outHelpInfo = true;
			}
			
			// parse run type info 
			else if (RunTypeOptStr.equals(args[cnt])) {
				String str = args[++cnt];
				runType = str.equals("Acsc") ? CmdRunner.RunType.Acsc : 
							str.equals("DStab") ? CmdRunner.RunType.DStab : 
								CmdRunner.RunType.Aclf;
			} 
			
			// parse run control file
			else if (ControlOptStr.equals(args[cnt])) {
				controlFilename = args[++cnt];
			} 
		}
	}
	
	private final static String RunTypeOptStr 	= "-t";
	private final static String ControlOptStr 	= "-c";
	private final static String HelpOptStr 		= "-h";

	private static String getHelpInfo() {
		return "java org.interpss.app.IpssCmd [-h] [-t Aclf|Acsc|DStab] -c controlFile \n"
				+ "  -h for help info\n"
				+ "  -t Simulation type Aclf|Acsc|DStab, default Aclf\n"
				+ "  -c JSON file to control the run\n";
	}	

}
