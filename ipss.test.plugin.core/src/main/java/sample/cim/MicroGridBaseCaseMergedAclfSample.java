package sample.cim;

import java.nio.file.Path;

/** P4 MicroGrid BaseCase-Merged SV-seeded NR load-flow sample. */
public class MicroGridBaseCaseMergedAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfUtil.casDir("MicroGrid-BaseCase-Merged",
				"MicroGrid/MicroGid-BaseCase/MicroGrid-BaseCase-Merged");
		if (!CgmesAclfUtil.requireDir(dir, "MicroGrid BaseCase-Merged")) {
			return;
		}
		Path eqBd = CgmesAclfUtil.requireFile(dir, "20171002T0930Z_ENTSO-E_EQ_BD_2.xml");
		Path beEq = CgmesAclfUtil.requireFile(dir, "20210325T1530Z_1D_BE_EQ_001.xml");
		Path nlEq = CgmesAclfUtil.requireFile(dir, "20210325T1530Z_1D_NL_EQ_001.xml");
		Path beSsh = CgmesAclfUtil.requireFile(dir, "20210325T1530Z_1D_BE_SSH_001.xml");
		Path nlSsh = CgmesAclfUtil.requireFile(dir, "20210325T1530Z_1D_NL_SSH_001.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "20210325T1530Z_1D_ASSEMBLED_TP_001.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "20210325T1530Z_1D_ASSEMBLED_SV_001.xml");
		CgmesAclfUtil.run("MicroGrid BaseCase-Merged", sv,
				eqBd, beEq, nlEq, beSsh, nlSsh, tp, sv);
	}
}
