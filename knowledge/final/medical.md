# Final cards: Health conditions & medications. Off by default; each card is tagged with the conditions it applies to (cond:).

### FC-002
title: Diabetes medication? Plan first
claim: Fasting on insulin or diabetes pills without a plan can cause dangerous lows. Your doctor will usually lower doses on fasting days.
why: Most diabetes drugs are dosed for days you eat. Remove the food and the same dose overshoots; repeated lows mean the dose is too high, not that fasting failed.
do: Agree on doses and target ranges with your doctor, check sugar 2–4 times a day, and never adjust insulin on your own.
grade: A
safety: stop
when: onboarding | plan | start
aud: t2d, meds
cond: diabetes-meds
from: CGF-06-006, CGF-06-007, CGF-15-017, CGF-06-008, CGF-03-007, CGF-00-002, CGF-16-008

### FC-003
title: Know the signs of a low
claim: Shaking, sweating, sudden hunger, nervousness, confusion or faintness can mean low blood sugar, especially if you take diabetes medication.
why: Without medication, the liver keeps glucose steady (often 60–75 mg/dL in long fasts, without symptoms). With medication it can crash quickly, and severe lows are dangerous.
do: Take sugar right away (juice, glucose tablets), even though it ends the fast, then call your doctor.
grade: A
safety: stop
when: symptom:dizzy | start | stage:12-20 | long
aud: t2d, meds, all
cond: diabetes-meds
from: CGF-10-006, CGF-00-017

### FC-004
title: Ketosis is not ketoacidosis
claim: Fasting ketosis is normal. Ketoacidosis is different and dangerous, and it's a risk with type 1 diabetes or SGLT2 drugs (names ending in "-gliflozin").
why: In ketosis, insulin is low but present and ketones are burned as fuel. In ketoacidosis, too little insulin lets ketones pile up, sometimes even with normal blood sugar.
do: Type 1 diabetes or a "-gliflozin" drug: don't fast without your doctor's explicit plan.
grade: A
safety: stop
when: onboarding | stage:20-24 | myth
aud: meds, t2d, all
cond: t1, diabetes-meds
from: CGF-05-011

### FC-005
title: When fasting stops lowering sugar
claim: If fasting no longer brings your blood sugar down, ask your doctor about LADA, a slow autoimmune diabetes often mistaken for type 2.
why: In LADA insulin is low rather than high, so fasting helps far less and ketoacidosis becomes a risk. C-peptide and antibody tests tell them apart.
grade: B
safety: caution
when: plateau | long | between
aud: t2d
cond: t2d
from: YT-06-009

### FC-006
title: Blood-pressure pills and fasting
claim: Fasting lowers blood pressure. On blood-pressure pills or diuretics it can drop too low, causing light-headedness when you stand.
why: Falling insulin makes the kidneys shed salt and water, which adds to the medication's effect.
do: Check your pressure at home while fasting, rise slowly, and let your doctor adjust doses. Never stop heart medication on your own.
grade: A
safety: caution
when: onboarding | start | long
aud: meds
cond: bp-heart
from: CGF-01-009, CGF-15-018, CGF-08-005

### FC-007
title: Medicines that need food
claim: Aspirin, metformin, iron and magnesium pills can upset an empty stomach. Ask whether to take them in your eating window.
why: Aspirin irritates the stomach lining; metformin causes nausea or diarrhea; iron causes pain and constipation; magnesium causes loose stools. Food buffers them.
do: Never just skip a prescribed medicine. Your pharmacist can help you time doses.
grade: A
safety: caution
when: onboarding | start
aud: meds
cond: daily-meds
from: CGF-10-005

### FC-008
title: Steroids: never skip doses
claim: If you take steroid replacement for adrenal insufficiency, or long-term prednisone, don't fast without your endocrinologist, and never skip a dose.
why: Without enough cortisol the body can't hold up blood sugar and blood pressure during a fast; adrenal crisis is a real risk.
grade: A
safety: stop
when: onboarding | plan
aud: meds
cond: steroids
from: SCI-91-009

### FC-009
title: Lithium or warfarin? Check first
claim: On lithium, dehydration or less salt can push levels toward toxicity. On warfarin, changing how you eat can shift your INR.
why: Kidneys handle lithium like sodium and hold it when salt and water fall. Warfarin's effect depends on steady vitamin K intake and liver metabolism.
do: Agree a plan with your prescriber, stay well hydrated, and get levels checked more often when you start.
grade: B
safety: caution
when: onboarding | plan | fluids
aud: meds
cond: lithium, warfarin
from: SCI-91-008, SCI-91-010

### FC-010
title: Thyroid medication and fasting
claim: On thyroid medication, recheck your levels when you start fasting or lose weight. The dose you need may change.
why: Weight loss and calmer inflammation can lower the requirement; symptoms are a poor guide. Fast longer only once levels are stable, not while a dose is being adjusted.
do: Racing heart, anxiety, heat intolerance or poor sleep while fasting: get your thyroid checked.
grade: B
safety: caution
when: onboarding | between | plan
aud: meds
cond: thyroid
from: YT-07-001, YT-07-002, SCI-91-011

### FC-011
title: On Ozempic-type drugs
claim: On semaglutide or tirzepatide, prioritize protein, strength training and fluids over longer fasts, and plan any fasting with your prescriber.
why: These drugs already cut appetite steeply, so muscle loss and dehydration from nausea become the risks. Combined with insulin or sulfonylureas, lows become likelier.
do: Use the time to build lasting habits: within a year of stopping, two-thirds of the lost weight typically returns.
grade: B
safety: caution
when: onboarding | plan | long
aud: meds, t2d
cond: glp1
from: SCI-91-006, SCI-91-007

### FC-012
title: Gout or kidney stones
claim: Fasting raises uric acid. If you've had gout or kidney stones, talk to your doctor before longer fasts, and drink plenty.
why: Ketones compete with uric acid for removal by the kidneys, and less fluid concentrates urine. About 6% on long-term ketogenic diets developed stones.
do: Keep urine pale; ask whether to continue uric-acid medication during fasts.
grade: B
safety: caution
when: onboarding | long | fluids
aud: all, meds
cond: gout-stones
from: CGF-10-004, SCI-91-012

### FC-013
title: Kidney, heart or cancer: specialist first
claim: With kidney disease, heart disease or cancer, fast only with your specialist's agreement, even though early studies look promising.
why: Small trials show benefits (better heart recovery after infarction, less kidney protein in diabetes), but electrolytes, medications and weight loss carry bigger risks in these conditions.
do: With kidney disease, take magnesium or potassium supplements only with your doctor's approval.
grade: B
safety: caution
when: onboarding | plan | long
aud: meds, all
cond: kidney, bp-heart, cancer
from: YT-04-004, YT-02-009, CGF-07-010

### FC-014
title: Gallstones and fast weight loss
claim: Rapid weight loss raises gallstone risk. Include some natural fat at meals, and check with your doctor if you've had gallbladder attacks.
why: Bile that sits unused in the gallbladder can crystallize into stones. Very low-fat eating and rapid loss both increase stasis.
grade: B
safety: caution
when: onboarding | long
aud: all
cond: gallbladder
from: YT-06-011

### FC-028
title: Blood-sugar herbs add up
claim: Bitter melon, cinnamon and some teas may lower blood sugar. On diabetes medication, that adds to the risk of lows.
why: Their modest glucose-lowering effect stacks on top of the drug's, and a fast removes the food that buffers it.
grade: C
safety: caution
when: fluids
aud: t2d, meds
cond: diabetes-meds, t2d
from: CGF-16-004, YT-04-013

### FC-123
title: PCOS and insulin
claim: PCOS, the most common hormonal disorder in women of reproductive age, is driven by insulin resistance, even in lean women.
why: High insulin pushes the ovaries to overproduce androgens and disrupts ovulation; lowering insulin often restores regular cycles.
do: Irregular periods, acne or excess hair with belly weight? Ask your doctor about PCOS.
grade: A
safety: none
when: onboarding
aud: women
cond: pcos
from: YT-06-001, CGF-01-022

### FC-130
title: Type 2 is too much insulin
claim: In type 2 diabetes insulin is usually high, not low, and it can often be put into remission through diet.
why: Fat in the liver and pancreas drives resistance and impairs insulin release; losing it can restore control, especially early in the disease.
do: Track remission with your doctor through HbA1c. Never stop medication on your own.
grade: A
safety: caution
when: onboarding | stage:54-72
aud: t2d
cond: t2d
from: CGF-00-001, CGF-00-004, CGF-06-004

### FC-132
title: High morning sugar while fasting
claim: Higher blood sugar in the morning or during a long fast isn't failure. It's your liver releasing stored sugar.
why: Before waking and during fasting, hormones push stored glucose out (the dawn phenomenon). With insulin resistance, blood tests show it.
do: Watch the trend over weeks, not one reading.
grade: B
safety: none
when: stage:24-48 | stage:12-20 | long
aud: t2d
cond: t2d
from: CGF-01-015, CGF-15-019

### FC-133
title: Religious fasts with a condition
claim: With diabetes, kidney disease or on diuretics, religious dry fasts need a plan from your doctor, and most traditions allow exemptions or water.
why: Food and water restriction together raise the risk of dehydration, low blood sugar and low blood pressure; Ramadan guidelines stratify risk and adjust medication timing.
grade: A
safety: caution
when: fastday | onboarding
aud: t2d, meds
cond: diabetes-meds, t2d, kidney, daily-meds
from: YT-07-011
