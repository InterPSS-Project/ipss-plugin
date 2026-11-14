package org.interpss.fadapter.psse.dyr;

import com.interpss.dstab.hvdc.model.Cdc6tParameters;

/** Decode exact named records into source constants; runtime preparation follows solved load flow. */
public final class Cdc6tRecordMapper {
    private Cdc6tRecordMapper() {}
    public static Cdc6tParameters prepare(PsseDyrRecord record){
        java.util.Objects.requireNonNull(record);
        String location=record.source()+":"+record.startLine();
        if(!"CDC6T".equals(record.canonicalModelName())||!record.hasNamedTarget())
            throw new IllegalArgumentException("Named CDC6T record required at "+location);
        if(record.parameterCount()!=Cdc6tParameters.CONSTANT_COUNT)
            throw new IllegalArgumentException("CDC6T requires exactly 32 constants at "+location);
        double[] constants=new double[Cdc6tParameters.CONSTANT_COUNT];
        for(int i=0;i<constants.length;i++)constants[i]=record.doubleParameter(i);
        try{return new Cdc6tParameters(record.deviceId(),constants);}
        catch(IllegalArgumentException failure){throw new IllegalArgumentException("Invalid CDC6T parameters at "+location+": "+failure.getMessage(),failure);}
    }
}
