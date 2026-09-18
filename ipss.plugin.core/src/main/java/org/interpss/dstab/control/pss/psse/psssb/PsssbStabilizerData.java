package org.interpss.dstab.control.pss.psse.psssb;

import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizerData;

/** PowerWorld/PSLF PSSSB data: PSS2A plus the transient voltage-boost branch. */
public final class PsssbStabilizerData extends Ieee1992PSS2AStabilizerData {
    private int sw1;
    private double td1;
    private double td2;
    private double vtl;
    private double vk;
    private double vcutoff;

    public int getSw1() { return sw1; }
    public void setSw1(int sw1) { this.sw1 = sw1; }
    public double getTd1() { return td1; }
    public void setTd1(double td1) { this.td1 = td1; }
    public double getTd2() { return td2; }
    public void setTd2(double td2) { this.td2 = td2; }
    public double getVtl() { return vtl; }
    public void setVtl(double vtl) { this.vtl = vtl; }
    public double getVk() { return vk; }
    public void setVk(double vk) { this.vk = vk; }
    public double getVcutoff() { return vcutoff; }
    public void setVcutoff(double vcutoff) { this.vcutoff = vcutoff; }
}
