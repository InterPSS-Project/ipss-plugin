"""Offline guards for source-specific PSS5C probe schemas and discriminators."""
from pathlib import Path
import unittest
import tempfile
from unittest.mock import patch
import pss5c_native_probe as probe
from analyze_pss5c_native_replay import compare, CHANNELS
from pss5c_native_probe import configuration,PSS_CON


class Pss5cNativeProbeTest(unittest.TestCase):
    def raw(self):
        return Path('ipss.test.plugin.core/testData/adpter/psse/v33/SMIB/SMIB_v33.raw').read_text()

    def test_record_has_thirty_constants_and_no_selector(self):
        cfg=configuration('flat',self.raw())
        fields=next(line.split() for line in cfg['dynamic'].splitlines() if "'PSS5C'" in line)
        self.assertEqual(30,len(fields[3:-1]));self.assertEqual(list(PSS_CON),list(map(float,fields[3:-1])))
        self.assertEqual([0],cfg['components'][1]['icons'],'Internal allocation is not an input field')

    def test_discriminators_change_only_declared_constants(self):
        baseline=list(PSS_CON)
        for mode,expected in [('transducer-fault',{0,1,2,4,5,6,7}),('gain-fault',{3,8})]:
            changed=configuration(mode,self.raw())['components'][1]['constants']
            self.assertEqual(expected,{i for i,(a,b) in enumerate(zip(baseline,changed)) if a!=b})
        self.assertEqual(baseline,list(PSS_CON))

    def test_native_states_and_variables_are_all_recorded(self):
        spec=configuration('fault',self.raw())['components'][1]
        self.assertEqual(13,len(spec['states']));self.assertEqual(6,len(spec['variables']))
        self.assertEqual(13,len(set(spec['states'])))

    def test_unknown_profile_is_not_a_silent_fallback(self):
        with self.assertRaises(AssertionError):configuration('unmapped',self.raw())

    def test_single_band_and_unit_frequency_discriminators_are_explicit(self):
        cfg=configuration('very-low-fault',self.raw())['components'][1]['constants']
        self.assertEqual({13,17,21},{i for i,(a,b) in enumerate(zip(PSS_CON,cfg)) if a!=b})
        for mode,expected in [('unit-frequency-fault',{10,14,18,22}),
                              ('unit-frequency-no-dli-fault',{0,10,14,18,22})]:
            cfg=configuration(mode,self.raw())['components'][1]['constants']
            self.assertEqual(expected,{i for i,(a,b) in enumerate(zip(PSS_CON,cfg)) if a!=b})

    def write_replay_pair(self,folder):
        times=[-.001,0,.05,.05,.1]
        native=['time_s,'+','.join(raw for _,raw in CHANNELS)]
        actual=['time_s,'+','.join(key for key,_ in CHANNELS)]
        for time in times:
            native.append(str(time)+','+','.join(['1']*7))
            actual.append(str(time)+','+','.join(['.5']*7))
        (folder/'native.csv').write_text('\n'.join(native)+'\n')
        replay=folder/'replay.csv';replay.write_text('\n'.join(actual)+'\n')
        return replay

    def test_replay_preserves_history_and_duplicate_rows_without_acceptance(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);replay=self.write_replay_pair(folder);report=compare(folder,replay)
            self.assertEqual(5,report['rows']);self.assertEqual(1,report['negative_history_rows'])
            self.assertEqual(1,report['duplicate_event_rows']);self.assertFalse(report['trajectory_acceptance'])
            self.assertEqual(.5,report['channels']['Vstab']['max_abs_error'])

    def test_replay_missing_rows_cannot_pass(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);replay=self.write_replay_pair(folder)
            replay.write_text('\n'.join(replay.read_text().splitlines()[:-1])+'\n')
            with self.assertRaises(AssertionError):compare(folder,replay)

    def test_replay_cannot_align_by_a_fitted_time_shift(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);replay=self.write_replay_pair(folder)
            replay.write_text(replay.read_text().replace('0,.5','0.001,.5'))
            with self.assertRaises(AssertionError):compare(folder,replay)

    def test_replay_nonfinite_outputs_are_rejected(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);replay=self.write_replay_pair(folder)
            replay.write_text(replay.read_text().replace(',.5',',NaN',1))
            with self.assertRaises(AssertionError):compare(folder,replay)

    def test_reusing_a_run_tag_cannot_reclassify_or_overwrite_old_evidence(self):
        with tempfile.TemporaryDirectory() as name:
            folder=Path(name);run=folder/'target/pss5c-native-existing';run.mkdir(parents=True)
            (run/'native.log').write_text('original');(run/'rejected-run.json').write_text('original metadata')
            before={p.name:p.read_bytes() for p in run.iterdir()}
            with patch.object(probe.sys,'argv',['probe','existing','.0005','flat']),patch.object(probe.Path,'cwd',return_value=folder):
                with self.assertRaises(FileExistsError):probe.main()
            self.assertEqual(before,{p.name:p.read_bytes() for p in run.iterdir()})


if __name__=='__main__':unittest.main()
