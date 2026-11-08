# Audit: existing in-app fasting copy vs knowledge base

Scope: `fasting-stages.md` / `journey_stage_*` strings, stage notifications, log totals.
Status: items 1–7 applied on 2026-11-07, in all 9 locales (`journey_stage_*`, `stage_*`, `notification_*`, `log_*_autophagy`) and in `fasting-stages.md`. Item 8 needed no change. Item 9 is covered by the knowledge cards.

| # | Where | Current copy | Finding | Fix | Source card |
|---|---|---|---|---|---|
| 1 | 48–54 h stage | "a ~2-day fast can raise GH secretion roughly 5-fold (Ho, J Clin Invest 1988)" | Wrong citation. Ho 1988 is a 5-day fast. The 2-day ~5× figure is Hartman 1992. | Cite Hartman 1992 J Clin Endocrinol Metab. | CGF-01-013 |
| 2 | 48–54 h stage | "That unexpected lift in energy… That's the hormone talking." | Attributes energy to GH. The energy effect is better explained by noradrenaline (Zauner 2000). | Credit adrenaline/noradrenaline, or drop the attribution. | CGF-01-012 |
| 3 | 72 h+ stage | Immune-cell regeneration stated as fact (Cheng 2014) | Mostly mouse data; the human evidence is a small chemotherapy pilot. | Frame as "in animal studies". | CGF-07-011 |
| 4 | 54–72 h stage | "insulin sensitivity resets toward a healthier baseline" | VERIFIED WRONG for the acute state. A 60 h fast *decreased* muscle insulin sensitivity in healthy men (clamp; Hoeks 2010 Diabetes, PMID 20573749). It's physiological glucose sparing that reverses on refeeding. The improvement comes from repeated fasting and fat loss over weeks. | Retitle the stage (e.g. "Deep fat-burning" / "Glucose sparing"). Move the insulin-sensitivity message to a between-fasts/long-term card. | SCI-90-001, CGF-00-003 |
| 5 | "Optimal autophagy" stage/notification; log "Autophagy: N hours" | Implies autophagy timing is known and measured | No validated human timing exists; hour thresholds come from animal studies. | Soften the title (e.g. "Deep autophagy (est.)"), or note "estimated" in log totals. | CGF-07-008 |
| 6 | 24–48 h stage | "clearance associated with longevity and lower disease risk" | The longevity link is animal and mechanistic data. | Add "in animal studies". | CGF-04-007, CGF-07-006 |
| 7 | 18–24 h ketosis | ~0.5 mmol/L crossed in 18–24 h | Plausible as "typical"; Anton 2018 puts the metabolic switch at 12–36 h depending on glycogen. Fung writes 36–48 h for ketones to "ramp up". | Keep the timing; add "for many people; varies with your last meal and activity". | SCI-14-001 |
| 8 | 8–12 h, 12–18 h | Gluconeogenesis, then fat burning | Consistent with Rothman 1991. Fung's own timeline is later (24 h–2 d), but the app's timing is better supported. | No change; optionally cite Rothman 1991. | CGF-01-006 |
| 9 | Stage coverage | 0–2, 2–5, 5–8 h have no core knowledge cards | Gap. | Step 3/4: source short-window cards (post-meal glucose, insulin fall, first hunger wave). | — |

Product decision (2026-11-08): the 54–72 h and 72 h+ titles stay "Insulin sensitivity" and "Immune cell regeneration". They are topic labels. The stage text carries the corrections from items 3 and 4: an acute dip that reverses on refeeding, and a mouse-only finding.
