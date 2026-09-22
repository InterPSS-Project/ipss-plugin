package sample.cim;

import java.nio.file.Path;

/** P4 MicroGrid Type2 Merged (with HVDC profiles) SV-seeded NR load-flow sample. */
public class MicroGridType2MergedAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir = CgmesAclfSample.casDir("MicroGrid-Type2-Merged",
				"MicroGrid/MicroGrid-Type2/MicroGrid-Type2-Merged");
		if (!CgmesAclfSample.requireDir(dir, "MicroGrid Type2 Merged")) {
			return;
		}
		Path eqBd = CgmesAclfSample.requireFile(dir, "20171002T0930Z_ENTSO-E_EQ_BD_2.xml");
		Path beEq = CgmesAclfSample.requireFile(dir, "20210401T1730Z_1D_BE_EQ_1.xml");
		Path nlEq = CgmesAclfSample.requireFile(dir, "20210401T1730Z_1D_NL_EQ_1.xml");
		Path hvdcEq = CgmesAclfSample.requireFile(dir, "20210401T1730Z_1D_HVDC_EQ_1.xml");
		Path beSsh = CgmesAclfSample.requireFile(dir, "20210401T1730Z_1D_BE_SSH_1.xml");
		Path nlSsh = CgmesAclfSample.requireFile(dir, "20210401T1730Z_1D_NL_SSH_1.xml");
		Path hvdcSsh = CgmesAclfSample.requireFile(dir, "20210401T1730Z_1D_HVDC_SSH_1.xml");
		Path tp = CgmesAclfSample.requireFile(dir, "20210401T1730Z_1D_ASSEMBLED_TP_1.xml");
		Path sv = CgmesAclfSample.requireFile(dir, "20210401T1730Z_1D_ASSEMBLED_SV_1.xml");
		CgmesAclfSample.run("MicroGrid Type2 Merged", sv,
				eqBd, beEq, nlEq, hvdcEq, beSsh, nlSsh, hvdcSsh, tp, sv);
	}
}
