package sample.cim;

import java.nio.file.Files;
import java.nio.file.Path;

/** P4 PowerFlow instance SV-seeded NR load-flow sample. */
public class PowerFlowAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir0 = CgmesAclfUtil.casDir("PowerFlow-Instance", "PowerFlow/PowerFlow");
		Path dir = Files.isDirectory(dir0)
				? dir0
				: CgmesAclfUtil.casDir("PowerFlow", "PowerFlow").resolve("PowerFlow");
		if (!CgmesAclfUtil.requireDir(dir, "PowerFlow instance")) {
			return;
		}
		Path eq = CgmesAclfUtil.requireFile(dir, "PowerFlow_EQ.xml");
		Path ssh = CgmesAclfUtil.requireFile(dir, "PowerFlow_SSH.xml");
		Path tp = CgmesAclfUtil.requireFile(dir, "PowerFlow_TP.xml");
		Path sv = CgmesAclfUtil.requireFile(dir, "PowerFlow_SV.xml");
		CgmesAclfUtil.run("PowerFlow", sv, eq, ssh, tp, sv);
	}
}
