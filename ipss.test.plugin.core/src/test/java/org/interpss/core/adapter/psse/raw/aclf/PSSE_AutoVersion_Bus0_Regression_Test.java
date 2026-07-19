package org.interpss.core.adapter.psse.raw.aclf;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.CorePluginTestSetup;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.interpss.plugin.pssl.plugin.IpssAdapter.PsseVersion;
import org.junit.jupiter.api.Test;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.adj.TapControl;

/**
 * Regression for desktop auto-import ({@code AclfDataLoader}):
 * {@link IpssAdapter#parsePsseVersion} + PSSE parse must not invent {@code Bus0}
 * from v34+ {@code @!} headers / system-wide data, and COD=±1 with CONT=0 must
 * use local (from-bus) tap voltage control instead of {@code Bus0}.
 */
public class PSSE_AutoVersion_Bus0_Regression_Test extends CorePluginTestSetup {

	@Test
	public void ieee9V36_autoVersion_noBus0() throws Exception {
		AclfNetwork net = loadWithAutoDetectedVersion("testData/psse/v36/ieee9_v36.raw");

		assertEquals(9, net.getNoBus());
		assertNull(net.getBus("Bus0"));
		assertFalse(net.getBusList().stream().anyMatch(b -> "Bus0".equals(b.getId())));
	}

	@Test
	public void texas2kV36_autoVersion_noBus0() throws Exception {
		AclfNetwork net = loadWithAutoDetectedVersion(
				"testData/psse/v36/Texas2k/Texas2k_series24_case1_2016summerPeak_v36.RAW");

		assertEquals(2000, net.getNoBus());
		assertNull(net.getBus("Bus0"));
		assertFalse(net.getBusList().stream().anyMatch(b -> "Bus0".equals(b.getId())));
		// Real Texas2K buses start at 1001
		assertNotNull(net.getBus("Bus1001"));
	}

	@Test
	public void tapVoltageControl_contZero_usesFromBusNotBus0() throws Exception {
		// Minimal v36 RAW with @! header + system-wide section + COD=1 CONT=0 on one xfr.
		// From-bus must not be Swing (factory rejects swing as vc bus).
		Path raw = Files.createTempFile("psse_cont0_tap", ".RAW");
		try {
			Files.writeString(raw, minimalV36RawWithLocalTapControl());

			assertEquals(PsseVersion.PSSE_36, IpssAdapter.parsePsseVersion(raw.toString()));

			AclfNetwork net = loadWithAutoDetectedVersion(raw.toString());

			assertEquals(2, net.getNoBus());
			assertNull(net.getBus("Bus0"));

			AclfBranch xfr = net.getBranch("Bus1->Bus2(1)");
			assertNotNull(xfr, "expected 2W transformer Bus1->Bus2(1)");
			TapControl tap = xfr.getTapControl();
			assertNotNull(tap, "COD=1 CONT=0 should create tap voltage control");
			assertEquals("Bus1", tap.getVcBusId());
			assertNotNull(tap.getVcBus());
			assertEquals("Bus1", tap.getVcBus().getId());
		}
		finally {
			Files.deleteIfExists(raw);
		}
	}

	@Test
	public void skipsInvalidBusNumberZeroRecord() throws Exception {
		// When system-wide lines leak into the bus section (wrong PSSE version), fields like
		// "GENERAL" parse as bus number 0. Those lines do not start with '0', so they are
		// not section terminators — parseBusLine must skip them.
		Path raw = Files.createTempFile("psse_busnum0", ".raw");
		try {
			String ieee9 = Files.readString(Path.of("testData/psse/v36/ieee9_v36.raw"));
			String injected = ieee9.replace(
					"0 / END OF SYSTEM-WIDE DATA, BEGIN BUS DATA\n",
					"0 / END OF SYSTEM-WIDE DATA, BEGIN BUS DATA\n"
							+ "GENERAL, THRSHZ=0.0001, PQBRAK=0.7\n"
							+ "GAUSS, ITMX=100, ACCP=1.6\n");
			assertTrue(injected.length() > ieee9.length(), "misaligned system-wide lines should be injected");
			Files.writeString(raw, injected);

			AclfNetwork net = loadWithAutoDetectedVersion(raw.toString());
			assertNull(net.getBus("Bus0"));
			assertEquals(9, net.getNoBus());
			assertNotNull(net.getBus("Bus1"));
		}
		finally {
			Files.deleteIfExists(raw);
		}
	}

	private static AclfNetwork loadWithAutoDetectedVersion(String filename) throws Exception {
		PsseVersion ver = IpssAdapter.parsePsseVersion(filename);
		return IpssAdapter.importAclfNet(filename)
				.setFormat(PSSE)
				.setPsseVersion(ver)
				.load()
				.getImportedObj();
	}

	/**
	 * Compact PSSE v36 RAW: {@code @!} label + system-wide block (as in Texas2K) and one
	 * transformer with COD1=1, CONT1=0 (local voltage control).
	 */
	private static String minimalV36RawWithLocalTapControl() {
		return """
				@!IC,SBASE,REV,XFRRAT,NXFRAT,BASFRQ
				0,  100.00, 36,     0,     1, 60.00     / PSS(R)E-36.2
				MINIMAL CONT0 TAP CONTROL CASE
				COMMENT
				GENERAL, THRSHZ=0.0001, PQBRAK=0.7
				GAUSS, ITMX=100, ACCP=1.6, ACCQ=1.6, ACCM=1.0, TOL=0.0001
				NEWTON, ITMXN=20, ACCN=1.0, TOLN=0.1, VCTOLQ=0.1, VCTOLV=0.00001, DVLIM=0.99, NDVFCT=0.99
				ADJUST, ADJTHR=0.005, ACCTAP=1.0, TAPLIM=0.05, SWVBND=100.0, MXTPSS=99, MXSWIM=10
				TYSL, ITMXTY=20, ACCTY=1.0, TOLTY=0.00001
				SOLVER, FNSL, ACTAPS=0
				RATING, 1, "RATE1 ", "RATING SET 1"
				0 / END OF SYSTEM-WIDE DATA, BEGIN BUS DATA
				@!   I,'NAME        ', BASKV, IDE,AREA,ZONE,OWNER, VM,        VA,    NVHI,   NVLO,   EVHI,   EVLO
				     1,'BUS-1       ',  115.0000,1,   1,   1,   1,1.00000,   0.0000,1.10000,0.90000,1.10000,0.90000
				     2,'BUS-2       ',  115.0000,3,   1,   1,   1,1.04000,   0.0000,1.10000,0.90000,1.10000,0.90000
				0 / END OF BUS DATA, BEGIN LOAD DATA
				0 / END OF LOAD DATA, BEGIN FIXED SHUNT DATA
				0 / END OF FIXED SHUNT DATA, BEGIN VOLTAGE DROOP CONTROL DATA
				0 / END OF VOLTAGE DROOP CONTROL DATA, BEGIN GENERATOR DATA
				     2,'1 ',    50.000,     0.000,  9999.000, -9999.000,1.04000,     2,   0,   100.000, 0.00000E+0, 4.00000E-2, 0.00000E+0, 0.00000E+0,1.00000,1,  100.0,  9999.000, -9999.000, 0,   1,1.0000
				0 / END OF GENERATOR DATA, BEGIN SWITCHING DEVICE RATING SET DATA
				0 / END OF SWITCHING DEVICE RATING SET DATA, BEGIN BRANCH DATA
				0 / END OF BRANCH DATA, BEGIN SYSTEM SWITCHING DEVICE DATA
				0 / END OF SYSTEM SWITCHING DEVICE DATA, BEGIN TRANSFORMER DATA
				     1,     2,     0,'1 ', 1, 1, 1, 0.00000E+00, 0.00000E+00,2,'XFR1                                    ',1,   1,1.0000,   0,1.0000,   0,1.0000,   0,1.0000,'            '
				 0.00000E+00, 1.00000E-01, 100.00
				1.00000, 115.00,  0.000,    0.00,    0.00,    0.00,    0.00,    0.00,    0.00,    0.00,    0.00,    0.00,    0.00,    0.00,    0.00, 1,      0,   0,1.10000,0.90000,1.10000,0.90000,  33, 0,0.00000,0.00000, 0.000
				1.00000, 115.00
				0 / END OF TRANSFORMER DATA, BEGIN AREA DATA
				0 / END OF AREA DATA, BEGIN TWO-TERMINAL DC DATA
				0 / END OF TWO-TERMINAL DC DATA, BEGIN VSC DC LINE DATA
				0 / END OF VSC DC LINE DATA, BEGIN IMPEDANCE CORRECTION DATA
				0 / END OF IMPEDANCE CORRECTION DATA, BEGIN MULTI-TERMINAL DC DATA
				0 / END OF MULTI-TERMINAL DC DATA, BEGIN MULTI-SECTION LINE DATA
				0 / END OF MULTI-SECTION LINE DATA, BEGIN ZONE DATA
				0 / END OF ZONE DATA, BEGIN INTER-AREA TRANSFER DATA
				0 / END OF INTER-AREA TRANSFER DATA, BEGIN OWNER DATA
				0 / END OF OWNER DATA, BEGIN FACTS DEVICE DATA
				0 / END OF FACTS DEVICE DATA, BEGIN SWITCHED SHUNT DATA
				0 / END OF SWITCHED SHUNT DATA, BEGIN GNE DATA
				0 / END OF GNE DATA, BEGIN INDUCTION MACHINE DATA
				0 / END OF INDUCTION MACHINE DATA
				Q
				""";
	}
}
