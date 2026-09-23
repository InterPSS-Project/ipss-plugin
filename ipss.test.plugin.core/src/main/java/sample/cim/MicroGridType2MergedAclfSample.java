package sample.cim;

import java.nio.file.Path;

/** P4 MicroGrid Type2 Merged (with HVDC profiles) SV-seeded NR load-flow sample. */
public class MicroGridType2MergedAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfUtil.casDir("MicroGrid-Type2-Merged",
				"MicroGrid/MicroGrid-Type2/MicroGrid-Type2-Merged");
		if (!CgmesAclfUtil.requireDir(dir, "MicroGrid Type2 Merged")) {
			return;
		}
		Path eqBd = CgmesAclfUtil.requireFile(dir, "20171002T0930Z_ENTSO-E_EQ_BD_2.xml");
		Path beEq = CgmesAclfUtil.requireFile(dir, "20210401T1730Z_1D_BE_EQ_1.xml");
		Path nlEq = CgmesAclfUtil.requireFile(dir, "20210401T1730Z_1D_NL_EQ_1.xml");
		Path hvdcEq = CgmesAclfUtil.requireFile(dir, "20210401T1730Z_1D_HVDC_EQ_1.xml");
		Path beSsh = CgmesAclfUtil.requireFile(dir, "20210401T1730Z_1D_BE_SSH_1.xml");
		Path nlSsh = CgmesAclfUtil.requireFile(dir, "20210401T1730Z_1D_NL_SSH_1.xml");
		Path hvdcSsh = CgmesAclfUtil.requireFile(dir, "20210401T1730Z_1D_HVDC_SSH_1.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "20210401T1730Z_1D_ASSEMBLED_TP_1.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "20210401T1730Z_1D_ASSEMBLED_SV_1.xml");
		CgmesAclfUtil.run("MicroGrid Type2 Merged", sv,
				eqBd, beEq, nlEq, hvdcEq, beSsh, nlSsh, hvdcSsh, tp, sv);
	}
}
