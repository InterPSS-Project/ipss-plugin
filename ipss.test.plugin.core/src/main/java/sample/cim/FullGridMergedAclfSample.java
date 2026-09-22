package sample.cim;

import java.nio.file.Path;

/** P4 FullGrid-Merged SV-seeded NR sample (soft converge). */
public class FullGridMergedAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfSample.casDir("FullGrid-Merged", "FullGrid/FullGrid-Merged");
		if (!CgmesAclfSample.requireDir(dir, "FullGrid-Merged")) {
			return;
		}
		Path eq = CgmesAclfSample.requireFile(dir, "FullGrid_EQ.xml");
		Path ssh = CgmesAclfSample.requireFile(dir, "FullGrid_SSH.xml");
		Path tp = CgmesAclfSample.requireFile(dir, "FullGrid_TP.xml");
		Path sv = CgmesAclfSample.requireFile(dir, "FullGrid_SV.xml");
		Path eqBd = CgmesAclfSample.requireFile(dir, "FullGrid_EQBD.xml");
		CgmesAclfSample.run("FullGrid-Merged", sv, eq, ssh, tp, sv, eqBd);
	}
}
