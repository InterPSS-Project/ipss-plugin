

package org.interpss.fadapter.export.psse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * PSSE JSon format data updater
 * 
 * @author mzhou
 *
 */
public abstract class BasePSSEJSonUpdater {	
	/**
	 * field name to store position lookup info
	 */
	protected LinkedHashMap<String, Integer> positionTable;  // 0, .... n-1
	
	/**
	 * Constructor
	 * 
	 * @param fieldDef the field definition list
	 */
	public BasePSSEJSonUpdater(List<String> fieldDef) {
		this(fieldDef, null);
	}
	
	/**
	 * Constructor
	 * 
	 * @param fieldDef field name definitions
	 * @param consumer a consumer to add more field names
	 */
	public BasePSSEJSonUpdater(List<String> fieldDef, Consumer<List<String>> consumer) {
		if (consumer != null)
			consumer.accept(fieldDef);
		
		this.positionTable = new LinkedHashMap<>();
		int cnt =0;
		for (String s : fieldDef) {
			this.positionTable.put(s.trim(), cnt++);
		}
	}
	
}