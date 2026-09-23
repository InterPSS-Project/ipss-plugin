package sample.cim;

import java.nio.file.Path;

/** P4 FullGrid-Merged SV-seeded NR sample (soft converge). */
public class FullGridMergedAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfUtil.casDir("FullGrid-Merged", "FullGrid/FullGrid-Merged");
		if (!CgmesAclfUtil.requireDir(dir, "FullGrid-Merged")) {
			return;
		}
		Path eq = CgmesAclfUtil.requireFile(dir, "FullGrid_EQ.xml");
		Path ssh = CgmesAclfUtil.requireFile(dir, "FullGrid_SSH.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "FullGrid_TP.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "FullGrid_SV.xml");
		Path eqBd = CgmesAclfUtil.requireFile(dir, "FullGrid_EQBD.xml");
		CgmesAclfUtil.run("FullGrid-Merged", sv, eq, ssh, tp, sv, eqBd);
	}
}
