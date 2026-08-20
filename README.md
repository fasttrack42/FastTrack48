[![GitHub](https://img.shields.io/github/v/release/fasttrack42/FastTrack48?include_prereleases&logo=github)](https://github.com/fasttrack42/FastTrack48/releases/latest)

# FastTrack48 

Free and Open Source Android app for tracking Intermittent/Prolonged Fasting.

> **FastTrack48 is a fork of [FastTrack](https://github.com/Darkrock-Studios/FastTrack) by
> [Adam Brown](https://github.com/Wavesonics) (Dark Rock Studios), used under the MIT License.**
> See [Attribution](#attribution) below.


[<img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/master/get-it-on-github.png" alt="Get it on GitHub" height="80">](https://github.com/fasttrack42/FastTrack48/releases/latest) [<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="80">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/{"id":"com.legbehindneck.fasttrack48","url":"https://github.com/fasttrack42/FastTrack48","author":"fasttrack42","name":"FastTrack48"})<!--  [<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80">](https://apt.izzysoft.de/fdroid/index/apk/com.legbehindneck.fasttrack48) [<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80">] --> (https://play.google.com/store/apps/details?id=com.legbehindneck.fasttrack48) 


<!--
[![F-Droid](https://img.shields.io/f-droid/v/com.legbehindneck.fasttrack48?logo=FDROID)](https://f-droid.org/en/packages/com.legbehindneck.fasttrack48/)
-->

Intermittent fasting has many benefits! But it can be hard to stay motivated during a fast.

This is a simple FOSS app intended to help keep you motivated. It is not a medical app and does not provide medical advice.

FastTrack48 tells you what changes are happening to your body while you fast, and you'll learn more
about each phase as you progress:

<div align=center>
<img src="https://github.com/fasttrack42/FastTrack48/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" height=640><img src="https://github.com/fasttrack42/FastTrack48/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" height=640><img src="https://github.com/fasttrack42/FastTrack48/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" height=640>


</div>

**Disclaimer** There's no pharma or food-industry profit motive to fund *rigorous, expensive, invasive* human trials (serial muscle biopsies, isotope tracers), hence, the data below is mostly derived from rodent studies 🐁 🐭, anecdotal testimonies 🗣️ and deep inference 🔍 🧩 🧩 🧩 🎯.

| Time Window | Labeled Process | Evidence Class | Basis |
|---|---|---|---|
| 0–8h | Post-absorptive, glycogenolysis | [FACT] | Standard glucose/insulin kinetics, directly measured in humans repeatedly |
| 8–16h | Rising gluconeogenesis, insulin ↓ | [FACT] | Well-established hepatic metabolism |
| 12–18h | Fat burning (lipolysis ↑) | [FACT] | Directly measured (plasma FFA, RQ) |
| 16–24h | Ketosis onset (BHB rises) | [FACT] | Directly measured; individual variance large — some reach it by 12h (low glycogen), others not till 30h+ |
| 18–72h+ | Autophagy | [INFERENCE] | No live human autophagy assay existed pre-2021. Best current human surrogate data (Bensalem/SAHMRI PBMC flux assay, 2022–2025) shows detectable flux changes at ~16h refeeding contrast and after 3-day water fasts in a randomised controlled crossover trial investigating three-day water-only fasting with or without exercise-induced glycogen depletion on autophagic activation, but a formal RCT running iTRE (20h fasts, 3x/week) for 6 months found flux increased only at the 6-month cohort level, not within-group from baseline in 121 humans with obesity randomized to standard care, calorie restriction, or intermittent fasting plus time-restricted eating, autophagic flux was significantly higher in the iTRE group compared to standard care after 6 months, though there was no significant increase from baseline within the iTRE group itself. The 24–48h figure is [INFERENCE] extrapolated from rodent mTOR-suppression kinetics, not measured onset in living humans. A parallel human RCT registered specifically to nail this kinetic question postulated an increase in autophagy between 12 and 36h of fasting based on theoretical transfer from animal experimental data, and a possible decrease after several days, noting no human studies had yet focused on the size and temporal kinetics of the effect |
| 24–48h | Autophagy peak | [INFERENCE] | No human peak has been located; claims of a 48h peak trace back to rodent studies |
| 48–54h | GH spike (5x baseline) | [FACT, magnitude confirmed] | GH pulse amplitude increase during fasting is real and repeatedly measured (1988 Ho et al., n=9, confirmed since); the tight 48–54h bracket in pop-science charts is a rounding of a continuous, individually-variable rise, not a threshold |
| 54–72h | Insulin sensitivity ↑ | [FACT] | Insulin sensitivity does rise with fasting duration and fat loss, but as a smooth dose-response curve tracked over days-to-weeks, not a switch flipped at hour 54 |
| 72h+ | mTOR ↓50%, protein catabolism ↑ | [FACT] | A 72-hour fast in eight healthy male volunteers significantly increased forearm net phenylalanine release and tended to decrease phenylalanine rate of disappearance, with mTOR phosphorylation decreased by approximately 50% following fasting — this is catabolic stress, not unambiguously "beneficial regeneration" |
| 72h+ | Immune cell regeneration | [ANECDOTAL, single-study] | Traces to one 2014 USC paper (Cheng et al., *Cell Stem Cell*) on **prolonged fasting + refeeding cycles in mice and a small human chemo-patient cohort**, showing stem-cell-mediated hematopoietic regeneration on refeeding — not fasting itself, and not independently replicated at scale in healthy humans |

**Notes**
- the underlying biology is a continuum with wide individual variance with glycogen stores, muscle mass, prior fasting adaptation, sex, age all shifting these windows by 6–24h in either direction.
- self-reported fasting-community data (r/fasting, Longo's own case reports) consistently claims subjective mental clarity and appetite suppression peaking days 2–3 — this is real pattern-of-report data but confounded by ketone-mediated euphoria and dehydration/electrolyte effect.

The calculations are based on a "rule-of-thumb" quality model for determining what fasting stage you are currently in. It has been to some degree
validated through real world testing using a ketone breath meter, as well as reading medical studies on the matter, but even still, it is just a rule-of-thumb. There are many things that would affect what stage you are in on a particular fast.

## Privacy

We don't collect any data whatsoever. The app doesn't have the INTERNET permission, and operates fully offline.

## Development

FastTrack48 is in active development; feel free to log bugs, request features, and join us on
[Telegram](https://t.me/FastTrack48)!

Pull requests are very much welcomed, feel free to open an issue to discuss it first though to make sure it is a
direction we want to go.

#### Releasing

See [docs](docs/HOW-TO-RELEASE.md) on how to publish a new release.

## Attribution

FastTrack48 is a **modified derivative work** — a direct fork — of **FastTrack**, created by
**Adam Brown** ([@Wavesonics](https://github.com/Wavesonics)) and published by
[Dark Rock Studios](https://darkrock.studio/).

| | |
|---|---|
| Original project | <https://github.com/Darkrock-Studios/FastTrack> |
| Original author | Adam Brown — <https://github.com/Wavesonics> |
| Publisher | Dark Rock Studios — <https://darkrock.studio/> |
| Original licence | MIT — Copyright © 2020 Adam Brown |

The original software remains Copyright © 2020 Adam Brown and is made available under the MIT
License. That licence continues to govern the original portions of this app, and its full text is
preserved verbatim in [`LICENSE`](LICENSE) and reproduced inside the app itself, under
**About → Acknowledgements**.

Every modification, addition and design change in FastTrack48 is the sole work and responsibility of
this project. Adam Brown and Dark Rock Studios do not maintain, endorse, sponsor or support
FastTrack48 and bear no responsibility for it. Please direct all issues, questions and support
requests about FastTrack48 to [this repository](https://github.com/fasttrack42/FastTrack48/issues) —
never to the upstream project.

The names "FastTrack", "Dark Rock Studios" and "Wavesonics" appear here solely to identify the origin
of the source code, as permitted by nominative fair use. They do not imply any affiliation with, or
endorsement by, their respective owners.

If you like what this app does, please also go and star
[the original project](https://github.com/Darkrock-Studios/FastTrack) — none of this would exist
without it.

## License

MIT — see [`LICENSE`](LICENSE).

```
Copyright (c) 2020 Adam Brown
Copyright (c) 2026 FastTrack48 contributors
```
