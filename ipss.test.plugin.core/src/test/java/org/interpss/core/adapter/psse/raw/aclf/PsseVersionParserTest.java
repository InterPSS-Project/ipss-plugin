package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.fadapter.psse.PsseRev;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link PsseRev} header REV edge cases that previously caused
 * desktop auto-import to fall back to v30 and create bogus {@code Bus0} entries.
 */
public class PsseVersionParserTest {

	private static int revFromFile(Path raw) throws Exception {
		for (String line : Files.readAllLines(raw)) {
			if (line.trim().isEmpty() || line.trim().startsWith("@!")) {
				continue;
			}
			return PsseRev.fromHeaderLine(line);
		}
		return PsseRev.defaultRev();
	}

	@Test
	public void parsesRevisionWithPsseCommentSuffix() throws Exception {
		Path raw = Files.createTempFile("psse_comment_suffix", ".RAW");
		try {
			Files.writeString(raw, """
					0,100.0,30 / PSS(tm)E-30 RAW created Fri, May 29 2020
					Random title line
					Random comment line
					""");

			assertEquals(30, revFromFile(raw));
		}
		finally {
			Files.deleteIfExists(raw);
		}
	}

	@Test
	public void skipsAtBangLabelLineBeforeHeader() throws Exception {
		Path raw = Files.createTempFile("psse_atbang_header", ".RAW");
		try {
			Files.writeString(raw, """
					@!IC,SBASE,REV,XFRRAT,NXFRAT,BASFRQ
					0,  100.00, 36,     0,     1, 60.00     / PSS(R)E-36.2
					TITLE LINE
					COMMENT LINE
					""");

			assertEquals(36, revFromFile(raw));
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

			assertEquals(35, revFromFile(raw));
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

			assertEquals(36, revFromFile(raw));
		}
		finally {
			Files.deleteIfExists(raw);
		}
	}

	@Test
	public void detectsTexas2kV36RawFile() throws Exception {
		assertEquals(36, revFromFile(Path.of(
				"testData/psse/v36/Texas2k/Texas2k_series24_case1_2016summerPeak_v36.RAW")));
	}

	@Test
	public void detectsIeee9V36RawFile() throws Exception {
		assertEquals(36, revFromFile(Path.of("testData/psse/v36/ieee9_v36.raw")));
	}
}
