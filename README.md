[![GitHub](https://img.shields.io/github/v/release/fasttrack42/FastTrack48?include_prereleases&logo=github)](https://github.com/fasttrack42/FastTrack48/releases/latest)

# FastTrack48 

Free and open-source Android app that tracks your fast and explains what is happening in your body as it progresses.

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

FastTrack48 explains the physiological changes associated with fasting and estimates which stage of a fast you may be in as it progresses. It is not a medical app and does not provide medical advice.

<div align=center class=scrn>
<img src="https://raw.githubusercontent.com/fasttrack42/FastTrack48/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.png" height="432"><img src="https://raw.githubusercontent.com/fasttrack42/FastTrack48/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.png" height="432"><img src="https://raw.githubusercontent.com/fasttrack42/FastTrack48/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.png" height="432">
</div>

# Features

## During a fast
- Radial dial showing elapsed time and the phase you are currently in.
- Ten fasting phases from the post-meal period through 72+ hours, covering changes in blood glucose, gluconeogenesis, fat metabolism, ketosis, autophagy, growth hormone, insulin sensitivity, and immune-cell regeneration.
- Each phase explains the associated physiological changes, with references to the underlying research.
- Tap any phase to learn more about glucose burning, fat burning, ketosis, and other optimal autophagy.
- You can choose whether upcoming phases remain hidden until reached or are visible from the start; each phase can be toggled individually.
- Ongoing notification display elapsed time and current stage.
- Notifications when you enter a new stage.
- Home screen widget.
- Launcher shortcuts: start a fast, start now, end fast.
- Share your current or finished fast as text.
- Six-screen intro explaining the app on first launch.

## Fasting History
- Each recorded fast shows its start time, total duration, hours in ketosis, and hours in autophagy.
- Add text notes to any fast.
- View previous fasts in list or calendar view.
- Tap any past day in the calendar to add a fast.
- Manually add a fast by entering its start date and time plus its duration, or by specifying an end time.
- Edit or delete any fast, including adding a forgotten fast with a backdated start time.
- Clear the entire fasting history.

## Import and export
- Export fasting history as CSV, iCalendar (.ics), or ActivityStreams 2.0 (JSON-LD).
- Import previous exports, including EasyFast `.zip` backups; overlapping entries are skipped to prevent duplicates.

## Body profile
- Age, sex, height, and weight, with metric or imperial units.

## Settings
- Light, dark, or system theme.
- Animated or static background.
- Configurable date and time format.
- Metric or imperial units.
- Independent controls for the ongoing notification and stage alerts.

## Privacy
No Internet permission. The app cannot make network connections.
- No account, no sign-up, no ads, no analytics, no crash reporting, no trackers, no third-party SDKs.
- All data stays in the app's private storage on the device.
- Free and open source under the MIT License. The complete source code is public, and the build is reproducible from it.

## Languages
- English, German, Spanish, French, Italian, Dutch, Brazilian Portuguese, Ukrainian, and Simplified Chinese.

---

Evidence for fasting physiology varies considerably by process. Some fasting responses are well established in humans, while others—particularly the timing of autophagy and claims about peaks —remain difficult to measure directly in living humans.

| Approx. window | Process                        | Human evidence   | Timing confidence |
| -------------- | ------------------------------ | ---------------- | ----------------- |
| 0–8h           | Post-absorptive / glycogen use | Strong           | High              |
| 8–16h          | Increasing gluconeogenesis, insulin ↓     | Strong           | Moderate          |
| 12–18h         | Fat burning (lipolysis ↑)         | Strong           | Moderate          |
| 16–24h         | Ketone production increases    | Strong           | Moderate          |
| 18-72h+           | Autophagy     | Limited/indirect | Low               |
| 48h+           | Increased GH spike (5x baseline)         | Strong           | Moderate          |
| 52h+           | Insulin sensitivity      | Strong           | Moderate          |
| 72h+           | mTOR ↓50%, protein catabolism ↑, Immune cell regeneration	  | Human evidence   | Moderate          |


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
preserved verbatim in [`LICENSE`](https://github.com/fasttrack42/FastTrack48/blob/master/LICENSE) and reproduced inside the app under
**About → Acknowledgements**.

Every modification, addition and design change in FastTrack48 is the sole work and responsibility of
this project. Adam Brown and Dark Rock Studios do not maintain, endorse, sponsor or support
FastTrack48 and bear no responsibility for it. Please direct all issues, questions and support
requests about FastTrack48 to [this repository](https://github.com/fasttrack42/FastTrack48/issues) —
never to the upstream project.

The names “FastTrack”, “Dark Rock Studios”, and “Wavesonics” are used solely to identify the upstream project, publisher, and author. This project is not affiliated with or endorsed by them.


## License

MIT — see [`LICENSE`](https://github.com/fasttrack42/FastTrack48/blob/master/LICENSE).

```
Copyright (c) 2020 Adam Brown
Copyright (c) 2026 FastTrack48 contributors
```
