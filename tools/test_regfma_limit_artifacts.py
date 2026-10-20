"""Synthetic negative controls; these fixtures are not simulator trajectories."""
import csv
import json
from pathlib import Path
import tempfile
import unittest
from check_regfma_limit_artifacts import verify, HEADERS, FILES
from regfma_limit_probe import configuration
from genrou_native_probe import f32
from hplntd_fault_probe import sha

class RegfmaLimitArtifactTest(unittest.TestCase):
    def fixture(self,folder):
        cfg=configuration('lower-shunt');lower=f32(1.075)
        for name in FILES:(folder/name).write_text('synthetic\n')
        (folder/'dynamic.dyr').write_text("1 'REGFMA1' '1' 1 1 "+' '.join(map(str,cfg['constants']))+' /\n')
        (folder/'native.log').write_text('Initial conditions check O.K.\n')
        live=[dict(stage=s,time_s=0,states=[0]*9,variables=[0]*8) for s in
            ('initialized','interval-end','post-shunt-on','interval-end','post-shunt-off','interval-end')]
        (folder/'live-states.json').write_text(json.dumps(live))
        times=[-.001,-.0005]+[i*.0005 for i in range(601)]+[.05,.1];times.sort()
        with (folder/'native.csv').open('w',newline='') as stream:
            writer=csv.writer(stream);writer.writerow(HEADERS)
            for t in times:
                row={k:0 for k in HEADERS};row['time_s']=t
                row.update(STATE_8=lower if .05<=t<=.1 else 1.077,STATE_5=1.02,
                           VAR_0=1.,V_BUS2=1.04 if .05<t<.1 else 1.01)
                writer.writerow([row[k] for k in HEADERS])
        m=dict(status='native-converged-profile',model='REGFMA1',profile='lower-shunt',configuration=cfg,
            trajectory_acceptance=False,step_s=.0005,actual_constants=list(map(f32,cfg['constants'])),
            final_constants=list(map(f32,cfg['constants'])),actual_icons=[1,1],
            allocation=dict(NICON=2,NCON=18,NSTATE=9,NVAR=8),sample_count=605,channel_count=24,
            events=[dict(action=a,nominal_s=t,actual_s=t) for a,t in (('shunt-on',.05),('shunt-off',.1))],
            library_sha256='0'*64,source_document_sha256='40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d',
            python_sha256='0'*64,wrapper_sha256='0'*64,files={n:sha(folder/n) for n in FILES})
        (folder/'manifest.json').write_text(json.dumps(m));verify(folder);return m

    def rejected(self,mutate):
        with tempfile.TemporaryDirectory() as directory:
            folder=Path(directory);m=self.fixture(folder);mutate(folder,m)
            (folder/'manifest.json').write_text(json.dumps(m))
            with self.assertRaises((AssertionError,ValueError)):verify(folder)

    def test_valid_synthetic_failure_controls_start_valid(self):
        with tempfile.TemporaryDirectory() as directory:self.fixture(Path(directory))
    def test_changed_unhashed_file_is_rejected(self):
        self.rejected(lambda p,m:(p/'native.out').write_text('changed'))
    def test_acceptance_promotion_is_rejected(self):
        self.rejected(lambda p,m:m.update(trajectory_acceptance=True))
    def test_changed_declared_profile_is_rejected(self):
        self.rejected(lambda p,m:m['configuration']['constants'].__setitem__(5,1.09))
    def test_wrong_allocation_is_rejected(self):
        self.rejected(lambda p,m:m['allocation'].update(NSTATE=8))
    def test_wrong_actual_constant_is_rejected(self):
        self.rejected(lambda p,m:m['actual_constants'].__setitem__(5,1.09))
    def test_missing_file_contract_is_rejected(self):
        self.rejected(lambda p,m:m['files'].pop('native.out'))
    def test_suspect_initialization_is_rejected_even_with_ok_marker(self):
        def change(p,m):
            (p/'native.log').write_text('Initial conditions check O.K.\nInitial conditions suspect:\n')
            m['files']['native.log']=sha(p/'native.log')
        self.rejected(change)
    def test_network_failure_is_rejected_even_with_ok_marker(self):
        def change(p,m):
            (p/'native.log').write_text('Initial conditions check O.K.\nNetwork not converged\n')
            m['files']['native.log']=sha(p/'native.log')
        self.rejected(change)
    def test_event_clock_mutation_is_rejected(self):
        self.rejected(lambda p,m:m['events'][0].update(actual_s=.051))
    def test_missing_state_slots_are_rejected(self):
        def change(p,m):
            live=json.loads((p/'live-states.json').read_text());live[0]['states'].pop()
            (p/'live-states.json').write_text(json.dumps(live));m['files']['live-states.json']=sha(p/'live-states.json')
        self.rejected(change)
    def test_configuration_is_fresh_and_unknown_profiles_fail(self):
        a=configuration('lower-shunt');a['constants'][5]=0
        self.assertEqual(1.075,configuration('lower-shunt')['constants'][5])
        with self.assertRaises(ValueError):configuration('unknown')

    def csv_mutation(self,change):
        def mutate(p,m):
            with (p/'native.csv').open(newline='') as f:rows=list(csv.reader(f))
            change(rows)
            with (p/'native.csv').open('w',newline='') as f:csv.writer(f).writerows(rows)
            m['files']['native.csv']=sha(p/'native.csv')
        self.rejected(mutate)

    def test_nonfinite_channel_cannot_be_hidden_by_updated_hash(self):
        self.csv_mutation(lambda rows:rows[10].__setitem__(9,'NaN'))
    def test_removed_history_row_is_rejected(self):
        self.csv_mutation(lambda rows:rows.pop(1))
    def test_missing_lower_bound_activity_is_rejected(self):
        self.csv_mutation(lambda rows:[r.__setitem__(9,'1.077') for r in rows[1:]])
    def test_missing_inward_release_is_rejected(self):
        self.csv_mutation(lambda rows:rows[-1].__setitem__(9,str(f32(1.075))))

if __name__=='__main__':unittest.main()
