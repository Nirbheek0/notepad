#!/usr/bin/env python3
"""Normalise Android lint / detekt reports and diff PR findings against a baseline.

Used by .github/workflows/universal-pr-check.yml. Two subcommands:

  collect  parse one or more report files into a normalised JSON list of findings
  compare  diff a base findings list against a head findings list and emit Markdown

A finding is identified by the stable key (tool, rule, file, line); its message is
carried as detail. A head finding that keeps the same (tool, rule, message) but
sits at a different location is reported as *moved* rather than being silently
counted as one resolved finding plus one new one, so relocations and
reintroductions are not missed.

Report formats understood (auto-detected from the root element):

  Android lint      <issues><issue id=.. severity=.. message=..><location file=.. line=.. column=../></issue></issues>
  detekt (checkstyle) <checkstyle><file name=..><error line=.. column=.. severity=.. message=.. source=detekt.Rule/></file></checkstyle>
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import xml.etree.ElementTree as ET

SEVERITY_ICON = {"error": "🔴", "warning": "🟡", "info": "⚪"}
DETAIL_LIMIT = 50
SEVERITY_RANK = {"error": 0, "warning": 1, "info": 2}

# Directories that mark the top of a source module, used to trim absolute paths
# (lint and detekt both emit absolute file names when no base path is configured).
_MODULE_ROOTS = ("app", "core", "data", "domain", "features")


def _to_int(value) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def _clean(text) -> str:
    """Collapse whitespace/newlines so a finding fits on one Markdown line."""
    return " ".join(str(text or "").split())


def relpath(path, root) -> str:
    """Return a repo-relative, POSIX-style path for a reported file."""
    if not path:
        return ""
    p = str(path).replace("\\", "/")
    if root:
        prefix = str(root).replace("\\", "/").rstrip("/") + "/"
        if p.startswith(prefix):
            p = p[len(prefix):]
    if p.startswith("/"):
        parts = p.split("/")
        for marker in _MODULE_ROOTS:
            if marker in parts:
                p = "/".join(parts[parts.index(marker):])
                break
    return p


def parse_report(path, root):
    """Parse one lint or detekt XML report into a list of normalised findings."""
    findings = []
    try:
        tree = ET.parse(path)
    except Exception as exc:  # noqa: BLE001 - report a warning, keep going
        print(f"::warning::could not parse report {path}: {exc}", file=sys.stderr)
        return findings

    r = tree.getroot()
    tag = r.tag.split("}")[-1]

    if tag == "issues":  # Android lint
        for issue in r.iter("issue"):
            sev = (issue.get("severity") or "warning").lower()
            sev = {"fatal": "error", "error": "error",
                   "warning": "warning", "information": "info"}.get(sev, sev)
            loc = issue.find("location")
            findings.append({
                "tool": "lint",
                "rule": issue.get("id") or "lint",
                "severity": sev,
                "file": relpath(loc.get("file") if loc is not None else "", root),
                "line": _to_int(loc.get("line") if loc is not None else None),
                "column": _to_int(loc.get("column") if loc is not None else None),
                "message": _clean(issue.get("message")),
            })
    elif tag == "checkstyle":  # detekt
        for fe in r.iter("file"):
            f = relpath(fe.get("name"), root)
            for err in fe.iter("error"):
                source = err.get("source") or "detekt"
                findings.append({
                    "tool": "detekt",
                    "rule": source.split(".")[-1],
                    "severity": (err.get("severity") or "warning").lower(),
                    "file": f,
                    "line": _to_int(err.get("line")),
                    "column": _to_int(err.get("column")),
                    "message": _clean(err.get("message")),
                })
    else:
        print(f"::warning::unrecognised report format in {path} (root <{tag}>)", file=sys.stderr)

    return findings


def _sort_key(f):
    return (f.get("tool", ""), f.get("file", ""), f.get("line", 0),
            f.get("column", 0), f.get("rule", ""), f.get("message", ""))


def load_findings(paths):
    out = []
    for p in paths or []:
        if not p or not os.path.exists(p):
            continue
        try:
            with open(p, encoding="utf-8") as fh:
                data = json.load(fh)
            if isinstance(data, list):
                out.extend(data)
        except Exception as exc:  # noqa: BLE001
            print(f"::warning::could not read findings file {p}: {exc}", file=sys.stderr)
    return out


def _exact(f):
    return (f.get("tool"), f.get("rule"), f.get("file"), f.get("line"))


def _loose(f):
    return (f.get("tool"), f.get("rule"), f.get("message"))


def compare(base, head):
    """Classify every head finding as new/moved/existing and find resolved ones."""
    base_exact, base_loose = {}, {}
    for b in base:
        base_exact.setdefault(_exact(b), []).append(b)
        base_loose.setdefault(_loose(b), []).append(b)

    consumed = set()
    new, moved, existing = [], [], []
    for h in head:
        candidates = [b for b in base_exact.get(_exact(h), []) if id(b) not in consumed]
        if candidates:
            consumed.add(id(candidates[0]))
            existing.append((candidates[0], h))
            continue
        candidates = [b for b in base_loose.get(_loose(h), []) if id(b) not in consumed]
        if candidates:
            same_file = [b for b in candidates if b.get("file") == h.get("file")]
            b = (same_file or candidates)[0]
            consumed.add(id(b))
            moved.append((b, h))
            continue
        new.append(h)

    resolved = [b for b in base if id(b) not in consumed]
    return {"new": new, "moved": moved, "existing": existing, "resolved": resolved}


def _link(f, server, repo, sha):
    if f.get("file"):
        return f"{server}/{repo}/blob/{sha}/{f['file']}#L{f.get('line', 0)}"
    return f"{server}/{repo}/actions/runs"


def _finding_line(f, sha, server, repo):
    icon = SEVERITY_ICON.get(f.get("severity"), "🟡")
    loc = f"{f['file']}:{f['line']}" if f.get("file") else "(no location reported)"
    msg = f.get("message") or "(no message)"
    return f"- {icon} **{f['rule']}** — `{loc}` — {msg} — [open]({_link(f, server, repo, sha)})"


def _moved_line(pair, server, repo, head_sha):
    b, h = pair
    icon = SEVERITY_ICON.get(h.get("severity"), "🟡")
    old = f"{b.get('file')}:{b.get('line')}" if b.get("file") else "(unknown)"
    new = f"{h.get('file')}:{h.get('line')}" if h.get("file") else "(unknown)"
    return (f"- {icon} **{h['rule']}** — moved `{old}` → `{new}` — "
            f"{h.get('message') or '(no message)'} — [open]({_link(h, server, repo, head_sha)})")


def _section(lines, title, items, renderer, empty_text):
    lines.append(f"### {title} ({len(items)})")
    lines.append("")
    if not items:
        lines.append(empty_text)
        lines.append("")
        return
    for item in items[:DETAIL_LIMIT]:
        lines.append(renderer(item))
    if len(items) > DETAIL_LIMIT:
        lines.append(f"- … and {len(items) - DETAIL_LIMIT} more — see the `*-findings` "
                     "artifacts attached to this workflow run.")
    lines.append("")


def render(diff, server, repo, head_sha, base_sha):
    n_new, n_moved = len(diff["new"]), len(diff["moved"])
    n_exist, n_resolved = len(diff["existing"]), len(diff["resolved"])
    lines = [
        "## ⚠️ Warning regression",
        "",
        "The PR head and the base-branch baseline were analysed with the same "
        "**Android lint** and **detekt** tasks. Findings are matched on rule and "
        "location (tool, rule, file, line); a finding that keeps its rule and "
        "message but moves is reported as *moved*, not as resolved + new.",
        "",
        "| Category | Count |",
        "| :--- | ---: |",
        f"| 🆕 New warnings | {n_new} |",
        f"| ↔️ Moved warnings | {n_moved} |",
        f"| ♻️ Existing warnings | {n_exist} |",
        f"| ✅ Resolved warnings | {n_resolved} |",
        "",
    ]
    if n_new == 0 and n_moved == 0 and n_resolved == 0:
        lines += ["No warnings were introduced, moved, or resolved by this pull request.",
                  ""]

    _section(lines, "🆕 New warnings", diff["new"],
             lambda f: _finding_line(f, head_sha, server, repo),
             "None — this pull request adds no new lint or detekt warnings.")
    _section(lines, "↔️ Moved warnings", diff["moved"],
             lambda p: _moved_line(p, server, repo, head_sha),
             "None — no existing warnings changed location.")
    _section(lines, "✅ Resolved warnings", diff["resolved"],
             lambda f: _finding_line(f, base_sha, server, repo),
             "None — every baseline warning is still present.")
    _section(lines, "♻️ Existing warnings", diff["existing"],
             lambda p: _finding_line(p[1], head_sha, server, repo),
             "None — the baseline was already clean.")
    return "\n".join(lines)


def render_errors(head, server, repo, head_sha):
    errors = [f for f in head if f.get("severity") == "error"]
    errors.sort(key=_sort_key)
    lines = []
    if not errors:
        return ""
    lines.append(f"- **{len(errors)} blocking analysis error(s) reported:**")
    for f in errors[:DETAIL_LIMIT]:
        lines.append("  " + _finding_line(f, head_sha, server, repo))
    if len(errors) > DETAIL_LIMIT:
        lines.append(f"  - … and {len(errors) - DETAIL_LIMIT} more.")
    return "\n".join(lines)


def cmd_collect(args):
    findings = []
    for path in args.files:
        findings.extend(parse_report(path, args.root))
    findings.sort(key=_sort_key)
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(findings, fh, indent=2, ensure_ascii=False)
    print(f"collected {len(findings)} finding(s) from {len(args.files)} report(s) -> {args.out}")
    return 0


def cmd_compare(args):
    base = load_findings(args.base)
    head = load_findings(args.head)
    diff = compare(base, head)
    markdown = render(diff, args.server, args.repo, args.head_sha, args.base_sha)
    with open(args.out, "w", encoding="utf-8") as fh:
        fh.write(markdown + "\n")

    errors_md = render_errors(head, args.server, args.repo, args.head_sha)
    if args.errors_out:
        with open(args.errors_out, "w", encoding="utf-8") as fh:
            fh.write(errors_md)

    print(f"base={len(base)} head={len(head)} "
          f"new={len(diff['new'])} moved={len(diff['moved'])} "
          f"existing={len(diff['existing'])} resolved={len(diff['resolved'])}")
    return 0


def build_parser():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    c = sub.add_parser("collect", help="normalise report files into JSON")
    c.add_argument("--root", default="", help="workspace root used to relativise paths")
    c.add_argument("--out", required=True, help="output JSON file")
    c.add_argument("files", nargs="*", help="report XML files to parse")
    c.set_defaults(func=cmd_collect)

    d = sub.add_parser("compare", help="diff base vs head findings and emit Markdown")
    d.add_argument("--base", nargs="*", default=[], help="baseline findings JSON")
    d.add_argument("--head", nargs="*", default=[], help="head findings JSON")
    d.add_argument("--server", default="https://github.com")
    d.add_argument("--repo", required=True, help="owner/repo")
    d.add_argument("--head-sha", required=True)
    d.add_argument("--base-sha", required=True)
    d.add_argument("--out", required=True, help="output Markdown file")
    d.add_argument("--errors-out", default="", help="optional output file for analysis errors")
    d.set_defaults(func=cmd_compare)
    return parser


def main(argv=None):
    args = build_parser().parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
