package sample.cim;

import java.nio.file.Path;

/** P4 SmallGrid-Merged SV-seeded NR load-flow sample. */
public class SmallGridMergedAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfUtil.casDir("SmallGrid-Merged", "SmallGrid/SmallGrid-Merged");
		if (!CgmesAclfUtil.requireDir(dir, "SmallGrid-Merged")) {
			return;
		}
		Path eq = CgmesAclfUtil.requireFile(dir, "SmallGrid_EQ.xml");
		Path ssh = CgmesAclfUtil.requireFile(dir, "SmallGrid_SSH.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "SmallGrid_TP.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "SmallGrid_SV.xml");
		Path eqBd = CgmesAclfUtil.requireFile(dir, "SmallGrid_EQBD.xml");
		CgmesAclfUtil.run("SmallGrid-Merged", sv, eq, ssh, tp, sv, eqBd);
	}
}
