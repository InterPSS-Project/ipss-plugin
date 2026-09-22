package sample.cim;

import java.nio.file.Path;

/** P4 PST PhaseTapChangerLinear Type2 SV-seeded NR load-flow sample. */
public class PstType2AclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfSample.casDir("PST-PhaseTapChangerLinear-Type2",
				"PST/PST_PhaseTapChangerLinear_Type2");
		if (!CgmesAclfSample.requireDir(dir, "PST Type2")) {
			return;
		}
		Path eq = CgmesAclfSample.requireFile(dir, "PST_Type2_EQ.xml");
		Path ssh = CgmesAclfSample.requireFile(dir, "PST_Type2_SSH.xml");
		Path tp = CgmesAclfSample.requireFile(dir, "PST_Type2_TP.xml");
		Path sv = CgmesAclfSample.requireFile(dir, "PST_Type2_SV.xml");
		CgmesAclfSample.run("PST Type2", sv, eq, ssh, tp, sv);
	}
}
