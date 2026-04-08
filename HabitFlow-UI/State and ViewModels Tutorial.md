# How State & ViewModels Work
### A beginner-friendly guide to HabitFlow's "Brains"

---

## 🧠 The Big Picture: What is a ViewModel?

If a Compose Screen is a puppet, the **ViewModel** is the puppeteer. 

A screen is designed to be completely "dumb." It doesn't know how to save data, it doesn't know what day it is, and it doesn't know how to calculate streaks. It only knows how to draw shapes and colors based on the instructions it receives. 

The **ViewModel** is the brain. It talks to the database, does the math, and hands the final result to the screen as a single package called **State (`uiState`)**.

### Why do we need them?
If you rotate your Android phone from portrait to landscape, Android physically destroys the screen and rebuilds it to fit the new dimensions. If the screen held your data, you would lose everything you typed the second you flipped your phone! 

ViewModels exist safely "to the side." When the screen is destroyed and rebuilt, the new screen reconnects to the existing ViewModel, so your data is perfectly safe.

Here is how the 4 ViewModels in **Section D** operate.

---

## 1️⃣ HomeViewModel.kt — The Dashboard Manager

### What is its job?
To figure out exactly which habits should be visible on the Home screen today, and calculate the little progress bar at the top (e.g., "3 of 5 habits done").

### Key Concepts:
- **The `combine()` Blender:** The ViewModel listens to three separate live feeds at the same time:
  1. The master list of all habits.
  2. The list of *only* the habits you checked off today.
  3. The date you tapped on the calendar chips (e.g., "show me tomorrow's habits").

  If *any* of those three things change (e.g., midnight rolls over, or you check off a habit), the `combine()` block acts like a blender. It mixes the new data together, filters out what shouldn't be seen (like Thursday habits on a Friday), and spits out a brand new, perfectly formatted `uiState` for the screen to draw.

---

## 2️⃣ HabitDetailViewModel.kt — The Deep-Dive Brain

### What is its job?
To manage the deep-dive profile of one specific habit, handling its completion history, stats, and the "Delete" action.

### Key Concepts:
- **`SavedStateHandle` (The URL parameters):** How does the ViewModel know *which* habit you clicked? When you navigate, the app passes the `habitId` via the NavGraph (like `habit/5/detail`). The `SavedStateHandle` grabs this ID safely. Even if the app goes fully into the background and Android kills it for memory, the ID is preserved.
- **Two Parallel Jobs:** This ViewModel runs two background tasks at once. One task watches the habit's heavy data (like its 100-day completion history). The second task watches *only* today's completion button. Because they are split, checking off the habit today doesn't force the app to re-calculate your 100-day history. It's an invisible performance boost.
- **The Delete Flare (`isDeleted`):** When you delete a habit, the ViewModel tells the database to erase it. Then, it flips a switch in `uiState` called `isDeleted = true`. The screen sees this "flare" and automatically navigates back to the home screen.

---

## 3️⃣ HabitFormViewModel.kt — The Draft Board

### What is its job?
To manage what you're typing as you create a new habit or edit an existing one.

### Key Concepts:
- **Detecting Mode:** When this ViewModel boots up, it checks the `habitId`. If the ID is `-1` (a fake number), it knows you are creating a new habit. If it is a real number (like `5`), it instantly asks the database for Habit #5 and pre-fills the form with its title and color so you can edit it.
- **Updating per Keystroke:** Every time you type a letter in the title box, the screen sends that letter to the ViewModel. The ViewModel clones the `uiState`, replaces the title with the new letter, and sends it back to the screen. This happens in milliseconds.
- **Localized Validation:** If you hit "Save" with an empty title, the ViewModel doesn't send back the English phrase "Title is required." It sends back an ID like `R.string.habit_title_error`. The screen translates that ID into Spanish, French, or English depending on the user's phone settings.

---

## 4️⃣ ProgressViewModel.kt — The Math Nerd

### What is its job?
To do all the heavy lifting and math required to show your overall progress, so the screen can be extremely fast.

### Key Concepts:
- **Pre-computed Maps:** When the ViewModel gets the raw log data from the database, it groups everything by habit. For example, it counts that you've completed Habit #1 five times this week, hitting 71% (0.71) of your goal.
- **Summing Streaks:** It adds up the active streaks of every single habit you own to give you "Total Streak Days".
- **Dumb Screen, Smart Brain:** By the time the data reaches `ProgressScreen`, all the math is already done. The screen just looks at the number `0.71` and draws a progress bar filled 71% of the way. 

---

## ⏱️ Coroutines and "viewModelScope"

Throughout these files, you will notice the phrase:
`viewModelScope.launch { ... }`

A **Coroutine** is a lightweight background worker. Writing to a database or calculating 1,000 dates takes time. If you forced the main screen thread to do it, your app would visually freeze. 

By wrapping the work in `viewModelScope.launch`, we tell Android: *"Hey, do this math on a background worker."*

Even better, `viewModelScope` is strictly tied to the ViewModel's life. If you get bored waiting for a slow database and press the "Back" button to leave the screen, the ViewModel is destroyed, and **every background worker is instantly cancelled.** This prevents the app from wasting battery or crashing by trying to update a screen that you already left.

---

## ✏️ Quick Vocabulary Recap

| Term | Plain English |
|---|---|
| **ViewModel** | The brain for a specific screen. Calculates data and survives screen rotations. |
| **State (`uiState`)** | A single snapshot containing every piece of data a screen needs to draw itself. |
| **`viewModelScope`** | A sandbox for background workers. If the screen is closed, the sandbox is destroyed to save battery. |
| **`SavedStateHandle`** | A survivor backpack. Holds route parameters (like `habitId`) so they aren't lost if the app is killed by the system. |
| **`update { }`** | A function that safely clones the old `uiState`, changes one piece of data, and packages it up as the new state. |
| **Validation** | Checking if user input is allowed (e.g., ensuring a habit title isn't left blank) before saving it to the database. |
