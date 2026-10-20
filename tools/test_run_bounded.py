import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

import run_bounded


class BoundedRunTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.log = Path(self.directory.name) / "run.log"

    def run_command(self, code, **options):
        return run_bounded.run([sys.executable, "-c", code], self.log,
                               min_free=1, max_log=1024, **options)

    def test_preserves_complete_small_output_and_exit_code(self):
        self.assertEqual(7, self.run_command("print('complete'); raise SystemExit(7)"))
        self.assertEqual("complete\n", self.log.read_text())
        status = json.loads(self.log.with_name("run.log.status.json").read_text())
        self.assertEqual("completed", status["state"])
        self.assertEqual(7, status["exit_code"])

    def test_stops_noisy_process_and_caps_disk_bytes(self):
        self.assertEqual(75, self.run_command("import sys,time; sys.stdout.write('x'*100000); sys.stdout.flush(); time.sleep(60)"))
        self.assertEqual(1024, self.log.stat().st_size)
        status = json.loads(self.log.with_name("run.log.status.json").read_text())
        self.assertEqual("stopped", status["state"])
        self.assertIn("log budget", status["reason"])

    @unittest.skipUnless(os.name == "nt", "Windows job ownership")
    def test_budget_stops_descendant_after_its_parent_exits(self):
        pid_file = Path(self.directory.name) / "child.pid"
        child = ("import os,sys,time; from pathlib import Path; "
                 f"Path({str(pid_file)!r}).write_text(str(os.getpid())); "
                 "time.sleep(.3); sys.stdout.write('x'*100000); sys.stdout.flush(); time.sleep(60)")
        parent = ("import subprocess,sys; "
                  f"subprocess.Popen([sys.executable, '-c', {child!r}])")
        self.assertEqual(75, self.run_command(parent))
        self.assertEqual(1024, self.log.stat().st_size)
        self.assert_process_stopped(int(pid_file.read_text()))

    @unittest.skipUnless(os.name == "nt", "Windows job ownership")
    def test_budget_stops_descendant_while_parent_is_running(self):
        pid_file = Path(self.directory.name) / "child.pid"
        child = ("import os,time; from pathlib import Path; "
                 f"Path({str(pid_file)!r}).write_text(str(os.getpid())); time.sleep(60)")
        parent = ("import subprocess,sys,time; from pathlib import Path; "
                  f"subprocess.Popen([sys.executable, '-c', {child!r}]); "
                  f"p=Path({str(pid_file)!r})\n"
                  "while not p.exists(): time.sleep(.01)\n"
                  "sys.stdout.write('x'*100000); sys.stdout.flush(); time.sleep(60)")
        self.assertEqual(75, self.run_command(parent))
        self.assert_process_stopped(int(pid_file.read_text()))

    @unittest.skipUnless(os.name == "nt", "Windows job ownership")
    def test_job_assignment_failure_never_launches_command(self):
        marker = Path(self.directory.name) / "should-not-exist"
        with patch.object(run_bounded.WindowsJob, "assign", side_effect=OSError("assignment failed")):
            with self.assertRaisesRegex(OSError, "assignment failed"):
                self.run_command(f"from pathlib import Path; Path({str(marker)!r}).touch()")
        self.assertFalse(marker.exists())

    def assert_process_stopped(self, pid):
        import ctypes
        from ctypes import wintypes
        api = ctypes.WinDLL("kernel32", use_last_error=True)
        api.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        api.OpenProcess.restype = wintypes.HANDLE
        api.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
        api.WaitForSingleObject.restype = wintypes.DWORD
        api.TerminateProcess.argtypes = [wintypes.HANDLE, wintypes.UINT]
        api.CloseHandle.argtypes = [wintypes.HANDLE]
        handle = api.OpenProcess(0x100001, False, pid)  # SYNCHRONIZE | PROCESS_TERMINATE
        if not handle:
            self.assertEqual(87, ctypes.get_last_error())  # Process already removed.
            return
        try:
            result = api.WaitForSingleObject(handle, 3000)
            if result != 0:
                api.TerminateProcess(handle, 99)  # Cleanup this test's known child on failure.
            self.assertEqual(0, result, "Descendant survived the bounded run")
        finally:
            api.CloseHandle(handle)

    def test_low_disk_never_launches_command(self):
        with patch.object(run_bounded.subprocess, "Popen") as launch:
            with self.assertRaises(OSError):
                self.run_command("raise SystemExit(99)", free_bytes=lambda: 0)
            launch.assert_not_called()
        self.assertFalse(self.log.exists())

    def test_disk_falling_during_run_stops_process(self):
        readings = iter([10000, 0])
        self.assertEqual(75, self.run_command("import time; time.sleep(60)",
                         free_bytes=lambda: next(readings, 0)))
        status = json.loads(self.log.with_name("run.log.status.json").read_text())
        self.assertIn("reserved minimum", status["reason"])

    def test_existing_log_is_never_overwritten(self):
        self.log.write_text("reference bytes")
        with self.assertRaises(FileExistsError):
            self.run_command("print('replacement')")
        self.assertEqual("reference bytes", self.log.read_text())

    def test_rejects_invalid_limits(self):
        for value in ["nan", "inf", "0", "-1"]:
            with self.assertRaises(Exception):
                run_bounded.positive_size(value)

    def test_existing_status_is_never_overwritten(self):
        status = self.log.with_name("run.log.status.json")
        status.write_text("keep this status")
        with self.assertRaises(FileExistsError):
            self.run_command("print('replacement')")
        self.assertEqual("keep this status", status.read_text())
        self.assertFalse(self.log.exists())


if __name__ == "__main__":
    unittest.main()
