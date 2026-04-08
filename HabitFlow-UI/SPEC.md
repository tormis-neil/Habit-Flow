# HabitFlow: MCO 1 Technical Specification

## 🏗️ Architecture Requirements
- **Pattern**: MVVM with a Repository abstraction.
- **Local Storage**: Room Database with specialized DAOs.
- **UI Framework**: Jetpack Compose with Material 3 Theming.
- **Target SDK**: API 26 (Android 8.0).

## 💾 Data Entities
### HabitEntity
- `id`: Long (Primary Key, Auto-generate)
- `title`: String
- `description`: String
- `frequency`: String (Enum: DAILY/WEEKLY)
- `startDate`: Long (Timestamp)
- `isEnabled`: Boolean (Default: true)

### HabitLogEntity
- `logId`: Long (Primary Key)
- `habitId`: Long (Foreign Key -> HabitEntity.id, OnDelete: CASCADE)
- `dateCompleted`: Long (Timestamp - Date only)
- `timestamp`: Long (Actual completion time)

## ⚙️ Required DAO Operations
- **Upsert**: Insert or update habit details.
- **Log Completion**: Insert a log entry for a specific date.
- **History Query**: Retrieve all logs for a specific habit ID.
- **Active Filter**: Retrieve only habits where `isEnabled` is true.

## 📊 State Management
- **StateFlow**: Mandatory for reactive UI updates; no LiveData.
- **ViewModels**: Must be lifecycle-aware and handle all streak calculations.
