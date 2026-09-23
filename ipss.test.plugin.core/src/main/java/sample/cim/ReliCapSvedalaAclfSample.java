package sample.cim;

import java.nio.file.Path;

/** P4 ReliCap Svedala IGM SV-seeded NR load-flow sample. */
public class ReliCapSvedalaAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfUtil.casDir("ReliCap-Svedala-cimxml", "Instance/Svedala/Grid/cimxml");
		if (!CgmesAclfUtil.requireDir(dir, "ReliCap Svedala")) {
			return;
		}
		Path eq = CgmesAclfUtil.requireFile(dir, "20220615T2230Z__Svedala_EQ_1.xml");
		Path ssh = CgmesAclfUtil.requireFile(dir, "20220615T2230Z_2D_Svedala_SSH_1.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "20220615T2230Z_2D_Svedala_TP_1.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "20220615T2230Z_2D_Svedala_SV_1.xml");
		CgmesAclfUtil.run("ReliCap Svedala", sv, eq, ssh, tp, sv);
	}
}
