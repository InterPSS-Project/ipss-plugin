package org.interpss.dstab.control.exc.psse.st5c;

import org.interpss.dstab.control.exc.psse.st5b.St5bData;

/** Native PSS/E IEEE 421.5-2016 ST5C excitation-system parameters. */
public final class St5cData extends St5bData {
    private int oel=1,uel=1;

    @Override public void setValue(String name,int value){switch(name.toLowerCase()){
        case "oel"->oel=value;case "uel"->uel=value;default->super.setValue(name,value);}}
    @Override public void setValue(String name,double value){switch(name.toLowerCase()){
        case "oel"->oel=(int)value;case "uel"->uel=(int)value;default->super.setValue(name,value);}}
    public int getOel(){return oel;}public void setOel(int value){oel=value;}
    public int getUel(){return uel;}public void setUel(int value){uel=value;}
}
