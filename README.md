# Focus-Flow 🍅

A beautiful, context-aware application blocker and focus timer developed in modern Kotlin and Jetpack Compose. **Focus-Flow** integrates classical time-blocking paradigms (e.g., customized Pomodoro loops) with selective runtime boundaries to quiet distracting applications and restore peak attention.

---

## 🎨 Design Philosophy \& Visual Theme

Focus-Flow is crafted under a cohesive, elegant **Deep Cosmic Dark Slate** aesthetic. The interface is optimized for low-glare productivity session viewing using:
- **Atmospheric Starry Contrast**: Dark background elements layered over generous negative space.
- **Vibrant Interactive Accents**: Neon Space Coral (`#FF6B6B`) representing active high-intensity Focus blocks, and soft cosmic Mint Teal (`#4ECCA3`) indicating restorative transitions and rest breaks.
- **Tactile Material Response**: Soft cards, container-based responsive scaling, and smooth progress sweeps designed around clean 8dp spatial grids.

---

## ⚙️ Core Application Modules (Tabs)

Focus-Flow is divided into four cleanly segmented task structures, accessible via the bottom Navigation Bar:

### 1. Focus Launcher Directives (Tab 1)
The primary screen centers your current focus loop activation controls.
- **Focus Blocks**: Custom sessions with dynamic task naming fields and a duration slider.
- **Pomodoro Cycles**: Structured work loops designed automatically to partition minutes into focused sprints (default 25 min) followed by regular restorative periods (5 min break).
- **Rest Breaks**: Casual decompression filters that temporarily lift background app blockages while tracking rest cycles with high-precision countdown widgets.

### 2. Selective App Blocklist (Tab 2)
The configuration deck for setting boundary protocols.
- **Boundary Toggles**: Allows selective toggling of local application software to decide which programs must remain closed during active countdown sessions.
- **Granular Control**: Simple checkboxes or list actions ensure zero background leakages while permitting necessary tools (e.g., dialer, calculator) to function seamlessly.

### 3. Historical Logs \& Interrupted Attempt Analytics (Tab 3)
A dashboard tracking your attention growth and session completion.
- **Historical Metrics**: Detailed reports indicating finished work items, break intervals completed, and active session duration sums.
- **Blocked Attempt Counters**: Visual indicators summarizing times when background restricted software files were opened during active timers, showcasing an aggregate reduction over time.

### 4. Advanced Automation (Tab 4)
Contextual smart toggles designed to trigger blocks based on your real-world environments.
- **Wi-Fi Anchors**: Link focus states automatically to home or office access points, automatically engaging silent focus parameters upon linking to designated network SSIDs.
- **Dynamic System Triggers**: High-efficiency background monitors that help suppress persistent non-essential notification popups.

---

## 🚀 Step-by-Step Usage Guide

To optimize your daily focus flow using the Android app, follow these steps:

### Step 1: Select Your Restricted Apps
1. Navigate to the **Blocklist** tab using the bottom navigation bar.
2. Locate the list of installed applications.
3. Toggle the switches on the specific apps you find most distracting (e.g., social media clients, entertainment platforms).

### Step 2: Configure Your Session
1. Return to the main **Focus** tab.
2. Select your preferred style at the mode selector row:
   - **Focus Block**: Best for non-standard goals. Enter a descriptive goal title (e.g., "Review Codebase Structure") and move the duration slider to your desired pace.
   - **Pomodoro Loop**: The standard productivity loop. Customize your work sprint duration, or select quick presets like **15m**, **25m**, **45m**, or **60m**.
   - **Rest Break**: Configure a dedicated break session with quick access presets like **5m**, **10m**, or **15m**.

### Step 3: Launch Focus Mode
1. Tap the **Initiate Focus Blocker** (or **Start Pomodoro Loop**) action button at the bottom.
2. The app will trigger a soft audible alert and open the **Active Focus Clock Card**.
3. You will see a beautiful, flowing arc indicator sweeping smoothly as the background timer decrements.

### Step 4: Work and Transition
1. While the countdown is running, selected blocklist apps are gracefully restricted if opened.
2. If using the classical **Pomodoro Loop**, the app will trigger alert sounds at limits and cycle into a restful **5-minute break**.
3. You can manually advance or transition states instantly via the **Resume Work Sprint** and **Take a 5-min Rest Break** buttons.
4. If you must interrupt the session, simply tap **Deactivate Block Filter** or **Terminate Session**.

---

## 🏗️ Technical Architecture Details

Focus-Flow is designed with professional, modern multi-layered Android architectures:
- **Clean MVVM Architecture**: Structured Separation of Concerns across database layers, repositories, ViewModel layers, and Compose render loops.
- **Local SQLite Storage (Room Engine)**: Direct schema validation using Room migration configurations (`version 4`) to store Focus histories, blocklogs, and user network definitions securely.
- **Robust Foreground Blocker Service**: Leverages low-footprint Android Services (`FocusBlockerService`) to observe system events and actively enforce focus parameters reliably across runtime configuration modifications.
