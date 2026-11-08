#!/usr/bin/env python3
"""Export the knowledge base into the Android app. Stdlib only.

Inputs
  core.jsonl                final cards (run build.py first); English card text
  i18n/en.json              English UI strings: {"ui": {key: text}}
  i18n/<locale>.json        translations: {"cards": {"FC-030": {"title", "claim", "why", "do", "counter"}},
                                           "ui": {key: text}}
  references.json           the bibliography: one verified entry per cite key used in the cards
                            ({key, kind: book|study|guideline|web|video, short, text, url})
Outputs
  app/src/main/res/values[-xx]/strings_knowledge.xml
  app/src/main/java/.../data/knowledge/KnowledgeCatalog.kt      (generated; do not edit)
  app/src/main/java/.../data/knowledge/KnowledgeReferences.kt   (generated; do not edit)
  SOURCES.md                                                    (generated; do not edit)

Fails (non-zero) on a translation whose format placeholders differ from English, on unknown keys,
on a card field present in a translation but absent in English, or on a card cite with no
entry in references.json. Missing translations are
reported and fall back to English at runtime.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
APP = ROOT.parent / "app" / "src" / "main"
RES = APP / "res"
KOTLIN_OUT = APP / "java" / "com" / "legbehindneck" / "fasttrack48" / "data" / "knowledge" / "KnowledgeCatalog.kt"
REFS_OUT = KOTLIN_OUT.with_name("KnowledgeReferences.kt")
SOURCES_MD = ROOT / "SOURCES.md"
LOCALES = {"de": "values-de", "es": "values-es", "fr": "values-fr", "it": "values-it", "nl": "values-nl",
           "pt-BR": "values-pt-rBR", "uk": "values-uk-rUA", "zh-CN": "values-zh-rCN",
           # Traditional script, Taiwan vocabulary: one folder serves zh-TW, zh-HK and zh-MO (minSdk 26 matches by script).
           "zh-Hant": "values-b+zh+Hant"}
FIELDS = ("title", "claim", "why", "do", "counter")
STAGES = ("0-2", "2-5", "5-8", "8-12", "12-20", "20-24", "24-48", "48-54", "54-72", "72+")
CATEGORY = {"essentials": "Essentials", "journey": "Journey", "mind": "Mind", "food": "Food", "rhythm": "Rhythm",
            "comfort": "Comfort", "women": "Women", "medical": "Medical", "traditions": "Traditions"}
CONDITION = {"t2d": "T2d", "diabetes-meds": "DiabetesMeds", "t1": "T1", "bp-heart": "BpHeart", "kidney": "Kidney",
             "gout-stones": "GoutStones", "gallbladder": "Gallbladder", "thyroid": "Thyroid", "pcos": "Pcos",
             "glp1": "Glp1", "steroids": "Steroids", "lithium": "Lithium", "warfarin": "Warfarin",
             "daily-meds": "DailyMeds", "cancer": "Cancer"}
GATE = {"onboarding": "Onboarding", "age65": "Age65", "female": "Female"}
SAFETY = {"none": "None", "caution": "Caution", "stop": "Stop"}
PLACEHOLDER = re.compile(r"%(\d+\$)?[sd]")


def res_name(card_id, field):
    return f"kb_{card_id.lower().replace('-', '')}_{field}"


def esc(s):
    s = s.replace("\\", "\\\\").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    if s[:1] in ("@", "?"):
        s = "\\" + s
    return s


def xml_string(name, text, is_format):
    # aapt2 rejects stray '%' in strings it treats as format strings; card text is never formatted.
    attr = "" if is_format or "%" not in text else ' formatted="false"'
    return f'\t<string name="{name}"{attr}>{esc(text)}</string>\n'


BOOK = "Fung & Moore 2016, The Complete Guide to Fasting"
# Bibliography group per kind, in reading order; the app and SOURCES.md use the same grouping.
GROUP = {"book": "Book", "study": "Study", "guideline": "Study", "web": "Study", "video": "Talk"}
GROUP_ORDER = ("Book", "Study", "Talk")


def ref_key(s):
    """Card cite -> references.json key. CGF chapters share one book entry; videos drop timestamps."""
    if s.startswith("CGF"):
        return "CGF"
    if m := re.fullmatch(r"YT ([\w-]{11})(?:@[\d:]+)?(?:\s*\(.*\))?", s):
        return f"YT {m.group(1)}"
    return s


def readable_source(s, refs):
    """Internal shorthand -> the short cite shown on the card; its full entry is on Sources & credits."""
    s = re.sub(r"\s*\([^)]*\)$", "", s) if s.startswith("CGF") else s   # drop "(Megan)" asides
    if m := re.fullmatch(r"CGF ch(\d+)", s):
        return f"{BOOK}, ch. {m.group(1)}"
    if s == "CGF intro":
        return f"{BOOK}, introduction"
    if s == "CGF resources":
        return f"{BOOK}, part III"
    return refs[ref_key(s)]["short"]


def sources(c, refs):
    out = []
    for s in c["src"]:
        r = readable_source(s, refs)
        if r not in out:
            out.append(r)
    kind = {readable_source(s, refs): GROUP[refs[ref_key(s)]["kind"]] for s in c["src"]}
    # Book first, then studies, then talks: the order a reader would check them in.
    natural = lambda r: re.sub(r"\d+", lambda m: m.group(0).zfill(4), r)   # ch. 9 before ch. 12
    return sorted(out, key=lambda r: (GROUP_ORDER.index(kind[r]), natural(r)))


NBSP = "\u00a0"


def typography(loc, s):
    """French: no line break before ? ! : ; » or after «."""
    if loc != "fr":
        return s
    for mark in ("?", "!", ":", ";", "»"):
        s = s.replace(" " + mark, NBSP + mark)
    return s.replace("« ", "«" + NBSP)


def kt_str(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def main():
    cards = [json.loads(l) for l in (ROOT / "core.jsonl").read_text(encoding="utf-8").splitlines() if l.strip()]
    en_ui = json.loads((ROOT / "i18n" / "en.json").read_text(encoding="utf-8"))["ui"]
    by_id = {c["id"]: c for c in cards}
    problems, report = [], []
    refs = {r["key"]: r for r in json.loads((ROOT / "references.json").read_text(encoding="utf-8"))}
    for c in cards:
        for s in c["src"]:
            if ref_key(s) not in refs:
                problems.append(f"{c['id']}: cite '{s}' has no entry in references.json")
    if problems:
        sys.exit("\n".join(problems))

    def write_values(folder, card_text, ui_text, header):
        out = ['<?xml version="1.0" encoding="utf-8"?>\n',
               f"<!-- GENERATED by knowledge/export_android.py from knowledge/{header}. Do not edit. -->\n",
               "<resources>\n"]
        for k in en_ui:
            if k in ui_text:
                out.append(xml_string(f"kb_{k}", ui_text[k], bool(PLACEHOLDER.search(en_ui[k]))))
        for c in cards:
            for f in FIELDS:
                t = card_text.get(c["id"], {}).get(f)
                if t:
                    out.append(xml_string(res_name(c["id"], f), t, False))
        out.append("</resources>\n")
        d = RES / folder
        d.mkdir(parents=True, exist_ok=True)
        (d / "strings_knowledge.xml").write_text("".join(out), encoding="utf-8")

    en_cards = {c["id"]: {f: c[f] for f in FIELDS if c.get(f)} for c in cards}
    write_values("values", en_cards, en_ui, "core.jsonl + i18n/en.json")

    for loc, folder in LOCALES.items():
        path = ROOT / "i18n" / f"{loc}.json"
        if not path.exists():
            report.append(f"{loc}: no translation file (English fallback)")
            (RES / folder / "strings_knowledge.xml").unlink(missing_ok=True)
            continue
        tr = json.loads(path.read_text(encoding="utf-8"))
        ui = {k: typography(loc, v) for k, v in tr.get("ui", {}).items()}
        tcards = {cid: {f: typography(loc, v) for f, v in fs.items()} for cid, fs in tr.get("cards", {}).items()}
        for k, v in ui.items():
            if k not in en_ui:
                problems.append(f"{loc}: unknown ui key '{k}'")
            elif sorted(PLACEHOLDER.findall(v)) != sorted(PLACEHOLDER.findall(en_ui[k])):
                problems.append(f"{loc}: placeholder mismatch in ui '{k}'")
        for cid, fields in tcards.items():
            if cid not in by_id:
                problems.append(f"{loc}: unknown card '{cid}'")
                continue
            for f in fields:
                if f not in en_cards[cid]:
                    problems.append(f"{loc}: {cid}.{f} has no English source")
        miss_ui = [k for k in en_ui if k not in ui]
        miss_cards = [f"{cid}.{f}" for cid, fs in en_cards.items() for f in fs if not tcards.get(cid, {}).get(f)]
        report.append(f"{loc}: missing {len(miss_ui)} ui, {len(miss_cards)} card fields"
                      + (f" e.g. {(miss_ui + miss_cards)[:4]}" if miss_ui or miss_cards else ""))
        write_values(folder, tcards, ui, f"i18n/{loc}.json")

    # Generated Kotlin catalog
    lines = ["// GENERATED by knowledge/export_android.py from knowledge/core.jsonl. Do not edit.\n",
             "package com.legbehindneck.fasttrack48.data.knowledge\n\n",
             "import com.legbehindneck.fasttrack48.R\n\n",
             "internal val KnowledgeCatalog: List<KnowledgeCard> = listOf(\n"]
    for c in cards:
        stages = sorted(STAGES.index(w.split(":", 1)[1]) for w in c["when"] if w.startswith("stage:"))
        symptoms = sorted(w.split(":", 1)[1] for w in c["when"] if w.startswith("symptom:"))
        events = sorted(w for w in c["when"] if ":" not in w)
        ref = lambda f: f"R.string.{res_name(c['id'], f)}" if c.get(f) else "0"
        conds = ", ".join(f"HealthCondition.{CONDITION[x]}" for x in c.get("cond", []))
        gate = f"Gate.{GATE[c['gate']]}" if c.get("gate") else "null"
        lines.append(
            "\tKnowledgeCard(\n"
            f"\t\tid = {kt_str(c['id'])}, category = KnowledgeCategory.{CATEGORY[c['cat']]},\n"
            f"\t\ttitle = {ref('title')}, claim = {ref('claim')}, why = {ref('why')},\n"
            f"\t\taction = {ref('do')}, caveat = {ref('counter')},\n"
            f"\t\tgrade = Grade.{c['grade']}, safety = Safety.{SAFETY[c['safety']]},\n"
            f"\t\tstages = setOf({', '.join(map(str, stages))}),\n"
            f"\t\tevents = setOf({', '.join(map(kt_str, events))}),\n"
            f"\t\tsymptoms = setOf({', '.join(map(kt_str, symptoms))}),\n"
            f"\t\tconditions = setOf({conds}), gate = {gate},\n"
            f"\t\tsources = listOf({', '.join(map(kt_str, sources(c, refs)))}),\n"
            "\t),\n")
    lines.append(")\n")
    KOTLIN_OUT.parent.mkdir(parents=True, exist_ok=True)
    KOTLIN_OUT.write_text("".join(lines), encoding="utf-8")

    # Bibliography: only what the cards cite, grouped, alphabetical within a group.
    cited = {ref_key(s) for c in cards for s in c["src"]}
    bib = sorted((r for k, r in refs.items() if k in cited),
                 key=lambda r: (GROUP_ORDER.index(GROUP[r["kind"]]), r["text"].lower()))
    unused = sorted(set(refs) - cited)
    if unused:
        report.append(f"references.json: {len(unused)} entries no card cites: {unused[:4]}")
    kt = ["// GENERATED by knowledge/export_android.py from knowledge/references.json. Do not edit.\n",
          "package com.legbehindneck.fasttrack48.data.knowledge\n\n",
          "/** Every work the cards cite, for Sources & credits. Untranslated: citations read the same everywhere. */\n",
          "internal val KnowledgeReferences: List<KnowledgeReference> = listOf(\n"]
    for r in bib:
        kt.append(f"\tKnowledgeReference(ReferenceKind.{GROUP[r['kind']]}, {kt_str(r['text'])}, {kt_str(r['url'])}),\n")
    kt.append(")\n")
    REFS_OUT.write_text("".join(kt), encoding="utf-8")

    md = ["<!-- GENERATED by export_android.py from references.json. Do not edit. -->\n",
          "# Sources\n\n", en_ui["sources_credits"] + "\n\n", en_ui["sources_independence"] + "\n\n",
          en_ui["sources_medical"] + "\n"]
    heading = {"Book": en_ui["sources_books"], "Study": en_ui["sources_studies"], "Talk": en_ui["sources_talks"]}
    for g in GROUP_ORDER:
        md.append(f"\n## {heading[g]}\n\n")
        for r in (x for x in bib if GROUP[x["kind"]] == g):
            md.append(f"- {r['text']}" + (f" <{r['url']}>" if r["url"] else "") + "\n")
    SOURCES_MD.write_text("".join(md), encoding="utf-8")
    report.insert(0, f"{len(bib)} references -> {REFS_OUT.name}, {SOURCES_MD.name}")

    print(f"{len(cards)} cards, {len(en_ui)} ui strings -> values/strings_knowledge.xml, {KOTLIN_OUT.name}")
    print("\n".join(report))
    if problems:
        print("\n".join(problems), file=sys.stderr)
        sys.exit(f"{len(problems)} problem(s)")


if __name__ == "__main__":
    main()
