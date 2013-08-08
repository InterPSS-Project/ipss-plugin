package org.interpss.plugin;

import static org.junit.Assert.assertTrue;

import java.util.List;

import org.interpss.util.reader.TextFileReader;
import org.junit.Test;

public class TextFileReaderTest {
	@Test
	public void testCase1() throws Exception {
		TextFileReader reader = new TextFileReader("testData/psse/PSSE_5Bus_Test.raw");	
		List<String> content = reader.getFileContent();
		//System.out.println(content.size());
		assertTrue(content.size() == 43);
	}
}
