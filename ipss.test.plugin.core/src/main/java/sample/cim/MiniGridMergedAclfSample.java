package sample.cim;

import java.nio.file.Path;

/** P4 MiniGrid-Merged SV-seeded NR load-flow sample. */
public class MiniGridMergedAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfUtil.casDir("MiniGrid-Merged", "MiniGrid/MiniGrid-Merged");
		if (!CgmesAclfUtil.requireDir(dir, "MiniGrid-Merged")) {
			return;
		}
		Path eq = CgmesAclfUtil.requireFile(dir, "MiniGrid_EQ.xml");
		Path ssh = CgmesAclfUtil.requireFile(dir, "MiniGrid_SSH.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "MiniGrid_TP.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "MiniGrid_SV.xml");
		Path eqBd = CgmesAclfUtil.requireFile(dir, "MiniGrid_EQBD.xml");
		CgmesAclfUtil.run("MiniGrid-Merged", sv, eq, ssh, tp, sv, eqBd);
	}
}
