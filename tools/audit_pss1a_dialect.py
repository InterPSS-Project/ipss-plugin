"""Fixed PSS1A/IEEEST specialization of the cold allocation evidence auditor."""
import json
import sys
from audit_psssb_dialect import audit, verify

CONTRACT = dict(unknown_model='PSS1A', native_model='IEEEST',
                native_layout=(2, 17, 7, 2), helper_files=('psssb_dialect_probe.py',))


def verify_pss1a(folder):
    return verify(folder, **CONTRACT)


def audit_pss1a(folders):
    return audit(folders, **CONTRACT)


if __name__ == '__main__':
    print(json.dumps(audit_pss1a(sys.argv[1:]), indent=2))
