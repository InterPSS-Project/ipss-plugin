package org.interpss.dstab.renewable;

import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;

/** WECC REEC_B electrical controller producing REGC_A current commands. */
public final class Reecb1Model implements RenewableElectricalController {
    private static final double EPS = 1.0e-9;

    private final Reecb1Data data;
    private final Regca1Model converter;
    private Repca1Model plantController;
    private BaseDStabBus<?, ?> sensedBus;
    private double p0;
    private double q0;
    private double vref;
    private double vMeasured;
    private double pMeasured;
    private double pOrder;
    private double qCurrent;
    private double qOuterIntegral;
    private double vIntegral;
    private double effectivePmax;
    private double effectivePmin;
    private double effectiveQmax;
    private double effectiveQmin;
    private double effectiveVmax;
    private double effectiveVmin;
    private double ipcmd;
    private double iqcmd;

    public Reecb1Model(Reecb1Data data) {
        this(data, null);
    }

    public Reecb1Model(Reecb1Data data, Regca1Model converter) {
        this.data = data;
        this.converter = converter;
    }

    public void initialize(double p, double q, double v) {
        resolveSensedBus();
        double sensedV = sensedVoltage(v);
        p0 = p;
        q0 = q;
        vref = data.vref0() == 0.0 ? sensedV : data.vref0();
        vMeasured = sensedV;
        pMeasured = p;
        pOrder = p;
        qCurrent = q / nonzero(sensedV);
        qOuterIntegral = 0.0;
        vIntegral = qCurrent;
        effectivePmax = Math.max(data.pmax(), pOrder);
        effectivePmin = Math.min(data.pmin(), pOrder);
        effectiveQmax = Math.max(data.qmax(), q);
        effectiveQmin = Math.min(data.qmin(), q);
        effectiveVmax = Math.max(data.vmax(), 0.0);
        effectiveVmin = Math.min(data.vmin(), 0.0);
        ipcmd = p / nonzero(sensedV);
        iqcmd = -qCurrent;
        if (plantController != null) plantController.initialize(p, q, sensedV);
    }

    public void step(double dt, double p, double q, double v, double frequency) {
        double sensedV = sensedVoltage(v);
        boolean voltageDip = sensedV < data.vdip() || sensedV > data.vup();
        pMeasured = Repca1Model.lag(pMeasured, p, data.tp(), dt);
        vMeasured = Repca1Model.lag(vMeasured, sensedV, data.trv(), dt);
        if (plantController != null) plantController.step(dt, p, q, sensedV, frequency);

        double pref = p0 + (plantController == null ? 0.0 : plantController.getPref());
        double qref = q0 + (plantController == null ? 0.0 : plantController.getQref());
        double rateLimitedPref = Repca1Model.limit(pref,
                pOrder + data.dpmin() * dt, pOrder + data.dpmax() * dt);
        if (!voltageDip) {
            pOrder = Repca1Model.lag(pOrder,
                    Repca1Model.limit(rateLimitedPref, effectivePmin, effectivePmax), data.tpord(), dt);
            pOrder = Repca1Model.limit(pOrder, effectivePmin, effectivePmax);
        }

        double qTarget = data.pfFlag() == 1 && Math.abs(p0) > EPS
                ? pMeasured * q0 / p0 : qref;
        qTarget = Repca1Model.limit(qTarget, effectiveQmin, effectiveQmax);

        double rawIp = pOrder / nonzero(vMeasured);
        double ipMax = data.pqFlag() == 0 ? data.imax() : data.imax();
        rawIp = Repca1Model.limit(rawIp, 0.0, ipMax);
        double iqMax = data.pqFlag() == 0 ? data.imax() : remaining(data.imax(), rawIp);

        if (data.qFlag() == 0) {
            // Constant-Q/PF path: Q/V converted directly to current and lagged.
            if (!voltageDip) qCurrent = Repca1Model.lag(qCurrent,
                    qTarget / nonzero(vMeasured), data.tiq(), dt);
        } else {
            // VFlag=1 closes the outer Q loop; VFlag=0 is the direct voltage loop.
            double qError = data.vFlag() == 1 ? qTarget - q : 0.0;
            qOuterIntegral = Repca1Model.integrateWithAntiWindup(qOuterIntegral,
                    data.kqi(), qError, dt, data.kqp(), effectiveVmin, effectiveVmax, voltageDip);
            double voltageCommand = Repca1Model.limit(data.kqp() * qError + qOuterIntegral,
                    effectiveVmin, effectiveVmax);
            vIntegral = Repca1Model.integrateWithAntiWindup(vIntegral, data.kvi(), voltageCommand,
                    dt, data.kvp(), -iqMax, iqMax, voltageDip);
            qCurrent = Repca1Model.limit(data.kvp() * voltageCommand + vIntegral, -iqMax, iqMax);
        }

        double iqInjection = 0.0;
        if (voltageDip) {
            double error = Repca1Model.deadband(vref - vMeasured, data.dbd1(), data.dbd2());
            iqInjection = Repca1Model.limit(data.kqv() * error, data.iql1(), data.iqh1());
        }
        double rawIq = -(qCurrent + iqInjection);

        // PowerWorld's published REEC_B current-priority pseudo-code uses
        // nonnegative active current and symmetric reactive-current limits.
        if (data.pqFlag() == 0) {
            rawIq = Repca1Model.limit(rawIq, -data.imax(), data.imax());
            rawIp = Repca1Model.limit(rawIp, 0.0, remaining(data.imax(), rawIq));
        } else {
            rawIp = Repca1Model.limit(rawIp, 0.0, data.imax());
            rawIq = Repca1Model.limit(rawIq, -remaining(data.imax(), rawIp),
                    remaining(data.imax(), rawIp));
        }
        ipcmd = rawIp;
        iqcmd = rawIq;
    }

    private void resolveSensedBus() {
        if (converter == null || data.remoteBus() == 0) return;
        BaseDStabNetwork<?, ?> network = (BaseDStabNetwork<?, ?>) converter.getDStabBus().getNetwork();
        sensedBus = network.getDStabBus("Bus" + data.remoteBus());
        if (sensedBus == null) {
            throw new IllegalStateException("REECB1 remote bus not found: " + data.remoteBus());
        }
    }

    private double sensedVoltage(double localVoltage) {
        return sensedBus == null ? localVoltage : sensedBus.getVoltageMag();
    }

    private static double remaining(double total, double priority) {
        return Math.sqrt(Math.max(0.0, total * total - priority * priority));
    }

    private static double nonzero(double value) {
        return Math.max(0.01, Math.abs(value));
    }

    public Reecb1Data getData() { return data; }
    public Repca1Model getPlantController() { return plantController; }
    public void setPlantController(Repca1Model plantController) { this.plantController = plantController; }
    public double getIpcmd() { return ipcmd; }
    public double getIqcmd() { return iqcmd; }
}
