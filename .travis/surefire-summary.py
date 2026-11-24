"""Print per-test skip reasons and the overall totals from target/surefire-reports.

JUnit 4 @Ignore without a message records a bare <skipped/>, so for those the
matching @Ignore/assume line from the test source is printed. A class disabled
by a class-level @Ignore is reported by surefire as one testcase whose name
equals its classname.
"""
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET


def read_source(classname):
    path = os.path.join("src/test/java", classname.split("$")[0].replace(".", "/") + ".java")
    try:
        with open(path, encoding="utf-8") as f:
            return path, f.readlines()
    except OSError:
        return path, None


def source_reason(classname, method):
    path, lines = read_source(classname)
    if lines is None:
        return None
    simple = classname.split(".")[-1].split("$")[-1]
    if method == classname or method == simple or not method:
        for i, line in enumerate(lines):
            if re.search(r"\bclass\s+%s\b" % re.escape(simple), line):
                for prev in lines[max(0, i - 4):i]:
                    if "@Ignore" in prev:
                        return "whole class disabled upstream by %s at %s:%d" % (
                            prev.strip(), path, lines.index(prev) + 1)
        return None
    name = re.sub(r"\[.*$", "", method)
    for i, line in enumerate(lines):
        if re.search(r"\bvoid\s+%s\s*\(" % re.escape(name), line):
            for j in range(max(0, i - 4), i):
                if "@Ignore" in lines[j]:
                    return "disabled upstream by %s at %s:%d" % (lines[j].strip(), path, j + 1)
            for body in lines[i + 1:i + 40]:
                if re.search(r"assume", body, re.IGNORECASE):
                    return "assumption not met: %s" % body.strip()
    return None


tests = failures = errors = skipped = 0
skips = []
for path in sorted(glob.glob("target/surefire-reports/TEST-*.xml")):
    root = ET.parse(path).getroot()
    tests += int(root.get("tests", 0))
    failures += int(root.get("failures", 0))
    errors += int(root.get("errors", 0))
    skipped += int(root.get("skipped", 0))
    for case in root.iter("testcase"):
        sk = case.find("skipped")
        if sk is None:
            continue
        message = (sk.get("message") or "").strip()
        if not message:
            message = source_reason(case.get("classname", ""), case.get("name", "")) or "(no message)"
        skips.append("%s.%s: %s" % (case.get("classname"), case.get("name"), message))

print("Skipped tests (%d):" % len(skips))
for line in skips:
    print("  SKIPPED " + line)
print("Surefire totals: tests=%d passed=%d failures=%d errors=%d skipped=%d"
      % (tests, tests - failures - errors - skipped, failures, errors, skipped))
sys.exit(0 if tests > 0 and failures == 0 and errors == 0 else 1)
