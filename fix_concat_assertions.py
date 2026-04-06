#!/usr/bin/env python3
"""Fix assertTrue/assertEquals where message is a string concatenation like ""+expr."""
import re
import os
import sys

def find_matching_paren(text, start):
    depth = 0
    i = start
    in_string = False
    while i < len(text):
        c = text[i]
        if in_string:
            if c == '\\':
                i += 1
            elif c == '"':
                in_string = False
        else:
            if c == '"':
                in_string = True
            elif c == '(':
                depth += 1
            elif c == ')':
                depth -= 1
                if depth == 0:
                    return i
        i += 1
    return -1

def find_first_comma_at_depth0(text):
    depth = 0
    in_string = False
    i = 0
    while i < len(text):
        c = text[i]
        if in_string:
            if c == '\\':
                i += 1
            elif c == '"':
                in_string = False
        else:
            if c == '"':
                in_string = True
            elif c == '(':
                depth += 1
            elif c == ')':
                depth -= 1
            elif c == ',' and depth == 0:
                return i
        i += 1
    return -1

def process_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
    original = content

    for method in ['assertTrue', 'assertEquals']:
        i = 0
        while True:
            pattern = method + '("'
            idx = content.find(pattern, i)
            if idx == -1:
                break
            paren_pos = idx + len(method)
            paren_end = find_matching_paren(content, paren_pos)
            if paren_end == -1:
                i = idx + 1
                continue
            inner = content[paren_pos + 1:paren_end]
            # Find the first top-level comma to split message from rest
            comma_pos = find_first_comma_at_depth0(inner)
            if comma_pos == -1:
                i = idx + 1
                continue
            msg_part = inner[:comma_pos].strip()
            rest_part = inner[comma_pos + 1:].strip()
            new_inner = rest_part + ', ' + msg_part
            new_call = method + '(' + new_inner + ')'
            content = content[:idx] + new_call + content[paren_end + 1:]
            i = idx + len(new_call)

    if content != original:
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"  Fixed: {filepath}")

files = [
    "ipss.test.plugin.core/src/test/java/org/interpss/core/aclf/svc/IEEE14_PVLimit_SVCTest.java",
    "ipss.test.plugin.core/src/test/java/org/interpss/core/zeroz/IEEE14ZeroZBranchAclfTest.java",
    "ipss.test.plugin.core/src/test/java/org/interpss/core/dclf/Dclf_PSSE_ACTIVSg25kBus_Test.java",
    "ipss.test.plugin.core/src/test/java/org/interpss/core/adapter/psse/compare/PSSE_ACTIVSg2000BusCompare_Test.java",
    "ipss.test.plugin.core/src/test/java/org/interpss/core/adapter/psse/raw/aclf/PSSE_IEEE9Bus_Test.java",
    "ipss.test.plugin.core/src/test/java/org/interpss/core/adapter/psse/raw/aclf/Kundur_2Area_LCCHVDC2T_Test.java",
    "ipss.test.plugin.core/src/test/java/org/interpss/core/dstab/mach/SMIB_Gen_Test.java",
    "ipss.test.plugin.core/src/test/java/org/interpss/plugin/optadj/IEEE14_OptAdj_N1Scan_Test.java",
    "ipss.test.plugin.core/src/test/java/org/interpss/plugin/optadj/IEEE14_OptAdj_N1ScanSSAResult_Test.java",
    "ipss.test.plugin.core/src/test/java/org/interpss/plugin/optadj/IEEE14_OptAdj_SelOutge_Test.java",
    "ipss.test.plugin.core/src/test/java/org/interpss/plugin/optadj/IEEE14_OptAdj_SelOutge1_Test.java",
]

base = "/Users/mzhou/Documents/wspace/gitRepo/ipss-plugin"
for f in files:
    process_file(os.path.join(base, f))
