# Local renewable Q/V eigenanalysis

`LocalRenewableQvEigenAnalyzer` is a permanent diagnostic API for locating
small-signal voltage/reactive-control modes in local
`REPCA1 -> REECA1 -> REGCA1` chains. It combines a solved network sensitivity
with analytic controller derivatives and produces the continuous-time system

```text
delta_x_dot = A * delta_x
```

The eigenvalue with the greatest real part is reported as `sigma + j*omega`.
`sigma` is the local growth or decay rate in `1/s`, and
`abs(omega)/(2*pi)` is the oscillation frequency in hertz. The normalized
complex right eigenvector ranks the controller states associated with that
mode. Those values describe modal shape; they are not left/right participation
factors.

## Using the API

Start with a DStab network whose AC load flow has converged. Select the buses
whose renewable Q/V interaction should be analyzed:

```java
var analysis = LocalRenewableQvEigenAnalyzer.analyze(
        network, List.of("Bus1062", "Bus1063"));

var mode = analysis.dominantMode();
System.out.printf("growth=%g 1/s, frequency=%g Hz%n",
        mode.real(), mode.frequencyHz());
mode.participation().stream().limit(10).forEach(System.out::println);

var report = LocalRenewableQvEigenReportWriter.write(
        Path.of("target", "qv-eigenanalysis"), analysis);
```

The report directory contains:

- `summary.csv`: scope, dimensions, dominant eigenvalue/frequency, and the
  required interpretation qualifier;
- `coupling.csv`: incremental reactive-current-to-voltage-magnitude network
  sensitivity;
- `state-matrix.csv`: the complete controller Jacobian;
- `devices.csv`: operating point, base conversion, and controller parameters;
- `dominant-mode.csv`: normalized complex right-eigenvector components;
- `constraints.csv`: PI states initialized on anti-windup boundaries;
- `limiter-regions.csv`: active/clamped regional modes and tangent-cone tests.

## Interpreting a result safely

A positive `dominant_eigenvalue_real` is an ordinary local-mode result only
when `two_sided_linearization_valid=true`. If it is false, the operating point
lies on a PI limit and a conventional two-sided Jacobian does not exist. Inspect
`limiter-regions.csv` and consider a regional mode physically admissible only
when its `cone_feasible` value is true. Even then, this is an instantaneous
directional qualification, not proof that the nonlinear trajectory remains in
that region.

The current analyzer deliberately supports only active, local-voltage
`REPCA1/REECA1/REGCA1` Q/V loops with positive dynamic time constants. It does
not yet include voltage-dip switching, active-power coupling, remote-bus or
branch measurements, other dynamic model families, or general numerical
linearization of the complete DStab system. Use it to localize a suspected
controller mode; confirm the diagnosis with timestep convergence and matched
time-domain traces from InterPSS and an independent simulator.
