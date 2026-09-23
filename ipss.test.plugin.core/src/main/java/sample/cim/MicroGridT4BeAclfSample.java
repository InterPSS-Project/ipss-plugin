package sample.cim;

import java.nio.file.Path;

/** P4 MicroGrid T4 BE (cgmes2.4) SV-seeded NR sample (soft converge). */
public class MicroGridT4BeAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfUtil.cgmes24Dir();
		if (!CgmesAclfUtil.requireDir(dir, "cgmes2.4")) {
			return;
		}
		Path eq = CgmesAclfUtil.requireFile(dir, "MicroGrid_T4_BE_EQ_V2.xml");
		Path ssh = CgmesAclfUtil.requireFile(dir, "MicroGrid_T4_BE_SSH_V2.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "MicroGrid_T4_BE_TP_V2.xml");
		Path tpBd = CgmesAclfUtil.requireFile(dir, "MicroGrid_T4_BE_TP_BD_V2.xml");
		Path eqBd = CgmesAclfUtil.requireFile(dir, "MicroGrid_T4_BE_EQ_BD_V2.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "MicroGrid_T4_BE_SV_V2.xml");
		CgmesAclfUtil.run("MicroGrid T4 BE", sv, eq, ssh, tp, tpBd, eqBd);
	}
}
