package org.interpss.dstab.control.exc.psse.esac8b;

import org.interpss.dstab.control.exc.psse.ac8b.Ac8bExciter;

import com.interpss.dstab.controller.cml.annotate.AnController;
import com.interpss.dstab.mach.Machine;

/** Native PSS/E ESAC8B (Basler DECS) using the shared five-state AC8B PID engine. */
@AnController(input="mach.vt", output="this.outputSignal",
        refPoint="this.reference", display={})
public final class Esac8bExciter extends Ac8bExciter {
    private final Esac8bData data;

    public Esac8bExciter(String id, Esac8bData data, Machine machine) {
        super(id, "ESAC8B", data, machine);
        this.data = data;
    }

    @Override public Esac8bData getData() { return data; }
}
