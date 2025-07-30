package org.interpss.dep.QA.compare;

/**
 * Interface for implementing data/model comparison 
 * 
 * @author mzhou
 *
 * @param <T> Data/Model type for the comparison
 */
@Deprecated
public interface IDataComparator<TBase, TObj> {
	/**
	 * compare the obj against the base object. It is assumed that the base
	 * object containing correct data
	 * 
	 * @param base
	 * @param obj
	 * @return false if there is any difference
	 */
	boolean compare(TBase base, TObj obj);

	/**
	 * in case there is difference, the msg contains the difference details
	 * for output purpose 
	 * 
	 * @return
	 */
	String getMsg();
}
