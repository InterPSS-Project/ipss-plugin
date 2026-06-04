package org.interpss.plugin.optadj.optimizer.bean;
/** 

* @author  Donghao.F 

* @date 2026��4��17�� ����11:40:33 

* 

*/
/**
 * ����������࣬�洢�������������Լ����Ȩ����Ϣ
 */
public class GeneratorParameter {
    private final int index;
    private double lowerBound;
    private double upperBound;
    private double weight;
    
    public GeneratorParameter(int index) {
        this.index = index;
        this.lowerBound = Double.NEGATIVE_INFINITY;
        this.upperBound = Double.POSITIVE_INFINITY;
        this.weight = 0.0;
    }
    
	public void setLowerBound(double limit) {
		this.lowerBound = Math.max(this.lowerBound, limit);

	}

	public void setUpperBound(double limit) {
		this.upperBound = Math.min(this.upperBound, limit);

	}
    
    public void setWeight(double weight) {
        this.weight = weight;
    }
    
    public int getIndex() { return index; }
    public double getLowerBound() { return lowerBound; }
    public double getUpperBound() { return upperBound; }
    public double getWeight() { return weight; }
    
    public boolean hasLowerBound() {
        return lowerBound > Double.NEGATIVE_INFINITY;
    }
    
    public boolean hasUpperBound() {
        return upperBound < Double.POSITIVE_INFINITY;
    }
}
