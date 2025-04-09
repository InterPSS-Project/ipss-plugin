package org.interpss.dstab.dynLoad;

import org.apache.commons.math3.complex.Complex;
import com.interpss.dstab.device.DynamicGenDevice;

/**
 * A representation of the model object '<em><b>DER_A</b></em>' in positive sequence.
 */
public interface DER_A_PosSeq extends DynamicGenDevice {


	boolean calcIpIq(int flag);

	public void setPosSeqGenPQ(Complex gen);
	
	    
	    public Complex getPosSeqIpq();
	     
	     public Complex  getPosSeqnortonCurSource();
	    
	     public void    setPosSeqNortonCurSource( Complex Igen);
	     public void setNortonCurSource(Complex Igen);
	     
	     public Complex getNortonCurSource();

	 	 public void setPosSeqIpq(Complex ipq_pos);
	 	 
			 
			public Complex getPosSeqGenPQ();
			public void setPowerFactor(double pf);
			
			public void setDebugMode(boolean set) ;
			
			public double getPowerFactor();
			
			public Complex getInitGenPQ();
			
			public void setVoltageRecoveryFrac(double frac);
			
			public void setCallAfterStepManually(boolean bool);
			
			public void setVh0(double vh0);
			
			public void setVh1(double vh1) ;
			
			public void setVl0(double vl0);
			
			public void setVl1(double vl1) ;
			
			public void setTvl0(double time);
			
			public void setTvl1(double time);
			//public static void writeCsv(String fileName, String[] headers, boolean append, double[] dataRow);
			
			public void writeDataToFile(boolean tf);
			
			public void outputInternalStatesDuringSim(boolean tf);
			public void setFreqFlag(int flag);
			public void setFreqTripFlag(int flag);
			public void enableVoltageTripping(boolean tf);
			
			public void setOutputCSVFilename(String filename);

}
