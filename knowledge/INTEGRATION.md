# Phase 2: integrating the knowledge base into FastTrack

Goal: put 116 cards within one tap of the moment they matter, without adding a single interruption.
Content source: `core.jsonl` (built from `final/*.md`). Each card has `cat`, `when`, `cond`, `gate`,
`grade` → `label`, `safety`, `src`.

## 1. Categories (Settings › Knowledge)

| Toggle (user-facing) | file | default | cards | what it holds |
|---|---|---|---|---|
| *(no toggle)* Safety essentials | `essentials` | always on, contextual only | 8 | who shouldn't fast, "hungry is fine, sick is not", >3 days, refeeding, alcohol, periods stopping, 65+, "condition or medicine? turn on Health conditions" |
| Fasting journey | `journey` | on | 20 | what the body does, stage by stage |
| Mind & habits | `mind` | on | 21 | hunger, cravings, cues, motivation, lapses, sleep, stress, support |
| Food & drinks | `food` | on | 17 | what to drink, breaking a fast, eating between fasts |
| Rhythm & progress | `rhythm` | on | 17 | schedules, life fit, plateaus, measuring progress, myths |
| Comfort | `comfort` | on | 8 | harmless side effects in healthy people: headache, dizziness, cold, sleep, cramps, gut |
| Women's physiology | `women` | off (offered once) | 5 | cycle timing, perimenopause, conception, weighing by cycle |
| Health conditions | `medical` | off | 18 | per-condition guidance, shown only for the chips the user selects |
| *(follows Fast days)* Traditions | `traditions` | follows the lunar feature | 2 | the ancient fast-day calendar, keeping dry fasts short |

Health conditions, when enabled, shows chips grouped so they're recognized rather than recalled:
- **Blood sugar:** type 2 / prediabetes · insulin or diabetes pills · type 1
- **Heart & kidneys:** blood pressure or heart · kidney · gout or kidney stones
- **Hormones:** thyroid · PCOS
- **Medicines:** Ozempic-type drugs · steroids · lithium · warfarin · other daily medication
- **Other:** cancer · gallbladder

A medical card appears only if one of its `cond` tags is selected. Nothing is diagnosed or inferred.

Why this split:
- **Healthy and clinical content are separate domains.** Showing diabetes-drug warnings to a healthy
  user is noise. Repeated irrelevant warnings teach people to ignore all warnings (alarm fatigue and
  habituation), so they hurt safety rather than help it.
- **Self-relevance drives attention and memory** (the self-reference effect). The chips make the
  medical layer precise for those who need it and invisible to everyone else.
- **Five toggles plus one chip group** stays within comfortable working-memory limits. More
  categories would turn Settings into a chore.
- **Mind is a first-class category**, not a footnote. Behaviour, not physiology, decides whether
  fasting works for someone, and these are the cards (urge surfing, the what-the-hell effect, cues,
  boredom, self-efficacy) that most people have never heard.

## 2. Principles (each one maps to a concrete rule below)

| # | Principle | Evidence base | Rule in the app |
|---|---|---|---|
| P1 | **Pull, not push** | Autonomy (self-determination theory); notification fatigue | No new notifications. Knowledge lives where attention already is. |
| P2 | **Just in time** | Encoding specificity; "teachable moments" | Cards surface at their trigger (stage reached, fast ended, symptom picked), never at random. |
| P3 | **One thing at a time** | Cognitive-load theory | At most one automatic suggestion visible; sheets show ≤ 3 cards, most relevant first. |
| P4 | **Progressive disclosure** | Chunking | Title → claim → *Why* / *What to do* (expand) → source and label (expand). |
| P5 | **Novelty, not nagging** | Habituation; banner blindness | Each card surfaces automatically at most 3 times ever; unseen cards rotate first. |
| P6 | **Recognition over recall** | Hick's law; recognition > recall | "How's it going?" chips instead of search for symptoms, cravings and stalls. |
| P7 | **Efficacy with every warning** | Extended parallel process model (fear without a remedy backfires) | Every caution or stop card carries a *What to do*; calm, neutral styling, no red alarms. |
| P8 | **Non-punitive language** | What-the-hell effect; self-compassion | No "failed" or "broken" wording; ending early counts; a nibble doesn't reset the timer. |
| P9 | **No amplification** | Autonomy; reactance; reward-driven escalation | Facts speak for themselves. No praise, social comparison or rewards for fasting longer, no streaks, no "almost there" nudges. The end-of-fast sheet shows the existing summary and one practical break-fast card. |
| P10 | **No nocebo priming** | Nocebo effect; expectation shapes symptoms | Discomfort cards never surface automatically; they answer only when the reader opens a stage, a pager or "How's it going?". Warnings follow the answer, not precede it. |
| P11 | **Calibrated trust** | Credibility research | Quiet evidence label (*established / emerging / early research / clinical experience*) plus source on tap. Nothing presented as more certain than it is. |

## 3. Surfaces

### A. Stage sheet (existing `JourneyStageSheet`): primary surface
- Keeps its stage text, corrected per `AUDIT-stage-copy.md`.
- Adds a **"Go deeper"** section below the stage text: up to 3 cards whose `when` includes this
  stage, from enabled categories. It's collapsed to titles, and a tap expands a card in place.
- Order: safety cards for selected conditions first, then unseen cards, then by grade (A → D).
- Zero cost to anyone who doesn't scroll.

### B. Stage tip chip (fasting screen, under the milestone rows)
- When a new stage is reached during an active fast, a small text chip appears, e.g.
  "💡 Tip: Day two is the hardest". The words are required (see the "chrome icons need words" memory).
- The chip is not random, and never a discomfort card (P10). It shows the most relevant card for the stage: safety first, then cards
  not yet seen, then stronger evidence. The choice is fixed per fast and stage (stored in prefs), so it
  survives recomposition and app restarts.
- Tapping it opens a swipeable pager: the chosen card first, then the rest of the stage's cards
  (up to 5 in total), with "1 of N" and page dots. It disappears when the next stage begins or after one tap.
- At most one per stage per fast, never animated beyond a fade, and never shown in the first 2 h.
- Toggle: Settings › Knowledge › "Tips during fasts" (default **on**). It's the only automatic
  surface in the whole design.

### C. "How's it going?" helper (fasting screen, low-emphasis text button near the end-fast action)
Opens a sheet of chips (P6) in three groups:
- **Body:** headache · dizzy · tired · cold · can't sleep · cramps · heartburn · bloated · loose stools · constipated
- **Mind:** hungry · craving · bored · stressed · slipped up
- **Progress:** scale stuck · restarting after a break

Each chip shows its matching cards (`when: symptom:*`, `hunger`, `plateau`, `between`).
- Any Body chip also shows FC-016 ("Hungry is fine. Sick is not."), after the cards that answer the chip (P10).
- "Slipped up" shows FC-180, FC-099 and FC-098: compassion first (P8).

### D. Starting and ending a fast
- **Start:** nothing new, except essentials gated `onboarding` on the very first fast (FC-001 and
  FC-029, one calm sheet, shown once).
- **Crossing 72 h:** the 72 h+ stage copy itself says to continue only under medical supervision
  (the FC-017 message); no extra banner.
- **End sheet (existing `EndFastSheet`):**
  - The existing summary, unchanged (P9).
  - Then one card: FC-072 ("Eat normally") for 12–24 h and FC-070 ("Break it gently") for 24–72 h,
    each at most 3 times ever; FC-070 every time for 72–120 h; FC-018 (refeeding, the card's own
    five-day threshold) from 120 h.

### E. Learn library (replaces the Info / Tips tabs, `res/raw/info.md` and `tips.md`)
- Sections follow the enabled categories in the order above; the stage cards follow dial order.
- A search field over titles and claims.
- **"Can I have…?"** quick-lookup row at the top: the drink cards (FC-060, FC-061, FC-067) answer
  the single most common fasting question in one tap.
- Every card expands in place, with its label and sources at the bottom.
- Disabled categories don't appear. The footer has three parts:
  - "More topics in Settings › Knowledge"
  - the "not medical advice" line
  - a "Sources & credits" link, also reachable from About › Acknowledgements. It shows the credits, the independence notice and the full bibliography from `references.json`.

### F. Profile-gated essentials (use data already in Profile; never ask again)
- Age ≥ 65 → FC-015 once, in the first-fast sheet or when age is entered.
- BMI < 20 → FC-001 once when starting a fast, repeated at most every 30 days.
- Sex set to female → one inline offer in Learn: "Show cycle-aware tips? [Turn on] [No thanks]".
  It's shown once. FC-021 (periods stopping) is shown once at the same moment. Caveat: `Gender`
  defaults to `Male`, so this only reaches users who actively chose female, which is the correct,
  privacy-respecting behaviour.

### G. Fast-days sheet (existing `FastDaysSheet`)
- When fast days are enabled: FC-160 and FC-020 at the bottom ("About fast days").
- If any of FC-133's conditions are selected: FC-133 too.

### H. Settings › Knowledge (new section, after Phases)
- Five category toggles, then "Health conditions" (off; turning it on reveals the chips).
- "Women's physiology" toggle.
- "Tips during fasts" toggle.
- "Show evidence labels" (on).
- "Reset seen tips".
- A greyed note: "Safety essentials are always on, and shown only when relevant."

## 4. What doesn't change
- No new notifications. Existing stage alerts stay opt-in, using the corrected stage copy.
- No new data collection. Gates use Profile and fast data the app already has; conditions are
  opt-in chips, stored locally and included in the existing backup.

## 5. Implementation plan

1. **Export step:** `knowledge/export_android.py` reads `core.jsonl` and writes:
   - `res/values/strings_knowledge.xml`: `kb_fc_030_title`, `_claim`, `_why`, `_do` (translatable;
     Crowdin picks them up). Sources and labels are `translatable="false"` or reuse 4 label strings.
   - `data/knowledge/KnowledgeCards.kt`: a generated list of `KnowledgeCard(id, cat, when, cond,
     gate, grade, safety, srcs, @StringRes …)`.
2. **Pure selection function:** `select(trigger, settings, profile, seen): List<KnowledgeCard>`.
   It handles category/condition filtering, gates, the exposure cap and ordering. It's
   unit-testable with no UI.
3. **Settings datasource:** enabled categories, selected conditions, tips toggle, labels toggle,
   and a per-card seen counter (existing settings store).
4. **UI:** in order A, E, H, then D, C, B, F, G. Each step is independently shippable.
5. **Stage copy:** apply `AUDIT-stage-copy.md`, including retitling 54–72 h and softening
   "Optimal autophagy".

Translation load: about 6,400 words per locale × 9 locales.
