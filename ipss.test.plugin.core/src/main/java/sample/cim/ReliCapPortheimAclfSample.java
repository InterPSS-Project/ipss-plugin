package sample.cim;

import java.nio.file.Path;

/** P4 ReliCap Portheim IGM SV-seeded NR sample (soft converge). */
public class ReliCapPortheimAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfUtil.casDir("ReliCap-Portheim-cimxml", "Instance/Portheim/Grid/cimxml");
		if (!CgmesAclfUtil.requireDir(dir, "ReliCap Portheim")) {
			return;
		}
		Path eq = CgmesAclfUtil.requireFile(dir, "20241223T0642Z_2D_Portheim_EQ_1.xml");
		Path ssh = CgmesAclfUtil.requireFile(dir, "20241223T0642Z_2D_Portheim_SSH_1.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "20241223T0642Z_2D_Portheim_TP_1.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "20241223T0642Z_2D_Portheim_SV_1.xml");
		CgmesAclfUtil.run("ReliCap Portheim", sv, eq, ssh, tp, sv);
	}
}
