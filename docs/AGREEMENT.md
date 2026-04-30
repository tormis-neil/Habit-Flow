# Habit Flow — MCO 2 Working Agreement

**Project:** Habit Flow (Android)
**Phase:** MCO 2 — Cloud-Enabled, Data-Driven, Enhanced UX (backend focus)
**Phase 1 start date:** May 1, 2026 (Day 1)
**Target completion:** May 14, 2026 (Day 14) — assumed deadline, best-effort
**Working branch:** `claude/habit-flow-backend-BZY2u`

---

## 1. Scope

This phase focuses on the **backend layer** of the Android app:

- Cloud sync (Firebase)
- Authentication (email + username + password)
- Offline-first data strategy with background sync
- Reminders & notifications
- Hilt dependency injection

**Advanced UI work is out of scope.** UI files are touched only when a feature
absolutely requires it (e.g., adding a reminder time picker to the habit form,
adding sign-in / sign-up screens). No redesigns, no new screens beyond auth.

---

## 2. Confirmed decisions

| # | Question | Decision |
|---|---|---|
| 1 | Cloud backend | **Firebase** (Firestore + Firebase Auth) |
| 2 | Auth method | **Email + Username + Password** via Firebase Auth (Username stored in Firestore user profile, since Firebase Auth itself only knows email) |
| 3 | Repo / project rename | Rename `HabitFlow-UI/` → `HabitFlow/` (the "UI" suffix no longer reflects the project, since backend code is now part of it) |
| 4 | `FakeDataSource.kt` | Repurpose as a **test fixture** under `app/src/test/` — remove from production source set |
| 5 | Two-week deadline | Treated as an **assumed deadline**, not a hard one. We aim for it; we don't compromise correctness or learning to hit it |

---

## 3. Working rules

These rules govern how Claude collaborates with the developer for the rest of MCO 2.

### 3.1 Source-first, edit-second

Before editing or creating **any** file, Claude will:

1. **Show the full source** of the file (or the new file's complete contents) in chat.
2. **Explain it line by line / section by section** — what it does, why it is structured that way, what each annotation/API means.
3. **Give the manual Android Studio steps** — where to place the file, which Gradle sync to run, which plugin / dependency line to add, which IDE setting to flip.
4. **Wait for explicit confirmation** before applying the change with the `Edit` / `Write` tools.

The developer applies changes manually in Android Studio for learning and
troubleshooting. Claude acts as the guide, verifier, and second pair of eyes.

### 3.2 Best-practice guardrails

Claude will follow these standards throughout MCO 2:

- **Hilt** for DI — no manual factories, no `lazy` god objects in `Application`.
- **Real Room migrations** — `Migration(from, to)` objects only. `fallbackToDestructiveMigration()` is forbidden once user data is at stake.
- **Interface-first repositories** — ViewModels depend on `interface`, never on the implementation class.
- **`suspend` + `Dispatchers.IO` at the data layer only.** ViewModels use `viewModelScope` and stay dispatcher-neutral.
- **Reactive joins via `kotlinx.coroutines.flow.combine`** — no manual polling or `runBlocking`.
- **WorkManager** for background sync — `PeriodicWorkRequest` + `OneTimeWorkRequest` with `Constraints` (network), exponential backoff, and unique work names.
- **Conflict resolution: last-write-wins** via server `updatedAt`. Deletes are **tombstones** (`isDeleted = true`), never hard deletes that the cloud can't observe.
- **Stable cross-device IDs:** every entity gets a `uuid: String` from creation. The Room `Long` `id` stays as the local primary key.
- **Permissions handled at runtime** for `POST_NOTIFICATIONS` (API 33+). Manifest declares `INTERNET`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`.
- **ProGuard / R8 keep rules** added for every reflective dependency (Firebase, kotlinx-serialization) before the first release build.
- **Single source of truth:** Room is the local cache; Firestore is the cloud truth; the repository is the merge layer. UI never reads remote directly.

### 3.3 Action safety

- Claude will not run destructive git commands (`reset --hard`, force push, branch delete) without explicit instruction.
- Claude will not push to any branch other than `claude/habit-flow-backend-BZY2u`.
- Claude will not open pull requests unless explicitly asked.
- Claude will not skip git hooks or signing.

### 3.4 Trade-off authority

The **developer** decides scope cuts. If the timeline slips, Claude proposes
candidates for cutting (likely candidate: Day-10 missed-habit notifications)
and the developer confirms before anything is dropped.

---

## 4. Definition of done for MCO 2

A feature is "done" when **all** of the following hold:

1. The code is on `claude/habit-flow-backend-BZY2u` and builds with `./gradlew assembleDebug`.
2. Unit tests pass with `./gradlew test`.
3. The feature works end-to-end on a physical device or emulator.
4. The developer has manually applied / reviewed the change in Android Studio (no Claude-only edits in the final commit).
5. Documentation in `docs/` is updated when architecture changes.

---

## 5. Communication cadence

- **Each working day:** start with the day's plan; end with a short status (what shipped, what's blocked, what's next).
- **On any architectural decision** (e.g., Firestore data shape, sync conflict edge case, schema change): Claude pauses, presents options with trade-offs, and waits for the developer's call.
- **On any error during manual setup** (Gradle sync fail, Firebase init crash, etc.): the developer pastes the log; Claude diagnoses root cause, no shortcuts like `--no-verify` or `fallbackToDestructiveMigration`.
