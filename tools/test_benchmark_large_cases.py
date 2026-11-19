import unittest
import subprocess
import sys
import uuid
from pathlib import Path
import numpy as np
from scipy.sparse import coo_matrix
from benchmark_large_cases import canonical_tables, jacobian, reference_solve, residual


class LargeCaseEquationsTest(unittest.TestCase):
    def setUp(self):
        self.pv, self.pq = np.array([1]), np.array([2])
        series = np.array([3-15j, 2-10j, 4-20j])
        f, t = np.array([0, 0, 1]), np.array([1, 2, 2])
        phase, tap = np.array([.13, -.08, .04]), np.array([1.1, .9, 1.])
        a = -series / tap * np.exp(1j * phase)
        b = -series / tap * np.exp(-1j * phase)
        diagonal = np.array([.02+.03j, 0-.04j, .01+.05j])
        np.add.at(diagonal, f, series/tap**2)
        np.add.at(diagonal, t, series)
        self.edge = f, t, a, b
        self.bus = dict(type=np.array([3, 2, 1]), desired_vm=np.array([1.03, 1.02, 1.]),
                        yii_real=diagonal.real, yii_imag=diagonal.imag)
        self.y = coo_matrix((np.r_[diagonal, a, b],
            (np.r_[np.arange(3), f, t], np.r_[np.arange(3), t, f])), (3, 3)).tocsr()
        self.v = np.array([1.03, 1.02*np.exp(-.03j), .98*np.exp(-.06j)])
        self.s = self.v * np.conj(self.y @ self.v)

    def test_jacobian_against_independent_finite_differences(self):
        analytic = jacobian(self.y, self.v, self.pv, self.pq).toarray()
        numerical = np.empty_like(analytic)
        for k in range(3):
            def shifted(h):
                vm, va = abs(self.v), np.angle(self.v)
                if k < 2:
                    va[k+1] += h
                else:
                    vm[2] += h
                return residual(self.y, self.s, vm*np.exp(1j*va), self.pv, self.pq)
            numerical[:, k] = (shifted(1e-6) - shifted(-1e-6)) / 2e-6
        np.testing.assert_allclose(analytic, numerical, rtol=1e-8, atol=1e-8)

    def test_normalization_retains_taps_phases_shunts_and_injections(self):
        buses, generators, branches = canonical_tables(self.bus, self.edge, self.s, 100.)
        rebuilt = np.diag((buses[:, 4] + 1j*buses[:, 5]) / 100.)
        for row in branches:
            f, t = row[:2].astype(int)
            y = 1/complex(row[2], row[3])
            phase = np.deg2rad(row[9])
            rebuilt[f, f] += y
            rebuilt[t, t] += y
            rebuilt[f, t] += -y*np.exp(1j*phase)
            rebuilt[t, f] += -y*np.exp(-1j*phase)
        np.testing.assert_allclose(rebuilt, self.y.toarray(), atol=1e-13)
        np.testing.assert_allclose(-(buses[:, 2]+1j*buses[:, 3])/100, self.s)
        np.testing.assert_array_equal(generators[:, 0], [0, 1])

    def test_independent_newton_converges_to_known_solution(self):
        v, history = reference_solve(self.y, self.s, np.array([1.03, 1.02, 1.], dtype=complex), self.pv, self.pq)
        self.assertGreater(len(history), 1)
        np.testing.assert_allclose(v, self.v, atol=1e-9)

    def test_rejects_asymmetric_two_port_magnitudes(self):
        f, t, a, b = self.edge
        with self.assertRaisesRegex(ValueError, "asymmetric"):
            canonical_tables(self.bus, (f, t, a, b*1.1), self.s, 100.)

    def test_legacy_cli_rejects_unmatched_large_case_profile(self):
        unused = Path(__file__).resolve().parents[1] / "target" / ("unused-cli-" + uuid.uuid4().hex)
        result = subprocess.run([sys.executable, str(Path(__file__).with_name("rustpower_benchmark.py")),
                "--output", str(unused), "--phase", "all", "--cases", "activsg70k"],
                capture_output=True, text=True)
        self.assertEqual(result.returncode, 2)
        self.assertIn("benchmark_prepared_newton.py", result.stderr)
        self.assertFalse(unused.exists())


if __name__ == "__main__":
    unittest.main()
