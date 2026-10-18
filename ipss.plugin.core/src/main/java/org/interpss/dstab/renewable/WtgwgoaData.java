package org.interpss.dstab.renewable;

/** Eight-CON PSS/E 36.7 WTGWGOA order; powers use the connected machine base. */
public record WtgwgoaData(double voltageThreshold,double faultPower,double firstRampRate,
        double holdPower,double secondRampRate,double holdTime,double hysteresis,double voltageFilterTime) {
    public WtgwgoaData {
        for(double value:new double[]{voltageThreshold,faultPower,firstRampRate,holdPower,
                secondRampRate,holdTime,hysteresis,voltageFilterTime})
            if(!Double.isFinite(value))throw new IllegalArgumentException("WTGWGOA parameters must be finite");
        if(faultPower<0 || holdPower<0 || hysteresis<0 || holdTime<0 || voltageFilterTime<0)
            throw new IllegalArgumentException("WTGWGOA powers, hysteresis and times must be non-negative");
        if(firstRampRate<=0 || secondRampRate<=0)
            throw new IllegalArgumentException("WTGWGOA ramp rates must be positive");
    }
    /** The documented disabled selection is accepted, not silently corrected. */
    public boolean enabled(){return holdPower>faultPower;}
}
