package sample.cim;

import java.nio.file.Path;

/** P4 RealGrid-Merged SV-seeded NR sample (soft converge; large net). */
public class RealGridMergedAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfSample.casDir("RealGrid-Merged", "RealGrid/RealGrid-Merged");
		if (!CgmesAclfSample.requireDir(dir, "RealGrid-Merged")) {
			return;
		}
		Path eq = CgmesAclfSample.requireFile(dir, "RealGrid_EQ.xml");
		Path ssh = CgmesAclfSample.requireFile(dir, "RealGrid_SSH.xml");
		Path tp = CgmesAclfSample.requireFile(dir, "RealGrid_TP.xml");
		Path sv = CgmesAclfSample.requireFile(dir, "RealGrid_SV.xml");
		CgmesAclfSample.run("RealGrid-Merged", sv, eq, ssh, tp, sv);
	}
}
