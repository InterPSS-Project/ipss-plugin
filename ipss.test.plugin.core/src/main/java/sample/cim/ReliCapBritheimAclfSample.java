package sample.cim;

import java.nio.file.Path;

/** P4 ReliCap Britheim IGM SV-seeded NR load-flow sample. */
public class ReliCapBritheimAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfUtil.casDir("ReliCap-Britheim-cimxml", "Instance/Britheim/Grid/cimxml");
		if (!CgmesAclfUtil.requireDir(dir, "ReliCap Britheim")) {
			return;
		}
		Path eq = CgmesAclfUtil.requireFile(dir, "20220615T2230Z__Britheim_EQ_1.xml");
		Path ssh = CgmesAclfUtil.requireFile(dir, "20220615T2230Z_2D_Britheim_SSH_1.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "20220615T2230Z_2D_Britheim_TP_1.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "20220615T2230Z_2D_Britheim_SV_1.xml");
		CgmesAclfUtil.run("ReliCap Britheim", sv, eq, ssh, tp, sv);
	}
}
