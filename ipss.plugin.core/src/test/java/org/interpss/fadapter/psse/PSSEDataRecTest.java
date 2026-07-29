package org.interpss.fadapter.psse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tokenizer coverage for PSS/E free-format RAW lines.
 */
public class PSSEDataRecTest {

	@Test
	public void spaceAfterQuotedName_splitsFields() {
		// Sample v31/v33 multi-terminal DC headers omit the comma after NAME.
		PSSEDataRec rec = new PSSEDataRec("\"MULTERM_DC_1\" 4, 5, 4, 1,   212,    400.00,     0");
		assertEquals("MULTERM_DC_1", rec.getString(0));
		assertEquals(4, rec.getInt(1));
		assertEquals(5, rec.getInt(2));
		assertEquals(4, rec.getInt(3));
		assertEquals(1, rec.getInt(4));
		assertEquals(212, rec.getInt(5));
		assertEquals(400.0, rec.getDouble(6), 1.0E-9);
		assertEquals(0, rec.getInt(7));
	}

	@Test
	public void commaAfterQuotedName_unchanged() {
		PSSEDataRec rec = new PSSEDataRec("\"FACTS_DVCE_1\",   153,     0,1,     0.000");
		assertEquals("FACTS_DVCE_1", rec.getString(0));
		assertEquals(153, rec.getInt(1));
		assertEquals(0, rec.getInt(2));
		assertEquals(1, rec.getInt(3));
	}

	@Test
	public void trailingSpaceBeforeComma_keepsSingleNumericField() {
		// Bus BASKV like `13.2 ,  2` must not insert an empty field.
		PSSEDataRec rec = new PSSEDataRec("     91,'BUS-91       ', 13.2 ,  2,   1,   1,   1,0.95308,  27.4891");
		assertEquals(91, rec.getInt(0));
		assertEquals("BUS-91", rec.getString(1).trim());
		assertEquals(13.2, rec.getDouble(2), 1.0E-9);
		assertEquals(2, rec.getInt(3));
		assertEquals(1, rec.getInt(4));
	}
}
