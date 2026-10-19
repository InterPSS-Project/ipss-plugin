"""Measurement profiles change selectors, not physical model parameters."""
from pathlib import Path
import unittest
from ieeest_measurement_probe import configuration
from ieeest_input_probe import configuration as power_configuration


class IeeestMeasurementProbeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw = (Path(__file__).resolve().parents[1] / 'ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()

    def test_only_signal_and_measurement_bus_change(self):
        for signal in ('frequency', 'voltage'):
            for location in ('local', 'remote'):
                for event in ('flat', 'fault'):
                    cfg = configuration('-'.join((signal, location, event)), self.raw)
                    base = power_configuration('accelerating-' + event, self.raw)
                    self.assertEqual(base['raw'], cfg['raw'])
                    self.assertEqual(base['constants'], cfg['constants'])
                    self.assertEqual(base['apply_fault'], cfg['apply_fault'])
                    self.assertEqual((1, 2), cfg['frequency_buses'])
                    for actual, expected in zip(cfg['components'], base['components']):
                        self.assertEqual(expected['constants'], actual['constants'])
                        self.assertEqual(expected['states'], actual['states'])
                    icons = [2 if signal == 'frequency' else 5, 0 if location == 'local' else 2]
                    self.assertEqual(icons, cfg['components'][-1]['icons'])
                    fields = cfg['dynamic'].splitlines()[-1].split()
                    self.assertEqual(23, len(fields))
                    self.assertEqual(list(map(str, icons)), fields[3:5])
                    self.assertNotIn('variables', cfg['components'][-1])

    def test_unknown_measurements_are_rejected(self):
        for profile in ('derivative-local-fault', 'frequency-missing-flat', 'voltage-local-fit'):
            with self.assertRaises(ValueError):
                configuration(profile, self.raw)


if __name__ == '__main__':
    unittest.main()
