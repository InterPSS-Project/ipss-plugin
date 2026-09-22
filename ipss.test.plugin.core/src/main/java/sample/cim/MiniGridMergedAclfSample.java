package sample.cim;

import java.nio.file.Path;

/** P4 MiniGrid-Merged SV-seeded NR load-flow sample. */
public class MiniGridMergedAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfSample.casDir("MiniGrid-Merged", "MiniGrid/MiniGrid-Merged");
		if (!CgmesAclfSample.requireDir(dir, "MiniGrid-Merged")) {
			return;
		}
		Path eq = CgmesAclfSample.requireFile(dir, "MiniGrid_EQ.xml");
		Path ssh = CgmesAclfSample.requireFile(dir, "MiniGrid_SSH.xml");
		Path tp = CgmesAclfSample.requireFile(dir, "MiniGrid_TP.xml");
		Path sv = CgmesAclfSample.requireFile(dir, "MiniGrid_SV.xml");
		Path eqBd = CgmesAclfSample.requireFile(dir, "MiniGrid_EQBD.xml");
		CgmesAclfSample.run("MiniGrid-Merged", sv, eq, ssh, tp, sv, eqBd);
	}
}
