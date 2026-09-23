package sample.cim;

import java.nio.file.Path;

/** P4 PST PhaseTapChangerTable Type3 SV-seeded NR load-flow sample. */
public class PstTableType3AclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfUtil.casDir("PST-PhaseTapChangerTable-Type3",
				"PST/PST_PhaseTapChangerTable_Type3");
		if (!CgmesAclfUtil.requireDir(dir, "PST Table Type3")) {
			return;
		}
		Path eq = CgmesAclfUtil.requireFile(dir, "PST_Type3_EQ.xml");
		Path ssh = CgmesAclfUtil.requireFile(dir, "PST_Type3_SSH.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "PST_Type3_TP.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "PST_Type3_SV.xml");
		CgmesAclfUtil.run("PST Table Type3", sv, eq, ssh, tp, sv);
	}
}
