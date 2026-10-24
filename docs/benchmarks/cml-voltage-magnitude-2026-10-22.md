# Immutable voltage magnitude reuse screen

This isolated candidate reuses the magnitude of the same exact immutable Complex object in the two standard imported synchronous-machine classes. Voltage identity changes force recalculation; custom complex subclasses always execute their live abs method. The base multiplier is read and divided on every observation. Compensated sensing follows the original live calculation, and removing compensation allows ordinary sensing again. No integration stage, equation, limiter, derivative or network tolerance changes.

| Case | Control median, s | Candidate median, s | Median paired saving, s | Candidate faster |
| --- | ---: | ---: | ---: | ---: |
| 1 | 8.9395273 | 8.9821789 | 0.0859199 | 3/5 |
| 2 | 6.2820189 | 6.4248808 | -0.1190302 | 2/5 |

The candidate is not promoted. Case 1 has a higher candidate median and mixed paired results; Case 2 loses a median 0.1190302 seconds across pairs and wins only two of five. This does not establish a dependable cross-case gain. Removing repeated magnitude arithmetic alone does not close the Case 1 gap, and the extra cache/dispatch path is not justified by these complete-run measurements.

Sixteen alternating warmups precede five order-alternating measurement pairs in the same JVM per case. One logical CPU, Java sparse solving, 1/240-second steps, 1e-5 voltage-correction tolerance, one ST1 step, and the existing experimental network acceleration are retained. Complete-case timing includes fresh loading/preparation, initialization, simulation, full recording and writer closure, including compilation and garbage collection inside the timed case.

All 52 recordings match the retained baseline byte for byte. Enabled-model counts distinguish the arms; they are not cache-hit counts. Four focused tests cover 100,000 randomized complex values, repeated and replaced identities, zeros/subnormals/large/nonfinite values, live custom magnitude methods, both machine types, live base changes, rectangular-coordinate edits and polar conversion. A fully initialized small-system fixture checks compensated sensing, changed compensation, and return to ordinary sensing. Comparisons canonicalize NaNs. Initial fixture attachment and generic-type errors are retained alongside their corrections.

The screen itself does not promote production changes. Complete raw timings, pair differences, run statuses, commands, source/classes, test reports, runtime/input/output hashes and recording dependencies are retained. Equal baseline recordings do not establish independent-reference accuracy. Broader-case accuracy/performance and the complete eight-second objective remain unresolved.
