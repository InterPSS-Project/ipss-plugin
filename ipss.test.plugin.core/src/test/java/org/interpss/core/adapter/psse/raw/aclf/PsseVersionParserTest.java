package org.interpss.core.adapter.psse.raw.aclf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.jupiter.api.Test;

public class PsseVersionParserTest {
	@Test
	public void parsesRevisionWithPsseCommentSuffix() throws Exception {
		Path raw = Files.createTempFile("Texas7k_testheader", ".RAW");
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
}
