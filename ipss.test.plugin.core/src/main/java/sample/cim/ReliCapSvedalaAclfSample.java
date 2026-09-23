package sample.cim;

import java.nio.file.Path;

/** P4 ReliCap Svedala IGM SV-seeded NR load-flow sample. */
public class ReliCapSvedalaAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfSample.casDir("ReliCap-Svedala-cimxml", "Instance/Svedala/Grid/cimxml");
		if (!CgmesAclfSample.requireDir(dir, "ReliCap Svedala")) {
			return;
		}
		Path eq = CgmesAclfSample.requireFile(dir, "20220615T2230Z__Svedala_EQ_1.xml");
		Path ssh = CgmesAclfSample.requireFile(dir, "20220615T2230Z_2D_Svedala_SSH_1.xml");
		Path tp = CgmesAclfSample.requireFile(dir, "20220615T2230Z_2D_Svedala_TP_1.xml");
		Path sv = CgmesAclfSample.requireFile(dir, "20220615T2230Z_2D_Svedala_SV_1.xml");
		CgmesAclfSample.run("ReliCap Svedala", sv, eq, ssh, tp, sv);
	}
}
