package sample.cim;

import java.nio.file.Path;

/** P4 RealGrid-Merged SV-seeded NR sample (soft converge; large net). */
public class RealGridMergedAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfUtil.casDir("RealGrid-Merged", "RealGrid/RealGrid-Merged");
		if (!CgmesAclfUtil.requireDir(dir, "RealGrid-Merged")) {
			return;
		}
		Path eq = CgmesAclfUtil.requireFile(dir, "RealGrid_EQ.xml");
		Path ssh = CgmesAclfUtil.requireFile(dir, "RealGrid_SSH.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "RealGrid_TP.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "RealGrid_SV.xml");
		CgmesAclfUtil.run("RealGrid-Merged", sv, eq, ssh, tp, sv);
	}
}
