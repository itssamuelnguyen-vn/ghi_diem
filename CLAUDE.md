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
(+ icons extended), Navigation Compose (string routes), Room (KSP, schemas in `app/schemas`), kotlinx.serialization,
WorkManager (Drive sync), Glance (widget), Play services auth (Drive, `drive.appdata` only; Drive REST is plain
HttpURLConnection copied from so_no). compile/target SDK 37, minSdk 29. No DI: `GhiDiemApp` holds `db`, `prefs`,
`repo`, `speaker`, `sounds`. No ViewModels: screens read Room Flows with `collectAsState` and write through
`app.repo` (every write calls `GhiDiemApp.changed`: widget refresh + Drive upload 20 s later).

## Layout

```
GhiDiemApp    wiring: db, prefs, repo, speaker, sounds, volumeHandler, changed() → widget + syncSoon()
MainActivity  volume keys → app.volumeHandler, widget intent (EXTRA_MATCH) → AppNav opens that match, sync on start
Feedback      Speaker (text-to-speech, Vietnamese if installed), Sounds (whistle, buzz)
core/   Rules (Sport, Rules JSON, Kind, Ev, splitPlayers, time formats), Scoring (replay events → Board: Rally for
        badminton / volleyball / table tennis, Tennis, Football + FootballClock; Turns = doubles service order),
        Stats (match stats + charts, Result / Record / Standings, Fixtures: round-robin and knockout, smallScores)
data/   Entities (MatchRow, EventRow, TournamentRow), AppDatabase v2 (+ MatchDao), Repo (all writes), Results
        (MatchRow → Result), Prefs, Backup (+ Snapshot JSON, SnapshotFiles safety copies)
sync/   Drive + GoogleAuth (copied from so_no), SnapshotSync (`ghidiem-data.json` in appDataFolder) + SyncWorker
widget/ ScoreWidget (live score, +1 buttons via ScoreAction)
ui/     Theme (+ Team colours), AppNav, HomeScreen, NewMatchScreen (+ RulesForm, shared with tournaments),
        MatchCommon (MatchUi/rememberMatch, MatchFeedback: keep awake, voice, whistles, volume keys; Panel, clock
        and result texts), MatchScreen, BoardScreen (full-screen landscape), StatsScreen (bars + charts), Share
        (text + 1080×1350 image card via FileProvider), TournamentScreens, PlayersScreen, SettingsScreen (+ sync)
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
- **Doubles**: names are "Player 1 / Player 2" (`splitPlayers`), `MatchRow.doubles` turns tracking on. Badminton
  follows courts: the serving pair swaps after winning a rally on its serve, the server is the player in the court
  of the server's score (even = right), the receiver stands diagonally. Table tennis and tennis use service turns
  (A1, B1, A2, B2): a new turn at each change of server (table tennis), each game and each tie-break change
  (tennis). Tennis receivers keep courts: player 1 right (deuce), player 2 left.
- **Taking points back**: "Hoàn tác" removes the last undoable event; a long press on a side's panel removes that
  side's last point / goal (wherever it is, the replay fixes the rest); any event can be deleted from the timeline.
  None of these work on a match ended by hand (`manualEnd`) until "Chơi tiếp".
- **Time-outs**: volleyball 2 per set (30 s), table tennis 1 per match (60 s); a countdown banner and a whistle.
- **Football cards**: the card dialog asks the player (name or shirt number; picks: booked players first). Cards
  are counted per lowercase name: a second yellow sends off, a red sends off (unnamed red = "?"); `Board.onField`
  = `Rules.players` minus sent off, `booked` / `sentOff` names; 11-a-side below 7 players warns that the match must
  stop. Renaming a card's player in the timeline re-replays, so the count stays right.
- **Football extras**: own goal (event `side` = the team credited, note = who put it in), substitution (note
  "out → in"), shoot-out (`SHOOTOUT_START` side = first kicker; 5 each then sudden death, decided as soon as one
  side cannot catch up; a decided shoot-out finishes the match like a won rally match does, undo reopens it).
- **Tournaments**: entrants one per line, line order = seeds. Round-robin fixtures "rr:i:j" (circle method),
  standings by points (football 3/1/0, else wins), then set / goal difference, then points. Knockout "ko:round:slot"
  with standard seeding and byes; a fixture's winner comes from its match (`winner` side); draws do not advance.
  Matches are created on first tap of a fixture (`Repo.fixtureMatch`) and linked by `tournamentId` + `fixture`.
- **Sync / backup**: one JSON snapshot of all tables (`Backup`), same Drive design as so_no (upload / download /
  conflict → user picks; the losing side kept in SnapshotFiles).
- Schema changes: bump the `@Database` version and add a migration (AutoMigration when possible). Never destructive
  migrations: these are real records.

## Roadmap

Done 0.2.0 (2026-11-15, DB v2 AutoMigration): doubles service order, volleyball / table tennis time-outs, football
own goals / substitutions / penalty shoot-out, match statistics + charts, player records + head-to-head,
tournaments (round-robin, knockout), voice, whistles, volume keys, full-screen board, image share, backup file,
Google Drive sync, home-screen widget with +1 buttons.
Done 0.2.1 (2026-11-15): second review fixes (fixture double tap, stale knockout names, stopped rally matches
no longer counted as draws, missing-match screen, build script works with redirected output).
Done 0.2.2 (2026-11-15): long press on a panel takes back that side's last point / goal (`Repo.undoSide`, also in
the full-screen board); "↶" button on the widget (`UndoAction` = `Repo.undo`).
Done 0.2.3 (2026-11-15): football cards per player and players on the pitch (`Rules.players` 5 / 7 / 11).

Done 0.1.0 (2026-11-15): five sports, rules per match, clock, timeline with times, undo / delete / note per event,
callouts and change-ends alerts, swap sides, rematch, history with filter, share as text.
Ideas not built (ask first): more sports (owner said five are enough), editing a tournament's entrants after
creation, choosing who serves first inside a doubles pair.
