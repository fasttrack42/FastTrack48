#!/usr/bin/env python3
"""Parse + validate the knowledge base. Stdlib only.

  cards/*.md  -> kb.jsonl    source cards (provenance layer, one fact per card)
  final/*.md  -> core.jsonl  distilled, shippable cards (FC-NNN), each citing its sources via `from:`

Exits non-zero, listing every violation, if any card breaks the schema in README.md, if a final card
cites an unknown source, or if any source safety card (stop/caution) is not covered by a final card.
"""
import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC_DIR, FINAL_DIR = ROOT / "cards", ROOT / "final"
SRC_OUT, FINAL_OUT = ROOT / "kb.jsonl", ROOT / "core.jsonl"

SRC_KEYS = ("claim", "why", "do", "grade", "src", "counter", "safety", "when", "aud", "tier", "note")
FINAL_KEYS = ("title", "claim", "why", "do", "grade", "counter", "safety", "when", "aud", "cond", "gate",
              "from", "note")
SRC_REQUIRED = ("claim", "why", "grade", "src", "safety", "when", "aud", "tier")
FINAL_REQUIRED = ("title", "claim", "why", "grade", "safety", "when", "aud", "from")
WORD_LIMITS = {"title": 6, "claim": 25, "why": 40, "do": 30}
GRADES = {"A", "B", "C", "D"}
# In-app evidence label (quiet, user-facing). C = animal/mechanistic only.
LABELS = {"A": "established", "B": "emerging", "C": "early research", "D": "clinical experience"}
SAFETY = {"none", "caution", "stop"}
# Final-card categories = file stems in final/. Default visibility in the app:
#   locked: always on, shown only in context (not a toggle) · on/off: user toggle default
#   lunar: follows the fast-days feature
CATEGORIES = {"essentials": "locked", "journey": "on", "mind": "on", "food": "on", "rhythm": "on",
              "comfort": "on", "women": "off", "medical": "off", "traditions": "lunar"}
# Health-profile chips (Settings > Health conditions); medical cards must name at least one.
CONDITIONS = {"t2d", "diabetes-meds", "t1", "bp-heart", "kidney", "gout-stones", "gallbladder", "thyroid",
              "pcos", "glp1", "steroids", "lithium", "warfarin", "daily-meds", "cancer"}
# Context gates for essentials (derived from data the app already has; never asked separately).
GATES = {"onboarding", "age65", "female"}
TIERS = {"core", "tail", "drop"}
AUD = {"all", "beginner", "advanced", "t2d", "meds", "women"}
STAGES = ("0-2", "2-5", "5-8", "8-12", "12-20", "20-24", "24-48", "48-54", "54-72", "72+")
EVENTS = {"onboarding", "plan", "start", "hunger", "break", "eat", "between", "plateau",
          "long", "fluids", "myth", "fastday", "history"}
SRC_ID_RE = re.compile(r"^(CGF|YT|BLOG|SCI)-\d{2}-\d{3}$")
FINAL_ID_RE = re.compile(r"^FC-\d{3}$")
HEAD_RE = re.compile(r"^###\s+(\S+)\s*$")


def parse(path, keys):
    key_re = re.compile(r"^(" + "|".join(keys) + r"):\s*(.*)$")
    cards, cur, key = [], None, None
    for n, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.rstrip()
        if m := HEAD_RE.match(line):
            cur = {"id": m.group(1), "_loc": f"{path.parent.name}/{path.name}:{n}", "_err": []}
            cards.append(cur)
            key = None
            continue
        if cur is None or not line.strip():
            continue
        if m := key_re.match(line):
            key = m.group(1)
            if key in cur:
                cur["_err"].append(f"duplicate key '{key}'")
            cur[key] = m.group(2).strip()
        elif key:
            cur[key] = (cur[key] + " " + line.strip()).strip()
        else:
            cur["_err"].append(f"line {n}: text before any key")
    return cards


def words(s):
    return len(re.findall(r"\S+", s))


def split(c, key, sep):
    if c.get(key):
        c[key] = [x.strip() for x in c[key].split(sep) if x.strip()]


def validate_common(c, required, id_re):
    errs = c.pop("_err")
    if not id_re.match(c["id"]):
        errs.append(f"bad id '{c['id']}'")
    errs += [f"missing '{k}'" for k in required if not c.get(k)]
    errs += [f"'{k}' has {words(c[k])} words > {lim}"
             for k, lim in WORD_LIMITS.items() if c.get(k) and words(c[k]) > lim]
    if c.get("grade") and c["grade"] not in GRADES:
        errs.append(f"bad grade '{c['grade']}'")
    if c.get("safety") and c["safety"] not in SAFETY:
        errs.append(f"bad safety '{c['safety']}'")
    split(c, "aud", ",")
    errs += [f"bad aud '{a}'" for a in c.get("aud", []) if a not in AUD]
    split(c, "when", "|")
    for w in c.get("when", []):
        kind, _, arg = w.partition(":")
        if kind == "stage":
            if arg not in STAGES:
                errs.append(f"bad stage '{w}'")
        elif kind == "symptom":
            if not re.fullmatch(r"[a-z]+", arg):
                errs.append(f"bad symptom '{w}'")
        elif w not in EVENTS:
            errs.append(f"bad when '{w}'")
    return errs


def load(dir_, keys, required, id_re, extra):
    files = sorted(dir_.glob("*.md"))
    cards, problems, seen = [], [], {}
    for f in files:
        for c in parse(f, keys):
            loc = c["_loc"]
            if c["id"] in seen:
                problems.append(f"{loc} {c['id']}: duplicate id (first at {seen[c['id']]})")
            seen.setdefault(c["id"], loc)
            errs = validate_common(c, required, id_re) + extra(c)
            problems += [f"{loc} {c['id']}: {e}" for e in errs]
            cards.append(c)
    return files, cards, problems


def src_extra(c):
    errs = []
    if c.get("tier") and c["tier"] not in TIERS:
        errs.append(f"bad tier '{c['tier']}'")
    split(c, "src", ";")
    return errs


def final_extra(c):
    split(c, "from", ",")
    split(c, "cond", ",")
    errs = [] if c.get("from") else ["empty 'from'"]
    cat = c["_loc"].split("/")[1].split(".")[0]
    c["cat"] = cat
    if cat not in CATEGORIES:
        return errs + [f"file '{cat}.md' is not a category {sorted(CATEGORIES)}"]
    c["default"] = CATEGORIES[cat]
    errs += [f"bad cond '{x}'" for x in c.get("cond", []) if x not in CONDITIONS]
    if cat == "medical" and not c.get("cond"):
        errs.append("medical card needs 'cond'")
    if cat != "medical" and c.get("cond"):
        errs.append("only medical cards may carry 'cond' (move it, or split the card)")
    if c.get("gate"):
        if cat != "essentials":
            errs.append("only essentials may carry 'gate'")
        elif c["gate"] not in GATES:
            errs.append(f"bad gate '{c['gate']}'")
    if cat == "essentials" and c.get("safety") == "none":
        errs.append("essentials must be safety cards (stop/caution)")
    return errs


def write_jsonl(path, cards):
    with path.open("w", encoding="utf-8") as fh:
        for c in cards:
            fh.write(json.dumps({k: v for k, v in c.items() if not k.startswith("_")},
                                ensure_ascii=False) + "\n")


def main():
    sfiles, src, problems = load(SRC_DIR, SRC_KEYS, SRC_REQUIRED, SRC_ID_RE, src_extra)
    if not sfiles:
        sys.exit(f"no source cards in {SRC_DIR}")
    by_id = {c["id"]: c for c in src}

    ffiles, final, fproblems = load(FINAL_DIR, FINAL_KEYS, FINAL_REQUIRED, FINAL_ID_RE, final_extra) \
        if FINAL_DIR.is_dir() else ([], [], [])
    problems += fproblems
    covered = set()
    for c in final:
        for sid in c.get("from", []):
            if sid not in by_id:
                problems.append(f"{c['_loc']} {c['id']}: unknown source '{sid}'")
                continue
            covered.add(sid)
            s = by_id[sid]
            if s["tier"] == "drop":
                problems.append(f"{c['_loc']} {c['id']}: cites dropped source '{sid}'")
            # a final card may never be less cautious than a safety source it absorbs
            rank = {"none": 0, "caution": 1, "stop": 2}
            if rank.get(s["safety"], 0) > rank.get(c.get("safety"), 0):
                problems.append(f"{c['_loc']} {c['id']}: safety '{c.get('safety')}' weaker than "
                                f"source {sid} '{s['safety']}'")
        # derived fields for the app
        c["label"] = LABELS.get(c.get("grade"), "")
        c["src"] = sorted({r for sid in c.get("from", []) if sid in by_id for r in by_id[sid]["src"]})

    if final:
        for s in src:
            if s["safety"] != "none" and s["tier"] != "drop" and s["id"] not in covered:
                problems.append(f"{s['_loc']} {s['id']}: safety card ({s['safety']}) not covered "
                                f"by any final card")

    if problems:
        print("\n".join(problems), file=sys.stderr)
        sys.exit(f"{len(problems)} problem(s)")

    write_jsonl(SRC_OUT, src)
    tiers = dict(sorted(Counter(c["tier"] for c in src).items()))
    print(f"{len(src)} source cards from {len(sfiles)} files -> {SRC_OUT.name}  tiers={tiers}")
    if not final:
        return
    write_jsonl(FINAL_OUT, final)
    stage_cov = Counter(w for c in final for w in c["when"] if w.startswith("stage:"))
    missing = [s for s in STAGES if f"stage:{s}" not in stage_cov]
    unref = [s["id"] for s in src if s["tier"] == "core" and s["id"] not in covered]
    print(f"{len(final)} final cards from {len(ffiles)} files -> {FINAL_OUT.name}  "
          f"grades={dict(sorted(Counter(c['grade'] for c in final).items()))}  "
          f"safety={dict(sorted(Counter(c['safety'] for c in final).items()))}")
    print(f"words: {sum(words(c[k]) for c in final for k in ('title','claim','why','do') if c.get(k))} "
          f"(user-facing, per locale)")
    print(f"stage coverage: {dict((s, stage_cov.get('stage:'+s, 0)) for s in STAGES)}"
          + (f"  MISSING={missing}" if missing else ""))
    by_cat = Counter(c["cat"] for c in final)
    print("categories: " + "  ".join(f"{k}[{v}]={by_cat.get(k, 0)}" for k, v in CATEGORIES.items()))
    print(f"source core cards not absorbed into a final card: {len(unref)}")
    if "-v" in sys.argv and unref:
        print("  " + " ".join(unref))


if __name__ == "__main__":
    main()
