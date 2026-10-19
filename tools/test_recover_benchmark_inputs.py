"""Content identity, containment and atomic recovery regression guards."""
import base64
from pathlib import Path
import subprocess
import tempfile
import unittest
from recover_benchmark_inputs import canonical,digest,find,recover,relative_path


class RecoveryTest(unittest.TestCase):
    def fixture(self,root):
        def git(*args):return subprocess.check_output(['git',*args],cwd=root,stderr=subprocess.DEVNULL)
        git('init','-q');(root/'marker.txt').write_text('fixture')
        git('add','marker.txt');git('-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','fixture')
        return git

    def test_text_newlines_are_the_only_hash_normalization(self):
        self.assertEqual(b'a\nb\nc\n',canonical(b'a\r\nb\rc\n','x.dyr'))
        self.assertEqual(b'a\r\n',canonical(b'a\r\n','x.bin'))
        self.assertNotEqual(digest(b'a\n'),digest(b'a'))

    def test_unsafe_input_paths_are_rejected(self):
        for path in ['', '.', '../x', '/x', 'C:/x', 'a/../x']:
            with self.assertRaises(ValueError):relative_path(path)

    def test_pinned_worktree_content_is_not_a_nearest_name_match(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);self.fixture(root);(root/'input.dyr').write_bytes(b'exact\r\n')
            body,source=find(root,'input.dyr',digest(b'exact\n'));self.assertEqual(b'exact\r\n',body);self.assertEqual('pinned-worktree',source['source'])
            with self.assertRaises(ValueError):find(root,'input.dyr',digest(b'nearby\n'))

    def test_deleted_input_is_recovered_only_from_exact_git_content(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);git=self.fixture(root);(root/'old.dyr').write_bytes(b'old\n')
            git('-c','core.autocrlf=false','add','old.dyr');git('-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','old')
            (root/'old.dyr').unlink();git('add','-A');git('-c','user.name=Fixture','-c','user.email=fixture@example.invalid','commit','-qm','removed')
            body,source=find(root,'old.dyr',digest(b'old\n'));self.assertEqual(b'old\n',body);self.assertEqual('history',source['source']);self.assertTrue(source['git_blob'])

    def test_assets_are_content_verified_not_trusted_by_filename(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);assets=root/'assets';assets.mkdir();expected=digest(b'expected\n')
            (assets/(expected+'.b64')).write_bytes(base64.b64encode(b'wrong\n'))
            with self.assertRaises(ValueError):find(root,'missing.dyr',expected,assets)

    def test_missing_role_cannot_leave_a_partial_recovery(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);self.fixture(root);(root/'good.raw').write_bytes(b'good')
            catalog={'benchmarks':[{'name':'case','inputs':{'raw':{'path':'good.raw','sha256':digest(b'good')},'dyr':{'path':'missing.dyr','sha256':digest(b'missing')}}}]}
            output=root/'target'/'new'
            with self.assertRaises(ValueError):recover(root,catalog,output)
            self.assertFalse(output.exists())

    def test_recovery_cannot_escape_or_overwrite_output(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);self.fixture(root);catalog=dict(benchmarks=[])
            with self.assertRaises(ValueError):recover(root,catalog,root/'elsewhere')
            output=root/'target'/'exists';output.mkdir(parents=True)
            with self.assertRaises(FileExistsError):recover(root,catalog,output)

    def test_asset_only_workspace_retains_exact_input_paths(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);self.fixture(root);assets=root/'assets';assets.mkdir();body=b'1 MODEL 0 /\n';expected=digest(body)
            (assets/(expected+'.b64')).write_bytes(base64.b64encode(body))
            catalog=dict(benchmarks=[dict(name='case',inputs=dict(dyr=dict(path='old/input.dyr',sha256=expected)))])
            output=root/'target'/'new';result=recover(root,catalog,output,assets)
            self.assertEqual(body,(output/'case/workspace/old/input.dyr').read_bytes());self.assertEqual('complete',result['status'])
            self.assertEqual('content-addressed-asset',result['benchmarks'][0]['inputs']['dyr']['source'])


if __name__=='__main__':unittest.main()
