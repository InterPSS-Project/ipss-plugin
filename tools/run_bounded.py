"""Run a local command with bounded console capture and a free-space reserve."""
import argparse
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
import threading
import time

GIB = 1024 ** 3
MIB = 1024 ** 2


class WindowsJob:
    """Own only this run's processes, including descendants of exited parents."""

    def __init__(self):
        import ctypes
        from ctypes import wintypes

        class BasicLimits(ctypes.Structure):
            _fields_ = [("process_time", ctypes.c_int64), ("job_time", ctypes.c_int64),
                        ("flags", wintypes.DWORD), ("min_working_set", ctypes.c_size_t),
                        ("max_working_set", ctypes.c_size_t), ("active_processes", wintypes.DWORD),
                        ("affinity", ctypes.c_size_t), ("priority", wintypes.DWORD),
                        ("scheduling", wintypes.DWORD)]

        class IoCounters(ctypes.Structure):
            _fields_ = [(name, ctypes.c_uint64) for name in
                        ("reads", "writes", "other", "read_bytes", "write_bytes", "other_bytes")]

        class ExtendedLimits(ctypes.Structure):
            _fields_ = [("basic", BasicLimits), ("io", IoCounters),
                        ("process_memory", ctypes.c_size_t), ("job_memory", ctypes.c_size_t),
                        ("peak_process_memory", ctypes.c_size_t), ("peak_job_memory", ctypes.c_size_t)]

        self.api = ctypes.WinDLL("kernel32", use_last_error=True)
        for name, arguments, result in [
            ("CreateJobObjectW", [ctypes.c_void_p, wintypes.LPCWSTR], wintypes.HANDLE),
            ("SetInformationJobObject", [wintypes.HANDLE, ctypes.c_int, ctypes.c_void_p,
                                          wintypes.DWORD], wintypes.BOOL),
            ("AssignProcessToJobObject", [wintypes.HANDLE, wintypes.HANDLE], wintypes.BOOL),
            ("CloseHandle", [wintypes.HANDLE], wintypes.BOOL),
        ]:
            function = getattr(self.api, name)
            function.argtypes, function.restype = arguments, result
        self.handle = self.api.CreateJobObjectW(None, None)
        if not self.handle:
            raise ctypes.WinError(ctypes.get_last_error())
        limits = ExtendedLimits()
        limits.basic.flags = 0x2000  # JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
        if not self.api.SetInformationJobObject(self.handle, 9, ctypes.byref(limits),
                                               ctypes.sizeof(limits)):
            error = ctypes.WinError(ctypes.get_last_error())
            self.close()
            raise error

    def assign(self, process):
        import ctypes
        if not self.api.AssignProcessToJobObject(self.handle, int(process._handle)):
            raise ctypes.WinError(ctypes.get_last_error())

    def close(self):
        if self.handle:
            import ctypes
            if not self.api.CloseHandle(self.handle):
                raise ctypes.WinError(ctypes.get_last_error())
            self.handle = None


def stop_process_tree(process, job=None):
    if job is not None:
        job.close()
    else:
        import signal
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
    try:
        process.wait(timeout=3)
    except subprocess.TimeoutExpired:
        if os.name != "nt":
            import signal
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        else:
            process.kill()
        process.wait(timeout=3)


def run(command, log, min_free=2 * GIB, max_log=16 * MIB, free_bytes=None):
    """Keep the complete log up to its cap; a cap stops the command with failure."""
    if not command or min_free <= 0 or max_log <= 0:
        raise ValueError("A command and positive storage budgets are required")
    command = list(command)
    command[0] = shutil.which(command[0]) or command[0]
    log = Path(log).resolve()
    log.parent.mkdir(parents=True, exist_ok=True)
    free_bytes = free_bytes or (lambda: shutil.disk_usage(log.parent).free)
    if free_bytes() < min_free + max_log:
        raise OSError("Insufficient free space for the requested reserve and bounded log")
    status_path = log.with_name(log.name + ".status.json")
    if log.exists() or status_path.exists():
        raise FileExistsError("Refusing to overwrite an existing log or status")
    status = {"state": "starting", "min_free_bytes": min_free,
              "max_log_bytes": max_log, "captured_bytes": 0}
    with status_path.open("x") as initial_status:
        initial_status.write(json.dumps(status, indent=2) + "\n")
    stopped = threading.Event()
    failure = []
    process = None
    job = None
    try:
        with log.open("xb") as output:
            if os.name == "nt":
                job = WindowsJob()
                # The launcher cannot start the command until job assignment succeeds.
                # This avoids an uncontained descendant racing ahead of assignment.
                launcher = ("import json,subprocess,sys; "
                            "command=json.loads(sys.stdin.buffer.readline()); "
                            "sys.exit(subprocess.call(command, stdin=subprocess.DEVNULL))")
                process = subprocess.Popen([sys.executable, "-c", launcher],
                                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                           stdin=subprocess.PIPE,
                                           creationflags=subprocess.CREATE_NO_WINDOW)
                try:
                    job.assign(process)
                    process.stdin.write((json.dumps(command) + "\n").encode("utf-8"))
                    process.stdin.close()
                except BaseException:
                    process.kill()
                    process.wait(timeout=3)
                    process.stdin.close()
                    raise
            else:
                process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                           stdin=subprocess.DEVNULL, start_new_session=True)
            status["state"] = "running"
            status_path.write_text(json.dumps(status, indent=2) + "\n")

            def capture():
                try:
                    while True:
                        data = process.stdout.read1(65536)
                        if not data:
                            break
                        remaining = max_log - status["captured_bytes"]
                        output.write(data[:remaining])
                        output.flush()
                        status["captured_bytes"] += min(len(data), remaining)
                        if len(data) > remaining:
                            failure.append("Console output exceeded the log budget")
                            stopped.set()
                            break
                except (OSError, ValueError) as error:
                    failure.append("Console capture failed: " + str(error))
                    stopped.set()

            reader = threading.Thread(target=capture, daemon=True)
            reader.start()
            while process.poll() is None and not stopped.is_set():
                if free_bytes() < min_free:
                    failure.append("Free space fell below the reserved minimum")
                    stopped.set()
                    break
                time.sleep(.1)
            if stopped.is_set():
                stop_process_tree(process, job)
            else:
                process.wait()
            reader.join(timeout=5)
            # The reader can reach the budget after the command's parent exits.
            if stopped.is_set():
                stop_process_tree(process, job)
            if reader.is_alive():
                failure.append("A descendant retained the console pipe")
                stop_process_tree(process, job)
                reader.join(timeout=1)
            process.stdout.close()
            code = 75 if failure else process.returncode
            status.update(state="stopped" if failure else "completed", exit_code=code,
                          reason="; ".join(failure) if failure else None)
    except BaseException as error:
        if process is not None:
            stop_process_tree(process, job)
        status.update(state="failed", reason=str(error))
        raise
    finally:
        if job is not None:
            job.close()
        if process is not None:
            for pipe in (process.stdout, process.stdin):
                if pipe is not None:
                    pipe.close()
        status_path.write_text(json.dumps(status, indent=2) + "\n")
    return code


def positive_size(value):
    value = float(value)
    if not math.isfinite(value) or value <= 0:
        raise argparse.ArgumentTypeError("Storage budgets must be finite and positive")
    return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--min-free-gib", type=positive_size, default=2)
    parser.add_argument("--max-log-mib", type=positive_size, default=16)
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    try:
        return run(command, args.log, int(args.min_free_gib * GIB), int(args.max_log_mib * MIB))
    except (OSError, ValueError) as error:
        print("Run not completed: " + str(error), file=sys.stderr)
        return 75


if __name__ == "__main__":
    sys.exit(main())
