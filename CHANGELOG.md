# Changelog

## Upcoming Features
### Performance Optimizations
- **Handler Reuse**: Replaced repeated Handler instantiations with reusable class-level handlers, reducing object allocations and garbage collection pressure
- **Device Type Caching**: Cached device type detection results to avoid repeated SharedPreferences reads during key event processing
- **DeviceManager Optimization**: Added volatile caching layer to DeviceManager.getDevice() to prevent redundant storage access
- **Reduced Memory Allocations**: Eliminated temporary object creation in hot code paths for better performance during rapid typing

### Visual Feedback
- **Modifier State Indicators**: Colored badges now appear at the top of the keyboard when Shift, Ctrl, or Alt modifiers are active. Blue badges indicate active/one-shot state, orange badges indicate locked/latched state.

### Phone Integration (Q25)
- **Alt+Currency Key Speaker Toggle**: Press Alt+Currency Key during a phone call to toggle speakerphone on/off (Unihertz Q25 only)

### Q25 Device Support
- **Custom CTRL Key**: On Q25 devices, keycode 60 now acts as the CTRL modifier key for all keyboard shortcuts and navigation mode
- **Custom SYM Key**: On Q25 devices, keycode 58 now acts as the SYM key for accessing special characters and emojis
- **Disabled Swipe Features**: Swipe-to-delete and swipe pad navigation are disabled on Q25 devices for better stability

## New Features TypeQ25 0.2

### Keyboard Enhancements
- **Swipe Pad Navigation**: The keyboard status bar now doubles as a swipe pad, allowing you to move the cursor by swiping
- **Touch-Enabled Emojis and Symbols**: Emojis and symbols on the SYM keyboard are now also directly touchable for easier input
- **Keyboard Layout Conversion**: Added support for converting between different keyboard layouts (AZERTY, QWERTZ, etc.)

### Auto-Capitalization
- **Smart Sentence Capitalization**: Automatically capitalizes the first letter after sentences ending with periods, exclamation marks, or question marks

### Settings & Customization
- **Customizable Navigation Mode**: Navigation mode and Ctrl+key assignments can now be configured directly from the app settings
- **Quick Settings Access**: Added a quick toggle button (gear icon) to access settings directly from the keyboard
- **Enhanced Dictionary Management**: 
  - Added search functionality in the dictionary corrections interface
  - Custom dictionary entries now appear at the top of the list for easier access
  - Ricette TypeQ25: autocorrections that are valid in all the languages. (such as ppp-> %)
  - Added a lot of new unicode chara for sym layer page 2

### User Interface
- **UI Improvements**: Redesigned and improved the app's user interface, various issues solved (white font on light background in android light mode)
- **Multi-Language Support**: Added translations for multiple languages (may require manual review and corrections)

## Bug Fixes

- **Fixed Alt+Space Pop-up Issue**: Resolved a bug that caused an unwanted pop-up to appear when pressing Alt+Space or Alt+Letter+Space
- **Fixed Speech Recognition Focus**: Fixed an issue where Google Voice Typing would incorrectly shift focus to another app when activated


*This changelog covers all changes since the last release (v0.1-alpha).*

