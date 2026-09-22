package sample.cim;

import java.nio.file.Files;
import java.nio.file.Path;

/** P4 PowerFlow instance SV-seeded NR load-flow sample. */
public class PowerFlowAclfSample {
	public static void main(String[] args) throws Exception {
		Path dir0 = CgmesAclfSample.casDir("PowerFlow-Instance", "PowerFlow/PowerFlow");
		Path dir = Files.isDirectory(dir0)
				? dir0
				: CgmesAclfSample.casDir("PowerFlow", "PowerFlow").resolve("PowerFlow");
		if (!CgmesAclfSample.requireDir(dir, "PowerFlow instance")) {
			return;
		}
		Path eq = CgmesAclfSample.requireFile(dir, "PowerFlow_EQ.xml");
		Path ssh = CgmesAclfSample.requireFile(dir, "PowerFlow_SSH.xml");
		Path tp = CgmesAclfSample.requireFile(dir, "PowerFlow_TP.xml");
		Path sv = CgmesAclfSample.requireFile(dir, "PowerFlow_SV.xml");
		CgmesAclfSample.run("PowerFlow", sv, eq, ssh, tp, sv);
	}
}
