package sample.cim;

import java.nio.file.Path;

/** P4 MicroGrid T4 BE (cgmes2.4) SV-seeded NR sample (soft converge). */
public class MicroGridT4BeAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfSample.cgmes24Dir();
		if (!CgmesAclfSample.requireDir(dir, "cgmes2.4")) {
			return;
		}
		Path eq = CgmesAclfSample.requireFile(dir, "MicroGrid_T4_BE_EQ_V2.xml");
		Path ssh = CgmesAclfSample.requireFile(dir, "MicroGrid_T4_BE_SSH_V2.xml");
		Path tp = CgmesAclfSample.requireFile(dir, "MicroGrid_T4_BE_TP_V2.xml");
		Path tpBd = CgmesAclfSample.requireFile(dir, "MicroGrid_T4_BE_TP_BD_V2.xml");
		Path eqBd = CgmesAclfSample.requireFile(dir, "MicroGrid_T4_BE_EQ_BD_V2.xml");
		Path sv = CgmesAclfSample.requireFile(dir, "MicroGrid_T4_BE_SV_V2.xml");
		CgmesAclfSample.run("MicroGrid T4 BE", sv, eq, ssh, tp, tpBd, eqBd);
	}
}
