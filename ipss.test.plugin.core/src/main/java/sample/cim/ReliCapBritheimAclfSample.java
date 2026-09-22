package sample.cim;

import java.nio.file.Path;

/** P4 ReliCap Britheim IGM SV-seeded NR load-flow sample. */
public class ReliCapBritheimAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfSample.casDir("ReliCap-Britheim-cimxml", "Instance/Britheim/Grid/cimxml");
		if (!CgmesAclfSample.requireDir(dir, "ReliCap Britheim")) {
			return;
		}
		Path eq = CgmesAclfSample.requireFile(dir, "20220615T2230Z__Britheim_EQ_1.xml");
		Path ssh = CgmesAclfSample.requireFile(dir, "20220615T2230Z_2D_Britheim_SSH_1.xml");
		Path tp = CgmesAclfSample.requireFile(dir, "20220615T2230Z_2D_Britheim_TP_1.xml");
		Path sv = CgmesAclfSample.requireFile(dir, "20220615T2230Z_2D_Britheim_SV_1.xml");
		CgmesAclfSample.run("ReliCap Britheim", sv, eq, ssh, tp, sv);
	}
}
