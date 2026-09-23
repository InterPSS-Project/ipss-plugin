package sample.cim;

import java.nio.file.Path;

/** P4 PST PhaseTapChangerLinear Type1 SV-seeded NR load-flow sample. */
public class PstType1AclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfUtil.casDir("PST-PhaseTapChangerLinear-Type1",
				"PST/PST_PhaseTapChangerLinear_Type1");
		if (!CgmesAclfUtil.requireDir(dir, "PST Type1")) {
			return;
		}
		Path eq = CgmesAclfUtil.requireFile(dir, "PST_Type1_EQ.xml");
		Path ssh = CgmesAclfUtil.requireFile(dir, "PST_Type1_SSH.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "PST_Type1_TP.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "PST_Type1_SV.xml");
		CgmesAclfUtil.run("PST Type1", sv, eq, ssh, tp, sv);
	}
}
