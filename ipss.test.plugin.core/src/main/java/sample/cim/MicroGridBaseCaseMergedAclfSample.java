package sample.cim;

import java.nio.file.Path;

/** P4 MicroGrid BaseCase-Merged SV-seeded NR load-flow sample. */
public class MicroGridBaseCaseMergedAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfSample.casDir("MicroGrid-BaseCase-Merged",
				"MicroGrid/MicroGid-BaseCase/MicroGrid-BaseCase-Merged");
		if (!CgmesAclfSample.requireDir(dir, "MicroGrid BaseCase-Merged")) {
			return;
		}
		Path eqBd = CgmesAclfSample.requireFile(dir, "20171002T0930Z_ENTSO-E_EQ_BD_2.xml");
		Path beEq = CgmesAclfSample.requireFile(dir, "20210325T1530Z_1D_BE_EQ_001.xml");
		Path nlEq = CgmesAclfSample.requireFile(dir, "20210325T1530Z_1D_NL_EQ_001.xml");
		Path beSsh = CgmesAclfSample.requireFile(dir, "20210325T1530Z_1D_BE_SSH_001.xml");
		Path nlSsh = CgmesAclfSample.requireFile(dir, "20210325T1530Z_1D_NL_SSH_001.xml");
		Path tp = CgmesAclfSample.requireFile(dir, "20210325T1530Z_1D_ASSEMBLED_TP_001.xml");
		Path sv = CgmesAclfSample.requireFile(dir, "20210325T1530Z_1D_ASSEMBLED_SV_001.xml");
		CgmesAclfSample.run("MicroGrid BaseCase-Merged", sv,
				eqBd, beEq, nlEq, beSsh, nlSsh, tp, sv);
	}
}
