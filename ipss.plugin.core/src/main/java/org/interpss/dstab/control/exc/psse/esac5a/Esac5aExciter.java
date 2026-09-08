package org.interpss.dstab.control.exc.psse.esac5a;

import org.interpss.dstab.control.exc.psse.ac5c.Ac5cExciter;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.mach.Machine;

/**
 * IEEE 421.5/PSS/E ESAC5A simplified rotating AC excitation system.
 *
 * <p>The five dynamic states and their numerical integration are shared with
 * AC5C. ESAC5A disables the revision-C limiter locations, field-current
 * compensation, internal field limits, and loaded rectifier.</p>
 */
@AnController(input="mach.vt",output="this.outputSignal",refPoint="this.reference",display={})
public final class Esac5aExciter extends Ac5cExciter {
    private final Esac5aData data;
    /** Backward-compatible ESAC5A names for the effective regulator limits. */
    public double vrmax,vrmin;

    public Esac5aExciter(String id,Esac5aData data,Machine machine){
        super(id,"ESAC5A",data,machine,true);this.data=data;
    }

    @Override public boolean initStates(BaseDStabBus<?,?> bus,Machine machine){
        boolean initialized=super.initStates(bus,machine);vrmax=vamax;vrmin=vamin;return initialized;
    }

    @Override public Esac5aData getData(){return data;}
}
