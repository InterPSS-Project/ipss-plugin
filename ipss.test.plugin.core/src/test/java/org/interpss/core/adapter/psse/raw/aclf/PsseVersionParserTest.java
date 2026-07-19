package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link IpssAdapter#parsePsseVersion(String)} edge cases that previously
 * caused desktop auto-import to fall back to PSSE_30 and create bogus {@code Bus0} entries.
 */
public class PsseVersionParserTest {

	@Test
	public void parsesRevisionWithPsseCommentSuffix() throws Exception {
		Path raw = Files.createTempFile("psse_comment_suffix", ".RAW");
		try {
			Files.writeString(raw, """
					0,100.0,30 / PSS(tm)E-30 RAW created Fri, May 29 2020
					Random title line
					Random comment line
					""");

			assertEquals(IpssAdapter.PsseVersion.PSSE_30, IpssAdapter.parsePsseVersion(raw.toString()));
		}
		finally {
			Files.deleteIfExists(raw);
		}
	}

	@Test
	public void skipsAtBangLabelLineBeforeHeader() throws Exception {
		// Texas2K / modern RAW exports start with @!IC,SBASE,REV,... — parts[2] is "REV", not 36
		Path raw = Files.createTempFile("psse_atbang_header", ".RAW");
		try {
			Files.writeString(raw, """
					@!IC,SBASE,REV,XFRRAT,NXFRAT,BASFRQ
					0,  100.00, 36,     0,     1, 60.00     / PSS(R)E-36.2
					TITLE LINE
					COMMENT LINE
					""");

			assertEquals(IpssAdapter.PsseVersion.PSSE_36, IpssAdapter.parsePsseVersion(raw.toString()));
		}
		finally {
			Files.deleteIfExists(raw);
		}
	}

	@Test
	public void skipsBlankAndAtBangLinesBeforeHeader() throws Exception {
		Path raw = Files.createTempFile("psse_blank_atbang", ".RAW");
		try {
			Files.writeString(raw, """

					@!IC,SBASE,REV,XFRRAT,NXFRAT,BASFRQ
					0,100.0,35 / PSS(R)E-35
					TITLE
					COMMENT
					""");

			assertEquals(IpssAdapter.PsseVersion.PSSE_35, IpssAdapter.parsePsseVersion(raw.toString()));
		}
		finally {
			Files.deleteIfExists(raw);
		}
	}

	@Test
	public void stripsSlashCommentFromRevisionField() throws Exception {
		Path raw = Files.createTempFile("psse_rev_slash", ".RAW");
		try {
			Files.writeString(raw, """
					0,  100.00, 36     / PSS(R)E-36.2    MON, MAR 31 2025
					TITLE
					COMMENT
					""");

			assertEquals(IpssAdapter.PsseVersion.PSSE_36, IpssAdapter.parsePsseVersion(raw.toString()));
		}
		finally {
			Files.deleteIfExists(raw);
		}
	}

	@Test
	public void detectsTexas2kV36RawFile() throws Exception {
		assertEquals(IpssAdapter.PsseVersion.PSSE_36,
				IpssAdapter.parsePsseVersion(
						"testData/psse/v36/Texas2k/Texas2k_series24_case1_2016summerPeak_v36.RAW"));
	}

	@Test
	public void detectsIeee9V36RawFile() throws Exception {
		assertEquals(IpssAdapter.PsseVersion.PSSE_36,
				IpssAdapter.parsePsseVersion("testData/psse/v36/ieee9_v36.raw"));
	}
}
