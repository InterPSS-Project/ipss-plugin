package sample.cim;

import java.nio.file.Path;

/** P4 CAS Svedala-Merged SV-seeded NR load-flow sample. */
public class SvedalaMergedAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfUtil.casDir("CAS-Svedala-Merged", "Svedala/Svedala-Merged");
		if (!CgmesAclfUtil.requireDir(dir, "CAS Svedala-Merged")) {
			return;
		}
		Path eq = CgmesAclfUtil.requireFile(dir, "Svedala_EQ.xml");
		Path ssh = CgmesAclfUtil.requireFile(dir, "Svedala_SSH.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "Svedala_TP.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "Svedala_SV.xml");
		Path eqBd = CgmesAclfUtil.requireFile(dir, "Svedala_EQBD.xml");
		CgmesAclfUtil.run("Svedala-Merged", sv, eq, ssh, tp, sv, eqBd);
	}
}
