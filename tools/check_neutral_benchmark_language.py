"""Check documentation and newly introduced Java files for neutral benchmark prose.

Existing Java paths at the policy baseline are grandfathered. Class names,
package names, protocol identifiers and resource paths are not renamed.
Run from any directory with Python 3 and Git; no simulator or Maven is needed.
"""

from pathlib import Path
import re
import subprocess
import sys


JAVA_BASELINE = "8e7337574b69eb332991a0c539ef0a0a18662bbf"
VENDOR_WORDING = re.compile(r"PSS(?:/|\N{REGISTERED SIGN})E", re.IGNORECASE)
DOC_SUFFIXES = {".md", ".markdown", ".rst", ".adoc"}


def is_document(path):
    item = Path(path)
    return item.suffix.lower() in DOC_SUFFIXES or (
        item.suffix.lower() in {".txt", ".html", ".htm"}
        and (len(item.parts) == 1 or "docs" in item.parts)
    )


def violations(path, content, existing_java):
    if not is_document(path) and not (
        path.endswith(".java") and path not in existing_java
    ):
        return []
    return [
        f"{path}:{number}: use neutral benchmark/reference-simulator wording"
        for number, line in enumerate(content.splitlines(), 1)
        if VENDOR_WORDING.search(line)
    ]


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args])


def check(root):
    # Full history is required in CI so the explicit grandfathering boundary
    # cannot silently advance whenever another Java file is introduced.
    existing = set(git(root, "ls-tree", "-r", "--name-only", JAVA_BASELINE)
                   .decode("utf-8").splitlines())
    candidates = set(git(root, "ls-files", "-z", "--cached", "--others",
                         "--exclude-standard").decode("utf-8").split("\0"))
    errors = []
    checked = 0
    for relative in sorted(candidates - {""}):
        path = root / relative
        if not path.is_file() or not (
            is_document(relative) or
            relative.endswith(".java") and relative not in existing
        ):
            continue
        checked += 1
        errors.extend(violations(relative, path.read_text(encoding="utf-8-sig"), existing))
    return checked, errors


def main():
    root = Path(subprocess.check_output(
        ["git", "rev-parse", "--show-toplevel"], text=True).strip())
    checked, errors = check(root)
    for error in errors:
        print(error)
    print(f"Neutral benchmark language: {checked} documents/new Java files checked; "
          f"{len(errors)} violations.")
    return bool(errors)


if __name__ == "__main__":
    sys.exit(main())
