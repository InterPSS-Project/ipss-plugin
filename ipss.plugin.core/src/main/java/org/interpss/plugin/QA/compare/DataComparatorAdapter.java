package org.interpss.plugin.QA.compare;


/**
 * IDataComparator adapter implementation
 * 
 * @author mzhou
 *
 * @param <T>
 */
public abstract class DataComparatorAdapter<TBase, TObj> implements IDataComparator<TBase, TObj> {
	protected String msg = "";
	
	@Override public String getMsg() {
		return this.msg;
	}
}
