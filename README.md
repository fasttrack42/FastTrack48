[![GitHub](https://img.shields.io/github/v/release/fasttrack42/FastTrack48?include_prereleases&logo=github)](https://github.com/fasttrack42/FastTrack48/releases/latest)

# FastTrack48 

Free and Open Source Android app for tracking Intermittent/Prolonged Fasts.

> **FastTrack48 is a fork of [FastTrack](https://github.com/Darkrock-Studios/FastTrack) by
> [Adam Brown](https://github.com/Wavesonics) (Dark Rock Studios), used under the MIT License.**
> See [Attribution](#attribution) below.

<div align="center">
  
<a href="https://github.com/fasttrack42/FastTrack48/releases/latest"><img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/master/get-it-on-github.png" alt="Get it on GitHub" height="80"><a/> <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22:%22com.legbehindneck.fasttrack48%22,%22url%22:%22https://github.com/fasttrack42/FastTrack48/%22,%22author%22:%22FatTrack48%22,%22name%22:%22FastTrack48%22%7D"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a><a href="https://play.google.com/store/apps/details?id=com.legbehindneck.fasttrack48"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80"></a>

<!--  <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80">](https://apt.izzysoft.de/fdroid/index/apk/com.legbehindneck.fasttrack48) [](https://play.google.com/store/apps/details?id=com.legbehindneck.fasttrack48)
-->
<!--
[![F-Droid](https://img.shields.io/f-droid/v/com.legbehindneck.fasttrack48?logo=FDROID)](https://f-droid.org/en/packages/com.legbehindneck.fasttrack48/)
-->
</div>

FastTrack48 tells you what changes are happening to your body while you fast, and you'll learn more
about each phase as you progress.  It is not a medical app and does not provide medical advice.

<div align=center class=scrn>
<img src="https://raw.githubusercontent.com/fasttrack42/FastTrack48/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" height="432"><img src="https://raw.githubusercontent.com/fasttrack42/FastTrack48/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" height="432"><img src="https://raw.githubusercontent.com/fasttrack42/FastTrack48/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" height="432">
</div>

# Features

## During a fast
- Radial dial showing elapsed time and the phase you are currently in.
- Ten stages from the last meal to 72 hours+: blood sugar rise, blood sugar fall, blood sugar stabilises, gluconeogenesis, fat burning, ketosis, autophagy, growth hormone surge, insulin sensitivity, immune cell regeneration.
- At each stage you get an explaination of what is happening in your body, with references.
- Tappable bubbles for each phase — glucose burning, fat burning, ketosis, autophagy, optimal autophagy.
- Phases can appear only once you reach them, or be shown from the start; each is individually toggleable.
- Ongoing notification display elapsed time and current stage.
- Notifications when you enter a new stage.
- Home screen widget .
- Launcher shortcuts: start a fast, start now, end fast.
- Share your current or finished fast as text.
- Six-screen intro explaining the app on first launch.

## Fasting History
- Every fast recorded with start time, total length, hours in ketosis and hours in autophagy.
- Add text notes for any entry.
- List and calendar views of your previous fasts.
- Tap any past day in the calendar to add a fast for it.
- Manual entry: set start date and time plus a duration, or set an end time and let the app calculate.
- Edit or delete any entry, including backdating a start you forgot to log.
- Clear the whole logbook.

## Import and export
- Export as CSV (Excel-compatible).
- Export as iCalendar (.ics) to load your fasts into any calendar app.
- Export as ActivityStreams 2.0 (JSON-LD).
- Import a previous export back into the app.
- Import EasyFast .zip backups; overlapping entries are skipped to avoid duplicates.

## Body profile
- Age, sex, height and weight, in metric or imperial.
- BMI with its category, and BMR in kcal per day.

## Appearance and settings
- Light, dark, or follow the system theme.
- Animated or plain background.
- Configurable date and time format.
- Metric / imperial units.
- Toggles for the ongoing notification and for stage alerts.

## Privacy
- No internet permission. The app is technically incapable of transmitting anything.
- No account, no sign-up, no ads, no analytics, no crash reporting, no trackers, no third-party SDKs.
- All data stays in the app's private storage on the device.
- Free and open source under the MIT licence; the full source is public and the build is reproducible from it.

## Localisation Languages
English, German, Spanish, French, Italian, Dutch, Portuguese (Brazil), Ukrainian, Chinese (Simplified).

---

**Disclaimer** There's no pharma or food-industry profit motive to fund *rigorous, expensive, invasive* human trials (muscle biopsies, isotope tracers, etc); the data below is derived from rodent studies 🐁 🐭, anecdotal testimonies 🗣️ and personal experiences.

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
- the actual time windows can vary depending on your glycogen stores, muscle mass, prior fasts, sex, age and can drift 6–24h in either direction.

The calculations are based on a "rule-of-thumb" quality model for determining what fasting stage you are currently in. It has been to some degree
validated through real world testing using a ketone breath meter, as well as reading medical studies on the matter, but even still, it is just a rule-of-thumb. There are many things that would affect what stage you are in on a particular fast.

## Development

FastTrack48 is in active development; feel free to log bugs, request features, and join us on
[Telegram](https://t.me/FastTrack48)!


## Attribution

FastTrack48 is a **modified derivative work** — a fork — of **FastTrack**, created by
**Adam Brown** ([@Wavesonics](https://github.com/Wavesonics)) and published by
[Dark Rock Studios](https://darkrock.studio/).

| | |
|---|---|
| Original project | <https://github.com/Darkrock-Studios/FastTrack> |
| Original author | Adam Brown — <https://github.com/Wavesonics> |
| Publisher | Dark Rock Studios — <https://darkrock.studio/> |
| Original licence | MIT — Copyright © 2020 Adam Brown |

The original software remains Copyright © 2020 Adam Brown and is made available under the MIT
License which continues to govern the original portions of this app, its full text
preserved verbatim in [`LICENSE`](LICENSE) and reproduced inside the app under
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
