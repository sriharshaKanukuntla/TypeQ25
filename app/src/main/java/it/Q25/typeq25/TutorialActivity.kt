package it.srik.TypeQ25

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.pager.*
import kotlinx.coroutines.launch

class TutorialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                TutorialScreen(
                    onComplete = {
                        // Mark tutorial as completed
                        SettingsManager.setTutorialCompleted(this, true)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun TutorialScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState()
    val scope = rememberCoroutineScope()
    val totalPages = 6

    Scaffold(
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Skip button
                    TextButton(
                        onClick = onComplete,
                        enabled = pagerState.currentPage < totalPages - 1
                    ) {
                        Text(if (pagerState.currentPage < totalPages - 1) "Skip" else "")
                    }

                    // Page indicator
                    HorizontalPagerIndicator(
                        pagerState = pagerState,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        activeColor = MaterialTheme.colorScheme.primary
                    )

                    // Next/Done button
                    Button(
                        onClick = {
                            if (pagerState.currentPage < totalPages - 1) {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                onComplete()
                            }
                        }
                    ) {
                        Text(if (pagerState.currentPage < totalPages - 1) "Next" else "Done")
                    }
                }
            }
        }
    ) { paddingValues ->
        HorizontalPager(
            count = totalPages,
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> BasicTypingPage()
                2 -> ModifierKeysPage()
                3 -> SymbolPickerPage()
                4 -> AdvancedFeaturesPage()
                5 -> TipsAndTricksPage()
            }
        }
    }
}

@Composable
fun WelcomePage() {
    TutorialPageLayout(
        icon = Icons.Default.Keyboard,
        title = "Welcome to TypeQ25!",
        subtitle = "Your physical keyboard companion for the Q25",
        iconTint = MaterialTheme.colorScheme.primary
    ) {
        TutorialItem(
            icon = Icons.Default.Keyboard,
            title = "Physical Keyboard First",
            description = "Designed specifically for your Q25's physical QWERTY keyboard"
        )
        TutorialItem(
            icon = Icons.Default.Speed,
            title = "Fast & Efficient",
            description = "Type faster with keyboard shortcuts and smart features"
        )
        TutorialItem(
            icon = Icons.Default.Settings,
            title = "Highly Customizable",
            description = "Customize layouts, shortcuts, and modifier behavior"
        )
    }
}

@Composable
fun BasicTypingPage() {
    TutorialPageLayout(
        icon = Icons.Default.Keyboard,
        title = "Basic Typing",
        subtitle = "Master the fundamentals"
    ) {
        TutorialItem(
            icon = Icons.Default.KeyboardAlt,
            title = "Standard Typing",
            description = "Type normally using your physical keyboard"
        )
        TutorialItem(
            icon = Icons.Default.ShortText,
            title = "Shift for Capitals",
            description = "Press Shift once to capitalize next letter\nDouble-tap Shift for CAPS LOCK"
        )
        TutorialItem(
            icon = Icons.Default.Backspace,
            title = "Quick Delete",
            description = "Backspace deletes characters\nCtrl+Backspace deletes whole words"
        )
        TutorialItem(
            icon = Icons.Default.Check,
            title = "Auto-Correction",
            description = "Smart auto-correct learns from your typing patterns"
        )
        TutorialItem(
            icon = Icons.Default.MoreVert,
            title = "Quick Actions",
            description = "• Tap ⋮ button in status bar for quick actions menu\n• Copy, Paste, Cut, Select All, Undo\n• Fast access to common editing tasks"
        )
        TutorialItem(
            icon = Icons.Default.Mic,
            title = "0 Key for Speech-to-Text (Q25)",
            description = "• Press 0 key in any text field to start voice input\n• Hands-free typing with voice recognition\n• Automatic punctuation and capitalization"
        )
    }
}

@Composable
fun ModifierKeysPage() {
    TutorialPageLayout(
        icon = Icons.Default.BorderColor,
        title = "Modifier Keys",
        subtitle = "Ctrl, Alt, and Symbol key usage"
    ) {
        TutorialItem(
            icon = Icons.Default.ControlCamera,
            title = "Ctrl Key (⚙️ or Right Shift on Q25)",
            description = "• Single-shot: Hold Ctrl, press key (modifier for one key)\n• Latched mode: Press Ctrl twice quickly (stays active until next Ctrl press)\n• Right Shift acts as Ctrl on Q25\n• Additional features available when Ctrl is latched\n• Ctrl+Space: Switch keyboard layout\n• Ctrl+C/V/X/A: Copy/Paste/Cut/Select All"
        )
        TutorialItem(
            icon = Icons.Default.AppShortcut,
            title = "Alt Key (🔣 key on Q25)",
            description = "• Single-shot: Hold Alt, press key (modifier for one key)\n• Latched mode: Press Alt twice quickly (stays active until next key)\n• Alt+W/E/R/S/D/F: Numbers 1-6\n• Alt+Z/X/C: Numbers 7-9\n• Alt+letters: Type special characters"
        )
        TutorialItem(
            icon = Icons.Default.EmojiEmotions,
            title = "SYM Key (⚙️ key on Q25)",
            description = "• Press SYM to open Symbol Picker popup\n• Navigate with arrow keys or touch\n• Quick access to emojis and symbols"
        )
    }
}

@Composable
fun SymbolPickerPage() {
    TutorialPageLayout(
        icon = Icons.Default.EmojiSymbols,
        title = "Symbol Picker",
        subtitle = "Your emoji and symbol companion"
    ) {
        TutorialItem(
            icon = Icons.Default.Star,
            title = "Favorites Tab (Default)",
            description = "• Opens by default for quick access\n• Long-press any symbol or emoji to add to Favorites\n• Up to 26 favorite items (Q-Z layout)\n• Long-press again to remove from Favorites\n• Star indicators show favorited items"
        )
        TutorialItem(
            icon = Icons.Default.KeyboardAlt,
            title = "Physical Key Insertion",
            description = "Press Q-Z keys to insert symbol/emoji shown on that button"
        )
        TutorialItem(
            icon = Icons.Default.SwapHoriz,
            title = "Navigation Shortcuts",
            description = "• Ctrl+T: Cycle between Favorites/Symbols/Emojis\n• Ctrl latch + S/J: Previous page\n• Ctrl latch + F/L: Next page\n• Ctrl latch + E/I: Previous category\n• Ctrl latch + D/K: Next category\n• DPad keys: Navigate UI elements"
        )
        TutorialItem(
            icon = Icons.Default.History,
            title = "Recent Items",
            description = "• Alt+W through Alt+C: Insert recent items (1-9)\n• Alt+0: Insert 10th recent item\n• Most used items appear first"
        )
        TutorialItem(
            icon = Icons.Default.TouchApp,
            title = "Touch Support",
            description = "• Tap buttons to insert\n• Long-press to add/remove favorites\n• Swipe left/right to change pages\n• Tap categories to switch"
        )
    }
}

@Composable
fun AdvancedFeaturesPage() {
    TutorialPageLayout(
        icon = Icons.Default.AutoAwesome,
        title = "Advanced Features",
        subtitle = "Power user tricks"
    ) {
        TutorialItem(
            icon = Icons.Default.EmojiEmotions,
            title = "Emoji & Symbol Shortcodes",
            description = "• Type :smile: to insert 😊\n• Type :tm: to insert ™, :copy: to insert ©\n• Suggestions appear as you type\n• Tab/Enter to insert, Esc to dismiss\n• Alt+1-9,0 for quick selection\n• Tap outside popup to dismiss\n• Toggle in Settings → Text Input"
        )
        TutorialItem(
            icon = Icons.Default.NavigateBefore,
            title = "Navigation Mode",
            description = "• Double-press Ctrl to enable Nav Mode\n• Use keyboard as D-pad for navigation\n• Exit with Ctrl again"
        )
        TutorialItem(
            icon = Icons.Default.Apps,
            title = "Power Shortcuts & Launcher Shortcuts",
            description = "• Launcher Shortcuts: Press a letter key in the launcher to open assigned apps\n• Power Shortcuts: Press SYM + letter key from anywhere to launch apps\n• Configure in Settings → Advanced Settings\n• Assign your favorite apps to keys A-Z\n• Quick app launching without touching the screen\n• Toast notification shows which key to press"
        )
        TutorialItem(
            icon = Icons.Default.Mic,
            title = "Speech-to-Text",
            description = "• Q25: Press 0 key to start voice input\n• Other devices: Alt+Ctrl together\n• Works in any text field\n• Automatic punctuation"
        )
        TutorialItem(
            icon = Icons.Default.Phone,
            title = "Phone Speakerphone Toggle (Q25)",
            description = "• Press Alt+Currency Key during a phone call\n• Quickly toggle speakerphone on/off\n• No need to touch the screen while on a call"
        )
        TutorialItem(
            icon = Icons.Default.Lightbulb,
            title = "Flashlight Toggle",
            description = "• Double-tap SYM key quickly to toggle flashlight\n• Works when keyboard is not active\n• Can be used on lock screen and home screen"
        )
        TutorialItem(
            icon = Icons.Default.AttachMoney,
            title = "Currency Symbol",
            description = "• Q25 has a dedicated currency key\n• Assign your desired currency from Settings\n• Quick access to your preferred currency symbol"
        )
        TutorialItem(
            icon = Icons.Default.Swipe,
            title = "Swipe Pad Navigation",
            description = "• The keyboard status bar doubles as a swipe pad\n• Swipe to move the cursor left/right\n• Quick cursor positioning without arrow keys\n• Available on supported devices (Titan 2)"
        )
        TutorialItem(
            icon = Icons.Default.Language,
            title = "Keyboard Layout Editing",
            description = "• Convert between QWERTY, AZERTY, QWERTZ layouts\n• Settings → Keyboard Layout\n• Edit non-English layouts to customize key mappings\n• Match your physical keyboard layout\n• Seamless typing in different languages\n• Switch layouts quickly with Ctrl+Space"
        )
        TutorialItem(
            icon = Icons.Default.ViewCompact,
            title = "Minimal UI Mode",
            description = "• Enable in Settings → Appearance\n• Hides suggestions, variations, mic, quick actions and settings\n• Perfect for physical keyboard-only typing\n• Saves screen space and battery"
        )
    }
}

@Composable
fun TipsAndTricksPage() {
    TutorialPageLayout(
        icon = Icons.Default.Lightbulb,
        title = "Tips & Tricks",
        subtitle = "Get the most out of TypeQ25"
    ) {
        TutorialItem(
            icon = Icons.Default.Speed,
            title = "Typing Faster",
            description = "• Use Ctrl+Backspace to delete words quickly\n• Learn keyboard shortcuts for common actions\n• Enable word predictions in settings"
        )
        TutorialItem(
            icon = Icons.Default.Settings,
            title = "Quick Settings Access",
            description = "• Tap the gear icon on the keyboard\n• Access settings without leaving your app\n• Quick toggle for common preferences\n• No interruption to your workflow"
        )
        TutorialItem(
            icon = Icons.Default.Palette,
            title = "Customize Your Experience",
            description = "• Settings → Customization for layouts\n• Create custom keyboard shortcuts\n• Adjust modifier key behavior\n• Customize SYM layer emojis and symbols"
        )
        TutorialItem(
            icon = Icons.Default.Book,
            title = "Enhanced Dictionary Management",
            description = "• Search functionality in dictionary corrections\n• Custom entries appear at the top\n• Universal corrections (TypeQ25)\n• Add your own autocorrections easily"
        )
        TutorialItem(
            icon = Icons.Default.School,
            title = "Status Bar Guide",
            description = "• Shows active modifiers (Ctrl, Alt, Shift)\n• Displays current keyboard mode\n• Tap to access quick settings"
        )
        TutorialItem(
            icon = Icons.Default.Refresh,
            title = "View This Tutorial Again",
            description = "Settings → Help & About → Tutorial"
        )
    }
}

@Composable
fun TutorialPageLayout(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(72.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        content()
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun TutorialItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(24.dp)
        )
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
