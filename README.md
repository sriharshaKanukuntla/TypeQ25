# TypeQ25

A comprehensive physical keyboard input method (IME) for Android devices with physical QWERTY keyboards. Specifically designed and optimized for devices like the Unihertz Titan 2 and Zinwa/Unihertz Q25. Built as a hobby project to make typing on physical keyboards more efficient, powerful, and enjoyable.

## ✨ Key Features Overview

- **Smart Modifier System** - One-shot and latched modes for Shift, Ctrl, and Alt
- **Navigation Mode** - Navigate UI without touching screen (double-tap Ctrl)
- **Power Shortcuts** - Launch apps from anywhere with SYM + letter key
- **Launcher Shortcuts** - Quick app launching from home screen
- **Symbol Picker** - Comprehensive emoji and symbol access with favorites
- **Keyboard Layout Editing** - Customize and convert between QWERTY/AZERTY/QWERTZ layouts
- **Multi-Language Support** - Auto-correction for English, Italian, French, German, Polish, Spanish
- **Emoji Shortcodes** - Type `:smile:` to insert 😊
- **0 Key Voice Input** - Quick speech-to-text on Q25 (press 0 key)
- **Auto-Focus** - Automatically focus input fields when opening apps (with Accessibility permission)
- **And much more...**

## 📋 Detailed Features

### Long Press for Special Characters or Capital Letters

Long-press any key to get its Alt+key combination. For example:
- Long-press Q → 0
- Long-press A → @
- Long-press Z → !

Alternatively, long press can be configured to type capital letters:
- Long-press a → A

The long-press duration is fully configurable in settings (default 500ms).
### Modifier Keys

**Shift, Ctrl, and Alt** work in two modes:

- **One-shot**: Tap once to activate for the next key only (so you don't need to press 2 keys to do a combination)
- **Latch**: Double-tap to keep it active until you tap it again

**Caps Lock**: Double-tap Shift to enable/disable caps lock. Tap Shift once while caps lock is on to turn it off.

**Visual Indicators**: When modifiers are active, colored badges appear at the top of the keyboard:
- **Blue badges** - Modifier is pressed or in one-shot mode
- **Orange badges** - Modifier is locked/latched (double-tapped)
- LED strip at the bottom also shows modifier states

### Keyboard Shortcuts

Standard shortcuts work as you'd expect:
- `Ctrl+C` / `Ctrl+X` / `Ctrl+V` - Copy, cut, paste (On Q25: keycode 60 acts as Ctrl)
- `Ctrl+A` - Select all
- `Ctrl+Z` - Undo
- `Ctrl+Backspace` - Delete last word
- `Ctrl+E, S, D, F or I, J, K, L` - Navigate (E=Up, D=Down, S=Left, F=Right)
- `Ctrl+W` / `Ctrl+R` - Expand text selection left/right
- `Ctrl+T` - Tab
- `Ctrl+Y` / `Ctrl+H` - Page up/down
- `Ctrl+Q` - Escape
- `Alt+Currency Key` - Toggle phone speakerphone during calls (Q25 only)

**Note for Q25:** Keycode 58 acts as the SYM key for accessing special characters and emojis.

### Navigation Mode

Double-tap Ctrl when not in a text field to enter navigation mode. This lets you navigate the Android UI using keyboard shortcuts:
- **ESDF or IJKL** - Arrow key navigation (E=Up, D=Down, S=Left, F=Right)
- **T** - Tab key
- **Y/H** - Page up/down
- **Q** - Escape
- **A** - Select all
- **W/R** - Expand text selection left/right
- All mappings are fully customizable in Settings → Nav Mode

Press Back or Ctrl again to exit. A persistent notification indicates when Nav Mode is active.

### Launcher Shortcuts

Press any alphabetic key on the home screen (launcher) to quickly launch assigned apps. First press opens an assignment dialog where you can select which app to launch. The O and P keys are excluded to allow system functionality. This feature can be enabled/disabled in Advanced Settings.

### Power Shortcuts

**Global App Launching from Anywhere**: Press SYM key, then any letter (A-Z) to launch apps without needing a text field or launcher:
1. Press SYM key (Q25: ⚙️ key)
2. A toast message appears: "Press a key to open the app"
3. Press any letter key to launch the assigned app
4. If unassigned, opens dialog to select an app

Configure in Settings → Advanced Settings → Power Shortcuts. Works system-wide, perfect for quick multitasking.

### SYM Key for Symbols, Emojis & Favorites

Press the SYM key to activate the Symbol Picker popup with multiple tabs:

**Favorites Tab** (opens by default):
- Long-press any symbol/emoji to add to favorites (up to 26 items, Q-Z layout)
- Quick access to your most-used characters
- Star indicators show favorited items
- Long-press again to remove from favorites

**Symbols & Emojis Tabs**:
- Navigate with physical keys (Q-Z) to insert characters
- Touch support: tap to insert, long-press to favorite
- Swipe left/right to change pages
- Recent items accessible via Alt+W through Alt+0 (1-10)

**Keyboard Navigation**:
- `Ctrl+T` - Cycle between Favorites/Symbols/Emojis tabs
- `Ctrl latch + S/J` - Previous page
- `Ctrl latch + F/L` - Next page
- `Ctrl latch + E/I` - Previous category
- `Ctrl latch + D/K` - Next category
- DPad keys - Navigate UI elements

Fully customizable in Settings → SYM Customization:
- Auto-close behavior
- Enable/disable emoji or symbols pages
- Swap page order (symbols first or emoji first)
- Customize which symbol/emoji each key produces

### Emoji & Symbol Shortcodes

Type shortcodes to quickly insert emojis and symbols without opening the Symbol Picker:
- Type `:smile:` to insert 😊
- Type `:heart:` to insert ❤️
- Type `:tm:` to insert ™
- Type `:copy:` to insert ©
- Suggestions appear as you type
- Use Tab/Enter to insert, Esc to dismiss
- Alt+1-9,0 for quick selection
- Tap outside popup to dismiss
- Toggle feature in Settings → Text Input


### Character Variations

After typing a letter, the status bar shows available accent variations (à, é, ñ, ç, etc.). Tap any variation to replace the character. Multi-tap variations can also be configured for keys to cycle through characters by pressing repeatedly.

### Keyboard Layout Support & Editing

**Multiple Layout Support:**
- QWERTY (standard)
- AZERTY (French)
- QWERTZ (German)
- Arabic
- Greek
- Bulgarian (Phonetic & Traditional)
- Russian (Translit)

**Layout Features:**
- Quick layout switching with `Ctrl+Space`
- View mappings for any layout in Settings → Keyboard Layout
- **Edit non-English layouts**: Customize key mappings for AZERTY, QWERTZ, and other layouts
- Import/export custom layouts
- Multi-tap character cycling support
- All customizations saved automatically

### Voice Input & Speech Recognition

**Quick Voice Input:**
- **Q25 devices**: Press 0 key in any text field to start voice input
- **Other devices**: Use Alt+Ctrl together or tap microphone button
- Choose preferred speech recognition app (Google, others)
- Automatic punctuation and capitalization
- Works in any text field system-wide

### Auto-Focus Input Fields

Automatically focus text input fields when opening apps (requires Accessibility permission):
- Works with browsers (Chrome, Firefox, etc.)
- Messaging apps (WhatsApp, Telegram, Signal)
- Social media apps
- Search interfaces
- Start typing immediately without tapping the field
- Configure in Settings → Text Input

### Auto-Capitalization

**Smart Capitalization:**
- Automatically capitalizes the first letter when starting to type in an empty field
- Capitalizes after sentence-ending punctuation (period, exclamation, question mark) + space
- Automatically disabled in password fields for security
- Both features can be toggled independently in settings

### Auto-Correction & Dictionary

**Multi-Language Support:**
- Built-in dictionaries: English, Italian, French, German, Polish, Spanish
- Add custom languages and corrections easily
- TypeQ25 universal corrections (work across all languages)
- Corrections for common typos and punctuation (e.g., `im` → `I'm`, `ppp` → `%`)

**Dictionary Management:**
- Search functionality in corrections interface
- Custom entries appear at top of list
- Easy-to-edit JSON format for advanced users
- Spell checker integration (Android system)
- Toggle spell checking in settings

### Quick Actions Menu

Tap the ⋮ (three dots) button in the status bar for quick access to:
- Copy
- Paste
- Cut
- Select All
- Undo
- Fast access without keyboard shortcuts

### Clipboard History

Access recent clipboard items with quick keyboard shortcuts:
- Recent clipboard management
- Quick paste from history
- Integrated into workflow

### Device-Specific Features

**Q25 Enhancements:**
- **0 Key**: Press 0 for instant voice input
- **SYM Key** (⚙️/keycode 58): Access symbol picker
- **Ctrl Key** (Right Shift/keycode 60): Acts as Ctrl modifier
- **Currency Key**: Customizable currency symbol (Settings → Currency Key)
- **Alt+Currency Key**: Toggle phone speakerphone during calls
- **Double-tap SYM**: Quick flashlight toggle (works on lock screen and home screen)

**Titan 2 Features:**
- Swipe-to-delete enabled
- Swipe pad navigation on status bar
- Touch-sensitive keyboard support

### Other Conveniences

- **Double space to period**: Tap space twice to insert period + space + capitalize next letter
- **Clear Alt on Space**: Automatically disable Alt/Alt-Lock when pressing space
- **Minimal UI mode**: Hide suggestions, variations, mic, and quick actions for distraction-free typing
- **Compact status bar**: Minimal vertical space usage
- **Visual modifier indicators**: Color-coded badges show Shift/Ctrl/Alt status (blue=active/one-shot, orange=locked/latched)
- **LED strip**: Bottom LED strip also indicates modifier states
- **Update checker**: Check for new releases directly from settings

## Installation

1. Build the app or install the APK
2. Open the app and go to Settings → System → Languages & input → Virtual keyboard → Manage keyboards
3. Enable "TypeQ25 Physical Keyboard"
4. When typing, switch to TypeQ25 from your keyboard selector

## ⚙️ Configuration

Open the TypeQ25 app to access comprehensive settings organized into categories:

### Keyboard & Timing
- **Long Press Duration**: Adjust timing for long-press activation (default 500ms)
- **Long Press Modifier**: Choose between Alt+key or Shift+key for long press

### Text Input
- **Auto-Capitalize First Letter**: Toggle automatic capitalization
- **Auto-Capitalize After Period**: Toggle sentence capitalization
- **Spell Checker**: Enable Android system spell checking
- **Double Space to Period**: Toggle double-space feature
- **Clear Alt on Space**: Auto-clear Alt modifier on space press
- **Emoji Shortcodes**: Enable/disable :smile: style emoji insertion
- **Auto-Focus Input Fields**: Enable accessibility service for auto-focus
- **Speech Recognition App**: Choose preferred voice input app

### Auto-Correction
- **Languages**: Select active correction languages (English, Italian, French, German, Polish, Spanish)
- **Edit Corrections**: Add/edit/search custom corrections for each language
- **TypeQ25 Universal**: Corrections that work across all languages
- **Custom Languages**: Add your own language dictionaries

### Customization
- **Keyboard Layout**: Choose and customize QWERTY/AZERTY/QWERTZ layouts
- **Layout Editor**: Edit key mappings for non-English layouts
- **SYM Customization**: Configure emoji/symbol mappings and behavior
- **Character Variations**: Add multi-tap variations for keys
- **Currency Key**: Customize Q25 currency key symbol
- **Minimal UI Mode**: Hide extra UI elements for clean interface

### Advanced
- **Navigation Mode**: Enable and configure nav mode key mappings
- **Launcher Shortcuts**: Enable and assign app shortcuts for home screen
- **Power Shortcuts**: Enable and assign global app launching shortcuts
- **Swipe to Delete**: Enable/disable swipe gesture (Titan 2 only)
- **Trackpad Debug**: Test touch-sensitive keyboard events

### Help & About
- **Tutorial**: Interactive guide to TypeQ25 features
- **GitHub**: View source code and contribute
- **Build Info**: App version and build number
- **Check for Updates**: Check latest GitHub release

## 💻 Requirements

- Android 10 (API 29) or higher
- A device with a physical QWERTY keyboard
- Optimized for:
  - Unihertz Titan 2
  - Zinwa/Unihertz Q25
  - Can be adapted for other devices via JSON configuration files

## 🔨 Building

This project is built with:
- Kotlin
- Jetpack Compose for UI
- Gradle build system
- Minimum SDK: 29 (Android 10)
- Target SDK: 35 (Android 15)

Open the project in Android Studio and build as usual. The app uses auto-incrementing build numbers stored in `app/build.properties`.

## 🌍 Supported Languages

**UI Translations:**
- English
- Italian (Italiano)
- French (Français)
- German (Deutsch)
- Polish (Polski)
- Spanish (Español)

**Auto-Correction Dictionaries:**
- English (en)
- Italian (it)
- French (fr)
- German (de)
- Polish (pl)
- Spanish (es)
- TypeQ25 Universal (works across all languages)

## 🤝 Contributing

This is a personal hobby project, but contributions are welcome! Feel free to:
- Report bugs via GitHub Issues
- Suggest new features
- Submit pull requests
- Improve translations
- Add support for new keyboard layouts

## 📱 Installation

1. Download and install the APK (from Releases) or build from source
2. Go to: **Settings → System → Languages & input → Virtual keyboard → Manage keyboards**
3. Enable "**TypeQ25 Physical Keyboard**"
4. Open the TypeQ25 app and follow the interactive tutorial
5. When typing, switch to TypeQ25 from your keyboard selector
6. Configure settings to your preferences

This project is initially cloned from Pasteria project and made several improvements to work with Zinwa Q25
