#!/usr/bin/env python3
"""ci.b / ci.c: the scanners behind `make security` and `make security-report` (fleet CICD.md).

Pinned Docker images, no accounts. Accepted findings live in .security-allow.yml, each with a reason and an expiry
date; an expired entry stops counting, so the finding fails the gate again.

  security.py secrets | deps | sast     fail on secrets, high/critical dependency findings, Semgrep ERROR findings
  security.py trivy | zap               report-only scans into reports/security/
  security.py summary                   counts per scanner, for docs/SECURITY-REPORT.md
"""
import datetime
import json
import os
import re
import subprocess
import sys
import time
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPORTS = os.path.join(ROOT, "reports", "security")
IMAGES = {
    "gitleaks": "zricethezav/gitleaks:v8.21.2",
    "osv": "ghcr.io/google/osv-scanner:v1.9.1",
    "semgrep": "semgrep/semgrep:1.95.0",
    "trivy": "aquasec/trivy:0.57.1",
    "zap": "ghcr.io/zaproxy/zaproxy:2.15.0",
}
LOCKFILES = ["apps/web/pnpm-lock.yaml", "engine/console-e2e/pnpm-lock.yaml", "engine/app/gradle.lockfile", "engine/core/gradle.lockfile"]
EXCLUDES = ["node_modules", "build", "dist", "coverage", ".gradle", ".kotlin", "reports", "backups", ".git", "test-evidence", ".idea", "vendor", "console-demo"]
PORT = os.environ.get("PORT", "47831")


def docker(image, args, mounts=None, check=False):
    cmd = ["docker", "run", "--rm", "--platform", "linux/amd64", "-v", f"{ROOT}:/src:ro" if not mounts else f"{ROOT}:/src", "-w", "/src"]
    for m in mounts or []:
        cmd += ["-v", m]
    return subprocess.run(cmd + [image] + args, capture_output=True, text=True, check=check)


def allowed():
    """The unexpired entries of .security-allow.yml as (scanner, id, path-or-None); expired ones are listed and ignored."""
    path = os.path.join(ROOT, ".security-allow.yml")
    entries, cur = [], None
    for line in open(path).read().splitlines() if os.path.exists(path) else []:
        m = re.match(r"^- (\w+):\s*(.*)$", line) or re.match(r"^  (\w+):\s*(.*)$", line)
        if not m:
            continue
        if line.startswith("- "):
            cur = {}
            entries.append(cur)
        cur[m.group(1)] = m.group(2).strip().strip("\"'")
    today, live = datetime.date.today(), []
    for e in entries:
        for need in ("scanner", "id", "reason", "expires"):
            if not e.get(need):
                sys.exit(f".security-allow.yml: an entry has no '{need}': {e}")
        if datetime.date.fromisoformat(e["expires"]) < today:
            print(f"allow-list entry expired on {e['expires']}, no longer accepted: {e['scanner']} {e['id']}")
            continue
        live.append(e)
    return live


def is_allowed(allow, scanner, ident, path=""):
    return any(a["scanner"] == scanner and a["id"] == ident and (not a.get("path") or a["path"] == path) for a in allow)


def secrets():
    base = ["--no-banner", "--redact", "--exit-code", "1", "--config", "/src/.gitleaks.toml"]
    bad = 0
    for label, args in (("working tree", ["dir", "/src"]), ("git history", ["git", "/src"])):
        r = docker(IMAGES["gitleaks"], args + base, mounts=None)
        out = (r.stdout + r.stderr).strip().splitlines()
        if r.returncode:
            bad += 1
            print(f"gitleaks ({label}): secrets found")
            print("\n".join(l for l in out if any(k in l for k in ("Finding", "File", "RuleID", "Fingerprint", "leaks found"))))
        else:
            print(f"gitleaks ({label}): clean")
    return bad


def run_osv():
    r = docker(IMAGES["osv"], ["--format", "json"] + [a for lf in LOCKFILES for a in ("--lockfile", lf)])
    try:
        return json.loads(r.stdout)
    except ValueError:
        sys.exit(f"osv-scanner failed:\n{r.stdout}\n{r.stderr}")


def osv_findings(report):
    """(package, version, advisory id, aliases, max severity score) per vulnerability group."""
    out = []
    for res in report.get("results", []):
        for pkg in res.get("packages", []):
            name, ver = pkg["package"]["name"], pkg["package"]["version"]
            for g in pkg.get("groups", []):
                try:
                    score = float(g.get("max_severity") or 0)
                except ValueError:
                    score = 0.0
                out.append((name, ver, g["ids"][0], g["ids"], score))
    return out


def deps():
    allow, bad = allowed(), 0
    report = run_osv()
    os.makedirs(REPORTS, exist_ok=True)
    json.dump(report, open(os.path.join(REPORTS, "osv.json"), "w"), indent=1)
    for name, ver, ident, ids, score in osv_findings(report):
        if score < 7.0:  # below high (CVSS 7.0): reported, not failing
            continue
        if any(is_allowed(allow, "osv", i) for i in ids):
            continue
        bad += 1
        print(f"osv-scanner: {name} {ver} {ident} (CVSS {score}): fix by upgrading, or accept in .security-allow.yml")
    print(f"osv-scanner: {'FAILED' if bad else 'no unaccepted high/critical findings'}")
    return bad


def run_semgrep():
    args = ["semgrep", "scan", "--config", "p/owasp-top-ten", "--metrics=off", "--json", "--quiet"]
    for e in EXCLUDES:
        args += ["--exclude", e]
    r = docker(IMAGES["semgrep"], args + ["/src"])
    try:
        return json.loads(r.stdout)
    except ValueError:
        sys.exit(f"semgrep failed:\n{r.stdout[:2000]}\n{r.stderr[-2000:]}")


def sast():
    allow, bad = allowed(), 0
    report = run_semgrep()
    os.makedirs(REPORTS, exist_ok=True)
    json.dump(report, open(os.path.join(REPORTS, "semgrep.json"), "w"), indent=1)
    for f in report.get("results", []):
        path = f["path"].removeprefix("/src/")
        if f["extra"]["severity"] != "ERROR" or is_allowed(allow, "semgrep", f["check_id"], path):
            continue
        bad += 1
        print(f"semgrep: {path}:{f['start']['line']} {f['check_id']}: {f['extra']['message'][:120]}")
    print(f"semgrep: {'FAILED' if bad else 'no unaccepted ERROR findings'}")
    return bad


def trivy():
    """No Dockerfile yet (ci-deploy adds one): scan the filesystem, the lockfiles and the source."""
    os.makedirs(REPORTS, exist_ok=True)
    skip = ",".join(f"**/{e}" for e in EXCLUDES if e not in ("vendor", "console-demo"))
    r = docker(IMAGES["trivy"], ["fs", "--scanners", "vuln,secret,misconfig", "--skip-dirs", skip, "--format", "json", "--quiet", "/src"],
               mounts=[f"{os.path.expanduser('~')}/.cache/trivy:/root/.cache/trivy"])
    open(os.path.join(REPORTS, "trivy.json"), "w").write(r.stdout)
    print("trivy fs: written to reports/security/trivy.json")


def up(url):
    try:
        return urllib.request.urlopen(url, timeout=3).status == 200
    except Exception:
        return False


def zap():
    """ZAP baseline (passive) against the engine's console on localhost; starts `make dev` when nothing answers."""
    os.makedirs(REPORTS, exist_ok=True)
    started = False
    if not up(f"http://localhost:{PORT}/ready"):
        env = dict(os.environ, NO_BROWSER="1")
        subprocess.Popen(["make", "-C", ROOT, "dev"], env=env, stdout=open(os.path.join(REPORTS, "engine.log"), "w"), stderr=subprocess.STDOUT)
        started = True
        for _ in range(90):
            time.sleep(2)
            if up(f"http://localhost:{PORT}/ready"):
                break
        else:
            sys.exit("the engine did not come up for the ZAP baseline (see reports/security/engine.log)")
    try:
        r = docker(IMAGES["zap"], ["zap-baseline.py", "-t", f"http://host.docker.internal:{PORT}/", "-J", "zap.json", "-r", "zap.html", "-I"],
                   mounts=[f"{REPORTS}:/zap/wrk:rw"])
        open(os.path.join(REPORTS, "zap.log"), "w").write(r.stdout + r.stderr)
        print("zap baseline: written to reports/security/zap.json")
    finally:
        if started:
            subprocess.run(["make", "-C", ROOT, "stop"], capture_output=True)


def summary():
    def load(name):
        p = os.path.join(REPORTS, name)
        return json.load(open(p)) if os.path.exists(p) and os.path.getsize(p) else None
    counts = {}
    osv = load("osv.json")
    if osv:
        f = osv_findings(osv)
        counts["osv-scanner"] = f"{len(f)} findings ({sum(1 for x in f if x[4] >= 7.0)} high/critical)"
    sg = load("semgrep.json")
    if sg:
        sev = [x["extra"]["severity"] for x in sg.get("results", [])]
        counts["semgrep"] = f"{len(sev)} findings ({sev.count('ERROR')} ERROR)"
    tv = load("trivy.json")
    if tv:
        v = [x for r in tv.get("Results", []) for x in r.get("Vulnerabilities") or []]
        s = [x for r in tv.get("Results", []) for x in r.get("Secrets") or []]
        m = [x for r in tv.get("Results", []) for x in r.get("Misconfigurations") or []]
        counts["trivy fs"] = f"{len(v)} vulnerabilities ({sum(1 for x in v if x['Severity'] in ('HIGH', 'CRITICAL'))} high/critical), {len(s)} secrets, {len(m)} misconfigurations"
    zp = load("zap.json")
    if zp:
        alerts = [a for site in zp.get("site", []) for a in site.get("alerts", [])]
        counts["zap baseline"] = f"{len(alerts)} alerts ({sum(1 for a in alerts if a.get('riskcode') in ('3',))} high)"
    for k, v in counts.items():
        print(f"{k}: {v}")


if __name__ == "__main__":
    step = sys.argv[1] if len(sys.argv) > 1 else ""
    fn = {"secrets": secrets, "deps": deps, "sast": sast, "trivy": trivy, "zap": zap, "summary": summary}.get(step)
    if not fn:
        sys.exit(__doc__)
    sys.exit(1 if fn() else 0)
