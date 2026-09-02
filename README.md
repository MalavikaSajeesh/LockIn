# LockIn

Lock apps behind your to-do list. Finish tasks, earn app time.

Everything runs on your phone. No backend, no API key, no account, no cost.

---

## How it works

1. Pick the apps you want gated, and give each one a daily minute budget.
2. Add today's tasks. Each task carries a weightage from 0 to 100%.
3. Finishing tasks worth X% of weightage unlocks X% of every locked app's
   daily budget. Finishing *every* task for the day unlocks it completely,
   even if the weightages only added up to 30.
4. If you genuinely need in, spend one of your 10 weekly emergency tokens.

Three ways to mark a task done: take a photo (checked on-device by ML Kit),
run a countdown timer to completion, or just tick it off.

---

## What changed in this version

This is a rebuild, not a patch. The previous drop had a project structure that
compiled but did not work end to end.

### Bugs that stopped it running at all

| Problem | Fix |
|---|---|
| `LockScreenActivity` was never declared in the manifest | Declared, with `singleTask`, `excludeFromRecents`, empty `taskAffinity` |
| `android:packageNames=""` in the accessibility config scoped the service to an empty package list, so it received no events | Attribute removed entirely |
| CI ran `gradle assembleDebug`, but `setup-gradle` puts no `gradle` binary on PATH unless you ask for one | `gradle-version: '8.7'` added to the setup step |
| App label was hardcoded `"LockIt"`, ignoring `@string/app_name` | Uses `@string/app_name` |
| Light theme parent on a near-black background gave dark status-bar icons | Dark parent |

### Core logic that was stubbed out

| Problem | Fix |
|---|---|
| `LockScreenActivity` passed `totalTasksToday = 1, completedTasksToday = 0, completedWeightageSum = 0` as literals into the calculator, so it always reported 0% and 0 minutes | Reads the real task list |
| The gate condition was `if (!allowedNow \|\| totalWeightage >= 0)`. `totalWeightage` comes from `COALESCE(SUM(...), 0)` over non-negative values, so the right side is unconditionally true and the left side was dead code | Single real check via `UnlockRepository` |
| `minutesUsedToday` was never written to by anything, so the daily budget never ticked down | The service bills foreground time; a one-minute watchdog locks you out mid-session when it runs out |
| Both lock-screen buttons called `finish()`, which dropped you back into the app you had just tried to block | "Open my tasks" launches `MainActivity`; the token button spends a token and writes the skip expiry; back goes home |
| `MLKitVerifier` was fully written and never called from anywhere | `CameraVerifyScreen` calls it |
| `VerificationMethod.TIMER` was selectable with no screen behind it | `TimerVerifyScreen` |
| `LOCATION` and `HEALTH_DATA` were selectable and silently did nothing | Removed from the enum until they're built |
| `RECURRING` was stored but nothing ever created the next day's instance | Rolls forward on resume |
| `todayKey` was computed once at ViewModel construction, so leaving the app open past midnight filed tasks under yesterday | Now a flow that re-emits on day change |
| The service's `CoroutineScope` was never cancelled | Cancelled in `onDestroy` |
| `PACKAGE_USAGE_STATS`, `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS` were declared and unused | Removed |

The structural fix behind several of these: **`UnlockRepository` now owns every
"is this app allowed right now" decision.** Before, the accessibility service
and the lock screen each guessed separately, and both guessed wrong.

### What's new

- **App picker with real icons.** Every launchable app on the phone, as a
  searchable grid you tap. No more typing `com.instagram.android`. This needs
  the `<queries>` block in the manifest — on Android 11+ without it,
  `queryIntentActivities` returns only LockIn itself and the grid is empty.
- **New theme.** Deep ink base, violet primary, emerald for progress, amber
  for tokens. Red is now reserved for the locked state, so it means something;
  previously it was also the FAB and every accent, which reads as a permanent
  error state. The home screen leads with an animated gradient progress ring.
- **New icon.** Adaptive: violet gradient background, white padlock with a
  check in its body, inside the 66dp safe zone, with a monochrome layer for
  Android 13 themed icons.
- **Setup screen.** The two required permissions were previously only
  described in this README, so a fresh install looked broken. Now their live
  state is visible in-app with deep links to the right settings pages.
- **Renamed properly** to `com.lockin.app` / `LockIn` / `lockin.db`, not just
  the display label.

---

## Build and install

### In Android Studio

1. Open Android Studio, **Open**, select this `LockIn` folder.
2. Let Gradle sync. It will generate `gradlew` and the wrapper JAR on first
   sync (`gradle/wrapper/gradle-wrapper.properties` is already here; the JAR
   is binary and is not committed).
3. Plug in your phone with USB debugging on, hit **Run**.

### Via GitHub Actions, no Android Studio

```
git init
git add .
git commit -m "LockIn"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/lockin-app.git
git push -u origin main
```

Then repo → **Actions** → **Build Debug APK** → wait → download the
**LockIn-debug-apk** artifact and install the `.apk` on your phone.

### First launch

Open the **Setup** tab and grant both:

- **Accessibility service** — lets LockIn see which app is in the foreground
- **Display over other apps** — lets the lock screen appear on top

If the lock stops firing after a while, exclude LockIn from battery
optimisation. Samsung, Xiaomi and OnePlus are the usual culprits.

---

## Honest limitations

**This is friction, not a vault.** Settings → Accessibility → off disables the
lock in about eight seconds, and no Android app can prevent that; the platform
gives you no primitive for it. LockIn works by making an impulse cost you
several deliberate steps. That's genuinely effective against reflex, and
useless against determination.

**The photo check is rough.** ML Kit's base model knows roughly 400 generic
categories. It can tell food from furniture. It cannot confirm *you* did a
specific thing, and it cannot tell a live scene from a photo of a photo.
Treat it as a nudge toward honesty.

**Play Store distribution is a problem.** Google rejects apps that use the
Accessibility API for non-accessibility purposes. Fine for sideloading onto
your own phone; a blocker for publishing.

**No backup yet.** A reinstall wipes every task.

---

## Added in this round

- **Streaks and history** (`HistoryScreen`, `StreakCalculator`).
  `completedAtEpochMillis` was already being written and never read; this is
  what it was for. A contribution-graph grid of the last 12 weeks, current and
  longest streak, perfect days, lifetime tasks done. A day counts toward a
  streak when every task on it got finished. An unfinished *today* does not
  break the streak, it just doesn't extend it yet — otherwise the number would
  read zero every morning, which is exactly the wrong feedback.

- **Backup and restore** (`BackupManager`, on the Setup tab). Plain JSON via
  the system file picker. No Google Drive, no account, no network permission.
  The file lands wherever you point it — internal storage, SD card, or any
  cloud app you happen to have installed, because the picker treats them all
  alike. LockIn never sees where it went. Restoring replaces everything.

- **Edit and reorder tasks.** `AddTaskScreen` doubles as the edit screen. Up
  and down arrows on each row, persisted through a new `sortOrder` column.
  Weightage headroom now excludes the task being edited, so editing one no
  longer counts it against itself.

- **Notifications** (`Notifier`, `MorningReminderWorker`). A morning list at
  8am, and a one-shot nudge when an app's earned time drops under 5 minutes.
  Both off until you turn them on in Setup. Two channels, so you can silence
  one without the other.

- **Home screen widget** (`LockInWidgetProvider`). Today's unlock percentage,
  progress bar, and task count; tap to open. Plain RemoteViews rather than
  Glance — no extra dependency and no risk of a Compose-compiler version
  mismatch. Refreshed whenever task state changes.

- **Per-app weightage mapping** (`TaskAppLink`). A task with no links applies
  to every locked app, as before; a task with links applies only to those. So
  "go to the gym" can gate Instagram while leaving your podcast app alone.
  Set it with the chips on the task screen. The Apps tab shows how many tasks
  gate each app. Links carry across when a recurring task rolls into a new day.

- **Location verification** (`LocationVerifyScreen`). Stand at the place and
  save it while creating the task; verifying compares your GPS fix against it
  within a radius you choose. Honestly the most reliable of the three checks —
  much harder to fake than a photo. Uses `FusedLocationProviderClient`.

Also: editable per-app budgets, and a `LOCATION` entry restored to
`VerificationMethod`.

**Database is now version 2** with `fallbackToDestructiveMigration()`.
Upgrading from the previous build wipes the database. That was a deliberate
call because v1 never worked well enough to hold real data — but if you have
anything in there, export it first.

## Still not built

Scheduled lock windows, a cooldown timer on the lock screen, Health Connect
step verification, and a hard mode that makes changing settings costly.

---

## Cost

Nothing. Room, ML Kit, CameraX, WorkManager and the accessibility service all
run locally. GitHub Actions is free at this scale.
