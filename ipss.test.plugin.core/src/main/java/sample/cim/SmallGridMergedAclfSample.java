package sample.cim;

import java.nio.file.Path;

/** P4 SmallGrid-Merged SV-seeded NR load-flow sample. */
public class SmallGridMergedAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfSample.casDir("SmallGrid-Merged", "SmallGrid/SmallGrid-Merged");
		if (!CgmesAclfSample.requireDir(dir, "SmallGrid-Merged")) {
			return;
		}
		Path eq = CgmesAclfSample.requireFile(dir, "SmallGrid_EQ.xml");
		Path ssh = CgmesAclfSample.requireFile(dir, "SmallGrid_SSH.xml");
		Path tp = CgmesAclfSample.requireFile(dir, "SmallGrid_TP.xml");
		Path sv = CgmesAclfSample.requireFile(dir, "SmallGrid_SV.xml");
		Path eqBd = CgmesAclfSample.requireFile(dir, "SmallGrid_EQBD.xml");
		CgmesAclfSample.run("SmallGrid-Merged", sv, eq, ssh, tp, sv, eqBd);
	}
}
