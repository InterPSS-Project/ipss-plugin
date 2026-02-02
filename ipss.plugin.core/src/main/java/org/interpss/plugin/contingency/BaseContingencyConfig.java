package org.interpss.plugin.contingency;

public abstract class BaseContingencyConfig {
    private boolean turnOffIslandBus = true;
    private boolean autoTurnLine2Xfr = true;
    private double overloadThreshold = 100.0; // in percent

    // Getters and setters
    public boolean isTurnOffIslandBus() { return turnOffIslandBus; }
    public void setTurnOffIslandBus(boolean turnOffIslandBus) { this.turnOffIslandBus = turnOffIslandBus; }
    
    public boolean isAutoTurnLine2Xfr() { return autoTurnLine2Xfr; }
    public void setAutoTurnLine2Xfr(boolean autoTurnLine2Xfr) { this.autoTurnLine2Xfr = autoTurnLine2Xfr; }
    
    public double getOverloadThreshold() { return overloadThreshold; }
    public void setOverloadThreshold(double overloadThreshold) { this.overloadThreshold = overloadThreshold; }
    
}
