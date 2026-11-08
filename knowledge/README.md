# FastTrack knowledge base

Source material for in-app fasting knowledge. **Not shipped as-is.** Cards are paraphrased
(never verbatim book text) and each one cites its source, so the app can be built from them
without copyright exposure.

```
knowledge/
  README.md          this file: schema and rules
  STATUS.md          iteration log, findings, gaps
  AUDIT-stage-copy.md  problems found in the current in-app stage copy
  cards/*.md         SOURCE cards: one fact each, with provenance (book chapter, talk, paper)
  final/*.md         FINAL cards (FC-NNN): distilled, user-facing set; each cites its sources via `from:`
  build.py           validates both layers -> kb.jsonl (sources) + core.jsonl (final, shippable)
```

Run: `python3 -I knowledge/build.py` (stdlib only; `-v` lists unabsorbed source cards).

### Two layers
- **Source cards** (`cards/`) are the audit trail: everything extracted, graded and annotated, including
  overstatements kept as `drop`. They're never shown directly in the app.
- **Final cards** (`final/`) are what the app ships. Extra keys: `title` (≤ 6 words) and `from`
  (comma-separated source IDs). `src` is derived from the sources, and `label` from `grade`:
  A → *established*, B → *emerging*, C → *early research*, D → *clinical experience*.
- The build **fails** if a final card cites an unknown or dropped source, if a final card is less
  cautious than any safety source it absorbs, or if any non-dropped source safety card (stop/caution)
  isn't covered by some final card. No safety fact can be silently lost in distillation.

### Final-card categories (file name = category)
`essentials` (always on, contextual; may carry `gate: onboarding|age65|female`) · `journey` · `mind` ·
`food` · `rhythm` · `comfort` (healthy-body side effects; no diagnoses assumed) · `women` (off) ·
`medical` (off; every card needs `cond:` from the health-profile chips) · `traditions` (follows fast days).
The build rejects `cond` outside `medical`, `gate` outside `essentials`, and non-safety essentials.
See `INTEGRATION.md` for how each category surfaces in the app.


## Card format

Each card starts with a `### <id>` heading, followed by `key: value` lines. A value continues
on following lines until the next key or heading. Blank lines are ignored.

```
### CGF-03-004
claim: Fasting does not slow metabolism in the short term; resting energy use rises ~12% by day 4.
why: Falling insulin lets noradrenaline rise; it releases stored fuel and keeps you alert to find food.
do: Expect energy, not sluggishness, on days 2-4. Fatigue usually means low salt, not low metabolism.
grade: B
src: CGF ch3; Zauner 2000 AJCN
counter: n=11, healthy lean adults. Much longer fasts do eventually lower energy use.
safety: none
when: stage:48-54 | myth
aud: all
tier: core
```

| key | required | rule |
|---|---|---|
| `id` | yes | `<SRC>-<NN>-<NNN>`. SRC: `CGF` book, `YT` talk, `BLOG`, `SCI` science outside Fung. Unique. |
| `claim` | yes | ≤ 25 words. The fact, paraphrased. |
| `why` | yes | ≤ 40 words. The mechanism. |
| `do` | no | ≤ 30 words. What the user does with it. Missing means it's purely explanatory. |
| `grade` | yes | `A` RCTs/meta-analyses or textbook physiology · `B` small human trials, observational · `C` animal/mechanistic only · `D` clinical experience, expert opinion, framing |
| `src` | yes | `CGF chN` / `YT <videoId>@mm:ss` / `BLOG <slug>` / `Author Year Journal`. `;`-separated. Every cite used by a final card needs an entry in `references.json` (export fails otherwise). |
| `counter` | no | Counter-evidence, overstatements, caveats. Required when grade is C/D and the claim is physiological. |
| `safety` | yes | `none` · `caution` (needs care or monitoring) · `stop` (contraindication or stop-fasting sign) |
| `when` | yes | One or more triggers, `\|`-separated (vocabulary below). |
| `aud` | yes | One or more of `all`, `beginner`, `advanced`, `t2d`, `meds`, `women`. `,`-separated. |
| `tier` | yes | `core` (candidate for the compact in-app set) · `tail` (library only) · `drop` (kept for audit; not shipped) |
| `note` | no | Free-form internal note (verification to do, conflicts with existing app copy). |

### `when` vocabulary

These map one-to-one onto app moments, so phase 2 can surface each card just in time.

- `stage:<range>`: matches the dial stages `0-2 2-5 5-8 8-12 12-20 20-24 24-48 48-54 54-72 72+`.
- `onboarding`: first launch / before the first fast.
- `plan`: choosing a fast type or length.
- `start`: the moment a fast starts.
- `hunger`: the user feels hungry (wave, cravings, social triggers).
- `symptom:<x>`: `headache dizzy cramps constipation insomnia cold fatigue heartburn` etc.
- `break`: breaking / refeeding.
- `eat`: what to eat in the eating window.
- `between`: between fasts, habits, scheduling.
- `plateau`: weight stalls.
- `long`: preparing for or during fasts ≥ 3 days.
- `fluids`: what is allowed while fasting (water, tea, sweeteners).
- `myth`: answers a common objection.
- `fastday`: traditional/religious fast days (lunar feature).
- `history`: history and context.

## Rules

1. **Paraphrase.** No sentence copied from the source. Short attributed quotes (≤ 15 words)
   only when the wording itself is the point.
2. **Grade honestly.** Grade the claim, not the person making it. If Fung overstates, the card
   records his claim and `counter` records the correction, or the claim is rewritten to the
   defensible version.
3. **One fact per card.** If a card needs "and also", split it.
4. **Safety is never optional.** Every `stop`/`caution` card is `tier: core`.
5. **Compact.** Every core card becomes a string translated into 9 locales. Prefer fewer,
   denser cards.

## References and attribution
- `references.json` is the bibliography, one verified entry per cite key:
  - `kind`: book, study, guideline, web or video
  - `short`: the cite shown on a card
  - `text`: the full citation
  - `url`: a DOI, PubMed, guideline or YouTube link
- Studies were verified against PubMed or Crossref. Videos were verified with yt-dlp (title, channel, date). Books were checked against their own front matter or ISBN listings.
- `export_android.py` generates `SOURCES.md` and the app's `KnowledgeReferences.kt` from it. Both list only works a published card cites.
- In the app, Library › footer › Sources & credits and About › Acknowledgements show:
  - the credits, the independence notice and the "not medical advice" line, translated
  - every citation, untranslated

