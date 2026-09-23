package sample.cim;

import java.nio.file.Path;

/** P4 PST PhaseTapChangerLinear Type2 SV-seeded NR load-flow sample. */
public class PstType2AclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfUtil.casDir("PST-PhaseTapChangerLinear-Type2",
				"PST/PST_PhaseTapChangerLinear_Type2");
		if (!CgmesAclfUtil.requireDir(dir, "PST Type2")) {
			return;
		}
		Path eq = CgmesAclfUtil.requireFile(dir, "PST_Type2_EQ.xml");
		Path ssh = CgmesAclfUtil.requireFile(dir, "PST_Type2_SSH.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "PST_Type2_TP.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "PST_Type2_SV.xml");
		CgmesAclfUtil.run("PST Type2", sv, eq, ssh, tp, sv);
	}
}
