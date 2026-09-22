package sample.cim;

import java.nio.file.Path;

/** P4 MiniGrid NB (cgmes2.4) SV-seeded NR load-flow sample. */
public class MiniGridNbAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfSample.cgmes24Dir();
		if (!CgmesAclfSample.requireDir(dir, "cgmes2.4")) {
			return;
		}
		Path eq = CgmesAclfSample.requireFile(dir, "MiniGrid_NB_EQ_V3.xml");
		Path ssh = CgmesAclfSample.requireFile(dir, "MiniGrid_NB_SSH_V3.xml");
		Path tp = CgmesAclfSample.requireFile(dir, "MiniGrid_NB_TP_V3.xml");
		Path tpBd = CgmesAclfSample.requireFile(dir, "MiniGrid_NB_TP_BD_V3.xml");
		Path eqBd = CgmesAclfSample.requireFile(dir, "MiniGrid_NB_EQ_BD_V3.xml");
		Path sv = CgmesAclfSample.requireFile(dir, "MiniGrid_NB_SV_V3.xml");
		CgmesAclfSample.run("MiniGrid NB", sv, eq, ssh, tp, tpBd, eqBd);
	}
}
