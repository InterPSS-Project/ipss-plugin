package sample.cim;

import java.nio.file.Path;

/** P4 MiniGrid NB (cgmes2.4) SV-seeded NR load-flow sample. */
public class MiniGridNbAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfUtil.cgmes24Dir();
		if (!CgmesAclfUtil.requireDir(dir, "cgmes2.4")) {
			return;
		}
		Path eq = CgmesAclfUtil.requireFile(dir, "MiniGrid_NB_EQ_V3.xml");
		Path ssh = CgmesAclfUtil.requireFile(dir, "MiniGrid_NB_SSH_V3.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "MiniGrid_NB_TP_V3.xml");
		Path tpBd = CgmesAclfUtil.requireFile(dir, "MiniGrid_NB_TP_BD_V3.xml");
		Path eqBd = CgmesAclfUtil.requireFile(dir, "MiniGrid_NB_EQ_BD_V3.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "MiniGrid_NB_SV_V3.xml");
		CgmesAclfUtil.run("MiniGrid NB", sv, eq, ssh, tp, tpBd, eqBd);
	}
}
