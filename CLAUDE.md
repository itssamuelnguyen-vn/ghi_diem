# Ghi Điểm

Android app for the owner only (sideloaded APK, never on the Play Store): a scorekeeper for football, badminton,
volleyball, table tennis and tennis, each with its own scoring rules, and a match clock so every point / goal is
saved with the time it happened. **UI is Vietnamese only** (strings written inline, no I18n layer); code, names,
comments and docs are English. The owner is a Rails developer new to Android: keep code simple, explain Android
concepts by comparison with Rails (Room ~ ActiveRecord, NavHost routes ~ routes.rb). Build rules (no Android
Studio, no emulator) are in `~/.claude/CLAUDE.md`.

## Build, test, install

```powershell
$env:JAVA_HOME='D:\dev\android-sdk\jbr'
.\gradlew.bat testDebugUnitTest                                   # JVM tests: scoring rules of every sport
powershell -ExecutionPolicy Bypass -File tool\build_apk.ps1        # = Ctrl+Shift+B → build\apk\ghidiem-<ver>-<stamp>.apk
```

From WSL redirect Gradle's output to a file and feed stdin from /dev/null, otherwise the call can hang:
`powershell.exe -NoProfile -Command "cd D:\dev\customized_apps\ghi_diem; .\gradlew.bat assembleRelease *> build\gradle.log" < /dev/null`
(the log is UTF-16: `iconv -f utf-16le -t utf-8 build/gradle.log`).

Release is R8-shrunk and signed with the local debug key. Bump `versionCode` / `versionName` in
`app/build.gradle.kts` for each APK given to the phone.

Stack (same versions as the other native apps, see `gradle/libs.versions.toml`): Kotlin, Compose + Material 3
(+ icons extended), Navigation Compose (string routes), Room (KSP, schemas in `app/schemas`), kotlinx.serialization.
compile/target SDK 37, minSdk 29. No DI: `GhiDiemApp` holds `db`, `repo`. No ViewModels: screens read Room Flows
with `collectAsState` and write through `app.repo`.

## Layout

```
GhiDiemApp    wiring: db, repo, app scope
core/   Rules (Sport, Rules JSON, Kind, Ev, time formats), Scoring (replay events → Board: Rally for badminton /
        volleyball / table tennis, Tennis, Football + FootballClock)
data/   Entities (MatchRow, EventRow), AppDatabase (+ MatchDao), Repo (all writes, clock, re-replay after each change)
ui/     Theme (+ Team colours), AppNav, HomeScreen (sports + live + history), NewMatchScreen (names, rules per sport,
        first server), MatchScreen (clock, banner, two tap panels, controls, timeline, dialogs, share)
```

## How scoring works

- **Events are the truth.** A match is a list of `EventRow`s (point / goal / card / period start / period end),
  each with `clockMs` (match clock). `Scoring.replay` rebuilds the whole board from them, so undo = delete the last
  event, and deleting any event in the middle just works. `MatchRow.score/detail/winner` are only a cache for the
  history list, refreshed by `Repo.refresh` after every change.
- **Clock** = stopwatch on the match row: `clockBaseMs` + time since `runningSince` (wall clock, 0 = paused), so it
  keeps counting while the app is closed. The first point starts it; a point while paused resumes it. Rally sports
  stop it when the match is won (undoing the last point reopens the match, clock paused). A match ended by hand
  (`manualEnd`) stays ended whatever events change; "Chơi tiếp" reopens it. Repo writes run under one Mutex and
  re-read the row, and period start/end are ignored when already in that state (double taps).
- **Badminton**: 21 (or 15 / 11), win by 2, cap = points + 9 (30); rally winner serves, from the right court on an
  even score; 60 s interval when the leader reaches 11; change ends after each game and at 11 in the deciding game;
  the winner of a game serves first in the next.
- **Volleyball**: 25 (deciding set 15), win by 2, no cap; rally winner serves; change ends after each set and at 8
  in the deciding set; first server alternates by set.
- **Table tennis**: 11, win by 2; service changes every 2 points, every point from 10-10; first server alternates
  by game; change ends at 5 in the deciding game.
- **Tennis**: 0/15/30/40, deuce/AD or no-ad (deciding point); sets to `games` (6 or 4) win by 2, 7-point tie-break
  at games-all (server alternates after 1 point then every 2, change ends every 6 points); final set: tie-break,
  super tie-break to 10 instead of the set, or advantage set. Change ends after odd games. Callouts: break / set /
  match point (worked out by trying the next point on a clone of the engine).
- **Football**: goals and yellow / red cards, periods started and ended by hand (first goal opens period 1). The
  clock shows the match minute (second half starts at 45:00 even if the first ran long) and stoppage time in amber;
  event minutes are written like a referee does: 23', 45+2'. Two optional extra-time periods. Ended by hand.
- Schema changes: bump the `@Database` version and add a migration (AutoMigration when possible). Never destructive
  migrations: these are real records.

## Roadmap

Done 0.1.0 (2026-11-15): five sports, rules per match, clock, timeline with times, undo / delete / note per event,
callouts and change-ends alerts, swap sides, rematch, history with filter, share as text.
Ideas not built (ask first): penalty shoot-out, timeouts (volleyball), doubles service order / positions,
statistics per player, backup / Drive sync, home-screen widget, landscape full-screen scoreboard for spectators.
