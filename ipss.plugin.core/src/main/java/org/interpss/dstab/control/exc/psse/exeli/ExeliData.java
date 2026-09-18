package org.interpss.dstab.control.exc.psse.exeli;

import org.interpss.dstab.control.base.BaseControllerData;

/** PSS/E EXELI all-static, transformer-fed excitation-system parameters. */
public final class ExeliData extends BaseControllerData {
    private double tfv, tfi, tnu, vpu, vpi, vpnf, dpnf, efdmin, efdmax;
    private double xe, tw, ks1, ks2, ts1, ts2, smax;

    public ExeliData() {
        setRangeParameters(new String[][] {
                {"tfv", "0", "1000"}, {"tfi", "0", "1000"},
                {"tnu", "0", "1000"}, {"vpu", "-10000", "10000"},
                {"vpi", "-10000", "10000"}, {"vpnf", "0", "10000"},
                {"dpnf", "0", "10000"}, {"efdmin", "-10000", "10000"},
                {"efdmax", "-10000", "10000"}, {"xe", "0", "10000"},
                {"tw", "0", "1000"}, {"ks1", "-10000", "10000"},
                {"ks2", "-10000", "10000"}, {"ts1", "0", "1000"},
                {"ts2", "0", "1000"}, {"smax", "0", "10000"}
        });
    }

    @Override
    public void setValue(String name, int value) {
        setValue(name, (double) value);
    }

    @Override
    public void setValue(String name, double value) {
        switch (name.toLowerCase()) {
            case "tfv" -> tfv = value;
            case "tfi" -> tfi = value;
            case "tnu" -> tnu = value;
            case "vpu" -> vpu = value;
            case "vpi" -> vpi = value;
            case "vpnf" -> vpnf = value;
            case "dpnf" -> dpnf = value;
            case "efdmin" -> efdmin = value;
            case "efdmax" -> efdmax = value;
            case "xe" -> xe = value;
            case "tw" -> tw = value;
            case "ks1" -> ks1 = value;
            case "ks2" -> ks2 = value;
            case "ts1" -> ts1 = value;
            case "ts2" -> ts2 = value;
            case "smax" -> smax = value;
            default -> { }
        }
    }

    public double getTfv() { return tfv; }
    public void setTfv(double value) { tfv = value; }
    public double getTfi() { return tfi; }
    public void setTfi(double value) { tfi = value; }
    public double getTnu() { return tnu; }
    public void setTnu(double value) { tnu = value; }
    public double getVpu() { return vpu; }
    public void setVpu(double value) { vpu = value; }
    public double getVpi() { return vpi; }
    public void setVpi(double value) { vpi = value; }
    public double getVpnf() { return vpnf; }
    public void setVpnf(double value) { vpnf = value; }
    public double getDpnf() { return dpnf; }
    public void setDpnf(double value) { dpnf = value; }
    public double getEfdmin() { return efdmin; }
    public void setEfdmin(double value) { efdmin = value; }
    public double getEfdmax() { return efdmax; }
    public void setEfdmax(double value) { efdmax = value; }
    public double getXe() { return xe; }
    public void setXe(double value) { xe = value; }
    public double getTw() { return tw; }
    public void setTw(double value) { tw = value; }
    public double getKs1() { return ks1; }
    public void setKs1(double value) { ks1 = value; }
    public double getKs2() { return ks2; }
    public void setKs2(double value) { ks2 = value; }
    public double getTs1() { return ts1; }
    public void setTs1(double value) { ts1 = value; }
    public double getTs2() { return ts2; }
    public void setTs2(double value) { ts2 = value; }
    public double getSmax() { return smax; }
    public void setSmax(double value) { smax = value; }
}
