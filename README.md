# HabitFlow

**HabitFlow** is a native Android application for building and tracking daily or weekly habits. It helps users stay consistent by visualizing streaks, monitoring progress, and managing habits through a clean, modern interface built entirely with Jetpack Compose and Material 3.

---

## Features

- **Create & Manage Habits** — Add habits with a title, description, frequency (daily or weekly), custom color, and start date.
- **Daily Completion Tracking** — Tap to mark habits as done each day; toggle to undo.
- **Streak Tracking** — Automatically calculates current and longest streaks based on completion history.
- **Progress Overview** — View overall completion rates and per-habit statistics.
- **Weekly Progress** — See a Monday-to-Sunday dot indicator showing which days a habit was completed.
- **Enable/Disable Habits** — Pause habits without deleting them or losing history.
- **Dark Theme Support** — Switch between light and dark themes via the Settings screen.
- **Persistent Storage** — All data is stored locally on the device using Room and survives app restarts.

---

## Architecture

HabitFlow follows the **MVVM (Model-View-ViewModel) + Repository** pattern, a recommended architecture for modern Android development.

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (View)                       │
│  Jetpack Compose Screens + Navigation                   │
│  HomeScreen · HabitDetailScreen · HabitFormScreen        │
│  ProgressScreen · SettingsScreen                        │
└──────────────────────┬──────────────────────────────────┘
                       │ observes StateFlow
┌──────────────────────▼──────────────────────────────────┐
│                  ViewModel Layer                        │
│  HomeViewModel · HabitDetailViewModel                   │
│  HabitFormViewModel · ProgressViewModel                 │
│  SettingsViewModel                                      │
└──────────────────────┬──────────────────────────────────┘
                       │ calls suspend functions
┌──────────────────────▼──────────────────────────────────┐
│               Repository Layer                          │
│  HabitRepository (interface)                            │
│  └── RoomHabitRepository (implementation)               │
│      Merges raw DB rows with streak calculations        │
└──────────────────────┬──────────────────────────────────┘
                       │ queries via DAO
┌──────────────────────▼──────────────────────────────────┐
│                 Data Layer (Room)                        │
│  HabitDatabase · HabitDao                               │
│  HabitEntity · HabitLogEntity · Converters              │
└─────────────────────────────────────────────────────────┘
```

### Layer Responsibilities

| Layer | Role |
|---|---|
| **View** | Renders the UI using Jetpack Compose. Observes `StateFlow` from ViewModels and reacts to state changes. Contains no business logic. |
| **ViewModel** | Holds UI state, handles user actions, and delegates data operations to the Repository. Lifecycle-aware — survives configuration changes like screen rotations. |
| **Repository** | Acts as the single source of truth. Combines raw database entities with computed streak data and exposes clean domain models (`Habit`, `DailyProgress`) to ViewModels. |
| **Data (Room)** | Manages the local SQLite database. Defines table schemas as entities and all SQL queries as DAO functions. |

---

## Database

HabitFlow uses **Room**, Android's official persistence library built on top of SQLite. The database file is named `habitflow_database` and contains two tables.

### Tables

#### `habits`

Stores each habit the user creates.

| Column | Type | Description |
|---|---|---|
| `id` | `Long` (PK, auto-generated) | Unique identifier |
| `title` | `String` | Name of the habit |
| `description` | `String` | Optional detail about the habit |
| `frequency` | `String` | `"DAILY"` or `"WEEKLY"` |
| `start_date` | `Long` | Start date stored as epoch day |
| `is_enabled` | `Boolean` | Whether the habit is active or paused |
| `color` | `String` | Hex color code (e.g., `#6750A4`) |

#### `habit_logs`

Records each time a habit is marked as complete. Each row is one completion event.

| Column | Type | Description |
|---|---|---|
| `logId` | `Long` (PK, auto-generated) | Unique log identifier |
| `habit_id` | `Long` (FK → `habits.id`) | Which habit was completed |
| `date_completed` | `Long` | Date of completion (epoch day) |
| `timestamp` | `Long` | Exact completion time (Unix ms) |

### Constraints

- **Foreign Key**: `habit_logs.habit_id` references `habits.id` with `ON DELETE CASCADE` — deleting a habit automatically removes all its logs.
- **Unique Index**: A composite unique index on `(habit_id, date_completed)` prevents duplicate completions on the same day.
- **Conflict Strategy**: Duplicate log inserts are silently ignored (`OnConflictStrategy.IGNORE`).

### Streak Calculation

Streaks are **not stored** in the database. They are computed on-the-fly by `StreakCalculator` from the completion dates in `habit_logs`:

- **Daily streaks**: Counts consecutive calendar days with a completion. A streak is considered active only if the last completion was today or yesterday.
- **Weekly streaks**: Groups completions by ISO week number and counts consecutive weeks. Active if the last completed week is the current or previous week.

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI Framework | Jetpack Compose + Material 3 |
| Navigation | Jetpack Navigation Compose |
| Database | Room (SQLite) |
| Annotation Processing | KSP (Kotlin Symbol Processing) |
| State Management | Kotlin StateFlow |
| Concurrency | Kotlin Coroutines |
| Architecture | MVVM + Repository |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 |

---

## Project Structure

```
app/src/main/java/com/habitflow/app/
├── HabitFlowApplication.kt              # Application class, initializes DB and Repository
├── data/
│   ├── local/
│   │   ├── Converters.kt                # Room type converters (e.g., LocalDate ↔ Long)
│   │   ├── HabitDatabase.kt             # Room database definition (singleton)
│   │   ├── ThemePreferences.kt          # SharedPreferences wrapper for theme setting
│   │   ├── dao/
│   │   │   └── HabitDao.kt              # All SQL queries (insert, update, delete, select)
│   │   └── entity/
│   │       ├── HabitEntity.kt           # habits table schema + mapping functions
│   │       └── HabitLogEntity.kt        # habit_logs table schema
│   └── repository/
│       └── RoomHabitRepository.kt       # Production repository implementation
├── domain/
│   ├── model/
│   │   └── Models.kt                    # Domain models (Habit, DailyProgress)
│   ├── repository/
│   │   └── HabitRepository.kt          # Repository interface (contract)
│   └── util/
│       ├── HabitConstants.kt            # App-wide constants
│       └── StreakCalculator.kt          # Pure streak computation logic
├── presentation/
│   ├── ui/
│   │   ├── MainActivity.kt             # Entry point, sets up theme and navigation
│   │   ├── NavGraph.kt                 # Screen routes and navigation map
│   │   ├── components/
│   │   │   └── Components.kt           # Reusable UI components
│   │   ├── screens/
│   │   │   ├── habits/
│   │   │   │   ├── HabitDetailScreen.kt # View habit details, streaks, and history
│   │   │   │   └── HabitFormScreen.kt   # Create or edit a habit
│   │   │   ├── home/
│   │   │   │   └── HomeScreen.kt        # Main habit list with completion toggles
│   │   │   ├── progress/
│   │   │   │   └── ProgressScreen.kt    # Overall progress and statistics
│   │   │   └── settings/
│   │   │       └── SettingsScreen.kt    # Theme toggle
│   │   └── theme/
│   │       └── Theme.kt                # Material 3 color scheme and typography
│   └── viewmodel/
│       ├── HabitDetailViewModel.kt
│       ├── HabitFormViewModel.kt
│       ├── HomeViewModel.kt
│       ├── ProgressViewModel.kt
│       ├── SettingsViewModel.kt
│       └── ViewModelFactory.kt          # Custom factory for dependency injection
└── utils/
    └── FakeDataSource.kt                # Mock data for development/testing
```

---

## Build & Run

**Prerequisites**: Android Studio with SDK 35, JDK 17

```bash
# Run unit tests
./gradlew test

# Build debug APK
./gradlew assembleDebug

# The APK will be at:
# app/build/outputs/apk/debug/app-debug.apk
```
