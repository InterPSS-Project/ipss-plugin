package sample.cim;

import java.nio.file.Path;

/** P4 CAS Svedala-Merged SV-seeded NR load-flow sample. */
public class SvedalaMergedAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfSample.casDir("CAS-Svedala-Merged", "Svedala/Svedala-Merged");
		if (!CgmesAclfSample.requireDir(dir, "CAS Svedala-Merged")) {
			return;
		}
		Path eq = CgmesAclfSample.requireFile(dir, "Svedala_EQ.xml");
		Path ssh = CgmesAclfSample.requireFile(dir, "Svedala_SSH.xml");
		Path tp = CgmesAclfSample.requireFile(dir, "Svedala_TP.xml");
		Path sv = CgmesAclfSample.requireFile(dir, "Svedala_SV.xml");
		Path eqBd = CgmesAclfSample.requireFile(dir, "Svedala_EQBD.xml");
		CgmesAclfSample.run("Svedala-Merged", sv, eq, ssh, tp, sv, eqBd);
	}
}
