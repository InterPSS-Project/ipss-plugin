package sample.cim;

import java.nio.file.Path;

/** P4 PST PhaseTapChangerLinear Type1 SV-seeded NR load-flow sample. */
public class PstType1AclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfSample.casDir("PST-PhaseTapChangerLinear-Type1",
				"PST/PST_PhaseTapChangerLinear_Type1");
		if (!CgmesAclfSample.requireDir(dir, "PST Type1")) {
			return;
		}
		Path eq = CgmesAclfSample.requireFile(dir, "PST_Type1_EQ.xml");
		Path ssh = CgmesAclfSample.requireFile(dir, "PST_Type1_SSH.xml");
		Path tp = CgmesAclfSample.requireFile(dir, "PST_Type1_TP.xml");
		Path sv = CgmesAclfSample.requireFile(dir, "PST_Type1_SV.xml");
		CgmesAclfSample.run("PST Type1", sv, eq, ssh, tp, sv);
	}
}
