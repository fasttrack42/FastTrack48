# Privacy Policy

- **Effective date:** 20 August 2026
- **Applies to:** the FastTrack48 Android application, all versions from 14.0725.23 onwards
- **Publisher / data controller:** `FastTrack42` (“we”, “us”)
- **Privacy contact:** `glwnd2030@gmail.com`
- **Source code:** https://github.com/fasttrack42/FastTrack48

---

## 1. Summary

FastTrack48 is an offline fasting tracker. It has **no user accounts, no servers, no
advertising, no analytics and no crash reporting**, and the app is not granted the Android
`INTERNET` permission, so it cannot transmit anything anywhere by itself.

Everything you enter stays in the app's private storage area on your device. We never
receive it, and we have no technical means of receiving it.

There are exactly three ways data can leave your device, all of them described in
section 4: Android's own cloud backup, an export or share that you start yourself, and
links you tap that open another app.

---

## 2. What the app stores on your device

FastTrack48 stores the following locally, in the app's private sandbox directory, using
an on-device database (Room), preference storage (DataStore) and encrypted file storage:

**Fasting data**
- start and end times of each fast, and the resulting duration
- notes you attach to a fasting entry
- derived figures such as time spent in ketosis or autophagy, and consecutive-day streaks

**Body profile (optional — the app is fully usable without it)**
- age, sex, height and weight, in metric or imperial units
- the BMI and BMR values calculated from them

**App settings**
- theme, locale, date and time format, notification and alert preferences, units
- which informational phases you have unlocked

We do not assign you an identifier of any kind. There is no account, no device ID, no
advertising ID, no email address and no telephone number. The app never asks for your
name, and does not read your contacts, location, camera, microphone, call logs, SMS,
installed-app list, or any health data held by other apps or by Health Connect.

### Special category data

Fasting history and body measurements are **health-related data**, treated as a special
category of personal data under Article 9 of the EU General Data Protection Regulation.
We apply the strictest handling to it: it is processed **only on your own device, only by
you**, and it is never transmitted to us or to any third party we control.

---

## 3. What we collect

**Nothing.** We operate no servers, no backend, no telemetry endpoint and no database.
We cannot see how many fasts you have logged, what you weigh, whether you opened the app
today.

The app bundles no advertising SDK, no analytics SDK, no crash-reporting SDK, no attribution
or tracking library, and no social-network SDK. Its third-party components are limited to
user-interface, storage and date-handling libraries that operate entirely offline.

### Permissions the app requests, and why

| Permission | Purpose |
| --- | --- |
| `POST_NOTIFICATIONS` | show the ongoing fast notification and stage-change alerts |
| `RECEIVE_BOOT_COMPLETED` | restore a fast in progress and its alerts after a restart |
| `VIBRATE` | vibrate on a stage-change alert, if you enable it |
| `FOREGROUND_SERVICE`, `WAKE_LOCK` | required by the Android components that schedule those alerts |

The app **does not request `INTERNET`**, and it explicitly removes the network-state
permission that one of its dependencies would otherwise add. This is verifiable: extract
any released APK or App Bundle and inspect its merged manifest, or read the manifest in
the public source repository.

---

## 4. The only ways data can leave your device

### 4.1 Android cloud backup (on by default, controlled by you)

FastTrack48 participates in Android's standard backup system. **If backup is enabled in
your Google account settings**, the Android operating system may copy the app's database
and preferences — which include your fasting history and body profile — to your personal
Google Drive backup, so that your data returns when you set up a new phone. The same
mechanism is used for device-to-device transfer.

This copy is made **by the operating system, not by the app**, into **your own** Google
account, and we have no access to it. Google acts as the provider of that backup service
and its handling is governed by the [Google Privacy Policy](https://policies.google.com/privacy).

You can prevent it at any time: **Settings → Google → Backup**, either by turning backup off
entirely or by excluding FastTrack48 from “App data”. Exact wording varies by manufacturer
and Android version.

### 4.2 Export and sharing that you initiate

You can export your logbook as CSV, as an iCalendar (`.ics`) file, or as Activity Streams
JSON, and you can send the result to any destination via the Android share sheet.

When you do this, **you** are choosing to move your health data out of the app. Once it
reaches another app, a cloud drive, a messaging service or an email provider, it is
governed by that recipient's own privacy policy and is outside our control and outside
the protections described in this document. Export files are written to the app's cache
directory and shared through a scoped `FileProvider`; they are not world-readable, and
temporary export files may be cleared by Android.

You can likewise import data from an EasyFast backup file that you supply. That file is
read locally and never transmitted.

### 4.3 Links that open other apps

Some screens contain links — the project's source repository and website, the original
upstream project, a Telegram channel, YouTube, and the Google Play listing. Tapping one
hands the link to your browser or to the relevant app, which then applies its own privacy
policy. We receive nothing when you do so, but the destination may log the visit as it
would any other.

### 4.4 Distribution through app stores

Installing and updating the app is a transaction between you and the store you obtained it
from (Google Play, F-Droid, or a directly downloaded APK). That store's own privacy policy
governs it. Google Play provides developers with aggregated, anonymous statistics such as
install counts and crash rates for the app as a whole; these are produced and aggregated by
Google, are not linked to you by us, and are not something the app itself reports.

---

## 5. Retention and deletion

Because your data lives only on your device, retention is entirely in your hands:

- delete individual entries in the journal
- clear the body profile in Settings
- delete all app data: **Android Settings → Apps → FastTrack48 → Storage → Clear storage**
- uninstalling the app removes its local data

If Android cloud backup has been enabled, a copy may persist in your Google account after
uninstall until it expires or you delete it from your Google backup settings.

We hold no copy of your data, so there is nothing for us to delete on request, and no
retention period for us to apply.

---

## 6. Your rights

### European Economic Area, United Kingdom and Switzerland

The GDPR grants you rights of access, rectification, erasure, restriction, portability and
objection, and the right not to be subject to solely automated decision-making.

Because the app processes your data exclusively on your own device and we never receive it,
we hold no personal data about you and therefore act as a controller for essentially no
processing. Practically, this means your rights are exercised **directly in the app**, and
immediately:

- **Access** — all your data is visible in the Journal and Settings screens
- **Rectification** — edit or correct any entry or profile field
- **Erasure** — delete entries, clear storage, or uninstall (section 5)
- **Portability** — export your complete history as CSV, iCalendar or Activity Streams JSON
- **Objection / restriction** — stop using a feature, revoke the notification permission, or
  disable Android backup

Where any processing does fall to us, the legal basis is your consent (Article 6(1)(a)) and,
for health-related data, your explicit consent (Article 9(2)(a)), given by entering the data;
consent is withdrawn by deleting it or uninstalling the app.

You have the right to lodge a complaint with your national data protection supervisory
authority. If you consider that we hold data about you, contact us at the address in
section 10 and we will respond within one month.

We do not carry out international transfers of personal data, because we do not receive
personal data.

### California

We do not sell personal information, and we do not share it for cross-context behavioural
advertising, as those terms are defined by the CCPA/CPRA. We do not collect personal
information from you at all, so there is no category of information collected, disclosed
or sold to enumerate. We do not use or disclose sensitive personal information for any
purpose requiring a right to limit. We do not discriminate against anyone for exercising
a privacy right.

### Other jurisdictions

The same position applies under comparable laws — including Brazil's LGPD, Canada's PIPEDA,
Australia's Privacy Act and Virginia's, Colorado's and Connecticut's consumer privacy
statutes: no data reaches us, so none is collected, sold, shared or profiled.

---

## 7. Children

FastTrack48 is a general-audience application intended for adults. It is not directed to
children, is not designed for or targeted at children, and is not enrolled in Google Play's
Families programme. We do not knowingly collect personal information from anyone, including
children under 13 (or under 16 in jurisdictions where that is the applicable age of digital
consent).

Fasting is not appropriate for children and adolescents without medical supervision. Parents
and guardians should not encourage its use by a minor without qualified medical advice.

---

## 8. Security

Your data is held in the app's private storage area, which Android isolates from other
applications, and preference storage additionally uses encrypted file storage. On a device
with a screen lock and full-disk or file-based encryption enabled — the default on modern
Android — that data is encrypted at rest by the operating system.

No safeguard is absolute. Data on a device that has been rooted, modified, infected with
malware, or physically handed to someone else may be accessible to that party. We can offer
no protection in those circumstances, and we cannot protect data after you have exported it
somewhere else. Keeping a device lock enabled, keeping Android updated, and installing the
app only from a reputable source are the meaningful protections available to you.

We do not operate an infrastructure that could suffer a data breach affecting your fasting
data. Should a security defect be found in the app itself, we will publish a fix and describe
the issue in the public repository.

---

## 9. Health and medical disclaimer

FastTrack48 is a **general wellness and self-tracking tool**. It is **not a medical device**,
is not certified as one under EU Regulation 2017/745 or by the U.S. Food and Drug
Administration, and is not intended to diagnose, treat, cure, mitigate or prevent any disease
or condition.

The information the app shows about physiological phases of fasting is **general educational
material** describing processes commonly described in the literature. It is a simplified
timeline based on elapsed time only. It is not a measurement, does not describe what is
actually happening in your body, and does not constitute medical advice, a diagnosis, or a
treatment recommendation. Individual responses to fasting differ substantially.

---

## 10. Contact

Privacy questions, requests and complaints:

- Email: `glwnd2030@gmail.com`
- Issue tracker: https://github.com/fasttrack42/FastTrack48/issues

Please do not include health information or other personal data in a public issue report.

---

## 11. Changes to this policy

If this policy changes materially — in particular if the app ever gains network access or
begins collecting anything — we will update this document, change the effective date at the
top, and describe the change in the release notes for the version that introduces it. The
full revision history of this document is public in the source repository. Continuing to use
the app after a change indicates acceptance of the revised policy; if you do not accept it,
uninstall the app.

---

## 12. Disclaimer of warranty and liability

FastTrack48 is free and open-source software provided **“as is”**, without warranty of any
kind, express or implied, including the implied warranties of merchantability, fitness for a
particular purpose and non-infringement, in accordance with the MIT Licence under which it is
distributed. You use it at your own risk. To the fullest extent permitted by law, the authors
and copyright holders are not liable for any claim, damages or other liability arising from
the software or its use, including any decision you take about your health or diet.

**Nothing in this section limits or excludes any liability that cannot lawfully be limited or
excluded.** In particular, it does not affect liability for death or personal injury caused by
negligence, for fraud, or your mandatory statutory rights as a consumer under the law of your
country of residence, including under EU and UK consumer protection law.

---

## 13. Attribution

FastTrack48 is a derivative work — a fork — of FastTrack by Adam Brown (@Wavesonics),
published by Dark Rock Studios and licensed under the MIT Licence. This privacy policy covers
FastTrack48 only. Adam Brown and Dark Rock Studios do not maintain, endorse or support
FastTrack48 and bear no responsibility for it, including for this policy. Direct all questions
about FastTrack48 to the contacts in section 10, never to the original project.
