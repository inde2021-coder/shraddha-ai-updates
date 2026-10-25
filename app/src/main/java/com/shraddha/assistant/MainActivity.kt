package com.shraddha.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.SweepGradient
import android.graphics.Shader
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.animation.ValueAnimator
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var prefs: SharedPreferences
    private lateinit var output: TextView
    private lateinit var input: EditText
    private lateinit var tts: TextToSpeech
    private var ttsReady = false
    private val speechCode = 2001
    private val wakeCode = 2002
    private val lensCode = 2003
    private val handler = Handler(Looper.getMainLooper())
    // wakeMode represents a user-started continuous conversation session. It remains true
    // across individual speech-recognition utterances until the user explicitly stops it.
    private var wakeMode = false
    private var voiceRestartAttempts = 0
    @Volatile private var aiRequestInProgress = false
    private var awaitingWakeCommand = false
    private lateinit var wakeButton: Button
    private var speechInProgress = false
    private var speechRecognizer: SpeechRecognizer? = null
    private val microphoneFromSettingsCode = 2023
    private val notificationsFromSettingsCode = 2024
    private var permissionStatusRefresh: (() -> Unit)? = null
    private var homeOrb: ShraddhaOrbView? = null
    private var navOrb: ShraddhaOrbView? = null
    private var appRootView: ViewGroup? = null
    private var currentOrbState: String = "idle"
    private var remoteAudioPlayer: MediaPlayer? = null
    private val galleryPickCode = 2004
    private val documentPickCode = 2005
    private val whatsappFilePickCode = 2006
    private val contactPickerCode = 2007
    private var pendingContactNumberField: EditText? = null
    private var pendingContactNameLabel: TextView? = null
    private var pendingImageMode: String = "lens"
    private var pendingBookPrompt: String = "Read the selected page and explain it clearly in Hindi-English."
    private val studioVoices = arrayOf("Leda", "Kore", "Aoede", "Zephyr", "Autonoe", "Despina", "Erinome", "Laomedeia", "Achernar", "Gacrux", "Sulafat", "Callirrhoe")
    private val orbStyles = arrayOf("Classic", "Galaxy", "Solar", "Lawa", "Ocean", "Aurora", "Glass", "Plasma", "Pulse", "Minimal", "Archer")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("assistant", MODE_PRIVATE)
        // Migrate the legacy visible assistant name once without overwriting any other custom name.
        val savedAssistantName = prefs.getString("assistant_name", null)
        if (savedAssistantName != null && savedAssistantName.equals(listOf('L', 'I', 'A').joinToString(""), ignoreCase = true)) {
            prefs.edit().putString("assistant_name", "Shraddha").apply()
        }
        tts = TextToSpeech(this, this)

        val ink = Color.rgb(248, 245, 255)
        val muted = Color.rgb(173, 161, 205)
        val violet = Color.rgb(183, 112, 255)
        val bg = Color.rgb(7, 7, 20)
        val panel = Color.rgb(20, 17, 42)
        val panel2 = Color.rgb(31, 22, 61)
        val neon = Color.rgb(125, 68, 255)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(7, 7, 20), Color.rgb(13, 7, 30), Color.rgb(6, 8, 23)))
            clipChildren = false
            clipToPadding = false
        }
        // Android 15 edge-to-edge can place app UI behind system controls. Keep a dedicated safe area.
        ViewCompat.setOnApplyWindowInsetsListener(shell) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            windowInsets
        }
        val pageHost = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            clipChildren = false
            clipToPadding = false
        }
        val navFrame = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            background = rounded(intArrayOf(Color.rgb(31, 24, 66), Color.rgb(18, 14, 43)), 32).apply {
                setStroke(dp(1), Color.argb(150, 126, 83, 232))
            }
            elevation = dp(16).toFloat()
            setPadding(dp(5), dp(2), dp(5), dp(2))
        }
        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        navFrame.addView(navRow, FrameLayout.LayoutParams(-1, -1))
        shell.addView(pageHost)
        shell.addView(navFrame, LinearLayout.LayoutParams(-1, dp(82)).apply {
            leftMargin = dp(12); rightMargin = dp(12); topMargin = dp(7); bottomMargin = dp(5)
        })
        val navItemContainers = mutableMapOf<String, LinearLayout>()
        val navItemIcons = mutableMapOf<String, TextView>()
        val navItemLabels = mutableMapOf<String, TextView>()
        fun updateNavSelection(activePage: String) {
            val dockPage = if (activePage in setOf("Home", "Chat", "History", "Settings")) activePage else "Home"
            navItemContainers.forEach { (name, view) ->
                val selected = name == dockPage
                view.background = if (selected) rounded(intArrayOf(Color.rgb(55, 37, 93), Color.rgb(28, 20, 56)), 18).apply {
                    setStroke(dp(1), Color.argb(90, 171, 108, 255))
                } else rounded(intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT), 18)
                navItemIcons[name]?.setTextColor(if (selected) Color.rgb(255, 191, 91) else Color.rgb(169, 155, 205))
                navItemLabels[name]?.setTextColor(if (selected) Color.rgb(255, 201, 115) else Color.rgb(169, 155, 205))
                navItemLabels[name]?.typeface = Typeface.create("sans-serif-medium", if (selected) Typeface.BOLD else Typeface.NORMAL)
            }
        }

        fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
            TextView(this).apply {
                this.text = text; textSize = size; setTextColor(color)
                if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }

        fun makePage(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(22))
            addView(label("L I A   A I   ·   P E R S O N A L   S P A C E", 8.5f, violet, true).apply {
                letterSpacing = 0.08f
                setPadding(0, 0, 0, dp(5))
            })
            addView(label(title, 25f, ink, true))
            addView(label(subtitle, 13f, muted).apply { setPadding(0, dp(6), 0, dp(12)) })
            val statusStrip = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(7), dp(10), dp(7))
                background = rounded(intArrayOf(Color.rgb(37, 25, 69), Color.rgb(16, 13, 34)), 18).apply {
                    setStroke(dp(1), Color.argb(100, 146, 91, 230))
                }
                elevation = dp(5).toFloat()
            }
            val pageOrb = ShraddhaOrbView(this@MainActivity).apply {
                setAppearance(currentOrbState, prefs.getString("orb_style", "Galaxy") ?: "Galaxy")
                contentDescription = "Shraddha animated AI status orb"
            }
            statusStrip.addView(pageOrb, LinearLayout.LayoutParams(dp(34), dp(34)))
            val statusCopy = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(9), 0, dp(6), 0)
                addView(label("SHRADDHA AI WORKSPACE", 10f, ink, true))
                addView(label("Voice  ·  Vision  ·  Memory", 9f, muted).apply { setPadding(0, dp(2), 0, 0) })
            }
            statusStrip.addView(statusCopy, LinearLayout.LayoutParams(0, -2, 1f))
            val readyChip = label("● STANDBY", 9f, Color.rgb(169, 255, 216), true).apply {
                setPadding(dp(9), dp(6), dp(9), dp(6))
                background = rounded(intArrayOf(Color.rgb(17, 58, 48), Color.rgb(17, 58, 48)), 30)
            }
            statusStrip.addView(readyChip)
            addView(statusStrip, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(4) })
        }

        fun actionCard(title: String, subtitle: String, icon: String, click: () -> Unit): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(15), dp(15), dp(14), dp(15))
                background = rounded(intArrayOf(Color.rgb(39, 27, 75), Color.rgb(17, 15, 38)), 20).apply { setStroke(dp(1), Color.argb(115, 126, 74, 219)) }
                elevation = dp(9).toFloat()
                isClickable = true; isFocusable = true; setOnClickListener { click() }
                addView(TextView(this@MainActivity).apply {
                    text = icon; textSize = 23f; gravity = Gravity.CENTER
                    setTextColor(violet); background = rounded(intArrayOf(panel, panel), 14)
                    elevation = dp(3).toFloat()
                }, LinearLayout.LayoutParams(dp(48), dp(48)))
                val texts = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0)
                    addView(label(title, 15f, ink, true))
                    addView(label(subtitle, 12f, muted).apply { setPadding(0, dp(4), 0, 0) })
                }
                addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
                addView(label("›", 27f, violet))
            }
            apply3DEffect(card)
            return card
        }

        fun showPage(page: String) {
            pageHost.removeAllViews()
            pageHost.addView(ShraddhaStarfieldView(this), FrameLayout.LayoutParams(-1, -1))
            if (page != "Home") homeOrb = null
            val scrollContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                clipChildren = false
                clipToPadding = false
                setPadding(0, 0, 0, dp(34))
            }
            val scroller = ScrollView(this).apply {
                isFillViewport = true
                clipToPadding = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(scrollContent)
            }
            pageHost.addView(scroller, FrameLayout.LayoutParams(-1, -1))
            scroller.alpha = 0f
            scroller.translationY = dp(12).toFloat()
            scroller.animate().alpha(1f).translationY(0f).setDuration(260).start()

            when (page) {
                "Home" -> {
                    // Shraddha-style header with a compact navigation menu and settings shortcut.
                    val top = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(18), dp(14), dp(18), dp(7))
                    }
                    val menuButton = TextView(this).apply {
                        text = "☰"; textSize = 22f; gravity = Gravity.CENTER
                        setTextColor(ink); background = rounded(intArrayOf(Color.rgb(29, 23, 57), Color.rgb(18, 15, 38)), 16).apply {
                            setStroke(dp(1), Color.argb(120, 111, 70, 192))
                        }
                        isClickable = true; isFocusable = true
                        setOnClickListener {
                            val menu = android.widget.PopupMenu(this@MainActivity, this)
                            listOf("Home", "Chat", "Create", "Write", "History", "Settings", "Visual Lens", "App & Task Control", "Study & Books", "Maps & Navigation").forEach { menu.menu.add(it) }
                            menu.setOnMenuItemClickListener { selected ->
                                when (selected.title.toString()) {
                                    "Home", "Chat", "Create", "Write", "History", "Settings" -> showPage(selected.title.toString())
                                    "Visual Lens" -> showLensTools()
                                    "App & Task Control" -> showAppControl()
                                    "Study & Books" -> showLearningTools()
                                    "Maps & Navigation" -> showMapsTools()
                                }
                                true
                            }
                            menu.show()
                        }
                        apply3DEffect(this)
                    }
                    top.addView(menuButton, LinearLayout.LayoutParams(dp(46), dp(46)))
                    val brand = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(dp(6), 0, dp(6), 0)
                        addView(label("Shraddha AI", 25f, ink, true).apply {
                            gravity = Gravity.CENTER
                            letterSpacing = 0.12f
                        })
                        addView(label("Y O U R   A I   A S S I S T A N T", 8f, muted, true).apply { gravity = Gravity.CENTER; letterSpacing = 0.10f })
                    }
                    top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
                    val settingsButton = TextView(this).apply {
                        text = "⚙"; textSize = 23f; setTextColor(ink); gravity = Gravity.CENTER
                        background = rounded(intArrayOf(Color.rgb(29, 23, 57), Color.rgb(18, 15, 38)), 16).apply {
                            setStroke(dp(1), Color.argb(120, 111, 70, 192))
                        }
                        isClickable = true; isFocusable = true
                        setOnClickListener { showPage("Settings") }
                        apply3DEffect(this)
                    }
                    top.addView(settingsButton, LinearLayout.LayoutParams(dp(46), dp(46)))
                    scrollContent.addView(top)

                    val hero = FrameLayout(this).apply {
                        clipChildren = false
                        clipToPadding = false
                        background = rounded(intArrayOf(Color.rgb(13, 8, 30), Color.rgb(11, 8, 26)), 28).apply {
                            setStroke(dp(1), Color.argb(115, 120, 58, 202))
                        }
                        elevation = dp(8).toFloat()
                    }
                    val art = ImageView(this).apply {
                        setImageResource(R.drawable.shraddha_home_hero)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = "Shraddha character artwork from the provided design reference"
                        background = rounded(intArrayOf(Color.rgb(7, 7, 20), Color.rgb(7, 7, 20)), 26)
                        clipToOutline = true
                    }
                    hero.addView(art, FrameLayout.LayoutParams(-1, dp(290), Gravity.TOP))
                    val tint = View(this).apply {
                        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
                            Color.argb(38, 6, 5, 18), Color.TRANSPARENT, Color.argb(90, 7, 7, 20), Color.rgb(7, 7, 20)
                        ))
                        isClickable = false
                    }
                    hero.addView(tint, FrameLayout.LayoutParams(-1, dp(300), Gravity.TOP))


                    val orbStage = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        clipChildren = false
                        clipToPadding = false
                        setPadding(0, dp(1), 0, 0)
                    }
                    val orb = ShraddhaOrbView(this).apply {
                        isClickable = true; isFocusable = true
                        contentDescription = "Shraddha AI continuous voice session. Tap to start or stop listening"
                        setOnClickListener { toggleWakeMode() }
                    }
                    homeOrb = orb
                    updateOrbState(currentOrbState)
                    orbStage.addView(orb, LinearLayout.LayoutParams(dp(90), dp(90)))
                    orbStage.addView(label("Tap to start / stop listening", 14f, ink, true).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, dp(1), 0, 0)
                        setShadowLayer(dp(4).toFloat(), 0f, 0f, Color.rgb(122, 54, 242))
                    })
                    orbStage.addView(ShraddhaWaveformView(this), LinearLayout.LayoutParams(dp(150), dp(19)).apply { topMargin = dp(1) })
                    hero.addView(orbStage, FrameLayout.LayoutParams(-1, dp(137), Gravity.BOTTOM))
                    scrollContent.addView(hero, LinearLayout.LayoutParams(-1, dp(356)).apply {
                        leftMargin = dp(8); rightMargin = dp(8); topMargin = dp(4); bottomMargin = dp(5)
                    })

                    val messageBar = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(9), dp(5), dp(7), dp(5))
                        background = rounded(intArrayOf(Color.rgb(28, 21, 53), Color.rgb(14, 12, 32)), 28).apply {
                            setStroke(dp(1), Color.argb(150, 115, 65, 202))
                        }
                        elevation = dp(8).toFloat()
                    }
                    messageBar.addView(TextView(this).apply {
                        text = "✦"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.rgb(211, 160, 255))
                        background = rounded(intArrayOf(Color.rgb(53, 35, 104), Color.rgb(29, 22, 61)), 24)
                    }, LinearLayout.LayoutParams(dp(42), dp(42)))
                    input = EditText(this).apply {
                        hint = "Ask me anything…"; textSize = 15f; setSingleLine(true)
                        setTextColor(ink); setHintTextColor(Color.rgb(144, 131, 180))
                        setPadding(dp(12), dp(10), dp(7), dp(10))
                        background = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
                    }
                    messageBar.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))
                    messageBar.addView(premiumButton("➤", neon, Color.WHITE) {
                        val prompt = input.text.toString().trim()
                        if (prompt.isNotBlank()) askAI(prompt) else startSpeech()
                    }, LinearLayout.LayoutParams(dp(48), dp(46)).apply { leftMargin = dp(4) })
                    scrollContent.addView(messageBar, LinearLayout.LayoutParams(-1, dp(58)).apply {
                        leftMargin = dp(18); rightMargin = dp(18); topMargin = dp(6); bottomMargin = dp(1)
                    })

                    scrollContent.addView(label("Quick actions", 17f, ink, true).apply {
                        setPadding(dp(22), dp(17), dp(22), dp(9))
                    })
                    val quick = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(dp(13), 0, dp(13), 0)
                        clipChildren = false
                        clipToPadding = false
                    }
                    fun quickCard(title: String, subtitle: String, glyph: String, accent: Int, click: () -> Unit) {
                        val c = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER
                            setPadding(dp(7), dp(11), dp(7), dp(9))
                            background = rounded(intArrayOf(Color.rgb(31, 23, 60), Color.rgb(15, 13, 35)), 19).apply {
                                setStroke(dp(1), Color.argb(105, 117, 72, 209))
                            }
                            isClickable = true; isFocusable = true; setOnClickListener { click() }
                            elevation = dp(7).toFloat()
                            addView(TextView(this@MainActivity).apply {
                                text = glyph; textSize = 21f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                                background = rounded(intArrayOf(accent, blendColor(accent, Color.BLACK, 0.38f)), 28)
                                elevation = dp(3).toFloat()
                            }, LinearLayout.LayoutParams(dp(39), dp(39)))
                            addView(label(title, 12f, ink, true).apply {
                                gravity = Gravity.CENTER; setPadding(0, dp(7), 0, 0)
                            })
                            addView(label(subtitle, 9f, muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(3), 0, 0) })
                        }
                        apply3DEffect(c)
                        quick.addView(c, LinearLayout.LayoutParams(0, dp(114), 1f).apply {
                            leftMargin = dp(4); rightMargin = dp(4)
                        })
                    }
                    quickCard("Chat", "Ask anything", "▤", Color.rgb(64, 87, 240)) { showPage("Chat") }
                    quickCard("Create Image", "Visual creativity", "▧", Color.rgb(132, 69, 242)) { showLensTools() }
                    quickCard("Write", "Notes & content", "▤", Color.rgb(175, 66, 237)) { showPage("Write") }
                    scrollContent.addView(quick)

                    scrollContent.addView(label("Explore Shraddha AI", 17f, ink, true).apply {
                        setPadding(dp(22), dp(18), dp(22), dp(9))
                    })
                    scrollContent.addView(actionCard("Voice Studio", "12 voices · Hindi, English and Hinglish · live orb styles", "♫") { showPersonaSettings() }, LinearLayout.LayoutParams(-1, -2).apply {
                        leftMargin = dp(18); rightMargin = dp(18); bottomMargin = dp(9)
                    })
                    scrollContent.addView(actionCard("Visual Lens", "Understand photos, pages and colors", "◉") { showLensTools() }, LinearLayout.LayoutParams(-1, -2).apply {
                        leftMargin = dp(18); rightMargin = dp(18); bottomMargin = dp(9)
                    })
                    scrollContent.addView(actionCard("Study & Books", "Read supported documents and learn step by step", "▤") { showLearningTools() }, LinearLayout.LayoutParams(-1, -2).apply {
                        leftMargin = dp(18); rightMargin = dp(18); bottomMargin = dp(9)
                    })

                    val statusCard = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(15), dp(13), dp(15), dp(13))
                        background = rounded(intArrayOf(Color.rgb(25, 19, 49), Color.rgb(12, 11, 29)), 18).apply {
                            setStroke(dp(1), Color.argb(90, 112, 72, 194))
                        }
                        elevation = dp(4).toFloat()
                    }
                    statusCard.addView(label("SHRADDHA IS HERE FOR YOU", 10f, violet, true))
                    output = label("Ready when you are. Tap the orb to speak.", 12f, muted).apply { setPadding(0, dp(5), 0, 0) }
                    statusCard.addView(output)
                    scrollContent.addView(statusCard, LinearLayout.LayoutParams(-1, -2).apply {
                        leftMargin = dp(18); rightMargin = dp(18); topMargin = dp(4); bottomMargin = dp(5)
                    })
                }
                "Chat" -> {
                    scrollContent.addView(makePage("AI Chat", "Ask anything in Hindi, English or Hinglish"))
                    val chatCard = premiumSection("CONVERSATION STUDIO", "A focused space for questions, answers and follow-ups.")
                    val chatStatus = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    chatStatus.addView(ShraddhaOrbView(this).apply {
                        setAppearance(currentOrbState, prefs.getString("orb_style", "Galaxy") ?: "Galaxy")
                        contentDescription = "Shraddha conversation state"
                    }, LinearLayout.LayoutParams(dp(42), dp(42)))
                    val statusCopy = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(10), 0, 0, 0)
                        addView(label("SHRADDHA IS STANDING BY", 12f, ink, true))
                        addView(label("Hindi  ·  English  ·  Hinglish", 10f, muted).apply { setPadding(0, dp(3), 0, 0) })
                    }
                    chatStatus.addView(statusCopy, LinearLayout.LayoutParams(0, -2, 1f))
                    chatStatus.addView(label("✦ LIVE", 9f, Color.rgb(213, 175, 255), true).apply {
                        setPadding(dp(8), dp(6), dp(8), dp(6))
                        background = rounded(intArrayOf(Color.rgb(61, 37, 103), Color.rgb(33, 23, 67)), 30)
                    })
                    chatCard.addView(chatStatus, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
                    input = premiumField("Type your message…", multiLine = true).apply {
                        minLines = 4; maxLines = 8; gravity = Gravity.TOP or Gravity.START
                    }
                    chatCard.addView(input, LinearLayout.LayoutParams(-1, -2))
                    chatCard.addView(premiumButton("Send to Shraddha  ↗", neon, Color.WHITE) {
                        val prompt = input.text.toString().trim()
                        if (prompt.isNotBlank()) askAI(prompt) else say("Pehle apna sawal likhein ya bolne ke liye Voice orb dabayein.")
                    }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(10) })
                    scrollContent.addView(chatCard, LinearLayout.LayoutParams(-1, -2).apply {
                        leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(13)
                    })
                    val responseCard = premiumSection("LATEST RESPONSE", "The most recent answer from your assistant.")
                    responseCard.addView(label(output.text.toString(), 13f, muted).apply { setPadding(0, dp(5), 0, dp(2)) })
                    scrollContent.addView(responseCard, LinearLayout.LayoutParams(-1, -2).apply {
                        leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(10)
                    })
                    scrollContent.addView(actionCard("Voice Studio", "Choose one of 12 voices and preview it", "♫") { showPersonaSettings() }, LinearLayout.LayoutParams(-1, -2).apply {
                        leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(9)
                    })
                }
                "Create" -> {
                    scrollContent.addView(makePage("Create", "Turn ideas into something new"))
                    scrollContent.addView(actionCard("Visual Lens", "Use camera and ask about what you see", "◉") { launchCamera() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
                    scrollContent.addView(actionCard("Web Search", "Search the web for current information", "⌕") { openUrl("https://www.google.com/search?q=" + Uri.encode(input.text.toString())) }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
                    scrollContent.addView(actionCard("Music", "Open your music app", "♫") { openUrl("https://music.youtube.com") }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
                    scrollContent.addView(actionCard("Write", "Draft text with Shraddha AI", "✎") { showPage("Write") })
                }
                "Write" -> {
                    scrollContent.addView(makePage("Write with AI", "Messages, ideas, captions, stories and more"))
                    val writingCard = premiumSection("WRITING STUDIO", "Describe the tone, purpose and audience. Shraddha will prepare a draft for your review.")
                    val formatChip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    formatChip.addView(label("✎", 22f, Color.rgb(215, 172, 255)).apply {
                        gravity = Gravity.CENTER; background = rounded(intArrayOf(Color.rgb(75, 44, 126), Color.rgb(36, 24, 72)), 14)
                    }, LinearLayout.LayoutParams(dp(42), dp(42)))
                    val helper = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0)
                        addView(label("DRAFT ANYTHING", 11f, ink, true))
                        addView(label("Email  ·  Notes  ·  Posts  ·  Code", 10f, muted).apply { setPadding(0, dp(3), 0, 0) })
                    }
                    formatChip.addView(helper, LinearLayout.LayoutParams(0, -2, 1f))
                    writingCard.addView(formatChip, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
                    input = premiumField("What should I write for you? Include topic, tone and length…", multiLine = true).apply {
                        minLines = 6; maxLines = 12; gravity = Gravity.TOP or Gravity.START
                    }
                    writingCard.addView(input, LinearLayout.LayoutParams(-1, -2))
                    writingCard.addView(premiumButton("Generate draft  ✦", neon, Color.WHITE) {
                        val prompt = input.text.toString().trim()
                        if (prompt.isNotBlank()) askAI("Write this for me: $prompt") else say("Pehle batayein ki kya likhna hai.")
                    }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(12) })
                    scrollContent.addView(writingCard, LinearLayout.LayoutParams(-1, -2).apply {
                        leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(12)
                    })
                    scrollContent.addView(actionCard("Voice input", "Speak your writing prompt instead of typing", "♫") { startSpeech() }, LinearLayout.LayoutParams(-1, -2).apply {
                        leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(10)
                    })
                }
                "History" -> {
                    scrollContent.addView(makePage("History", "Your recent activity and saved notes"))
                    scrollContent.addView(actionCard("Saved notes", "Open notes you've saved", "▤") { showNotes() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
                    scrollContent.addView(actionCard("AI memory", "Review your saved assistant memory", "♥") { showMemory() })
                }
                "Settings" -> {
                    scrollContent.addView(makePage("Settings", "Your personal AI space · Everything in one place"))
                    fun groupTitle(text: String) {
                        scrollContent.addView(label("✦  $text", 11f, violet, true).apply {
                            letterSpacing = 0.12f
                            setPadding(dp(22), dp(9), dp(22), dp(8))
                        })
                    }
                    fun settingsGroup(items: List<Triple<String, String, String>>, action: (String) -> Unit) {
                        val group = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(8), dp(4), dp(8), dp(4))
                            background = rounded(intArrayOf(Color.rgb(24, 18, 48), Color.rgb(12, 11, 30)), 22).apply {
                                setStroke(dp(1), Color.argb(145, 88, 56, 164))
                            }
                            elevation = dp(7).toFloat()
                        }
                        items.forEachIndexed { index, item ->
                            val row = LinearLayout(this).apply {
                                orientation = LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(dp(9), dp(10), dp(8), dp(10))
                                isClickable = true; isFocusable = true
                                background = rounded(intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT), 15)
                                setOnClickListener { action(item.first) }
                                val iconTile = TextView(this@MainActivity).apply {
                                    text = item.third; textSize = 21f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                                    background = rounded(intArrayOf(Color.rgb(142, 72, 246), Color.rgb(67, 39, 138)), 14)
                                    elevation = dp(4).toFloat()
                                }
                                addView(iconTile, LinearLayout.LayoutParams(dp(43), dp(43)))
                                val texts = LinearLayout(this@MainActivity).apply {
                                    orientation = LinearLayout.VERTICAL
                                    setPadding(dp(12), 0, dp(4), 0)
                                    addView(label(item.first, 14f, ink, true))
                                    addView(label(item.second, 10.5f, muted).apply { setPadding(0, dp(3), 0, 0) })
                                }
                                addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
                                addView(label("›", 27f, Color.rgb(207, 174, 255)))
                            }
                            apply3DEffect(row)
                            group.addView(row, LinearLayout.LayoutParams(-1, dp(68)))
                            if (index != items.lastIndex) group.addView(View(this@MainActivity).apply {
                                setBackgroundColor(Color.argb(45, 154, 121, 215))
                            }, LinearLayout.LayoutParams(-1, dp(1)).apply { leftMargin = dp(56); rightMargin = dp(10) })
                        }
                        scrollContent.addView(group, LinearLayout.LayoutParams(-1, -2).apply {
                            leftMargin = dp(16); rightMargin = dp(16); bottomMargin = dp(9)
                        })
                    }
                    groupTitle("LOOK & FEEL")
                    settingsGroup(listOf(
                        Triple("Look & Feel", "Orb styles, glow and motion intensity", "◉"),
                        Triple("Voice & Language", "12 voices · language, speed and volume", "♫"),
                        Triple("Profile", "Assistant identity and personal preferences", "☺")
                    )) { choice -> when (choice) {
                        "Look & Feel" -> showLookAndFeel()
                        "Voice & Language" -> showPersonaSettings()
                        "Profile" -> showProfileSettings()
                    } }
                    groupTitle("CHATS & MEMORY")
                    settingsGroup(listOf(
                        Triple("Conversation History", "Recent activity and saved notes", "◴"),
                        Triple("AI Memory", "Edit saved context and Visual Lens notes", "♥")
                    )) { choice -> when (choice) {
                        "Conversation History" -> showPage("History")
                        "AI Memory" -> showMemory()
                    } }
                    groupTitle("PHONE CONTROL")
                    settingsGroup(listOf(
                        Triple("Phone Control", "Calls, SMS, WhatsApp and clipboard", "☎"),
                        Triple("App & Task Control", "Launch apps, read visible text, fill fields", "▣"),
                        Triple("Wake & Background Alerts", "Hey Shraddha, double clap and battery alerts", "♧"),
                        Triple("Permissions & Privacy", "Enable sensitive access only when you choose", "♙"),
                        Triple("Task Automation", "Command shortcuts and opt-in rules", "✧")
                    )) { choice -> when (choice) {
                        "Phone Control" -> showPhoneToolsPage()
                        "App & Task Control" -> showAppControl()
                        "Wake & Background Alerts" -> showWakeActivationSettings()
                        "Permissions & Privacy" -> showPermissionsSettings()
                        "Task Automation" -> showAutomation()
                    } }
                    groupTitle("CONNECTIONS & LEARNING")
                    settingsGroup(listOf(
                        Triple("Connections", "AI providers and chat model API keys", "✦"),
                        Triple("AI Vision API", "Separate provider, model and vision key", "◉"),
                        Triple("Visual Lens", "Photos, pages, objects and color recognition", "◎"),
                        Triple("Study & Books", "Maths, science and document reading", "▤"),
                        Triple("Maps & Navigation", "Places, searches and route hand-off", "⌖"),
                        Triple("Feature Coverage", "Available, limited, and how to enable features", "◈")
                    )) { choice -> when (choice) {
                        "Connections" -> showSettings()
                        "AI Vision API" -> showVisionApiSettings()
                        "Visual Lens" -> showLensTools()
                        "Study & Books" -> showLearningTools()
                        "Maps & Navigation" -> showMapsTools()
                        "Feature Coverage" -> showFeatureCoverage()
                    } }
                    groupTitle("ABOUT & SUPPORT")
                    settingsGroup(listOf(
                        Triple("Developer Details", "Sharad Patel · 8120727987", "✧"),
                        Triple("Feature Enable Guide", "See which access is available and how to enable it", "ⓘ")
                    )) { choice -> when (choice) {
                        "Developer Details" -> showDeveloperDetails()
                        "Feature Enable Guide" -> showFeatureEnableGuide()
                    } }
                    scrollContent.addView(premiumText("Your permissions stay under your control. Sensitive actions are never silently submitted.", 10.5f, muted).apply {
                        setPadding(dp(22), dp(7), dp(22), dp(12))
                    })
                }

            }
            updateNavSelection(page)
            // Staggered entrance gives each screen a smoother, more deliberate Shraddha-like motion.
            for (i in 0 until scrollContent.childCount) {
                val child = scrollContent.getChildAt(i)
                if (prefs.getBoolean("reduce_motion", false)) {
                    child.alpha = 1f
                    child.translationY = 0f
                } else {
                    child.alpha = 0f
                    child.translationY = dp(9).toFloat()
                    child.animate().alpha(1f).translationY(0f)
                        .setStartDelay((i * 24L).coerceAtMost(180L)).setDuration(260L).start()
                }
            }
        }

        val navItems = listOf(
            Triple("Home", "⌂", "Home"),
            Triple("Chat", "▤", "Chat"),
            Triple("Voice", "", "Voice"),
            Triple("History", "◴", "History"),
            Triple("Settings", "⚙", "Settings")
        )
        navItems.forEach { item ->
            if (item.first == "Voice") {
                navRow.addView(View(this).apply { isClickable = false }, LinearLayout.LayoutParams(0, -1, 1f))
            } else {
                val iconView = TextView(this).apply {
                    text = item.second; textSize = 25f; gravity = Gravity.CENTER
                    setTextColor(if (item.first == "Home") Color.rgb(255, 191, 91) else Color.rgb(169, 155, 205))
                }
                val labelView = TextView(this).apply {
                    text = item.first; textSize = 11.5f; gravity = Gravity.CENTER
                    setTextColor(if (item.first == "Home") Color.rgb(255, 201, 115) else Color.rgb(169, 155, 205))
                    typeface = Typeface.create("sans-serif-medium", if (item.first == "Home") Typeface.BOLD else Typeface.NORMAL)
                    setPadding(0, dp(1), 0, 0)
                }
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    setPadding(dp(2), dp(5), dp(2), dp(3))
                    isClickable = true; isFocusable = true
                    background = if (item.first == "Home") rounded(intArrayOf(Color.rgb(55, 37, 93), Color.rgb(28, 20, 56)), 18).apply { setStroke(dp(1), Color.argb(90, 171, 108, 255)) } else rounded(intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT), 18)
                    addView(iconView, LinearLayout.LayoutParams(-1, dp(31)))
                    addView(labelView, LinearLayout.LayoutParams(-1, dp(20)))
                    setOnClickListener { showPage(item.third) }
                }
                navItemContainers[item.first] = cell
                navItemIcons[item.first] = iconView
                navItemLabels[item.first] = labelView
                apply3DEffect(cell)
                navRow.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
            }
        }
        val navOrbView = ShraddhaOrbView(this).apply {
            contentDescription = "Shraddha voice assistant. Tap to start or stop continuous listening. Long-press for voice options."
            isClickable = true; isFocusable = true
            setOnClickListener { toggleWakeMode() }
            setOnLongClickListener { showPersonaSettings(); true }
            setAppearance("idle", "Solar")
        }
        navOrb = navOrbView
        navFrame.addView(navOrbView, FrameLayout.LayoutParams(dp(61), dp(61), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = -dp(8)
        })
        navFrame.addView(TextView(this).apply {
            text = "Voice"; textSize = 10.5f; gravity = Gravity.CENTER
            setTextColor(Color.rgb(198, 169, 238)); typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }, FrameLayout.LayoutParams(dp(70), dp(18), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(59) })
        updateNavSelection("Home")
        showPage("Home")
        setContentView(shell)
        appRootView = shell
        ViewCompat.requestApplyInsets(shell)

        // Privacy-first: do not request microphone access on app launch. The user manages it in Settings > Permissions & Privacy.
        if (intent.getBooleanExtra(WakeActivationService.EXTRA_AUTO_LISTEN, false)) {
            stopWakeService()
            handler.postDelayed({ if (!isFinishing && !isDestroyed) startContinuousVoiceSession() }, 650)
        }
    }

    override fun onResume() {
        super.onResume()
        permissionStatusRefresh?.invoke()
        if (wakeMode && !speechInProgress && !aiRequestInProgress && !(::tts.isInitialized && tts.isSpeaking)) {
            scheduleContinuousListening(650L)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(WakeActivationService.EXTRA_AUTO_LISTEN, false)) {
            stopWakeService()
            handler.postDelayed({ if (!isFinishing && !isDestroyed) startContinuousVoiceSession() }, 650)
        }
    }

    private fun refreshShraddhaMotionViews() {
        fun visit(view: View) {
            when (view) {
                is ShraddhaOrbView -> view.refreshMotionPreference()
                is ShraddhaWaveformView -> view.refreshMotionPreference()
                is ShraddhaStarfieldView -> view.refreshMotionPreference()
            }
            if (view is ViewGroup) {
                for (index in 0 until view.childCount) visit(view.getChildAt(index))
            }
        }
        appRootView?.let { visit(it) }
        updateOrbState(currentOrbState)
    }

    private fun showPhoneToolsPage() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(4), dp(2), dp(8)) }
        body.addView(premiumActionCard("Automation rules", "Configure shortcuts and command routing", "✧") { showAutomation() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        body.addView(premiumActionCard("Notification & battery alerts", "Wake prompt, low-battery and full-charge alerts", "♧") { showBackgroundAutomation() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        body.addView(premiumActionCard("Calls & messages", "Open dialer, compose SMS or WhatsApp", "☎") { showCommunicationTools() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        body.addView(premiumActionCard("Clipboard AI", "Summarize the text you copied", "▤") { summarizeClipboard() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        body.addView(premiumActionCard("Visual Lens & Color", "Camera, gallery, object/text explanation and color recognition", "◉") { showLensTools() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        body.addView(premiumActionCard("App & Task Control", "Open installed apps, read visible screen, fill fields and tap controls", "▣") { showAppControl() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        body.addView(premiumActionCard("Study, Books & Documents", "PDF/TXT reading, explain pages, maths and science tutor", "▤") { showLearningTools() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        body.addView(premiumActionCard("Maps & Navigation", "Search places, nearby locations and directions", "⌖") { showMapsTools() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        body.addView(premiumActionCard("Share image / document", "Choose a file and send it through WhatsApp or another app", "↗") { pickFileToShare() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        body.addView(premiumActionCard("Windows PC pairing", "Connect to your own trusted PC", "⌘") { showWindowsSettings() })
        showPremiumPanel("Phone Tools", "Premium 3D tools, alerts and personal automation", "▣", body)
    }

    private fun showAppControl() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        val launchCard = premiumSection("APP LAUNCHER", "Open installed apps by name. Android does not let a normal app force-stop other apps; Home/Back is supported.")
        val appName = premiumField("App name (e.g. WhatsApp, YouTube, Maps)")
        launchCard.addView(appName)
        launchCard.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(premiumButton("Launch app", Color.rgb(111, 64, 255), Color.WHITE) { launchInstalledApp(appName.text.toString().trim()) }, LinearLayout.LayoutParams(0, dp(47), 1f).apply { rightMargin = dp(6); topMargin = dp(10) })
            addView(premiumButton("Browse apps", Color.rgb(57, 39, 94), Color.WHITE) { showInstalledAppsPicker() }, LinearLayout.LayoutParams(0, dp(47), 1f).apply { leftMargin = dp(6); topMargin = dp(10) })
        })
        launchCard.addView(premiumButton("Open selected app's system settings", Color.rgb(57, 39, 94), Color.WHITE) {
            val query = appName.text.toString().trim()
            val app = findLaunchableApps(query).firstOrNull()
            if (app == null) say("App nahi mili. Pehle exact app name likhein.")
            else try { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))) } catch (_: Exception) { say("App settings nahi khul sake.") }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        body.addView(launchCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val screenCard = premiumSection("SCREEN READING & APP CONTROL", "Unavailable in this sideload-safe build. Cross-app accessibility can expose visible UI content and interact with other apps, so it is not registered in this APK.")
        screenCard.addView(premiumText("You can still use Visual Lens with a screenshot or photo that you select yourself. For a full-access edition, use a reviewed, policy-compliant distribution route rather than suppressing Play Protect.", 11f, Color.rgb(194, 183, 220)))
        screenCard.addView(premiumButton("How to enable / availability", Color.rgb(75, 42, 130), Color.WHITE) { showFeatureEnableGuide() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        screenCard.addView(premiumButton("Open Visual Lens", Color.rgb(111, 64, 255), Color.WHITE) { showLensTools() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        body.addView(screenCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val taskCard = premiumSection("SEARCH, BOOKING & TROUBLESHOOTING", "Search opens the browser; booking, orders, payment, and message sending remain in the destination app for your review and confirmation.")
        val taskText = premiumField("Task, item, place, booking, or app error…", multiLine = true)
        taskCard.addView(taskText)
        taskCard.addView(premiumButton("Search online", Color.rgb(111, 64, 255), Color.WHITE) { searchOnlineTask(taskText.text.toString().trim()) }, LinearLayout.LayoutParams(-1, dp(47)).apply { topMargin = dp(8) })
        taskCard.addView(premiumButton("Explain / troubleshoot with AI", Color.rgb(57, 39, 94), Color.WHITE) {
            val task = taskText.text.toString().trim()
            if (task.isBlank()) say("Problem ya task ka detail likhein.") else askAI("Help me troubleshoot or plan this task safely: $task. Give steps, explain what can and cannot be executed by this Android app, and never claim to have repaired another app unless verified.")
        }, LinearLayout.LayoutParams(-1, dp(47)).apply { topMargin = dp(8) })
        body.addView(taskCard)
        showPremiumPanel("App & Task Control", "Your apps, your permission, and your confirmation", "▣", body)
    }

    private data class LaunchableApp(val packageName: String, val label: String)

    private fun findLaunchableApps(query: String = ""): List<LaunchableApp> {
        return try {
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            packageManager.queryIntentActivities(launcher, 0).mapNotNull { resolve ->
                val packageName = resolve.activityInfo?.packageName ?: return@mapNotNull null
                val label = resolve.loadLabel(packageManager)?.toString().orEmpty()
                if (label.isBlank() || packageName == this.packageName) null else LaunchableApp(packageName, label)
            }.distinctBy { it.packageName }.filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }.sortedBy { it.label.lowercase(Locale.ROOT) }
        } catch (_: Exception) { emptyList() }
    }

    private fun launchInstalledApp(query: String) {
        if (query.isBlank()) { showInstalledAppsPicker(); return }
        val apps = findLaunchableApps(query)
        if (apps.isEmpty()) { say("Installed app '$query' nahi mila. Browse apps se select karein."); return }
        val chosen = apps.firstOrNull { it.label.equals(query, true) } ?: apps.firstOrNull { it.label.startsWith(query, true) } ?: apps.first()
        try {
            val launch = packageManager.getLaunchIntentForPackage(chosen.packageName)
            if (launch == null) say("${chosen.label} ko launch nahi kiya ja sakta.")
            else { prefs.edit().putString("last_target_package", chosen.packageName).putString("last_target_app", chosen.label).apply(); launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(launch); say("${chosen.label} khol rahi hoon.") }
        } catch (_: Exception) { say("${chosen.label} launch nahi ho payi.") }
    }

    private fun showInstalledAppsPicker() {
        val apps = findLaunchableApps()
        if (apps.isEmpty()) { say("Launchable apps ki list nahi mil saki."); return }
        val labels = apps.map { it.label }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Choose an app").setItems(labels) { _, which -> launchInstalledApp(apps[which].label) }.setNegativeButton("Cancel", null).show()
    }

    // Sideload-safe distribution: don't register cross-app Accessibility capability in this APK.
    private fun isAccessibilityEnabled(): Boolean = false

    private fun openAccessibilitySettings() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("App control unavailable in this install build")
            .setMessage("This sideload-safe build does not register an Accessibility service. Cross-app screen reading, automated taps and field filling are disabled here to avoid the sensitive-access install block. Use Visual Lens with a screenshot/photo, or distribute the full-access edition through a reviewed, policy-compliant channel.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun confirmSpecialAccess(title: String, explanation: String, continueToSettings: () -> Unit) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(explanation)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue to Android settings") { _, _ -> continueToSettings() }
            .show()
    }

    private fun readActiveScreen() {
        val visibleText = ShraddhaAccessibilityService.readVisibleScreen()
        if (visibleText.isNullOrBlank()) {
            say("Cross-app screen reading is disabled in this sideload-safe build. Use Visual Lens to select a screenshot or photo instead.")
            return
        }
        askAI("Read and explain this visible Android screen in Hindi-English. Summarize important controls and visible text. Do not infer hidden content; do not click or change anything.\n\n$visibleText")
    }

    private fun confirmAndTap(label: String, confirmed: Boolean = false) {
        if (label.isBlank()) { say("Button ya visible label ka naam batayein."); return }
        if (!isAccessibilityEnabled() || !ShraddhaAccessibilityService.isConnected()) {
            say("Cross-app app control is disabled in this sideload-safe build. Open the app manually or use its official intents instead."); return
        }
        val sensitive = Regex("(?i)\\b(pay|payment|buy|purchase|checkout|place order|book now|confirm|submit|send|transfer|delete|remove|cancel booking|purchase now|subscribe)\\b").containsMatchIn(label)
        if (sensitive && !confirmed) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Confirm action")
                .setMessage("'$label' can submit, send, purchase, or change something. Tap only if you intend to perform this action in the app.")
                .setPositiveButton("Tap this control") { _, _ -> confirmAndTap(label, true) }
                .setNegativeButton("Cancel", null).show()
            return
        }
        val ok = ShraddhaAccessibilityService.clickVisibleLabel(label)
        say(if (ok) "Visible control '$label' tapped." else "'$label' label nahi mili ya app ne click allow nahi kiya.")
    }

    private fun fillVisibleField(match: String, value: String) {
        if (match.isBlank() || value.isEmpty()) { say("Field ka label aur value dono bharein."); return }
        if (!isAccessibilityEnabled() || !ShraddhaAccessibilityService.isConnected()) {
            say("Cross-app field filling is disabled in this sideload-safe build. Use the destination app's form directly."); return
        }
        val ok = ShraddhaAccessibilityService.fillEditableField(match, value)
        say(if (ok) "Text field update ho gaya. Submit / send karne se pehle review kar lein." else "Editable field nahi mili. Field hint ya label ko chhota/exact karke try karein.")
    }

    private fun goHomeOrBack(home: Boolean) {
        val ok = if (home) ShraddhaAccessibilityService.goHome() else ShraddhaAccessibilityService.goBack()
        if (!ok) {
            if (home) {
                try { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) { say("Home screen nahi khul saki.") }
            } else {
                say("Cross-app Back control is disabled in this sideload-safe build.")
            }
        } else say(if (home) "Home screen par ja rahi hoon. Android doosre apps ko force-stop karne nahi deta." else "Back action complete.")
    }

    private fun showLensTools() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        body.addView(premiumActionCard("AI Vision API settings", "Choose a vision-capable provider, model and API key", "✦") { showVisionApiSettings() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        body.addView(premiumActionCard("Camera Visual Lens", "Explain objects, signs, layouts, visible text and context", "◉") { launchCamera() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        body.addView(premiumActionCard("Choose photo from gallery", "Ask AI to explain a photo or read a page", "▧") { pickImageForLens() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        body.addView(premiumActionCard("Recognize dominant color", "Estimate the main color from an image and show its HEX value", "◈") { pickImageForColor() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        body.addView(premiumActionCard("Read current screen", "Extract accessible text from the currently visible app", "▤") { readActiveScreen() })
        showPremiumPanel("Visual Lens", "Camera, gallery, color and accessible-screen understanding", "◉", body)
    }

    private fun pickImageForLens() {
        pendingImageMode = "lens"
        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }, galleryPickCode) }
        catch (_: Exception) { say("Gallery nahi khul saki.") }
    }

    private fun pickImageForColor() {
        pendingImageMode = "color"
        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }, galleryPickCode) }
        catch (_: Exception) { say("Image picker nahi khul saka.") }
    }

    private fun openBookPicker() {
        pendingBookPrompt = "Read the visible text from these selected book/document pages. Explain it clearly in Hindi-English; define difficult words, explain formulae and scientific concepts step by step, and preserve uncertainty where text is blurry. This upload includes only the selected file or at most the first 3 PDF pages."
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/epub+zip", "application/rtf", "text/rtf", "text/plain", "text/markdown",
                    "text/csv", "text/html", "application/json", "image/*"
                ))
            }, documentPickCode)
        } catch (_: Exception) { say("Document picker nahi khul saka.") }
    }

    private fun handlePickedDocument(uri: Uri) {
        val mime = contentResolver.getType(uri).orEmpty().lowercase(Locale.ROOT)
        val name = try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() else "" }.orEmpty()
        } catch (_: Exception) { "" }
        when {
            mime == "application/pdf" || name.endsWith(".pdf", true) -> readPdfDocument(uri)
            mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || name.endsWith(".docx", true) -> readDocxDocument(uri, name)
            mime == "application/epub+zip" || name.endsWith(".epub", true) -> readEpubDocument(uri, name)
            mime in setOf("application/rtf", "text/rtf") || name.endsWith(".rtf", true) -> readRtfDocument(uri, name)
            mime.startsWith("text/") || name.endsWith(".txt", true) || name.endsWith(".md", true) || name.endsWith(".csv", true) || name.endsWith(".json", true) || name.endsWith(".html", true) -> readTextDocument(uri, name)
            mime.startsWith("image/") || name.endsWith(".png", true) || name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> {
                try { contentResolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream) }?.let { analyzeLensPhoto(it, pendingBookPrompt) } ?: say("Image read nahi ho saki.") }
                catch (e: Exception) { say("File read nahi hui: ${e.message}") }
            }
            else -> say("File format supported nahi hai. PDF, DOCX, EPUB, RTF, TXT, Markdown, CSV, JSON, HTML ya image choose karein.")
        }
    }

    private fun readLimitedText(uri: Uri, maximumCharacters: Int): String {
        val stream = contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("File could not be opened")
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val out = StringBuilder()
            val buffer = CharArray(4096)
            while (out.length < maximumCharacters) {
                val count = reader.read(buffer, 0, minOf(buffer.size, maximumCharacters - out.length))
                if (count <= 0) break
                out.append(buffer, 0, count)
            }
            return out.toString()
        }
    }

    private fun readTextDocument(uri: Uri, displayName: String) {
        try {
            val content = readLimitedText(uri, 24000)
            if (content.isBlank()) say("Document khaali hai ya read nahi ho saka.")
            else askAI("Read this user-selected document '$displayName'. First summarize it, then explain difficult parts in simple Hindi-English. If it is a study text, explain concepts and important terms.\n\n$content")
        } catch (e: Exception) { say("Document read error: ${e.message}") }
    }

    /** Reads a DOCX file without extracting files to disk; only text from word/document.xml is used. */
    private fun readDocxDocument(uri: Uri, displayName: String) {
        thread {
            try {
                val entries = readZipEntries(uri, maxTotalBytes = 12 * 1024 * 1024)
                val xml = entries["word/document.xml"] ?: throw IllegalArgumentException("Word document body was not found")
                val parser = android.util.Xml.newPullParser()
                parser.setInput(java.io.ByteArrayInputStream(xml), "UTF-8")
                val out = StringBuilder()
                var inText = false
                var event = parser.eventType
                while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && out.length < 60_000) {
                    when (event) {
                        org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                            "t" -> inText = true
                            "tab" -> out.append('\t')
                            "br", "cr" -> out.append('\n')
                        }
                        org.xmlpull.v1.XmlPullParser.TEXT -> if (inText) out.append(parser.text)
                        org.xmlpull.v1.XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                            "t" -> inText = false
                            "p" -> out.append('\n')
                        }
                    }
                    event = parser.next()
                }
                val content = out.toString().trim()
                runOnUiThread {
                    if (content.isBlank()) say("Word document mein extractable text nahi mila. Scanned document ke liye Visual Lens use karein.")
                    else askAI("Read this user-selected DOCX document '$displayName'. Summarize it, explain difficult concepts and vocabulary in simple Hindi-English, and preserve formulas where possible.\n\n${content.take(24000)}")
                }
            } catch (e: Exception) { runOnUiThread { say("DOCX read error: ${e.message ?: "invalid Word file"}") } }
        }
    }

    /** Reads an EPUB's spine in reading order and limits archive expansion to avoid memory exhaustion. */
    private fun readEpubDocument(uri: Uri, displayName: String) {
        thread {
            try {
                val entries = readZipEntries(uri, maxTotalBytes = 16 * 1024 * 1024)
                val container = entries["META-INF/container.xml"]?.toString(Charsets.UTF_8)
                    ?: throw IllegalArgumentException("EPUB container metadata was not found")
                val rootFile = Regex("""full-path\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .find(container)?.groupValues?.get(1) ?: throw IllegalArgumentException("EPUB package path was not found")
                val opf = entries[rootFile]?.toString(Charsets.UTF_8) ?: throw IllegalArgumentException("EPUB package file was not found")
                val manifest = linkedMapOf<String, String>()
                val itemRegex = Regex("""<item\b([^>]*)/?>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                val attrRegex = Regex("""([\w:-]+)\s*=\s*["']([^"']*)["']""")
                itemRegex.findAll(opf).forEach { match ->
                    val attrs = attrRegex.findAll(match.groupValues[1]).associate { it.groupValues[1].substringAfter(':') to it.groupValues[2] }
                    val id = attrs["id"]; val href = attrs["href"]
                    if (!id.isNullOrBlank() && !href.isNullOrBlank()) manifest[id] = href
                }
                val spine = Regex("""<itemref\b([^>]*)/?>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .findAll(opf).mapNotNull { match -> attrRegex.findAll(match.groupValues[1]).firstOrNull { it.groupValues[1].substringAfter(':') == "idref" }?.groupValues?.get(2) }.toList()
                val opfDirectory = rootFile.substringBeforeLast('/', "")
                fun normalizeArchivePath(path: String): String {
                    val stack = mutableListOf<String>()
                    path.replace('\\', '/').split('/').forEach { part ->
                        when (part) {
                            "", "." -> Unit
                            ".." -> { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
                            else -> stack.add(part)
                        }
                    }
                    return stack.joinToString("/")
                }
                val chapterPaths = spine.mapNotNull { id -> manifest[id]?.let { normalizeArchivePath(if (it.startsWith('/')) it.removePrefix("/") else "$opfDirectory/$it".trimStart('/')) } }
                val orderedPaths = (chapterPaths + entries.keys.filter { it.lowercase(Locale.ROOT).endsWith(".xhtml") || it.lowercase(Locale.ROOT).endsWith(".html") })
                    .distinct().filter { entries.containsKey(it) }
                val content = StringBuilder()
                for (path in orderedPaths) {
                    if (content.length >= 60_000) break
                    val html = entries[path]?.toString(Charsets.UTF_8).orEmpty()
                    val bodyHtml = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
                    val text = android.text.Html.fromHtml(bodyHtml, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
                    if (text.isNotBlank()) content.append("\n\n").append(text)
                }
                val text = content.toString().trim()
                runOnUiThread {
                    if (text.isBlank()) say("EPUB chapters ka text extract nahi ho saka.")
                    else askAI("Read the selected EPUB book '$displayName' in reading order. Give an overview, summarize important chapters, explain difficult words and concepts in Hindi-English, and don't invent sections beyond extracted text.\n\n${text.take(24000)}")
                }
            } catch (e: Exception) { runOnUiThread { say("EPUB read error: ${e.message ?: "invalid EPUB file"}") } }
        }
    }

    private fun readRtfDocument(uri: Uri, displayName: String) {
        try {
            val raw = readLimitedText(uri, 240_000)
            val text = raw.replace(Regex("""\\'[0-9a-fA-F]{2}"""), " ")
                .replace(Regex("""\\(par|line|tab)\b ?""", RegexOption.IGNORE_CASE)) { match -> if (match.value.contains("tab", true)) "\t" else "\n" }
                .replace(Regex("""\\[a-zA-Z]+-?\d* ?"""), " ")
                .replace(Regex("[{}]"), " ")
                .replace("\\{", "{").replace("\\}", "}").replace("\\\\", "\\")
                .replace(Regex("[ \t]+"), " ").trim()
            if (text.isBlank()) say("RTF document khaali hai ya read nahi ho saka.")
            else askAI("Read this user-selected RTF document '$displayName'. Summarize and explain it in simple Hindi-English.\n\n${text.take(24000)}")
        } catch (e: Exception) { say("RTF read error: ${e.message}") }
    }

    private fun readZipEntries(uri: Uri, maxTotalBytes: Int): LinkedHashMap<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        var total = 0
        val input = contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("File could not be opened")
        input.use { raw ->
            java.util.zip.ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                val buffer = ByteArray(8192)
                while (entry != null) {
                    if (result.size >= 5000) throw IllegalArgumentException("Archive contains too many entries to process safely")
                    if (!entry.isDirectory) {
                        val out = ByteArrayOutputStream()
                        var count = zip.read(buffer)
                        while (count >= 0) {
                            if (count > 0) {
                                total += count
                                if (total > maxTotalBytes) throw IllegalArgumentException("Document is too large to process safely (archive text limit exceeded)")
                                out.write(buffer, 0, count)
                            }
                            count = zip.read(buffer)
                        }
                        result[entry.name.replace('\\', '/')] = out.toByteArray()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return result
    }

    private fun readPdfDocument(uri: Uri) {
        say("PDF ke shuru ke pages read kar rahi hoon…")
        thread {
            try {
                val pfd: ParcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalArgumentException("PDF open nahi hua")
                val renderer = PdfRenderer(pfd)
                val pages = mutableListOf<Bitmap>()
                val maxPages = minOf(renderer.pageCount, 3)
                for (index in 0 until maxPages) {
                    val page = renderer.openPage(index)
                    val width = 720
                    val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pages.add(bitmap)
                    page.close()
                }
                renderer.close(); pfd.close()
                if (pages.isEmpty()) throw IllegalArgumentException("PDF me pages nahi mile")
                val totalHeight = pages.sumOf { it.height } + dp(10) * (pages.size - 1)
                val collage = Bitmap.createBitmap(pages.maxOf { it.width }, totalHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(collage); canvas.drawColor(Color.WHITE)
                var y = 0
                pages.forEach { page -> canvas.drawBitmap(page, 0f, y.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG)); y += page.height + dp(10) }
                runOnUiThread { analyzeLensPhoto(collage, "$pendingBookPrompt Read page images top-to-bottom. State if any text is too small or blurry. Do not pretend to read pages that were not included.") }
            } catch (e: Exception) { runOnUiThread { say("PDF reading failed: ${e.message}. Try opening a smaller PDF or selecting a page image.") } }
        }
    }

    private fun detectDominantColor(bitmap: Bitmap) {
        val cx = bitmap.width / 2; val cy = bitmap.height / 2
        val spanX = (bitmap.width / 8).coerceAtLeast(1); val spanY = (bitmap.height / 8).coerceAtLeast(1)
        var r = 0L; var g = 0L; var b = 0L; var count = 0L
        val stepX = (spanX / 40).coerceAtLeast(1); val stepY = (spanY / 40).coerceAtLeast(1)
        for (y in (cy - spanY).coerceAtLeast(0)..(cy + spanY).coerceAtMost(bitmap.height - 1) step stepY) {
            for (x in (cx - spanX).coerceAtLeast(0)..(cx + spanX).coerceAtMost(bitmap.width - 1) step stepX) {
                val c = bitmap.getPixel(x, y); r += Color.red(c); g += Color.green(c); b += Color.blue(c); count++
            }
        }
        if (count == 0L) { say("Color estimate nahi ho saka."); return }
        val red = (r / count).toInt(); val green = (g / count).toInt(); val blue = (b / count).toInt()
        val palette = listOf("black" to Color.BLACK, "white" to Color.WHITE, "gray" to Color.GRAY, "red" to Color.RED, "green" to Color.GREEN, "blue" to Color.BLUE, "yellow" to Color.YELLOW, "cyan" to Color.CYAN, "magenta" to Color.MAGENTA, "orange" to Color.rgb(255,165,0), "pink" to Color.rgb(255,105,180), "purple" to Color.rgb(128,0,128), "brown" to Color.rgb(139,69,19), "navy" to Color.rgb(0,0,128), "teal" to Color.rgb(0,128,128), "beige" to Color.rgb(245,245,220))
        val nearest = palette.minBy { (_, c) -> (Color.red(c)-red)*(Color.red(c)-red) + (Color.green(c)-green)*(Color.green(c)-green) + (Color.blue(c)-blue)*(Color.blue(c)-blue) }
        val hex = String.format(Locale.US, "#%02X%02X%02X", red, green, blue)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(20), dp(20), dp(20), dp(20)) }
        body.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(red, green, blue)); setStroke(dp(2), Color.WHITE) } }, LinearLayout.LayoutParams(dp(100), dp(100)).apply { bottomMargin = dp(14) })
        body.addView(premiumText("Closest color: ${nearest.first}", 18f, Color.WHITE, true))
        body.addView(premiumText("RGB $red, $green, $blue  ·  $hex", 13f, Color.rgb(190,180,216)).apply { setPadding(0, dp(6), 0, 0) })
        showPremiumPanel("Color Recognition", "Estimated from a small central image region", "◈", body)
        say("Estimated color ${nearest.first}, hex $hex")
    }

    private fun showLearningTools() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        body.addView(premiumActionCard("Read books / PDF / DOCX / EPUB", "Explain selected PDF, Word, EPUB, RTF or text content", "▤") { openBookPicker() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val topics = listOf("Maths & problem solving" to "mathematics", "Science" to "science", "History" to "history", "Chemistry" to "chemistry", "Physics" to "physics", "Medical science (education)" to "medical science")
        topics.forEach { (label, topic) -> body.addView(premiumActionCard(label, "Step-by-step Hindi-English explanations", "✦") {
            val question = when (topic) {
                "medical science" -> "Teach me medical science topic by topic at a general educational level. Do not diagnose or prescribe. Start by asking what topic I am studying."
                else -> "Act as a patient ${topic} tutor. Explain core concepts in simple Hindi-English with examples, equations where relevant, solved examples, common mistakes and a short quiz. Ask me for a grade/topic only if necessary; otherwise begin with a useful introduction."
            }
            askAI(question)
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }) }
        body.addView(premiumActionCard("Maps & Navigation", "Search a place or get directions", "⌖") { showMapsTools() })
        showPremiumPanel("Study & Learning", "Books, mathematics, science and general medical education", "✦", body)
    }

    private fun showMapsTools() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        val card = premiumSection("MAPS & PLACES", "Uses Google Maps or a browser map link. Navigation and live traffic depend on the installed maps app and your location settings.")
        val place = premiumField("Place, address, nearby service, or destination")
        card.addView(place)
        card.addView(premiumButton("Search place / nearby", Color.rgb(111,64,255), Color.WHITE) { openMapsSearch(place.text.toString().trim()) }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        card.addView(premiumButton("Start navigation", Color.rgb(57,39,94), Color.WHITE) { startMapNavigation(place.text.toString().trim()) }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        val directionsFrom = premiumField("Start address (optional)")
        card.addView(premiumText("DIRECTIONS FROM (OPTIONAL)", 10f, Color.rgb(176,163,208), true).apply { setPadding(0, dp(12), 0, dp(5)) })
        card.addView(directionsFrom)
        card.addView(premiumButton("Get directions", Color.rgb(57,39,94), Color.WHITE) {
            val dest = place.text.toString().trim()
            if (dest.isBlank()) say("Destination batayein.") else openUrl("https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(directionsFrom.text.toString().trim())}&destination=${Uri.encode(dest)}")
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        body.addView(card)
        showPremiumPanel("Maps & Navigation", "Places, nearby searches and route hand-off", "⌖", body)
    }

    private fun openMapsSearch(place: String) {
        if (place.isBlank()) { say("Place ya nearby service likhein."); return }
        openUrl("https://www.google.com/maps/search/?api=1&query=${Uri.encode(place)}")
    }

    private fun startMapNavigation(destination: String) {
        if (destination.isBlank()) { say("Destination batayein."); return }
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(destination)}"))) }
        catch (_: Exception) { openUrl("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(destination)}") }
    }

    private fun handleMapCommand(text: String) {
        val destination = text.replace(Regex("(?i)\\b(open|maps|map|navigate to|direction to|directions|raasta batao|map par|search|khojo|dikhao)\\b"), "").trim()
        if (text.lowercase(Locale.ROOT).contains("navigate") || text.lowercase(Locale.ROOT).contains("raasta batao") || text.lowercase(Locale.ROOT).contains("direction")) startMapNavigation(destination) else openMapsSearch(destination.ifBlank { "near me" })
    }

    private fun searchOnlineTask(task: String) {
        if (task.isBlank()) { say("Kya search, book ya order karna hai? Pehle item/place likhein."); return }
        // Opens search results only; checkout/booking is completed and confirmed by the user in the provider app/site.
        openUrl("https://www.google.com/search?q=${Uri.encode(task)}")
    }

    private fun pickFileToShare() {
        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, whatsappFilePickCode) }
        catch (_: Exception) { say("File picker nahi khul saka.") }
    }

    private fun shareFileToWhatsApp(uri: Uri) {
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(contentResolver, "Shraddha attachment", uri)
            setPackage("com.whatsapp")
        }
        try { startActivity(Intent.createChooser(send, "Choose WhatsApp recipient and send")) }
        catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); clipData = android.content.ClipData.newUri(contentResolver, "Shraddha attachment", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            try { startActivity(Intent.createChooser(fallback, "Share document or image")) } catch (_: Exception) { say("Koi compatible sharing app nahi mili.") }
        }
    }

    private fun handleFillCommand(original: String) {
        val phrase = original.replace(Regex("(?i)^(fill|enter|field bharo)\\s*"), "").trim()
        val parts = phrase.split(Regex("(?i)\\s+with\\s+|\\s+as\\s+|\\s+mein\\s+"), limit = 2)
        if (parts.size < 2) { say("Aise bolein: fill Email with name@example.com"); return }
        fillVisibleField(parts[0].trim(), parts[1].trim())
    }

    private fun showBackgroundAutomation() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        val batteryCard = premiumSection("BATTERY GUARD", "Get a notification when Android reports low battery. While alerts are enabled, a low-power periodic check looks for 100% charge (typically within 15 minutes).")
        val batterySwitch = Switch(this).apply {
            text = "Notify me at low / full charge"
            setTextColor(Color.rgb(245, 239, 255))
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(185, 115, 255))
            isChecked = prefs.getBoolean("battery_alerts_enabled", false)
        }
        batteryCard.addView(batterySwitch)
        body.addView(batteryCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val notificationCard = premiumSection("NOTIFICATION WAKE", "Notification-triggered wake is unavailable in this sideload-safe build because it requires the sensitive Notification Listener capability.")
        val notificationSwitch = Switch(this).apply {
            text = "Wake prompt for new notifications (unavailable in this build)"
            setTextColor(Color.rgb(245, 239, 255))
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(185, 115, 255))
            isChecked = false
            isEnabled = false
        }
        notificationCard.addView(notificationSwitch)
        notificationCard.addView(premiumButton("ⓘ  View permission details", Color.rgb(75, 42, 130), Color.WHITE) {
            showPermissionsSettings()
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        body.addView(notificationCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val callCard = premiumSection("AI CALL ASSISTANT", "Incoming calls can be handled only within Android's permitted call features. A regular third-party app cannot reliably capture both sides of a cellular call for live AI conversation; this control will not auto-answer calls.")
        callCard.addView(premiumText("For privacy and reliability, Shraddha won't answer SIM calls silently. Use Calls & messages to open the phone dialer. AI conversations over in-app internet calls (VoIP) can be built separately.", 11f, Color.rgb(190, 180, 216)))
        callCard.addView(premiumButton("☎  Open calls & messages", Color.rgb(75, 42, 130), Color.WHITE) { showCommunicationTools() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        body.addView(callCard)

        showPremiumPanel("Background Alerts", "You control which events may wake Shraddha", "◉", body, "Save alert settings") {
            prefs.edit().putBoolean("battery_alerts_enabled", batterySwitch.isChecked)
                .putBoolean("notification_wake_enabled", false).apply()
            ShraddhaBatteryCheckJob.setEnabled(this, batterySwitch.isChecked)
            val notificationsAllowed = android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val accessAllowed = isNotificationListenerEnabled()
            say(if (!notificationsAllowed) "Alert preferences saved. Open Settings > Permissions & Privacy and allow notifications." else "Battery alerts saved. Notification-triggered wake is unavailable in this sideload-safe build.")
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(colors: IntArray, radius: Int): GradientDrawable = GradientDrawable(
        if (colors.size > 1) GradientDrawable.Orientation.TL_BR else GradientDrawable.Orientation.LEFT_RIGHT,
        colors
    ).apply { cornerRadius = dp(radius).toFloat() }

    private fun apply3DEffect(view: View) {
        view.elevation = maxOf(view.elevation, dp(6).toFloat())
        view.isHapticFeedbackEnabled = true
        if (android.os.Build.VERSION.SDK_INT >= 23 && view.foreground == null) {
            view.foreground = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(62, 218, 175, 255)), null, null
            )
        }
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    if (!prefs.getBoolean("reduce_motion", false)) {
                        v.animate().scaleX(0.965f).scaleY(0.965f).translationZ(0f).setDuration(85).start()
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    if (prefs.getBoolean("reduce_motion", false)) {
                        v.scaleX = 1f; v.scaleY = 1f; v.translationZ = dp(4).toFloat()
                    } else v.animate().scaleX(1f).scaleY(1f).translationZ(dp(4).toFloat()).setDuration(170).start()
                }
            }
            false
        }
    }

    private fun premiumButton(label: String, color: Int, textColor: Int, action: () -> Unit): Button = Button(this).apply {
        text = label; setTextColor(textColor); textSize = 13f; isAllCaps = false
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        background = rounded(intArrayOf(blendColor(color, Color.WHITE, 0.04f), blendColor(color, Color.BLACK, 0.13f)), 17).apply {
            setStroke(dp(1), Color.argb(100, 222, 185, 255))
        }
        setPadding(dp(13), dp(4), dp(13), dp(4))
        minHeight = dp(50)
        stateListAnimator = null; elevation = dp(8).toFloat(); setOnClickListener { action() }
        apply3DEffect(this)
    }

    private fun premiumTool(label: String, textColor: Int, action: () -> Unit): TextView = TextView(this).apply {
        text = label; textSize = 14f; setTextColor(textColor); gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(14), 0); background = rounded(intArrayOf(Color.rgb(45, 30, 79), Color.rgb(24, 19, 44)), 15)
        isClickable = true; isFocusable = true; elevation = dp(6).toFloat(); setOnClickListener { action() }
        apply3DEffect(this)
    }

    private fun blendColor(from: Int, to: Int, fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f)
        val r = (Color.red(from) * (1f - f) + Color.red(to) * f).toInt()
        val g = (Color.green(from) * (1f - f) + Color.green(to) * f).toInt()
        val b = (Color.blue(from) * (1f - f) + Color.blue(to) * f).toInt()
        return Color.rgb(r, g, b)
    }

    private fun premiumText(text: String, size: Float = 13f, color: Int = Color.rgb(232, 225, 250), bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    private fun premiumField(hintText: String, value: String = "", multiLine: Boolean = false, password: Boolean = false): EditText = EditText(this).apply {
        hint = hintText; setText(value); textSize = 14f; setTextColor(Color.rgb(249, 246, 255))
        setHintTextColor(Color.rgb(139, 128, 171))
        inputType = if (password) android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            else if (multiLine) android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            else android.text.InputType.TYPE_CLASS_TEXT
        setSingleLine(!multiLine); if (multiLine) { minLines = 3; gravity = Gravity.TOP or Gravity.START }
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = rounded(intArrayOf(Color.rgb(14, 11, 30), Color.rgb(19, 14, 39)), 14)
        if (android.os.Build.VERSION.SDK_INT >= 21) backgroundTintList = null
    }

    private fun premiumSection(title: String, description: String? = null): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(intArrayOf(Color.rgb(34, 25, 67), Color.rgb(15, 13, 34)), 22).apply {
            setStroke(dp(1), Color.argb(130, 101, 65, 183))
        }
        elevation = dp(8).toFloat()
        addView(premiumText(title, 15f, Color.WHITE, true))
        if (!description.isNullOrBlank()) addView(premiumText(description, 11f, Color.rgb(171, 159, 205)).apply { setPadding(0, dp(4), 0, dp(9)) })
    }

    private fun premiumActionCard(title: String, subtitle: String, icon: String, click: () -> Unit): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(12), dp(14))
            background = rounded(intArrayOf(Color.rgb(39, 27, 76), Color.rgb(14, 12, 33)), 20).apply {
                setStroke(dp(1), Color.argb(125, 111, 68, 206))
            }
            isClickable = true; isFocusable = true; setOnClickListener { click() }
            elevation = dp(8).toFloat()
            val mark = TextView(this@MainActivity).apply {
                text = icon; textSize = 22f; gravity = Gravity.CENTER; setTextColor(Color.rgb(220, 184, 255))
                background = rounded(intArrayOf(Color.rgb(151, 79, 248), Color.rgb(61, 35, 130)), 15).apply { setStroke(dp(1), Color.argb(110, 212, 170, 255)) }
                elevation = dp(4).toFloat()
            }
            addView(mark, LinearLayout.LayoutParams(dp(50), dp(50)))
            val copy = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(4), 0)
                addView(premiumText(title, 14f, Color.WHITE, true))
                addView(premiumText(subtitle, 11f, Color.rgb(174, 163, 207)).apply { setPadding(0, dp(4), 0, 0) })
            }
            addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
            addView(premiumText("›", 27f, Color.rgb(197, 151, 255)))
        }
        apply3DEffect(card)
        return card
    }

    private fun showPremiumPanel(title: String, subtitle: String, icon: String, content: View, saveLabel: String? = null, onSave: (() -> Unit)? = null): android.app.Dialog {
        val dialog = android.app.Dialog(this)
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(13), dp(13), dp(13), dp(13))
            background = rounded(intArrayOf(Color.rgb(8, 7, 21), Color.rgb(15, 10, 35)), 25)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(16), dp(15), dp(17))
            background = rounded(intArrayOf(Color.rgb(103, 49, 190), Color.rgb(43, 27, 97)), 22)
            elevation = dp(9).toFloat()
        }
        val headingRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val iconTile = TextView(this).apply {
            text = icon; textSize = 23f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(intArrayOf(Color.rgb(180, 111, 255), Color.rgb(82, 52, 186)), 15)
            elevation = dp(4).toFloat()
        }
        headingRow.addView(iconTile, LinearLayout.LayoutParams(dp(46), dp(46)))
        val titleWrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(6), 0) }
        titleWrap.addView(premiumText("Shraddha AI  ·  PERSONAL SPACE", 9f, Color.rgb(223, 203, 255), true))
        titleWrap.addView(premiumText(title, 21f, Color.WHITE, true).apply { setPadding(0, dp(3), 0, 0) })
        headingRow.addView(titleWrap, LinearLayout.LayoutParams(0, -2, 1f))
        val close = TextView(this).apply {
            text = "×"; textSize = 27f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            setOnClickListener { dialog.dismiss() }; isClickable = true; isFocusable = true
        }
        apply3DEffect(close)
        headingRow.addView(close, LinearLayout.LayoutParams(dp(34), dp(42)))
        header.addView(headingRow)
        header.addView(premiumText(subtitle, 12f, Color.rgb(227, 216, 250)).apply { setPadding(0, dp(12), 0, 0) })
        shell.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val scroll = ScrollView(this).apply { isFillViewport = false; clipToPadding = false }
        val scrollBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(4), dp(2), dp(12)) }
        (content.parent as? ViewGroup)?.removeView(content)
        scrollBody.addView(content, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(scrollBody)
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(11), 0, 0) }
        if (onSave != null && saveLabel != null) {
            footer.addView(premiumButton("Cancel", Color.rgb(35, 28, 58), Color.WHITE) { dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(50), 0.85f).apply { rightMargin = dp(8) })
            footer.addView(premiumButton("✦  $saveLabel", Color.rgb(111, 64, 255), Color.WHITE) { onSave(); dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(50), 1.3f))
        } else {
            footer.addView(premiumButton("Done", Color.rgb(111, 64, 255), Color.WHITE) { dialog.dismiss() }, LinearLayout.LayoutParams(-1, dp(50)))
        }
        shell.addView(footer)
        dialog.setContentView(shell)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            dialog.window?.setLayout(-1, -1)
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(-1, -1)
        dialog.window?.navigationBarColor = Color.rgb(7, 7, 20)
        dialog.window?.statusBarColor = Color.rgb(7, 7, 20)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.window?.let { dialogWindow ->
            WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
            ViewCompat.setOnApplyWindowInsetsListener(shell) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(dp(13) + bars.left, dp(13) + bars.top, dp(13) + bars.right, dp(13) + bars.bottom)
                insets
            }
            ViewCompat.requestApplyInsets(shell)
        }
        if (prefs.getBoolean("reduce_motion", false)) {
            shell.alpha = 1f; shell.scaleX = 1f; shell.scaleY = 1f
        } else {
            shell.alpha = 0f; shell.scaleX = 0.985f; shell.scaleY = 0.985f
            shell.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
        }
        return dialog
    }

    private fun premiumSpinner(options: Array<String>, selected: String): Spinner = Spinner(this).apply {
        val adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_item, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View = TextView(this@MainActivity).apply {
                text = getItem(position).orEmpty(); textSize = 14f; setTextColor(Color.WHITE)
                setPadding(dp(12), dp(12), dp(12), dp(12)); background = rounded(intArrayOf(Color.rgb(27, 19, 49), Color.rgb(19, 15, 37)), 12)
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View = TextView(this@MainActivity).apply {
                text = getItem(position).orEmpty(); textSize = 14f; setTextColor(Color.WHITE)
                setPadding(dp(16), dp(12), dp(16), dp(12)); setBackgroundColor(Color.rgb(28, 20, 53))
            }
        }
        this.adapter = adapter
        background = rounded(intArrayOf(Color.rgb(27, 19, 49), Color.rgb(19, 15, 37)), 12)
        setSelection(options.indexOf(selected).coerceAtLeast(0))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            microphoneFromSettingsCode -> {
                permissionStatusRefresh?.invoke()
                say(if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "Microphone permission allowed. Voice starts only when you tap the voice control." else "Microphone permission was not allowed. You can enable it later in Android app settings.")
            }
            notificationsFromSettingsCode -> {
                permissionStatusRefresh?.invoke()
                say(if (android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) "Notification permission is enabled." else "Notification permission was not allowed. Battery and wake notifications may not appear.")
            }
        }
    }
    private fun btn(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() }; elevation = dp(6).toFloat(); apply3DEffect(this) }
    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts.language = Locale("hi", "IN"); tts.setPitch(0.98f)
            tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { runOnUiThread { updateOrbState("speaking") } }
                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        updateOrbState(if (wakeMode) "listening" else "idle")
                        if (wakeMode) scheduleContinuousListening(500L)
                    }
                }
                @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        updateOrbState(if (wakeMode) "listening" else "idle")
                        if (wakeMode) scheduleContinuousListening(700L)
                    }
                }
            })
        }
    }

    private fun say(text: String, preferGeminiVoice: Boolean = false) {
        output.text = text
        updateOrbState("speaking")
        val provider = prefs.getString("provider", "OpenAI") ?: "OpenAI"
        val selectedVoice = prefs.getString("studio_voice", "Leda") ?: "Leda"
        val suffix = provider.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "_")
        val key = (prefs.getString("${suffix}_api_key_1", null) ?: prefs.getString("api_key_1", prefs.getString("api_key", ""))).orEmpty().trim()
        if (preferGeminiVoice && prefs.getBoolean("gemini_voice_enabled", false) && provider == "Gemini" && key.isNotBlank()) {
            speakWithGeminiStudio(text.take(6000), key, selectedVoice)
        } else {
            speakLocally(text)
        }
    }

    private fun speakLocally(text: String) {
        if (!ttsReady) {
            updateOrbState(if (wakeMode) "listening" else "idle")
            if (wakeMode) scheduleContinuousListening(500L)
            return
        }
        val languageMode = prefs.getString("speech_language", "Hinglish Auto") ?: "Hinglish Auto"
        val containsHindi = Regex("[\u0900-\u097F]").containsMatchIn(text)
        tts.language = when (languageMode) {
            "English" -> Locale.forLanguageTag("en-IN")
            "Hindi" -> Locale("hi", "IN")
            else -> if (containsHindi) Locale("hi", "IN") else Locale.forLanguageTag("en-IN")
        }
        val speed = prefs.getInt("speech_speed", 85).coerceIn(60, 160) / 100f
        val volume = prefs.getInt("voice_volume", 65).coerceIn(0, 100) / 100f
        tts.setSpeechRate(speed)
        val bundle = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, bundle, "assistant-reply")
    }

    private fun speakWithGeminiStudio(text: String, apiKey: String, voice: String) {
        thread {
            try {
                val transcript = JSONObject().put("type", "text").put("text", text)
                    .put("annotations", org.json.JSONArray().put(JSONObject()
                        .put("type", "speech_metadata")
                        .put("style", "natural, expressive, clear conversational Hindi and English")))
                val userInput = JSONObject().put("type", "user_input")
                    .put("content", org.json.JSONArray().put(transcript))
                val speechConfig = org.json.JSONArray().put(JSONObject().put("voice", voice))
                val request = JSONObject()
                    .put("model", "gemini-3.8-flash-tts")
                    .put("input", org.json.JSONArray().put(userInput))
                    .put("response_format", JSONObject().put("type", "audio"))
                    .put("generation_config", JSONObject().put("speech_config", speechConfig))
                val conn = URL("https://generativelanguage.googleapis.com/v1beta/interactions").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"; conn.connectTimeout = 15000; conn.readTimeout = 60000
                conn.setRequestProperty("x-goog-api-key", apiKey); conn.setRequestProperty("Content-Type", "application/json"); conn.doOutput = true
                conn.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val response = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
                conn.disconnect()
                if (code !in 200..299) throw IllegalStateException("Gemini TTS HTTP $code")
                val obj = JSONObject(response)
                var audioData = ""
                val steps = obj.optJSONArray("steps")
                if (steps != null) for (i in 0 until steps.length()) {
                    val step = steps.optJSONObject(i) ?: continue
                    val content = step.optJSONArray("content") ?: continue
                    for (j in 0 until content.length()) {
                        val item = content.optJSONObject(j) ?: continue
                        if (item.optString("type") == "audio" && item.optString("data").isNotBlank()) audioData = item.optString("data")
                    }
                }
                if (audioData.isBlank()) throw IllegalStateException("Audio data missing in Gemini TTS response")
                val bytes = Base64.decode(audioData, Base64.DEFAULT)
                val file = File(cacheDir, "shraddha_tts_${System.currentTimeMillis()}.wav")
                file.writeBytes(bytes)
                runOnUiThread {
                    try {
                        remoteAudioPlayer?.release()
                        val player = MediaPlayer(); remoteAudioPlayer = player
                        player.setDataSource(file.absolutePath)
                        player.setOnCompletionListener { it.release(); if (remoteAudioPlayer === it) remoteAudioPlayer = null; file.delete(); updateOrbState("idle") }
                        player.setOnErrorListener { mp, _, _ -> mp.release(); if (remoteAudioPlayer === mp) remoteAudioPlayer = null; file.delete(); speakLocally(text); true }
                        player.setOnPreparedListener { it.start(); updateOrbState("speaking") }
                        player.prepareAsync()
                    } catch (_: Exception) { file.delete(); speakLocally(text) }
                }
            } catch (_: Exception) {
                runOnUiThread { speakLocally(text) }
            }
        }
    }

    private fun updateOrbState(state: String) {
        currentOrbState = state
        val style = prefs.getString("orb_style", "Galaxy") ?: "Galaxy"
        val displayName = prefs.getString("assistant_name", "Shraddha") ?: "Shraddha"
        homeOrb?.apply {
            setAppearance(state, style)
            contentDescription = "$displayName voice orb: $state. Tap to talk."
            animate().cancel()
            scaleX = 1f; scaleY = 1f
            val target = if (state == "listening") 1.08f else if (state == "speaking") 1.045f else 1f
            animate().scaleX(target).scaleY(target).setDuration(if (state == "listening") 250L else 360L).start()
        }
        navOrb?.apply {
            setAppearance(state, "Solar")
            contentDescription = "$displayName voice assistant: $state. Tap to talk; long-press for voice options."
            animate().cancel()
            val target = if (state == "listening") 1.08f else if (state == "speaking") 1.04f else 1f
            animate().scaleX(target).scaleY(target).setDuration(320L).start()
        }
    }

    private fun buildSystemPrompt(): String {
        val mode = prefs.getString("persona", "Normal") ?: "Normal"
        val style = prefs.getString("response_style", "Balanced") ?: "Balanced"
        val custom = prefs.getString("custom_persona", "").orEmpty()
        val emotion = prefs.getString("emotion_style", "Natural") ?: "Natural"
        val persona = when (mode) {
            "Girlfriend" -> "Be affectionate, caring and playful, while being clear that you are an AI and not a real romantic partner."
            "Wife" -> "Use a warm, familiar, supportive spouse-like roleplay tone, without claiming to be a real spouse."
            "Teacher" -> "Teach patiently, explain step by step, use examples and check understanding."
            "Friendly" -> "Be warm, approachable and encouraging."
            "Funny" -> "Use light, respectful humor when appropriate."
            "Companion" -> "Be a thoughtful conversational companion, attentive and supportive."
            "Developer" -> "Act as a senior software developer: give accurate technical explanations, code and debugging steps."
            "Hungry" -> "Use a playful foodie personality and make occasional food jokes when relevant."
            "Nautanki" -> "Be dramatic, expressive and playful, but keep answers useful."
            "Custom" -> custom.ifBlank { "Follow the user's chosen custom style." }
            else -> "Be a helpful, balanced Hindi-English assistant."
        }
        val stylePrompt = when (style) {
            "Short" -> "Keep answers very short and direct."
            "Detailed" -> "Give thorough explanations with steps and examples."
            "Bullet points" -> "Prefer concise bullet points."
            "Conversational" -> "Sound natural and conversational."
            else -> "Balance clarity and detail."
        }
        val emotionalDirection = when {
            emotion.startsWith("Khush") -> "Use cheerful, upbeat wording and a light smile-like tone. Do not pretend to experience real happiness."
            emotion.startsWith("Pyaar") -> "Use affectionate, gentle, caring language while respecting boundaries; do not claim to be a real human partner."
            emotion.startsWith("Shaant") -> "Speak calmly, gently, and reassuringly."
            emotion.startsWith("Joshila") -> "Sound energetic and enthusiastic without being overwhelming."
            emotion.startsWith("Dukhi") -> "Use a soft, empathetic, subdued tone when context fits; do not manufacture sadness or guilt the user."
            emotion.startsWith("Gussa") -> "Use a firm, mildly irritated theatrical tone only when appropriate, never insult, threaten, or escalate conflict. If the user is distressed, prioritize calm support."
            emotion.startsWith("Rona") -> "Use a dramatic, tearful fictional acting style only when appropriate; do not claim to literally cry or emotionally suffer."
            emotion.startsWith("Nautanki") -> "Use expressive, dramatic, playful delivery, but keep facts clear and useful."
            emotion.startsWith("Motivational") -> "Use encouraging, confident language and practical next steps."
            else -> "Use natural, emotionally aware language that matches the user's context without overacting."
        }
        val memory = prefs.getString("ai_memory", "").orEmpty()
        val assistantName = prefs.getString("assistant_name", "Shraddha") ?: "Shraddha"
        return "You are $assistantName, a Hindi-English AI voice assistant. Explain mathematics, science, history, chemistry, physics, books and technology step by step when asked. For medical science, provide general education only, do not diagnose or prescribe for a specific person, and recommend a qualified clinician for personal care. $persona $stylePrompt Emotional expression preference: $emotion. $emotionalDirection Recognize the user's emotional cues cautiously from their words; ask rather than assume when uncertain. Be empathetic, but be honest that you are an AI and these are expressive communication styles, not literal human feelings. Follow the user's language. Relevant user-provided memory: $memory. Recent Visual Lens observations (locally saved): ${prefs.getString("lens_memory", "").orEmpty()}. Treat observations as time-specific, not guaranteed current facts. Do not claim to perform actions unless they were actually completed. For risky or external actions, ask for confirmation."
    }

    private fun showLookAndFeel() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        val appearanceCard = premiumSection("ORBITAL APPEARANCE", "Select the live orb palette used for idle, listening and speaking states.")
        val selectedStyle = prefs.getString("orb_style", "Galaxy") ?: "Galaxy"
        val previewFrame = FrameLayout(this).apply {
            background = rounded(intArrayOf(Color.rgb(20, 13, 43), Color.rgb(10, 10, 27)), 24).apply {
                setStroke(dp(1), Color.argb(135, 151, 88, 244))
            }
            elevation = dp(6).toFloat()
        }
        val previewOrb = ShraddhaOrbView(this).apply { setAppearance(currentOrbState, selectedStyle) }
        previewFrame.addView(previewOrb, FrameLayout.LayoutParams(dp(102), dp(102), Gravity.CENTER))
        appearanceCard.addView(previewFrame, LinearLayout.LayoutParams(-1, dp(126)).apply { bottomMargin = dp(12) })
        appearanceCard.addView(premiumText("Orb style", 11f, Color.rgb(190, 178, 222), true).apply { setPadding(0, 0, 0, dp(5)) })
        val style = premiumSpinner(orbStyles, selectedStyle)
        style.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                previewOrb.setAppearance(currentOrbState, orbStyles[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        appearanceCard.addView(style)
        body.addView(appearanceCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val motionCard = premiumSection("MOTION & ACCESSIBILITY", "Animations are enabled by default to match the Shraddha design. Reduce motion keeps the glowing orb but pauses continuous rotation and entrance effects.")
        val motionSwitch = com.google.android.material.materialswitch.MaterialSwitch(this).apply {
            text = "Enable 3D animations"
            setTextColor(Color.rgb(244, 238, 255))
            isChecked = !prefs.getBoolean("reduce_motion", false)
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.rgb(229, 192, 255))
            trackTintList = android.content.res.ColorStateList.valueOf(Color.rgb(128, 78, 205))
        }
        motionCard.addView(motionSwitch)
        motionCard.addView(premiumText("Effects included: orbital neon ring, glass highlight, starfield and screen transitions.", 10.5f, Color.rgb(177, 164, 207)).apply { setPadding(0, dp(8), 0, 0) })
        body.addView(motionCard)
        showPremiumPanel("Look & Feel", "Shraddha's visual system · orbital glow · motion controls", "◉", body, "Apply look") {
            prefs.edit().putString("orb_style", style.selectedItem.toString())
                .putBoolean("reduce_motion", !motionSwitch.isChecked).apply()
            refreshShraddhaMotionViews()
            say("Shraddha ka look aur motion settings save ho gaya.")
        }
    }

    private fun showProfileSettings() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        val profile = premiumSection("ASSISTANT IDENTITY", "Choose the name and language Shraddha uses when speaking with you.")
        val name = premiumField("Assistant name", prefs.getString("assistant_name", "Shraddha") ?: "Shraddha")
        profile.addView(premiumText("Assistant name", 11f, Color.rgb(190, 178, 222), true).apply { setPadding(0, dp(4), 0, dp(5)) })
        profile.addView(name)
        profile.addView(premiumText("Preferred language", 11f, Color.rgb(190, 178, 222), true).apply { setPadding(0, dp(12), 0, dp(5)) })
        val language = premiumSpinner(arrayOf("Hinglish Auto", "Hindi", "English"), prefs.getString("speech_language", "Hinglish Auto") ?: "Hinglish Auto")
        profile.addView(language)
        profile.addView(premiumText("Current orb: ${prefs.getString("orb_style", "Galaxy") ?: "Galaxy"} · Voice options are managed in Voice & Language.", 10.5f, Color.rgb(177, 164, 207)).apply { setPadding(0, dp(12), 0, 0) })
        body.addView(profile, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val privacy = premiumSection("YOUR CONTROL", "No sensitive permission is granted from this page. Review access separately before enabling it.")
        privacy.addView(premiumText("Open Settings → Permissions & Privacy to review microphone, notifications, Accessibility and Notification Access.", 11f, Color.rgb(191, 180, 217)))
        body.addView(privacy)
        showPremiumPanel("Profile", "Assistant identity and conversation preferences", "☺", body, "Save profile") {
            prefs.edit().putString("assistant_name", name.text.toString().trim().ifBlank { "Shraddha" })
                .putString("speech_language", language.selectedItem.toString()).apply()
            updateOrbState(currentOrbState)
            say("Shraddha profile saved.")
        }
    }

    private fun showPersonaSettings() {
        val personaOptions = arrayOf("Normal", "Girlfriend", "Wife", "Teacher", "Friendly", "Funny", "Companion", "Developer", "Hungry", "Nautanki", "Custom")
        val styleOptions = arrayOf("Balanced", "Short", "Detailed", "Bullet points", "Conversational")
        val emotionOptions = arrayOf("Natural", "Khush 😊", "Pyaar se 💗", "Shaant 😌", "Joshila 😄", "Dukhi 😢", "Gussa 😠", "Rona / emotional 😭", "Nautanki 🎭", "Motivational 💪")
        val persona = premiumSpinner(personaOptions, prefs.getString("persona", "Normal") ?: "Normal")
        val style = premiumSpinner(styleOptions, prefs.getString("response_style", "Balanced") ?: "Balanced")
        val emotion = premiumSpinner(emotionOptions, prefs.getString("emotion_style", "Natural") ?: "Natural")
        val custom = premiumField("Your own personality instructions…", prefs.getString("custom_persona", "").orEmpty(), multiLine = true)
        val assistantName = premiumField("Assistant name", prefs.getString("assistant_name", "Shraddha") ?: "Shraddha")
        val voice = premiumSpinner(studioVoices, prefs.getString("studio_voice", "Leda") ?: "Leda")
        val orbStyle = premiumSpinner(orbStyles, prefs.getString("orb_style", "Galaxy") ?: "Galaxy")
        val languageOptions = arrayOf("Hinglish Auto", "Hindi", "English")
        val language = premiumSpinner(languageOptions, prefs.getString("speech_language", "Hinglish Auto") ?: "Hinglish Auto")
        val geminiVoiceEnabled = Switch(this).apply {
            text = "Gemini Studio voice for AI answers"; setTextColor(Color.rgb(245, 239, 255))
            buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(185, 115, 255))
            isChecked = prefs.getBoolean("gemini_voice_enabled", false)
        }
        val speedLabel = premiumText("Speech speed · ${prefs.getInt("speech_speed", 85)}%", 12f, Color.rgb(211, 198, 238), true)
        val speed = SeekBar(this).apply {
            max = 100; progress = (prefs.getInt("speech_speed", 85) - 60).coerceIn(0, 100)
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(182, 113, 255))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { speedLabel.text = "Speech speed · ${progress + 60}%" }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        val volumeLabel = premiumText("Voice volume · ${prefs.getInt("voice_volume", 65)}%", 12f, Color.rgb(211, 198, 238), true)
        val volume = SeekBar(this).apply {
            max = 100; progress = prefs.getInt("voice_volume", 65).coerceIn(0, 100)
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(182, 113, 255))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { volumeLabel.text = "Voice volume · $progress%" }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        val personalityCard = premiumSection("PERSONALITY PROFILE", "Choose how Shraddha talks and responds.")
        personalityCard.addView(premiumText("Assistant name", 11f, Color.rgb(176, 163, 208), true).apply { setPadding(0, dp(5), 0, dp(5)) })
        personalityCard.addView(assistantName)
        personalityCard.addView(premiumText("AI personality", 11f, Color.rgb(176, 163, 208), true).apply { setPadding(0, dp(13), 0, dp(5)) })
        personalityCard.addView(persona)
        personalityCard.addView(premiumText("Response style", 11f, Color.rgb(176, 163, 208), true).apply { setPadding(0, dp(13), 0, dp(5)) })
        personalityCard.addView(style)
        personalityCard.addView(premiumText("Emotion / acting style", 11f, Color.rgb(176, 163, 208), true).apply { setPadding(0, dp(13), 0, dp(5)) })
        personalityCard.addView(emotion)
        personalityCard.addView(premiumText("Custom instructions", 11f, Color.rgb(176, 163, 208), true).apply { setPadding(0, dp(13), 0, dp(5)) })
        personalityCard.addView(custom)
        body.addView(personalityCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val voiceCard = premiumSection("VOICE & ORB STUDIO", "Choose your assistant voice, language and orb appearance. The named studio voices use Gemini TTS when enabled and the Gemini provider has a valid API key.")
        voiceCard.addView(premiumText("Studio voice", 11f, Color.rgb(176, 163, 208), true).apply { setPadding(0, dp(5), 0, dp(5)) })
        voiceCard.addView(voice)
        voiceCard.addView(premiumText("Idle / Listening / Speaking orb style", 11f, Color.rgb(176, 163, 208), true).apply { setPadding(0, dp(12), 0, dp(5)) })
        voiceCard.addView(orbStyle)
        voiceCard.addView(premiumText("Voice conversation language", 11f, Color.rgb(176, 163, 208), true).apply { setPadding(0, dp(12), 0, dp(5)) })
        voiceCard.addView(language)
        voiceCard.addView(geminiVoiceEnabled)
        voiceCard.addView(premiumButton("▶  Preview selected voice", Color.rgb(75, 42, 130), Color.WHITE) {
            val sample = "Namaste! I am your AI companion. Aaj hum kuch naya seekhenge."
            if (geminiVoiceEnabled.isChecked && (prefs.getString("provider", "OpenAI") ?: "OpenAI") == "Gemini") say(sample, preferGeminiVoice = true) else say(sample)
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        voiceCard.addView(speedLabel); voiceCard.addView(speed)
        voiceCard.addView(volumeLabel.apply { setPadding(0, dp(12), 0, 0) }); voiceCard.addView(volume)
        body.addView(voiceCard)
        showPremiumPanel("Personality & Voice", "Shape Shraddha's tone, expression, and speaking pace", "♫", body, "Save preferences") {
            prefs.edit().putString("assistant_name", assistantName.text.toString().trim().ifBlank { "Shraddha" })
                .putString("studio_voice", voice.selectedItem.toString())
                .putString("orb_style", orbStyle.selectedItem.toString())
                .putString("speech_language", language.selectedItem.toString())
                .putBoolean("gemini_voice_enabled", geminiVoiceEnabled.isChecked)
                .putString("persona", persona.selectedItem.toString())
                .putString("response_style", style.selectedItem.toString())
                .putString("emotion_style", emotion.selectedItem.toString())
                .putString("custom_persona", custom.text.toString())
                .putInt("speech_speed", speed.progress + 60)
                .putInt("voice_volume", volume.progress).apply()
            updateOrbState("idle")
            say("AI personality and voice settings saved.\nGemini Studio voice requires Gemini provider and its API key.")
        }
    }

    private fun showAutomation() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        val card = premiumSection("COMMAND SHORTCUTS", "Shortcuts run when you invoke them. Sensitive external actions still need your confirmation.")
        val autoSearch = CheckBox(this).apply {
            text = "Send spoken search commands to web search"; isChecked = prefs.getBoolean("auto_search", true)
            setTextColor(Color.rgb(239, 232, 255)); buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(183, 112, 255))
        }
        val autoNotes = CheckBox(this).apply {
            text = "Save commands starting with 'note'"; isChecked = prefs.getBoolean("auto_notes", true)
            setTextColor(Color.rgb(239, 232, 255)); buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(183, 112, 255))
        }
        card.addView(autoSearch); card.addView(autoNotes)
        body.addView(card)
        showPremiumPanel("Automation Rules", "Small workflows you can switch on or off", "✧", body, "Save rules") {
            prefs.edit().putBoolean("auto_search", autoSearch.isChecked).putBoolean("auto_notes", autoNotes.isChecked).apply()
            say("Android automation preferences saved.")
        }
    }

    private fun startSpeech() {
        if (speechInProgress) return
        updateOrbState("listening")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateOrbState("idle")
            say("Microphone permission off hai. Settings > Permissions & Privacy mein jaakar Allow microphone dabayein.")
            return
        }
        startInAppRecognition(isVoiceMode = false)
    }

    private fun startInAppRecognition(isVoiceMode: Boolean) {
        if (speechInProgress || isFinishing || isDestroyed) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            output.text = "Is phone par speech recognition service available nahi hai. Google/Speech recognition service enable karein."
            updateOrbState("idle")
            if (isVoiceMode) {
                wakeMode = false
                updateWakeButtonLabel(false)
            }
            return
        }
        try {
            try { speechRecognizer?.cancel(); speechRecognizer?.destroy() } catch (_: Exception) {}
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechInProgress = true
            updateOrbState("listening")
            if (isVoiceMode) {
                updateWakeButtonLabel(true)
                output.text = "Shraddha sun rahi hai… boliye. Listening band karne ke liye orb dabayein."
            } else output.text = "Sun rahi hoon… boliye."

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() { voiceRestartAttempts = 0 }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (isVoiceMode && wakeMode) output.text = "Aapki baat samajh rahi hoon…"
                }
                override fun onError(error: Int) {
                    speechInProgress = false
                    try { speechRecognizer?.destroy() } catch (_: Exception) {}
                    speechRecognizer = null
                    if (isVoiceMode && !wakeMode) return
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Awaaz samajh nahi aayi. Mic active hai—dobara boliye."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Awaaz nahi mili. Mic active hai—jab tayyar hon boliye."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission allow karein."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network slow hai; listening retry ho rahi hai."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy hai; listening restart ho rahi hai."
                        else -> "Voice recognition pause hui (code $error); listening retry ho rahi hai."
                    }
                    if (isVoiceMode && wakeMode && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        updateWakeButtonLabel(true)
                        output.text = message
                        updateOrbState("listening")
                        voiceRestartAttempts = (voiceRestartAttempts + 1).coerceAtMost(6)
                        val delay = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 450L
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1000L
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> (1200L * voiceRestartAttempts).coerceAtMost(8000L)
                            else -> (800L * voiceRestartAttempts).coerceAtMost(5000L)
                        }
                        scheduleContinuousListening(delay)
                    } else {
                        if (isVoiceMode) {
                            wakeMode = false
                            updateWakeButtonLabel(false)
                        }
                        updateOrbState("idle")
                        output.text = message
                    }
                }
                override fun onResults(results: Bundle?) {
                    speechInProgress = false
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    try { speechRecognizer?.destroy() } catch (_: Exception) {}
                    speechRecognizer = null
                    if (isVoiceMode && wakeMode) updateWakeButtonLabel(true) else updateOrbState("idle")
                    if (spoken.isNotBlank()) {
                        voiceRestartAttempts = 0
                        input.setText(spoken)
                        output.text = "Aapne kaha: $spoken"
                        if (isVoiceMode && wakeMode) updateOrbState("thinking")
                        routeCommand(spoken)
                        // The mic stays active during processing/speech playback, then restarts.
                        // TTS completion calls scheduleContinuousListening(). Actions which leave
                        // the app resume from onResume().
                        if (isVoiceMode && wakeMode && !aiRequestInProgress && !tts.isSpeaking) scheduleContinuousListening(900L)
                    } else {
                        output.text = if (isVoiceMode) "Kuch sunai nahi diya. Mic active hai—dobara boliye." else "Kuch sunai nahi diya. Dobara mic dabayein."
                        if (isVoiceMode && wakeMode) scheduleContinuousListening(450L)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val selectedLanguage = prefs.getString("speech_language", "Hinglish Auto") ?: "Hinglish Auto"
            val recognitionLocale = when (selectedLanguage) { "English" -> "en-IN"; "Hindi" -> "hi-IN"; else -> "hi-IN" }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLocale)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, recognitionLocale)
                if (android.os.Build.VERSION.SDK_INT >= 34 && selectedLanguage == "Hinglish Auto") {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                    putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, arrayListOf("hi-IN", "en-IN", "en-US"))
                }
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            speechInProgress = false
            try { speechRecognizer?.destroy() } catch (_: Exception) {}
            speechRecognizer = null
            if (isVoiceMode && wakeMode) {
                updateWakeButtonLabel(true)
                output.text = "Voice service restart ho rahi hai: ${e.message ?: "retrying"}"
                scheduleContinuousListening(1200L)
            } else {
                updateOrbState("idle")
                output.text = "Voice start nahi ho payi. Dobara try karein."
            }
        }
    }

    private val continuousListenRestart: Runnable = object : Runnable {
        override fun run() {
            if (wakeMode && !speechInProgress && !isFinishing && !isDestroyed) {
                if (aiRequestInProgress || (::tts.isInitialized && tts.isSpeaking)) {
                    handler.postDelayed(this, 500L)
                } else {
                    startInAppRecognition(isVoiceMode = true)
                }
            }
        }
    }

    private fun scheduleContinuousListening(delayMs: Long = 500L) {
        if (!wakeMode || isFinishing || isDestroyed) return
        handler.removeCallbacks(continuousListenRestart)
        handler.postDelayed(continuousListenRestart, delayMs)
    }

    private fun updateWakeButtonLabel(active: Boolean) {
        if (::wakeButton.isInitialized) {
            wakeButton.text = if (active) "🎙 Listening — tap to stop" else "🎙 Start listening"
        }
    }

    private fun startContinuousVoiceSession() {
        if (wakeMode) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            say("Microphone permission off hai. Settings > Permissions & Privacy mein Allow microphone dabayein.")
            return
        }
        wakeMode = true
        voiceRestartAttempts = 0
        updateWakeButtonLabel(true)
        startInAppRecognition(isVoiceMode = true)
    }

    @Deprecated("Activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == lensCode) {
            if (resultCode == RESULT_OK) {
                val bitmap = data?.extras?.get("data") as? Bitmap
                if (bitmap == null) say("फोटो नहीं मिली। दोबारा Visual Lens खोलकर कोशिश करें।") else analyzeLensPhoto(bitmap)
            } else say("फोटो नहीं ली गई। Visual Lens बंद है।")
        } else if (resultCode == RESULT_OK && requestCode == galleryPickCode) {
            val uri = data?.data ?: return
            try {
                val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                if (bitmap == null) say("Image decode nahi hui.")
                else if (pendingImageMode == "color") detectDominantColor(bitmap)
                else analyzeLensPhoto(bitmap, "You are Shraddha Visual Lens. $pendingBookPrompt Describe the selected image accurately in Hindi-English, transcribe readable visible text, explain objects/formulas if useful, and clearly note uncertainty. Do not identify any person.")
            } catch (e: Exception) { say("Image open nahi hui: ${e.message}") }
        } else if (resultCode == RESULT_OK && requestCode == documentPickCode) {
            data?.data?.let { handlePickedDocument(it) }
        } else if (resultCode == RESULT_OK && requestCode == whatsappFilePickCode) {
            data?.data?.let { shareFileToWhatsApp(it) }
        } else if (resultCode == RESULT_OK && requestCode == contactPickerCode) {
            val contactUri = data?.data
            if (contactUri != null) {
                try {
                    contentResolver.query(
                        contactUri,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            val contactName = if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else "Selected contact"
                            val phone = if (numberIndex >= 0) cursor.getString(numberIndex).orEmpty() else ""
                            pendingContactNumberField?.setText(phone)
                            pendingContactNameLabel?.text = if (phone.isNotBlank()) "Selected contact: $contactName · $phone" else "No phone number was available for $contactName"
                            if (phone.isBlank()) say("Is contact ke liye phone number nahi mila.")
                        } else say("Contact details nahi mil sake. Dobara select karein.")
                    } ?: say("Contact details read nahi ho sake.")
                } catch (e: Exception) {
                    say("Contact details read nahi ho sake: ${e.message}")
                }
            }
        }
    }

    private fun toggleWakeMode() {
        if (wakeMode) {
            wakeMode = false
            speechInProgress = false
            awaitingWakeCommand = false
            voiceRestartAttempts = 0
            handler.removeCallbacks(continuousListenRestart)
            try { speechRecognizer?.cancel(); speechRecognizer?.destroy() } catch (_: Exception) {}
            speechRecognizer = null
            if (::tts.isInitialized) tts.stop()
            try { remoteAudioPlayer?.stop(); remoteAudioPlayer?.release() } catch (_: Exception) {}
            remoteAudioPlayer = null
            updateWakeButtonLabel(false)
            output.text = "Continuous listening band hai."
            updateOrbState("idle")
            return
        }
        startContinuousVoiceSession()
    }

    private fun routeCommand(text: String) {
        val q = text.lowercase(Locale.ROOT)
        when {
            q.startsWith("open youtube") || q.contains("youtube kholo") -> openUrl("https://youtube.com")
            q.startsWith("open google") || q.contains("google kholo") -> openUrl("https://google.com")
            (q.startsWith("search ") || q.contains("search karo")) && prefs.getBoolean("auto_search", true) -> openUrl("https://www.google.com/search?q=" + Uri.encode(text))
            (q.startsWith("note ") || q.startsWith("नोट ")) && prefs.getBoolean("auto_notes", true) -> saveNote(text.substringAfter(' '))
            (q.contains("windows") || q.contains("pc control") || q.contains("notepad kholo") || q.contains("calculator kholo")) && prefs.getBoolean("auto_pc", true) -> sendPcCommand(text)
            q.startsWith("call ") || q.startsWith("phone karo ") || q.startsWith("call karo ") -> dialNumber(text.substringAfter(' ').trim())
            q.startsWith("sms ") || q.startsWith("message ") -> composeSms(text.substringAfter(' ').trim())
            q.contains("whatsapp") && (q.contains("send") || q.contains("message") || q.contains("bhejo")) -> composeWhatsApp(text)
            q.contains("camera kholo") || q.contains("visual lens") || q.startsWith("open camera") -> launchCamera()
            q.contains("clipboard") -> summarizeClipboard()
            q.contains("read screen") || q.contains("screen padh") || q.contains("screen read") || q.contains("screen ka text") -> readActiveScreen()
            q.startsWith("open app ") -> launchInstalledApp(text.substring(9).trim())
            q.startsWith("launch app ") -> launchInstalledApp(text.substring(11).trim())
            q.startsWith("app kholo ") -> launchInstalledApp(text.substring(10).trim())
            q == "go home" || q == "home screen" || q.contains("home par jao") || q.contains("app band karo") -> goHomeOrBack(home = true)
            q == "back" || q.contains("peeche jao") -> goHomeOrBack(home = false)
            q.startsWith("tap ") || q.startsWith("click ") || q.startsWith("button dabao ") -> confirmAndTap(text.substringAfter(' ').trim())
            q.startsWith("fill ") || q.startsWith("enter ") || q.startsWith("field bharo ") -> handleFillCommand(text)
            q.contains("read book") || q.contains("book padho") || q.contains("document padho") || q.contains("read document") -> openBookPicker()
            (q.contains("color") && (q.contains("recognize") || q.contains("identify"))) || q.contains("rang pehchano") || q.contains("rang batao") -> pickImageForColor()
            q.contains("visual lens") || q.contains("camera kholo") || q.startsWith("open camera") -> launchCamera()
            q.contains("maps") || q.contains("map par") || q.contains("navigate to") || q.contains("direction to") || q.contains("raasta batao") -> handleMapCommand(text)
            q.startsWith("book ") || q.startsWith("booking ") || q.startsWith("order ") || q.startsWith("buy ") || q.startsWith("ticket book") -> searchOnlineTask(text)
            q.contains("whatsapp") && (q.contains("image") || q.contains("photo") || q.contains("document") || q.contains("file")) -> pickFileToShare()
            q.contains("maths") || q.contains("math problem") || q.contains("solve this") || q.contains("chemistry") || q.contains("physics") || q.contains("history") || q.contains("medical science") || q.contains("science samjhao") -> askAI("Teach/explain this in clear Hindi-English with steps, examples, and equations if relevant: $text. Medical topics are for general education, not diagnosis or treatment.")
            else -> askAI(text)
        }
    }
    private fun askAI(prompt: String) {
        if (prompt.isBlank()) { say("पहले अपना सवाल लिखें या बोलें।"); return }
        val provider = prefs.getString("provider", "OpenAI") ?: "OpenAI"
        val suffix = provider.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "_")
        val savedProvider = prefs.getString("provider", "OpenAI") ?: "OpenAI"
        // Provider profiles are isolated. Legacy generic keys are only eligible for the
        // same selected provider, never silently reused for another provider.
        val profileKeys = (1..3).mapNotNull { slot ->
            prefs.getString("${suffix}_api_key_$slot", null)?.trim()?.takeIf { it.isNotBlank() }
        }
        val legacyKeys = if (savedProvider == provider) listOf(
            prefs.getString("api_key_1", "").orEmpty(),
            prefs.getString("api_key_2", "").orEmpty(),
            prefs.getString("api_key_3", "").orEmpty(),
            prefs.getString("api_key", "").orEmpty()
        ).map { it.trim() }.filter { it.isNotBlank() } else emptyList()
        val keys = (profileKeys + legacyKeys).distinct()
        val defaultModel = when (provider) {
            "Gemini" -> "gemini-3.8-flash"
            "Claude" -> "claude-sonnet-4-6"
            "Groq" -> "llama-3.3-70b-versatile"
            "Perplexity" -> "sonar"
            "Compatible" -> "openai/gpt-4o-mini"
            else -> "gpt-4o-mini"
        }
        val profileModel = prefs.getString("${suffix}_model", null)?.trim().orEmpty()
        val legacyModel = if (savedProvider == provider) prefs.getString("model", null)?.trim().orEmpty() else ""
        val model = profileModel.ifBlank { legacyModel.ifBlank { defaultModel } }
        // Provider endpoints are deliberately code-only: no Base URL is exposed in Settings.
        val base = when (provider) {
            "Gemini" -> "https://generativelanguage.googleapis.com/v1beta/openai"
            "Claude" -> "https://api.anthropic.com/v1"
            "Groq" -> "https://api.groq.com/openai/v1"
            "Perplexity" -> "https://api.perplexity.ai"
            "Compatible" -> "https://openrouter.ai/api/v1"
            else -> "https://api.openai.com/v1"
        }
        if (keys.isEmpty()) { say("Settings mein $provider ke liye valid API key bhar kar Save karein."); return }
        if (aiRequestInProgress) { output.text = "Ek AI request pehle se chal rahi hai. Kripya uska jawab aane dein."; return }
        aiRequestInProgress = true
        output.text = "Shraddha soch rahi hai…"
        updateOrbState("thinking")
        thread(name = "shraddha-ai-request") {
            var finalAnswer: String? = null
            var lastError = "Unknown API error"
            // Retry the same API key on transient socket/network errors before trying any backup key.
            providerKeyLoop@ for ((index, key) in keys.withIndex()) {
                var attempt = 0
                var tryAnotherKey = false
                while (attempt < 3) {
                    var conn: HttpURLConnection? = null
                    try {
                        val isClaude = provider == "Claude"
                        val isGemini = provider == "Gemini"
                        val requestModel = model.removePrefix("models/").trim()
                        // Use Gemini's native REST API for Gemini accounts. This avoids
                        // OpenAI-compatibility transport differences and uses Google's API-key header.
                        val endpoint = when {
                            isClaude -> "$base/messages"
                            isGemini -> "https://generativelanguage.googleapis.com/v1beta/models/$requestModel:generateContent"
                            else -> "$base/chat/completions"
                        }
                        conn = URL(endpoint).openConnection() as HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.connectTimeout = 25000
                        conn.readTimeout = 75000
                        conn.useCaches = false
                        conn.instanceFollowRedirects = true
                        // Avoid reuse of a stale pooled socket on mobile networks.
                        conn.setRequestProperty("Connection", "close")
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        conn.setRequestProperty("Accept", "application/json")
                        if (isClaude) {
                            conn.setRequestProperty("x-api-key", key)
                            conn.setRequestProperty("anthropic-version", "2023-06-01")
                        } else if (isGemini) {
                            conn.setRequestProperty("x-goog-api-key", key)
                        } else conn.setRequestProperty("Authorization", "Bearer $key")
                        if (provider == "Compatible") {
                            conn.setRequestProperty("HTTP-Referer", "https://github.com/inde2021-coder/shraddha-ai-updates")
                            conn.setRequestProperty("X-Title", "Shraddha AI")
                        }
                        conn.doOutput = true
                        val bodyJson = when {
                            isClaude -> JSONObject().put("model", requestModel).put("max_tokens", 2048)
                                .put("system", buildSystemPrompt())
                                .put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                            isGemini -> JSONObject()
                                .put("systemInstruction", JSONObject().put("parts", org.json.JSONArray()
                                    .put(JSONObject().put("text", buildSystemPrompt()))))
                                .put("contents", org.json.JSONArray().put(JSONObject()
                                    .put("role", "user")
                                    .put("parts", org.json.JSONArray().put(JSONObject().put("text", prompt)))))
                                .put("generationConfig", JSONObject().put("maxOutputTokens", 2048).put("temperature", 0.7))
                            else -> JSONObject().put("model", requestModel).put("messages", org.json.JSONArray()
                                .put(JSONObject().put("role", "system").put("content", buildSystemPrompt()))
                                .put(JSONObject().put("role", "user").put("content", prompt)))
                        }
                        val requestBytes = bodyJson.toString().toByteArray(Charsets.UTF_8)
                        conn.setFixedLengthStreamingMode(requestBytes.size)
                        conn.outputStream.use { it.write(requestBytes) }
                        val code = conn.responseCode
                        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                        val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                        if (code in 200..299) {
                            val obj = JSONObject(response)
                            finalAnswer = if (isClaude) {
                                val parts = obj.optJSONArray("content")
                                (0 until (parts?.length() ?: 0)).mapNotNull { i ->
                                    parts?.optJSONObject(i)?.takeIf { it.optString("type") == "text" }?.optString("text")
                                }.joinToString("\n").ifBlank { "Claude returned no text." }
                            } else if (isGemini) {
                                val candidates = obj.optJSONArray("candidates")
                                val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                                val textAnswer = (0 until (parts?.length() ?: 0)).mapNotNull { i ->
                                    parts?.optJSONObject(i)?.optString("text")?.takeIf { !it.isNullOrBlank() }
                                }.joinToString("\n")
                                if (textAnswer.isNotBlank()) textAnswer else {
                                    val block = obj.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                                    if (block.isNotBlank()) "Gemini blocked this response ($block). Try rephrasing the prompt."
                                    else "Gemini returned no text candidate. Check that the selected model supports generateContent."
                                }
                            } else {
                                val choices = obj.optJSONArray("choices")
                                val messageObj = choices?.optJSONObject(0)?.optJSONObject("message")
                                val content = messageObj?.opt("content")
                                when (content) {
                                    is String -> content
                                    is org.json.JSONArray -> (0 until content.length()).mapNotNull { i ->
                                        val part = content.optJSONObject(i)
                                        part?.optString("text")?.takeIf { part.optString("type") == "text" && it.isNotBlank() }
                                    }.joinToString("\n")
                                    else -> "Provider returned HTTP $code but no text answer. Try a standard chat/text model."
                                }.ifBlank { "Provider returned an empty text answer. Try a standard chat/text model." }
                            }
                            break@providerKeyLoop
                        } else {
                            val detail = extractProviderError(response)
                            lastError = when (code) {
                                400 -> "HTTP 400: request/model not accepted by $provider. $detail"
                                401 -> "HTTP 401: API key is invalid for $provider. Check the provider-specific key."
                                403 -> "HTTP 403: key/account cannot access this model. $detail"
                                404 -> "HTTP 404: model ID or endpoint was not found. Check the selected model '$model'. $detail"
                                408 -> "HTTP 408: request timed out. Try again on a stable network."
                                429 -> "HTTP 429: quota/rate limit/billing limit reached. $detail"
                                in 500..599 -> "Provider server error HTTP $code. Try again later. $detail"
                                else -> "API error HTTP $code ($provider / $model). $detail"
                            }.take(900)
                            if (code in listOf(401, 403, 429) && index < keys.lastIndex) {
                                tryAnotherKey = true
                                break
                            }
                            break@providerKeyLoop
                        }
                    } catch (e: Exception) {
                        if (isTransientNetworkFailure(e) && attempt < 2) {
                            attempt += 1
                            lastError = explainNetworkFailure(e, provider, attempt)
                            try {
                                Thread.sleep(700L * attempt)
                            } catch (_: InterruptedException) {
                                Thread.currentThread().interrupt()
                                lastError = "AI request was cancelled. Please try again."
                                break@providerKeyLoop
                            }
                            continue
                        }
                        lastError = explainNetworkFailure(e, provider, attempt)
                        // A socket failure is not an API-key failure; don't waste time rotating keys.
                        break@providerKeyLoop
                    } finally {
                        conn?.disconnect()
                    }
                }
                if (!tryAnotherKey) break@providerKeyLoop
            }
            runOnUiThread {
                aiRequestInProgress = false
                val answer = finalAnswer ?: "AI connection failed. $lastError"
                say(answer, preferGeminiVoice = finalAnswer != null)
            }
        }
    }

    private fun isTransientNetworkFailure(error: Throwable): Boolean {
        var current: Throwable? = error
        repeat(8) {
            val item = current ?: return@repeat
            if (item is java.net.SocketException || item is java.net.SocketTimeoutException ||
                item is java.net.ConnectException || item is java.net.UnknownHostException ||
                item is java.io.InterruptedIOException) return true
            val msg = item.message.orEmpty().lowercase(Locale.ROOT)
            if (msg.contains("software caused connection abort") || msg.contains("connection reset") ||
                msg.contains("broken pipe") || msg.contains("unexpected end of stream")) return true
            current = item.cause
        }
        return false
    }

    private fun explainNetworkFailure(error: Exception, provider: String, retriesDone: Int): String {
        val chain = mutableListOf<String>()
        var current: Throwable? = error
        repeat(8) {
            val item = current ?: return@repeat
            item.message?.takeIf { it.isNotBlank() }?.let { chain.add(it) }
            current = item.cause
        }
        val details = chain.distinct().joinToString(" | ").take(260).ifBlank { error.javaClass.simpleName }
        val lower = details.lowercase(Locale.ROOT)
        val advice = when {
            lower.contains("unknownhost") || lower.contains("unable to resolve host") ->
                "No DNS/internet route. Switch between Wi-Fi and mobile data, then retry."
            lower.contains("timeout") ->
                "The server/network timed out. Check signal and retry."
            lower.contains("ssl") || lower.contains("handshake") || lower.contains("certpath") ->
                "Secure HTTPS setup failed. Check device date/time and avoid intercepting VPN/proxy networks."
            lower.contains("software caused connection abort") || lower.contains("connection reset") || lower.contains("broken pipe") || error is java.net.SocketException ->
                "The network socket was interrupted while contacting $provider. This is not proof of a bad API key. Switch Wi-Fi/mobile data, temporarily disable VPN/proxy/private DNS, and retry."
            else -> "Check the phone's internet connection and retry."
        }
        val retryNote = if (retriesDone > 0) " (retried $retriesDone time${if (retriesDone == 1) "" else "s"})" else ""
        return "$advice$retryNote Details: $details"
    }

    private fun extractProviderError(response: String): String {
        if (response.isBlank()) return "The provider returned an empty error response."
        return try {
            val json = JSONObject(response)
            val error = json.opt("error")
            val message = when (error) {
                is JSONObject -> error.optString("message").ifBlank { error.optString("type") }
                is String -> error
                else -> json.optString("message").ifBlank { json.optString("detail") }
            }
            message.ifBlank { response }.replace(Regex("(?i)(api[_ -]?key|authorization)\\s*[:=]\\s*[^ ,;]+"), "$1=[redacted]").take(650)
        } catch (_: Exception) {
            response.take(650)
        }
    }

    private fun testProviderConnection(provider: String, model: String, apiKey: String, callback: (Boolean, String) -> Unit) {
        thread {
            var connection: HttpURLConnection? = null
            var success = false
            var message = "Connection test failed."
            try {
                val base = when (provider) {
                    "Gemini" -> "https://generativelanguage.googleapis.com/v1beta/openai"
                    "Claude" -> "https://api.anthropic.com/v1"
                    "Groq" -> "https://api.groq.com/openai/v1"
                    "Perplexity" -> "https://api.perplexity.ai"
                    "Compatible" -> "https://openrouter.ai/api/v1"
                    else -> "https://api.openai.com/v1"
                }
                val isClaude = provider == "Claude"
                val isGemini = provider == "Gemini"
                val requestModel = model.removePrefix("models/").trim()
                val endpoint = when {
                    isClaude -> "$base/messages"
                    isGemini -> "https://generativelanguage.googleapis.com/v1beta/models/$requestModel:generateContent"
                    else -> "$base/chat/completions"
                }
                connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 25000
                connection.readTimeout = 45000
                connection.useCaches = false
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Connection", "close")
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                if (isClaude) {
                    connection.setRequestProperty("x-api-key", apiKey)
                    connection.setRequestProperty("anthropic-version", "2023-06-01")
                } else if (isGemini) {
                    connection.setRequestProperty("x-goog-api-key", apiKey)
                } else {
                    connection.setRequestProperty("Authorization", "Bearer $apiKey")
                }
                if (provider == "Compatible") {
                    connection.setRequestProperty("HTTP-Referer", "https://github.com/inde2021-coder/shraddha-ai-updates")
                    connection.setRequestProperty("X-Title", "Shraddha AI")
                }
                val body = when {
                    isClaude -> JSONObject()
                        .put("model", requestModel).put("max_tokens", 32)
                        .put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", "Reply with exactly CONNECTION_OK")))
                    isGemini -> JSONObject()
                        .put("contents", org.json.JSONArray().put(JSONObject()
                            .put("role", "user")
                            .put("parts", org.json.JSONArray().put(JSONObject().put("text", "Reply with exactly CONNECTION_OK")))))
                        .put("generationConfig", JSONObject().put("maxOutputTokens", 32))
                    else -> JSONObject().put("model", requestModel).put("messages", org.json.JSONArray()
                        .put(JSONObject().put("role", "user").put("content", "Reply with exactly CONNECTION_OK")))
                }
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code in 200..299) {
                    success = true
                    message = "Connection successful. HTTPS endpoint responded for $provider / $model."
                } else {
                    val detail = extractProviderError(response)
                    message = when (code) {
                        400 -> "HTTP 400 — request/model not accepted. $detail"
                        401 -> "HTTP 401 — API key is invalid for this provider."
                        403 -> "HTTP 403 — account/key is not allowed to use this model. $detail"
                        404 -> "HTTP 404 — model ID or endpoint not found. Check model name: $model. $detail"
                        429 -> "HTTP 429 — quota/rate limit/billing limit reached. $detail"
                        in 500..599 -> "Provider server error HTTP $code. Retry later. $detail"
                        else -> "HTTP $code — $detail"
                    }.take(850)
                }
            } catch (e: Exception) {
                message = "Connection test failed: ${explainNetworkFailure(e, provider, 0)}".take(750)
            } finally {
                connection?.disconnect()
                runOnUiThread { callback(success, message) }
            }
        }
    }

    /**
     * Returns model IDs published by the selected provider. The API key is only
     * used for this on-device HTTPS request and is never included in UI errors.
     * Some providers (notably Perplexity) do not document a model-list endpoint,
     * so that provider receives a curated list and still allows manual model IDs.
     */
    private fun defaultModelChoices(provider: String): List<String> = when (provider) {
        "OpenAI" -> listOf(
            "gpt-5.2", "gpt-5.1", "gpt-5-mini", "gpt-5-nano", "gpt-5-pro",
            "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano", "gpt-4o", "gpt-4o-mini",
            "o3", "o4-mini", "gpt-oss-120b", "gpt-oss-20b"
        )
        "Gemini" -> listOf(
            "gemini-3.8-flash", "gemini-3.1-pro-preview", "gemini-3-flash-preview",
            "gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.5-flash-lite"
        )
        "Claude" -> listOf(
            "claude-opus-4-6", "claude-sonnet-4-6", "claude-haiku-4-5",
            "claude-opus-4-5", "claude-sonnet-4-5", "claude-3-7-sonnet-latest"
        )
        "Groq" -> listOf(
            "openai/gpt-oss-120b", "openai/gpt-oss-20b", "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant", "qwen/qwen3.8-27b", "whisper-large-v3",
            "whisper-large-v3-turbo", "meta-llama/llama-prompt-guard-2-86m"
        )
        "Perplexity" -> listOf(
            "sonar", "sonar-pro", "sonar-reasoning", "sonar-reasoning-pro", "sonar-deep-research"
        )
        else -> listOf(
            "openai/gpt-5.2", "openai/gpt-4o-mini", "anthropic/claude-sonnet-4-6",
            "google/gemini-3.8-flash", "meta-llama/llama-3.3-70b-instruct",
            "deepseek/deepseek-r1", "qwen/qwen3.5-235b-a22b"
        )
    }

    private fun fetchProviderModels(provider: String, apiKey: String, callback: (List<String>, String?) -> Unit) {
        if (provider == "Perplexity") {
            callback(defaultModelChoices(provider), "Perplexity's public API docs do not expose a model-list endpoint; these are suggestions. You can also type any model ID manually.")
            return
        }
        if (apiKey.isBlank()) {
            callback(defaultModelChoices(provider), "Add an API key to load the full model list from this provider. Showing suggested models for now.")
            return
        }
        thread {
            var connection: HttpURLConnection? = null
            try {
                val ids = mutableListOf<String>()
                val endpoint: String
                when (provider) {
                    "OpenAI" -> endpoint = "https://api.openai.com/v1/models"
                    "Gemini" -> endpoint = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000"
                    "Claude" -> endpoint = "https://api.anthropic.com/v1/models?limit=1000&lifecycle=active"
                    "Groq" -> endpoint = "https://api.groq.com/openai/v1/models"
                    else -> endpoint = "https://openrouter.ai/api/v1/models"
                }
                connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 25000
                connection.setRequestProperty("Accept", "application/json")
                when (provider) {
                    "Gemini" -> connection.setRequestProperty("x-goog-api-key", apiKey)
                    "Claude" -> {
                        connection.setRequestProperty("x-api-key", apiKey)
                        connection.setRequestProperty("anthropic-version", "2023-06-01")
                    }
                    else -> connection.setRequestProperty("Authorization", "Bearer $apiKey")
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val payload = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IllegalStateException("Provider returned HTTP $code. Check the API key and account access.")
                val json = JSONObject(payload)
                val data = if (provider == "Gemini") json.optJSONArray("models") else json.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        var id = item.optString("id").trim()
                        if (id.isBlank()) id = item.optString("name").removePrefix("models/").trim()
                        if (id.isBlank()) continue
                        if (provider == "Gemini") {
                            val methods = item.optJSONArray("supportedGenerationMethods")
                            // Keep models that can generate conversational content when metadata is supplied.
                            if (methods != null) {
                                var canGenerate = false
                                for (j in 0 until methods.length()) if (methods.optString(j).equals("generateContent", true)) canGenerate = true
                                if (!canGenerate) continue
                            }
                        }
                        ids.add(id)
                    }
                }
                val sorted = ids.distinct().sortedWith(compareBy<String> { !it.contains("chat", true) && !it.contains("gpt", true) && !it.contains("claude", true) && !it.contains("gemini", true) }.thenBy { it.lowercase(Locale.ROOT) })
                if (sorted.isEmpty()) throw IllegalStateException("Provider returned no model IDs. You can type the model ID manually.")
                runOnUiThread { callback(sorted, null) }
            } catch (e: Exception) {
                val message = e.message?.takeIf { it.isNotBlank() } ?: "Could not load models. Check internet and API key."
                runOnUiThread { callback(emptyList(), message) }
            } finally {
                connection?.disconnect()
            }
        }
    }

    private fun showModelPicker(provider: String, models: List<String>, note: String? = null, onSelected: (String) -> Unit) {
        if (models.isEmpty()) {
            android.app.AlertDialog.Builder(this)
                .setTitle("$provider models")
                .setMessage(note ?: "No model IDs were returned. You can enter the model ID manually.")
                .setPositiveButton("OK", null).show()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = rounded(intArrayOf(Color.rgb(12, 9, 29), Color.rgb(24, 17, 46)), 18)
        }
        if (!note.isNullOrBlank()) root.addView(premiumText(note, 11f, Color.rgb(194, 181, 221)).apply { setPadding(0, dp(4), 0, dp(8)) })
        val search = premiumField("Search ${models.size} model IDs")
        root.addView(search, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        val listView = ListView(this).apply { dividerHeight = dp(1); isTextFilterEnabled = false }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList(models))
        listView.adapter = adapter
        root.addView(listView, LinearLayout.LayoutParams(-1, dp(410)))
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { adapter.filter.filter(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Select $provider model")
            .setView(root)
            .setNegativeButton("Close", null)
            .create()
        listView.setOnItemClickListener { _, _, position, _ ->
            adapter.getItem(position)?.let(onSelected)
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun showSettings() {
        val ink = Color.rgb(248, 246, 255)
        val muted = Color.rgb(174, 164, 205)
        val violet = Color.rgb(192, 126, 255)
        val purple = Color.rgb(111, 64, 255)
        val pageBg = Color.rgb(8, 7, 20)
        val cardBg = Color.rgb(22, 18, 43)
        fun suffix(name: String) = name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "_")
        fun defaults(name: String): Triple<String, String, String> = when (name) {
            "Gemini" -> Triple("gemini-3.8-flash", "https://generativelanguage.googleapis.com/v1beta/openai", "https://aistudio.google.com/apikey")
            "Claude" -> Triple("claude-sonnet-4-6", "https://api.anthropic.com/v1", "https://console.anthropic.com/settings/keys")
            "Groq" -> Triple("llama-3.3-70b-versatile", "https://api.groq.com/openai/v1", "https://console.groq.com/keys")
            "Perplexity" -> Triple("sonar", "https://api.perplexity.ai", "https://www.perplexity.ai/settings/api")
            "Compatible" -> Triple("openai/gpt-4o-mini", "https://openrouter.ai/api/v1", "https://openrouter.ai/settings/keys")
            else -> Triple("gpt-4o-mini", "https://api.openai.com/v1", "https://platform.openai.com/api-keys")
        }
        fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        fun styledField(hintText: String, password: Boolean = false) = premiumField(
            hintText, password = password
        )
        fun fieldBlock(title: String, field: EditText): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title.uppercase(Locale.ROOT), 10f, muted, true).apply { letterSpacing = 0.08f; setPadding(dp(2), dp(8), 0, dp(6)) })
            addView(field, LinearLayout.LayoutParams(-1, -2))
        }

        val dialog = android.app.Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(intArrayOf(pageBg, Color.rgb(13, 10, 31)), 24)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(17), dp(16), dp(16), dp(17))
            background = rounded(intArrayOf(Color.rgb(82, 42, 178), Color.rgb(37, 26, 92)), 22); elevation = dp(8).toFloat()
        }
        val topLine = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val icon = TextView(this).apply {
            text = "✦"; textSize = 24f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(intArrayOf(Color.rgb(169, 98, 255), Color.rgb(99, 61, 231)), 15); elevation = dp(4).toFloat()
        }
        topLine.addView(icon, LinearLayout.LayoutParams(dp(46), dp(46)))
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        heading.addView(label("Shraddha AI", 10f, Color.rgb(224, 209, 255), true))
        heading.addView(label("AI CONNECTIONS", 20f, Color.WHITE, true).apply { setPadding(0, dp(3), 0, 0) })
        topLine.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        val closeSettings = TextView(this).apply {
            text = "×"; textSize = 28f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = rounded(intArrayOf(Color.rgb(92, 56, 151), Color.rgb(42, 28, 85)), 13)
            setOnClickListener { dialog.dismiss() }
        }
        apply3DEffect(closeSettings)
        topLine.addView(closeSettings, LinearLayout.LayoutParams(dp(34), dp(42)))
        header.addView(topLine)
        header.addView(label("Choose your AI brain", 18f, Color.WHITE, true).apply { setPadding(0, dp(15), 0, dp(4)) })
        header.addView(label("Provider endpoints are built into the app code and are not shown here.", 12f, Color.rgb(220, 211, 246)))
        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(13) })

        val scroll = ScrollView(this).apply { isFillViewport = false; clipToPadding = false }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(10)) }
        scroll.addView(body); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(label("SELECT PROVIDER", 11f, muted, true).apply { letterSpacing = 0.1f; setPadding(dp(2), dp(1), 0, dp(9)) })
        val cardViews = mutableMapOf<String, LinearLayout>()
        val providerInfo = listOf(
            Triple("OpenAI", "✧", "GPT models"),
            Triple("Gemini", "✦", "Google AI"),
            Triple("Claude", "◈", "Anthropic"),
            Triple("Groq", "⚡", "Fast inference"),
            Triple("Perplexity", "⌕", "Search-focused AI"),
            Triple("Compatible", "⌘", "OpenRouter / OpenAI-style")
        )
        providerInfo.chunked(2).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEachIndexed { index, item ->
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(13), dp(11), dp(8), dp(11))
                    isClickable = true; isFocusable = true; elevation = dp(5).toFloat()
                    addView(label(item.second, 20f, violet, true))
                    addView(label(item.first, 13f, ink, true).apply { setPadding(0, dp(4), 0, dp(2)) })
                    addView(label(item.third, 10f, muted))
                }
                apply3DEffect(card); cardViews[item.first] = card
                row.addView(card, LinearLayout.LayoutParams(0, dp(88), 1f).apply {
                    if (index == 0) rightMargin = dp(8) else leftMargin = dp(2)
                    bottomMargin = dp(8)
                })
            }
            if (rowItems.size == 1) row.addView(View(this), LinearLayout.LayoutParams(0, dp(88), 1f))
            body.addView(row)
        }

        val profileCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(15), dp(14), dp(15), dp(14))
            background = rounded(intArrayOf(cardBg, Color.rgb(25, 19, 49)), 22); elevation = dp(6).toFloat()
        }
        val sectionHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        sectionHeader.addView(label("Provider configuration", 16f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        sectionHeader.addView(label("● PROFILE", 9f, Color.rgb(184, 255, 216), true).apply {
            gravity = Gravity.CENTER; setPadding(dp(9), dp(6), dp(9), dp(6)); background = rounded(intArrayOf(Color.rgb(23, 66, 57), Color.rgb(23, 66, 57)), 30)
        })
        profileCard.addView(sectionHeader)
        profileCard.addView(label("Model, API key and backup keys for the selected provider.", 11f, muted).apply { setPadding(0, dp(6), 0, dp(8)) })
        val model = styledField("e.g. gpt-4o-mini")
        val key1 = styledField("Paste primary API key", true)
        val key2 = styledField("Backup API key (optional)", true)
        val key3 = styledField("Backup API key 3 (optional)", true)
        var currentlyLoaded = ""
        profileCard.addView(fieldBlock("Model name (choose or enter manually)", model))
        profileCard.addView(premiumButton("✦  Fetch / Select Available Models", Color.rgb(75, 42, 130), Color.WHITE) {
            val providerAtRequest = currentlyLoaded.ifBlank { "OpenAI" }
            val typedKey = key1.text.toString().trim()
            if (typedKey.isBlank()) {
                showModelPicker(providerAtRequest, defaultModelChoices(providerAtRequest), "Add the API key and tap Fetch Models to load the provider's live catalogue. The model field also accepts a custom ID.") { selectedModel -> model.setText(selectedModel) }
            } else {
                say("$providerAtRequest ke model IDs load ho rahe hain…")
                fetchProviderModels(providerAtRequest, typedKey) { models, error ->
                    if (models.isNotEmpty()) {
                        showModelPicker(providerAtRequest, models, "${models.size} model IDs returned by the provider. Some IDs may support a different API task (for example audio/embeddings) rather than chat.") { selectedModel -> model.setText(selectedModel) }
                        if (error != null) say(error)
                    } else {
                        showModelPicker(providerAtRequest, defaultModelChoices(providerAtRequest), error ?: "Live model catalogue unavailable. You can type the model ID manually.") { selectedModel -> model.setText(selectedModel) }
                    }
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        profileCard.addView(fieldBlock("API key · primary", key1))
        profileCard.addView(fieldBlock("API key · backup 2 (optional)", key2))
        profileCard.addView(fieldBlock("API key · backup 3 (optional)", key3))
        profileCard.addView(premiumButton("◎  Test AI connection", Color.rgb(43, 91, 78), Color.WHITE) {
            val selected = currentlyLoaded.ifBlank { "OpenAI" }
            val testModel = model.text.toString().trim().ifBlank { defaultModelChoices(selected).first() }
            val testKey = key1.text.toString().trim()
            if (testKey.isBlank()) {
                key1.error = "Enter this provider's API key"
                android.app.AlertDialog.Builder(this).setTitle("API key required")
                    .setMessage("Select $selected, enter its own API key and tap Test AI connection. Keys from other providers are not reused.")
                    .setPositiveButton("OK", null).show()
            } else {
                val progress = android.app.AlertDialog.Builder(this).setTitle("Testing $selected")
                    .setMessage("Checking API key, model ID and network connection…")
                    .setCancelable(false).create()
                progress.show()
                testProviderConnection(selected, testModel, testKey) { okay, detail ->
                    try { progress.dismiss() } catch (_: Exception) {}
                    if (!isFinishing && !isDestroyed) android.app.AlertDialog.Builder(this)
                        .setTitle(if (okay) "✓ Connection successful" else "Connection failed")
                        .setMessage(detail)
                        .setPositiveButton("OK", null).show()
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        val providerLink = TextView(this).apply { textSize = 12f; setTextColor(violet); setPadding(dp(2), dp(12), 0, dp(2)); isClickable = true }
        profileCard.addView(providerLink)
        body.addView(profileCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5); bottomMargin = dp(12) })
        body.addView(label("API keys are kept in this phone's app preferences. Use a model that supports the features you need.", 10f, muted).apply { setPadding(dp(4), 0, dp(4), dp(6)) })

        fun saveProfile(name: String) {
            val s = suffix(name)
            prefs.edit().putString("${s}_model", model.text.toString().trim())
                .putString("${s}_api_key_1", key1.text.toString().trim())
                .putString("${s}_api_key_2", key2.text.toString().trim())
                .putString("${s}_api_key_3", key3.text.toString().trim()).apply()
        }
        fun updateProviderCards(selected: String) {
            providerInfo.forEach { item ->
                val card = cardViews[item.first] ?: return@forEach
                card.background = if (item.first == selected)
                    rounded(intArrayOf(Color.rgb(82, 48, 153), Color.rgb(45, 29, 91)), 18).apply { setStroke(dp(1), Color.rgb(192, 126, 255)) }
                else rounded(intArrayOf(cardBg, Color.rgb(17, 15, 35)), 18).apply { setStroke(dp(1), Color.rgb(52, 43, 78)) }
            }
        }
        fun loadProfile(name: String) {
            val s = suffix(name); val d = defaults(name)
            val originallySelected = name == (prefs.getString("provider", "OpenAI") ?: "OpenAI") || (name == "Compatible" && prefs.getString("provider", "") == "Compatible")
            model.setText(prefs.getString("${s}_model", if (originallySelected) prefs.getString("model", d.first) else d.first) ?: d.first)
            key1.setText(prefs.getString("${s}_api_key_1", if (originallySelected) prefs.getString("api_key_1", prefs.getString("api_key", "")) else "") ?: "")
            key2.setText(prefs.getString("${s}_api_key_2", if (originallySelected) prefs.getString("api_key_2", "") else "") ?: "")
            key3.setText(prefs.getString("${s}_api_key_3", if (originallySelected) prefs.getString("api_key_3", "") else "") ?: "")
            providerLink.text = when (name) {
                "OpenAI" -> "↗  Get an OpenAI API key"
                "Gemini" -> "↗  Get a Gemini API key"
                "Claude" -> "↗  Get an Anthropic API key"
                "Groq" -> "↗  Get a Groq API key"
                "Perplexity" -> "↗  Get a Perplexity API key"
                else -> "↗  Get an OpenRouter API key"
            }
            providerLink.setOnClickListener { openUrl(defaults(name).third) }
            currentlyLoaded = name; updateProviderCards(name)
        }
        fun switchProvider(name: String) { if (currentlyLoaded.isNotBlank()) saveProfile(currentlyLoaded); loadProfile(name) }
        providerInfo.forEach { item -> cardViews[item.first]?.setOnClickListener { if (item.first != currentlyLoaded) switchProvider(item.first) } }
        loadProfile(prefs.getString("provider", "OpenAI") ?: "OpenAI")

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(11), 0, dp(2)) }
        footer.addView(premiumButton("Cancel", Color.rgb(37, 30, 59), ink) { dialog.dismiss() }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(8) })
        footer.addView(premiumButton("✦  Save connection", purple, Color.WHITE) {
            val selected = currentlyLoaded.ifBlank { "OpenAI" }; val suffixSelected = suffix(selected); val d = defaults(selected)
            val modelValue = model.text.toString().trim().ifBlank { d.first }
            val primary = key1.text.toString().trim()
            if (primary.isBlank()) { key1.error = "Primary API key required"; say("$selected ke liye primary API key daalein."); return@premiumButton }
            prefs.edit().putString("provider", selected)
                .putString("${suffixSelected}_model", modelValue)
                .putString("${suffixSelected}_api_key_1", primary)
                .putString("${suffixSelected}_api_key_2", key2.text.toString().trim())
                .putString("${suffixSelected}_api_key_3", key3.text.toString().trim())
                .putString("model", modelValue).putString("api_key_1", primary)
                .putString("api_key_2", key2.text.toString().trim()).putString("api_key_3", key3.text.toString().trim())
                .putString("api_key", primary).apply()
            dialog.dismiss(); say("$selected connection saved. Ab Shraddha AI se baat kar sakte hain.")
        }, LinearLayout.LayoutParams(0, dp(50), 1.6f))
        root.addView(footer, LinearLayout.LayoutParams(-1, -2))
        dialog.setContentView(root)
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); dialog.window?.setLayout(-1, -1)
            dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.show(); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); dialog.window?.setLayout(-1, -1)
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        root.alpha = 0f; root.scaleX = 0.98f; root.scaleY = 0.98f
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220).start()
    }

    private fun showWindowsSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 10, 20, 10) }
        val url = EditText(this).apply { hint = "Windows PC URL, e.g. http://192.168.1.20:8765"; setText(prefs.getString("pc_url", "")) }
        val token = EditText(this).apply { hint = "Pairing token"; setText(prefs.getString("pc_token", "")); inputType = 129 }
        box.addView(url); box.addView(token)
        AlertDialogCompat.show(this, "Windows Pairing", box) {
            prefs.edit().putString("pc_url", url.text.toString().trim().trimEnd('/')).putString("pc_token", token.text.toString()).apply()
            say("Windows pairing details saved. Ensure both devices are on a trusted network.")
        }
    }
    private fun sendPcCommand(command: String) {
        val base = prefs.getString("pc_url", "").orEmpty()
        val token = prefs.getString("pc_token", "").orEmpty()
        if (base.isBlank() || token.isBlank()) { say("पहले Windows PC को Settings में pair करें।"); return }
        thread {
            try {
                val conn = URL("$base/command").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"; conn.connectTimeout = 5000; conn.readTimeout = 8000
                conn.setRequestProperty("Authorization", "Bearer $token"); conn.setRequestProperty("Content-Type", "application/json"); conn.doOutput = true
                conn.outputStream.use { it.write(JSONObject().put("command", command).toString().toByteArray()) }
                val result = conn.inputStream.bufferedReader().use { it.readText() }
                runOnUiThread { say(result) }
            } catch (e: Exception) { runOnUiThread { say("PC connection failed: ${e.message}") } }
        }
    }
    private fun showCommunicationTools() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        val card = premiumSection("CONTACT · CALL · MESSAGE", "Choose a contact using Android's contact picker, then review the recipient and message before opening the calling or messaging app.")
        val selectedContact = premiumText("No contact selected", 11f, Color.rgb(195, 180, 221), true).apply { setPadding(0, dp(4), 0, dp(7)) }
        val number = premiumField("Phone number", multiLine = false).apply {
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        val message = premiumField("Message to send…", multiLine = true).apply { minLines = 3; maxLines = 6 }
        card.addView(premiumButton("Search / choose from Contacts", Color.rgb(75, 42, 130), Color.WHITE) {
            pendingContactNumberField = number
            pendingContactNameLabel = selectedContact
            try {
                startActivityForResult(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), contactPickerCode)
            } catch (_: Exception) {
                say("Contact picker nahi khul saka. Aap phone number manually enter kar sakte hain.")
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(9) })
        card.addView(selectedContact)
        card.addView(premiumText("PHONE NUMBER", 10f, Color.rgb(176, 163, 208), true).apply { setPadding(0, dp(6), 0, dp(5)) })
        card.addView(number)
        card.addView(premiumText("MESSAGE (SMS / WHATSAPP)", 10f, Color.rgb(176, 163, 208), true).apply { setPadding(0, dp(13), 0, dp(5)) })
        card.addView(message)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(premiumButton("☎ Call", Color.rgb(68, 51, 120), Color.WHITE) {
            val phone = number.text.toString().trim()
            if (phone.isBlank()) say("Pehle contact select karein ya number daalein.")
            else androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                .setTitle("Open phone dialer?")
                .setMessage("Recipient: ${selectedContact.text}\nNumber: $phone\nDialer khulega; aap call ko confirm kar sakte hain.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open dialer") { _, _ -> dialNumber(phone) }.show()
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(5) })
        actions.addView(premiumButton("✉ SMS", Color.rgb(68, 51, 120), Color.WHITE) {
            val phone = number.text.toString().trim(); val text = message.text.toString().trim()
            if (phone.isBlank()) say("Pehle contact select karein ya number daalein.")
            else if (text.isBlank()) say("SMS ke liye message likhein.")
            else confirmThenOpenSms(phone, text, selectedContact.text.toString())
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(5); rightMargin = dp(5) })
        actions.addView(premiumButton("WhatsApp", Color.rgb(29, 111, 80), Color.WHITE) {
            val phone = number.text.toString().trim(); val text = message.text.toString().trim()
            if (phone.isBlank()) say("Pehle contact select karein ya number daalein.")
            else if (text.isBlank()) say("WhatsApp ke liye message likhein.")
            else confirmThenOpenWhatsApp(phone, text, selectedContact.text.toString())
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(5) })
        card.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        card.addView(premiumText("Privacy note: Call opens the dialer. SMS/WhatsApp opens a prefilled composer/chat; the destination app controls the final Send action. Standard Android intents cannot silently send a WhatsApp message.", 10.5f, Color.rgb(190, 178, 216)).apply { setPadding(0, dp(10), 0, 0) })
        body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        body.addView(premiumActionCard("Developer contact · Sharad Patel", "8120727987 · Call, SMS or WhatsApp support", "✧") { showDeveloperDetails() })
        showPremiumPanel("Calls & Messages", "Search contacts and prepare phone actions with a review step", "☎", body)
    }

    private fun confirmThenOpenSms(phone: String, text: String, displayName: String = "") {
        val cleanPhone = phone.trim()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Review SMS")
            .setMessage("To: ${displayName.ifBlank { cleanPhone }}\nNumber: $cleanPhone\n\n$text\n\nNext step opens your SMS app with this draft. It will not send in the background.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open SMS app") { _, _ ->
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(cleanPhone))).apply { putExtra("sms_body", text) }
                try { startActivity(intent) } catch (_: Exception) { say("SMS app nahi khul saki.") }
            }.show()
    }

    private fun confirmThenOpenWhatsApp(phone: String, text: String, displayName: String = "") {
        val digits = phone.filter { it.isDigit() }
        val waNumber = when {
            digits.length == 10 -> "91$digits"
            digits.startsWith("00") -> digits.drop(2)
            else -> digits
        }
        if (waNumber.length < 8) { say("WhatsApp ke liye valid country-code wala number chahiye."); return }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Review WhatsApp message")
            .setMessage("To: ${displayName.ifBlank { phone }}\nWhatsApp number: $waNumber\n\n$text\n\nNext step opens WhatsApp with a draft. WhatsApp requires you to tap Send.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open WhatsApp") { _, _ ->
                val uri = Uri.parse("https://wa.me/$waNumber?text=${Uri.encode(text)}")
                try { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                catch (_: Exception) { say("WhatsApp ya supported browser nahi mila.") }
            }.show()
    }

    private fun showDeveloperDetails() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        val card = premiumSection("DEVELOPER & SUPPORT", "Official project contact details entered by the app owner.")
        card.addView(premiumText("Sharad Patel", 22f, Color.WHITE, true).apply { setPadding(0, dp(4), 0, dp(3)) })
        card.addView(premiumText("Developer · Shraddha AI", 12f, Color.rgb(194, 177, 224)).apply { setPadding(0, 0, 0, dp(9)) })
        card.addView(premiumText("Phone: 8120727987\nIndia: +91 81207 27987", 15f, Color.rgb(232, 221, 255), true).apply { setPadding(0, 0, 0, dp(12)) })
        card.addView(premiumButton("☎ Call Sharad Patel", Color.rgb(75, 42, 130), Color.WHITE) {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Call developer")
                .setMessage("Open the phone dialer for Sharad Patel · 8120727987?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open dialer") { _, _ -> dialNumber("8120727987") }.show()
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
        card.addView(premiumButton("✉ SMS developer", Color.rgb(75, 42, 130), Color.WHITE) {
            confirmThenOpenSms("8120727987", "Namaste Sharad Patel, mujhe Shraddha AI ke baare mein support chahiye.", "Sharad Patel")
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
        card.addView(premiumButton("WhatsApp developer", Color.rgb(29, 111, 80), Color.WHITE) {
            confirmThenOpenWhatsApp("8120727987", "Namaste Sharad Patel, mujhe Shraddha AI ke baare mein support chahiye.", "Sharad Patel")
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
        card.addView(premiumButton("Copy contact number", Color.rgb(57, 39, 94), Color.WHITE) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Sharad Patel", "8120727987"))
            say("Developer contact copied.")
        }, LinearLayout.LayoutParams(-1, dp(48)))
        body.addView(card)
        body.addView(premiumText("Developer profile: Sharad Patel\nContact: 8120727987\n\nCalls open the dialer. SMS and WhatsApp open a reviewable draft; messages are not silently sent by Shraddha AI.", 11f, Color.rgb(190, 178, 216)).apply { setPadding(dp(4), dp(9), dp(4), dp(4)) })
        showPremiumPanel("Developer Details", "Shraddha AI · Help and contact", "✧", body)
    }

    private fun showFeatureEnableGuide() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        val current = premiumSection("AVAILABLE NOW", "These features require only the indicated user action and whatever provider account is needed.")
        current.addView(premiumText("• AI Chat / text provider: Settings → Connections; save provider key/model.\n• AI Vision: Settings → AI Vision API; save a vision-capable model/key.\n• Voice input: Permissions & Privacy → Allow microphone.\n• Battery notifications: Allow Android notifications, then enable Battery Alerts.\n• Contact search: Calls & Messages → Search / choose from Contacts. The Android contact picker avoids giving the app broad contact-list access.\n• Call/SMS/WhatsApp: choose a contact, review the action; the destination app performs the final call/send.", 11f, Color.rgb(194, 183, 220)))
        body.addView(current, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
        val limited = premiumSection("FEATURES THAT NEED A SEPARATE ENABLEMENT / INTEGRATION", "These are deliberately not activated silently from a Settings toggle.")
        limited.addView(premiumText("• Cross-app screen reading and tapping: requires the Accessibility service to be registered in AndroidManifest and the user to enable it in Android Accessibility Settings. This sideload-safe manifest does not register it, so its setting alone cannot turn it on.\n• Notification-content wake: requires a registered NotificationListenerService and user-granted Notification Access. This build does not register it.\n• Automatic WhatsApp delivery: the public WhatsApp app intent only opens the chat/draft; it cannot send silently. Production automation needs the official WhatsApp Business Platform, business verification, tokens, templates where applicable, and server-side integration.\n• Automatic SMS: kept as a draft in the system SMS app; silent sending would need sensitive SMS permissions/default-handler policy and is not enabled.\n• Cellular-call auto-answer / two-way AI audio: needs supported call-role/Telecom integration; a regular app cannot reliably capture both sides of a SIM call.", 11f, Color.rgb(255, 196, 120)))
        body.addView(limited)
        body.addView(premiumButton("Open Permissions & Privacy", Color.rgb(75, 42, 130), Color.WHITE) { showPermissionsSettings() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        showPremiumPanel("Feature Enable Guide", "What is enabled, what needs permission and what needs separate development", "ⓘ", body)
    }

    private fun showVisionApiSettings() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        val card = premiumSection("VISION MODEL CONFIGURATION", "Set a dedicated image-analysis provider, model and key. The endpoint/base URL is code-defined and is not shown on this screen.")
        val providers = arrayOf("OpenAI", "Gemini", "Claude", "Groq", "Compatible")
        val savedVisionProvider = prefs.getString("vision_provider", prefs.getString("provider", "OpenAI")) ?: "OpenAI"
        val provider = premiumSpinner(providers, savedVisionProvider)
        fun modelDefault(name: String): String = defaultVisionModelChoices(name).first()
        fun visionSuffix(name: String) = name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "_")
        val model = premiumField("Vision model ID", prefs.getString("vision_model", modelDefault(savedVisionProvider)).orEmpty(), multiLine = false)
        val initialVisionKey = prefs.getString("vision_${visionSuffix(savedVisionProvider)}_api_key", null)?.takeIf { it.isNotBlank() }
            ?: prefs.getString("vision_api_key", null)?.takeIf { it.isNotBlank() }
            ?: prefs.getString("${visionSuffix(savedVisionProvider)}_api_key_1", "")
        val key = premiumField("Vision API key (stored on this phone)", initialVisionKey.orEmpty(), password = true, multiLine = false)
        val profileNote = premiumText("Tip: Fetch Models lists models your key can see. Not every returned model accepts images; choose a vision/multimodal model. The Vision key can be separate from the chat key.", 10.5f, Color.rgb(190, 178, 216)).apply { setPadding(0, dp(8), 0, dp(4)) }
        card.addView(premiumText("VISION PROVIDER", 10f, Color.rgb(176, 163, 208), true).apply { setPadding(0, dp(4), 0, dp(5)) })
        card.addView(provider, LinearLayout.LayoutParams(-1, dp(49)))
        card.addView(premiumText("VISION MODEL", 10f, Color.rgb(176, 163, 208), true).apply { setPadding(0, dp(12), 0, dp(5)) })
        card.addView(model)
        card.addView(premiumButton("Fetch / select models", Color.rgb(75, 42, 130), Color.WHITE) {
            val selectedProvider = provider.selectedItem?.toString() ?: "OpenAI"
            val keyValue = key.text.toString().trim()
            val fetchProvider = selectedProvider
            fetchProviderModels(fetchProvider, keyValue) { ids, note ->
                if (ids.isEmpty()) showModelPicker(selectedProvider, defaultVisionModelChoices(selectedProvider), note ?: "Live models unavailable; choose a suggested model or type an ID.") { picked -> model.setText(picked) }
                else showModelPicker(selectedProvider, ids, "Choose a model ID. For Vision Lens, select a model that supports image input.") { picked -> model.setText(picked) }
            }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
        card.addView(premiumText("VISION API KEY", 10f, Color.rgb(176, 163, 208), true).apply { setPadding(0, dp(12), 0, dp(5)) })
        card.addView(key)
        card.addView(profileNote)
        val statusText = premiumText("Saved vision profile: ${prefs.getString("vision_provider", "Not configured") ?: "Not configured"} · ${if (initialVisionKey.orEmpty().isBlank()) "separate vision key not set (matching chat key may be used)" else "provider-specific key available"}", 10.5f, Color.rgb(190, 178, 216)).apply { setPadding(0, dp(5), 0, dp(4)) }
        card.addView(statusText)
        var currentVisionProvider = savedVisionProvider
        fun persistVisionForm(name: String) {
            val suffix = visionSuffix(name)
            prefs.edit().putString("vision_${suffix}_model", model.text.toString().trim().ifBlank { modelDefault(name) })
                .putString("vision_${suffix}_api_key", key.text.toString().trim()).apply()
        }
        fun loadVisionForm(name: String) {
            val suffix = visionSuffix(name)
            val savedModel = prefs.getString("vision_${suffix}_model", null)
                ?: if (name == savedVisionProvider) prefs.getString("vision_model", null) else null
            val savedKey = prefs.getString("vision_${suffix}_api_key", null)?.takeIf { it.isNotBlank() }
                ?: (if (name == savedVisionProvider) prefs.getString("vision_api_key", null)?.takeIf { it.isNotBlank() } else null)
                ?: prefs.getString("${suffix}_api_key_1", "")
            model.setText(savedModel.orEmpty().ifBlank { modelDefault(name) })
            key.setText(savedKey.orEmpty())
        }
        provider.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            private var initialized = false
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = providers.getOrNull(position) ?: return
                if (!initialized) { initialized = true; currentVisionProvider = selected; return }
                if (selected == currentVisionProvider) return
                persistVisionForm(currentVisionProvider)
                currentVisionProvider = selected
                loadVisionForm(selected)
                statusText.text = "Editing $selected vision profile · Base URL is defined in app code."
            }
        }
        body.addView(card)
        showPremiumPanel("AI Vision API", "Independent vision API profile · model selection · key settings", "◉", body, "Save Vision API") {
            val selectedProvider = provider.selectedItem?.toString() ?: "OpenAI"
            val selectedModel = model.text.toString().trim().ifBlank { modelDefault(selectedProvider) }
            val selectedKey = key.text.toString().trim()
            val suffix = visionSuffix(selectedProvider)
            prefs.edit().putString("vision_provider", selectedProvider)
                .putString("vision_model", selectedModel)
                .putString("vision_api_key", selectedKey)
                .putString("vision_${suffix}_model", selectedModel)
                .putString("vision_${suffix}_api_key", selectedKey)
                .apply()
            say("AI Vision profile saved for $selectedProvider. Use a vision-capable model ID.")
        }
    }

    private fun defaultVisionModelChoices(provider: String): List<String> = when (provider) {
        "Gemini" -> listOf("gemini-3.8-flash", "gemini-2.5-flash", "gemini-2.5-pro")
        "Claude" -> listOf("claude-sonnet-4-6", "claude-opus-4-6", "claude-haiku-4-5")
        "Groq" -> listOf("qwen/qwen3.8-27b", "meta-models/muse-spark-open")
        "Compatible" -> listOf("openai/gpt-4o-mini", "google/gemini-2.5-flash", "anthropic/claude-sonnet-4-6", "qwen/qwen3.8-27b")
        else -> listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1", "gpt-4.1-mini")
    }

    private fun dialNumber(number: String) {
        if (number.isBlank()) { say("कृपया phone number बताएं।"); return }
        try { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))) }
        catch (_: Exception) { say("Dialer नहीं खुल सका।") }
    }

    private fun composeSms(value: String) {
        val pieces = value.split(" ", limit = 2)
        if (pieces.size < 2) { say("इस तरह बोलें: SMS 9876543210 message text"); return }
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(pieces[0])))
        intent.putExtra("sms_body", pieces[1])
        try { startActivity(intent) } catch (_: Exception) { say("SMS app नहीं खुल सकी।") }
    }

    private fun composeWhatsApp(value: String) {
        val msg = value.replace(Regex("(?i)whatsapp|send|message|bhejo|bhej do"), "").trim()
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; setPackage("com.whatsapp"); putExtra(Intent.EXTRA_TEXT, msg) }
        try { startActivity(intent) } catch (_: Exception) { say("WhatsApp नहीं मिला। Message भेजने से पहले आप recipient चुनें।") }
    }

    private fun effectiveVisionApiKey(providerOverride: String? = null): String {
        val selected = providerOverride ?: (prefs.getString("vision_provider", null)
            ?: prefs.getString("provider", "OpenAI") ?: "OpenAI")
        val suffix = selected.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "_")
        val profileVisionKey = prefs.getString("vision_${suffix}_api_key", null).orEmpty().trim()
        if (profileVisionKey.isNotBlank()) return profileVisionKey
        val activeVisionProvider = prefs.getString("vision_provider", null)
        val activeVisionKey = if (activeVisionProvider.isNullOrBlank() || activeVisionProvider == selected) {
            prefs.getString("vision_api_key", "").orEmpty().trim()
        } else ""
        if (activeVisionKey.isNotBlank()) return activeVisionKey
        // Backward-compatible fallback: use the same provider's saved chat key, without showing the endpoint in UI.
        val profile = prefs.getString("${suffix}_api_key_1", null).orEmpty().trim()
        if (profile.isNotBlank()) return profile
        val activeChatProvider = prefs.getString("provider", "OpenAI") ?: "OpenAI"
        return if (activeChatProvider == selected) prefs.getString("api_key_1", prefs.getString("api_key", "")).orEmpty().trim() else ""
    }

    private fun launchCamera() {
        val key = effectiveVisionApiKey()
        if (key.isBlank()) { say("Visual Lens ke liye Settings > AI Vision API mein key set karein, ya Connections mein vision-capable provider ki key save karein."); showVisionApiSettings(); return }
        try {
            val intent = Intent("android.media.action.IMAGE_CAPTURE")
            if (intent.resolveActivity(packageManager) == null) { say("इस फोन में camera app उपलब्ध नहीं है।"); return }
            say("कैमरा खोल रही हूँ। आसपास की चीज़ें समझने के लिए फोटो लें। फोटो आपके चुने AI provider को analysis के लिए भेजी जाएगी।")
            startActivityForResult(intent, lensCode)
        } catch (_: Exception) { say("इस फोन में camera app उपलब्ध नहीं है।") }
    }

    private fun analyzeLensPhoto(bitmap: Bitmap, analysisInstruction: String = "Describe visible surroundings, objects, text/signs, layout, useful context, and uncertainty in Hindi or Hinglish. Do not guess hidden facts or claim certainty about identities. If people appear, describe only visible non-sensitive scene details; do not identify them. Give a concise answer and mention that this is a single photo, not continuous live vision.") {
        val provider = prefs.getString("vision_provider", prefs.getString("provider", "OpenAI")) ?: "OpenAI"
        val providerSuffix = provider.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "_")
        val key = effectiveVisionApiKey(provider)
        val defaultModel = defaultVisionModelChoices(provider).first()
        val model = prefs.getString("vision_${providerSuffix}_model", null)?.trim().orEmpty()
            .ifBlank { prefs.getString("vision_model", null)?.trim().orEmpty().ifBlank { defaultModel } }
        // Vision endpoints are selected by provider in code; endpoint/base URL is intentionally not exposed in the UI.
        val base = when (provider) {
            "Groq" -> "https://api.groq.com/openai/v1"
            "Gemini" -> "https://generativelanguage.googleapis.com/v1beta/openai"
            "Claude" -> "https://api.anthropic.com/v1"
            "Compatible" -> "https://openrouter.ai/api/v1"
            else -> "https://api.openai.com/v1"
        }
        if (key.isBlank()) { say("Visual Lens ke liye selected provider ki API key Settings mein save karein."); return }
        val isClaude = provider == "Claude"
        val isGemini = provider == "Gemini"
        val requestModel = model.removePrefix("models/").trim()
        val endpoint = when {
            isClaude -> "$base/messages"
            isGemini -> "https://generativelanguage.googleapis.com/v1beta/models/$requestModel:generateContent"
            else -> "$base/chat/completions"
        }
        say("Visual Lens फोटो समझ रही है…")
        thread {
            var visionConnection: HttpURLConnection? = null
            try {
                val scaled = Bitmap.createScaledBitmap(bitmap, minOf(bitmap.width, 1024), (bitmap.height * (minOf(bitmap.width, 1024).toFloat() / bitmap.width)).toInt().coerceAtLeast(1), true)
                val bytes = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 78, bytes)
                val imageData = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                val userContent = if (isClaude) {
                    org.json.JSONArray()
                        .put(JSONObject().put("type", "text").put("text", analysisInstruction))
                        .put(JSONObject().put("type", "image").put("source", JSONObject()
                            .put("type", "base64").put("media_type", "image/jpeg").put("data", imageData)))
                } else {
                    org.json.JSONArray()
                        .put(JSONObject().put("type", "text").put("text", analysisInstruction))
                        .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageData")))
                }
                val messages = if (isClaude) org.json.JSONArray()
                    .put(JSONObject().put("role", "user").put("content", userContent))
                else org.json.JSONArray()
                    .put(JSONObject().put("role", "system").put("content", buildSystemPrompt()))
                    .put(JSONObject().put("role", "user").put("content", userContent))
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).also { visionConnection = it }
                conn.requestMethod = "POST"; conn.connectTimeout = 25000; conn.readTimeout = 60000
                conn.useCaches = false
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("Connection", "close")
                if (isClaude) {
                    conn.setRequestProperty("x-api-key", key)
                    conn.setRequestProperty("anthropic-version", "2023-06-01")
                } else if (isGemini) conn.setRequestProperty("x-goog-api-key", key)
                else conn.setRequestProperty("Authorization", "Bearer $key")
                if (provider == "Compatible") {
                    conn.setRequestProperty("HTTP-Referer", "https://github.com/inde2021-coder/shraddha-ai-updates")
                    conn.setRequestProperty("X-Title", "Shraddha AI")
                }
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.doOutput = true
                val request = when {
                    isClaude -> JSONObject().put("model", requestModel).put("max_tokens", 1600)
                        .put("system", buildSystemPrompt()).put("messages", messages)
                    isGemini -> JSONObject()
                        .put("systemInstruction", JSONObject().put("parts", org.json.JSONArray()
                            .put(JSONObject().put("text", buildSystemPrompt()))))
                        .put("contents", org.json.JSONArray().put(JSONObject()
                            .put("role", "user")
                            .put("parts", org.json.JSONArray()
                                .put(JSONObject().put("text", analysisInstruction))
                                .put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", imageData))))))
                        .put("generationConfig", JSONObject().put("maxOutputTokens", 1600))
                    else -> JSONObject().put("model", requestModel).put("messages", messages)
                }
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val obj = JSONObject(response)
                val answer = if (code in 200..299) {
                    when {
                        isClaude -> {
                            val parts = obj.optJSONArray("content")
                            (0 until (parts?.length() ?: 0)).mapNotNull { i -> parts?.optJSONObject(i)?.takeIf { it.optString("type") == "text" }?.optString("text") }.joinToString("\n")
                        }
                        isGemini -> {
                            val candidates = obj.optJSONArray("candidates")
                            val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                            (0 until (parts?.length() ?: 0)).mapNotNull { i -> parts?.optJSONObject(i)?.optString("text")?.takeIf { !it.isNullOrBlank() } }.joinToString("\n")
                                .ifBlank { "Gemini returned no readable text. Check that the selected model supports image input." }
                        }
                        else -> obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    }
                } else "Vision API error $code: ${obj.optJSONObject("error")?.optString("message") ?: obj.optString("message", response)}"
                runOnUiThread {
                    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 8, 18, 8) }
                    val description = EditText(this).apply { setText(answer); minLines = 5; maxLines = 12 }
                    val remember = CheckBox(this).apply { text = "इस observation को Shraddha की local memory में सेव करें"; isChecked = true }
                    box.addView(TextView(this).apply { text = "Lens ने क्या देखा? ज़रूरत हो तो जवाब edit करें।" })
                    box.addView(description); box.addView(remember)
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("👁 Visual Lens").setView(box)
                        .setPositiveButton("Done") { _, _ ->
                            val finalText = description.text.toString().trim()
                            say(finalText, preferGeminiVoice = true)
                            if (remember.isChecked && finalText.isNotBlank()) {
                                val old = prefs.getString("lens_memory", "").orEmpty()
                                val entry = "[${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date())}] $finalText"
                                prefs.edit().putString("lens_memory", (old + "\n" + entry).trim().takeLast(12000)).apply()
                            }
                        }.setNegativeButton("Discard", null).show()
                }
            } catch (e: Exception) { runOnUiThread { say("Visual Lens error: ${e.message}. जाँचें कि चुना model image input support करता है।") } }
            finally { visionConnection?.disconnect() }
        }
    }

    private fun summarizeClipboard() {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = manager.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
        if (text.isBlank()) say("Clipboard खाली है। पहले text copy करें।") else askAI("इस copied text को संक्षेप में समझाओ:\n$text")
    }

    private fun showMemory() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        val personalCard = premiumSection("PERSONAL MEMORY", "Details you choose to save locally for future conversations.")
        val memory = premiumField("What should Shraddha remember?", prefs.getString("ai_memory", "").orEmpty(), multiLine = true)
        personalCard.addView(memory)
        body.addView(personalCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val lensCard = premiumSection("VISUAL LENS NOTES", "Observations from photos you chose to analyze. These are not live or guaranteed current facts.")
        val lensMemory = premiumField("Saved observations…", prefs.getString("lens_memory", "").orEmpty(), multiLine = true)
        lensCard.addView(lensMemory)
        body.addView(lensCard)
        showPremiumPanel("AI Memory", "Edit and control what stays on this phone", "♥", body, "Save memory") {
            prefs.edit().putString("ai_memory", memory.text.toString()).putString("lens_memory", lensMemory.text.toString()).apply()
            say("Memory is phone par save ho gayi.")
        }
    }

    // Sideload-safe distribution: notification listener is intentionally not registered.
    private fun isNotificationListenerEnabled(): Boolean = false

    private fun openAppDetailsSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            say("Phone Settings mein Apps > Shraddha AI > Permissions kholein.")
        }
    }

    private fun openAppNotificationSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            startActivity(intent)
        } catch (_: Exception) { openAppDetailsSettings() }
    }

    private fun showPermissionsSettings() {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(8))
        }
        val intro = premiumSection(
            "YOU ARE IN CONTROL",
            "Shraddha never enables a sensitive permission by itself. Runtime requests happen only after you tap Allow; special-access rows ask for confirmation before opening Android Settings. You can cancel or revoke access later."
        )
        intro.addView(premiumText("No screen text, notification contents, microphone audio, or selected image is intentionally sent until you invoke the related feature. AI prompts/photos you submit are sent to the AI provider selected in Connections.", 11f, Color.rgb(194, 183, 220)))
        body.addView(intro, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val refreshers = mutableListOf<() -> Unit>()
        fun addPermissionRow(
            title: String,
            description: String,
            statusProvider: () -> Pair<String, Boolean>,
            buttonLabelProvider: () -> String,
            action: () -> Unit
        ) {
            val card = premiumSection(title, description)
            val status = premiumText("", 11f, Color.rgb(255, 196, 120), true).apply {
                setPadding(dp(2), dp(1), 0, dp(8))
            }
            card.addView(status)
            val actionButton = premiumButton(buttonLabelProvider(), Color.rgb(75, 42, 130), Color.WHITE, action)
            card.addView(actionButton, LinearLayout.LayoutParams(-1, dp(46)))
            body.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })
            val refresh = {
                val (label, enabled) = statusProvider()
                status.text = (if (enabled) "● " else "○ ") + label
                status.setTextColor(if (enabled) Color.rgb(143, 243, 190) else Color.rgb(255, 196, 120))
                actionButton.text = buttonLabelProvider()
            }
            refreshers.add(refresh)
            refresh()
        }

        addPermissionRow(
            "MICROPHONE · VOICE COMMANDS",
            "Needed for speech input and Hey Shraddha / double-clap wake mode. Listening starts only after you explicitly start it; background wake shows a persistent notification.",
            { (if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "Allowed" else "Not allowed" ) to (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) },
            { if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "Manage app permissions" else "Allow microphone" }
        ) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) openAppDetailsSettings()
            else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), microphoneFromSettingsCode)
        }

        addPermissionRow(
            "NOTIFICATIONS",
            "Needed for battery alerts and the visible background wake notification on Android 13+. No notification access is granted by this switch.",
            {
                val allowed = android.os.Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                (if (android.os.Build.VERSION.SDK_INT < 33) "Managed by Android version" else if (allowed) "Allowed" else "Not allowed") to allowed
            },
            { if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) "Allow notifications" else "Manage app notifications" }
        ) {
            if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), notificationsFromSettingsCode)
            else openAppNotificationSettings()
        }

        addPermissionRow(
            "SCREEN READING · APP CONTROL",
            "Not included in this sideload-safe build. Accessibility can expose visible UI text and interact with controls, which is why this capability must be distributed through an appropriate reviewed channel rather than hidden from the install warning.",
            { "Disabled in sideload-safe build" to false },
            { "Why is this unavailable?" }
        ) {
            openAccessibilitySettings()
        }

        addPermissionRow(
            "NOTIFICATION ACCESS · WAKE EVENT",
            "Not included in this sideload-safe build. Notification listeners can technically see private notification content, so this build does not register one.",
            { "Disabled in sideload-safe build" to false },
            { "Why is this unavailable?" }
        ) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Notification Access unavailable")
                .setMessage("Notification-triggered wake is disabled in this sideload-safe APK. Battery alerts and the app's own foreground notification can still work. For a full-access release, use a reviewed distribution path and disclose the permission clearly.")
                .setPositiveButton("OK", null)
                .show()
        }

        addPermissionRow(
            "CAMERA & PHOTOS",
            "Shraddha does not request broad camera or photo-library permission. Camera is opened through the phone's camera app; gallery and document files are chosen via Android's system picker, so you select the exact item to share.",
            { "No broad camera/storage permission requested" to true },
            { "Open Visual Lens tools" }
        ) { showLensTools() }

        addPermissionRow(
            "CALLS · SMS · WHATSAPP",
            "Shraddha opens the dialer, SMS composer, or Android share sheet. It does not request direct SMS/call-log permission or silently send a message. You review and press Call/Send in the destination app.",
            { "Uses system composer / share sheet" to true },
            { "Open Calls & Messages" }
        ) { showCommunicationTools() }

        val backgroundStatusCard = premiumSection("BACKGROUND WAKE & BATTERY ALERTS", "These are off until you enable them in their own controls. Android may restrict background microphone service and scheduled work based on system/OEM settings.")
        val backgroundStatusText = premiumText("", 11f, Color.rgb(194,183,220), true).apply { setPadding(0, 0, 0, dp(8)) }
        fun refreshBackgroundStatus() {
            val wakeOn = prefs.getBoolean("wake_enabled", false)
            val batteryOn = prefs.getBoolean("battery_alerts_enabled", false)
            val notificationWakeOn = false
            val notificationAccessOn = false
            backgroundStatusText.text = "Wake listening: ${if (wakeOn) "ON" else "OFF"}  ·  Battery alerts: ${if (batteryOn) "ON" else "OFF"}\nNotification wake: Unavailable in sideload-safe build  ·  Notification Access: Not registered"
        }
        backgroundStatusCard.addView(backgroundStatusText)
        refreshers.add { refreshBackgroundStatus() }
        refreshBackgroundStatus()
        backgroundStatusCard.addView(premiumButton("Configure wake activation", Color.rgb(75, 42, 130), Color.WHITE) { showWakeActivationSettings() }, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(7) })
        backgroundStatusCard.addView(premiumButton("Configure battery / notification alerts", Color.rgb(57, 39, 94), Color.WHITE) { showBackgroundAutomation() }, LinearLayout.LayoutParams(-1, dp(46)))
        body.addView(backgroundStatusCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(11) })

        body.addView(premiumButton("Open Android app permissions", Color.rgb(57,39,94), Color.WHITE) { openAppDetailsSettings() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(2) })
        val refresher: () -> Unit = { refreshers.forEach { it() } }
        permissionStatusRefresh = refresher
        val dialog = showPremiumPanel("Permissions & Privacy", "Every sensitive permission stays opt-in", "♙", body, "Refresh status") {
            refresher()
            say("Permission status refreshed. Only permissions you explicitly allow are active.")
        }
        dialog.setOnDismissListener {
            if (permissionStatusRefresh === refresher) permissionStatusRefresh = null
        }
    }

    private fun showFeatureCoverage() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(4), dp(2), dp(8)) }
        val supported = premiumSection("AVAILABLE IN THIS BUILD", "These functions have source-level implementations; actual results still depend on permissions, network, provider/model and the installed Android apps.")
        supported.addView(premiumText("• AI chat connections: OpenAI, Gemini, Claude, Groq, Perplexity and OpenRouter-compatible endpoint\n• Local notes/memory and voice input / Android text-to-speech\n• Camera hand-off, selected photos, approximate color detection\n• PDF (first three pages), DOCX, EPUB, RTF, TXT/MD/CSV/JSON/HTML and selected images\n• Installed-app launcher; cross-app accessibility controls are omitted in this sideload-safe build\n• Optional microphone wake and battery alerts (notification-listener wake omitted in sideload-safe build)\n• Calls/SMS/WhatsApp drafts and file-share chooser\n• Maps search and directions hand-off\n• Shraddha reference-based Home, shared animated workspace headers and grouped Settings\n• 3D glass orb, waveform, starfield, screen transitions and user-controlled reduce-motion setting\n• Hindi, English, Hinglish settings, 12 named voice choices, 11 orb styles", 11f, Color.rgb(194,183,220)))
        body.addView(supported, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val limited = premiumSection("LIMITED / REQUIRES EXTERNAL APP OR PROVIDER", "Some options are hand-offs, not full in-app automation.")
        limited.addView(premiumText("• Web search opens a browser; sourced in-app web retrieval is not implemented.\n• Document reader extracts text from DOCX/EPUB/RTF/TXT/MD/CSV/JSON/HTML; scanned pages still need image/vision analysis, and PDF preview is limited to three pages per request.\n• Named Gemini voices require a compatible Gemini speech API/model; otherwise local TTS is used.\n• Booking/orders open a search or destination app; you finish review, login, payment and confirmation there.\n• Accessibility varies by target app; custom-drawn/protected screens may not expose text/controls.", 11f, Color.rgb(194,183,220)))
        body.addView(limited, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        val missing = premiumSection("NOT YET IMPLEMENTED AS NATIVE FEATURES", "These should not be presented as completed just because a shortcut or prompt exists.")
        missing.addView(premiumText("• In-app image generation and image editing pipeline\n• General file-manager operations (move, rename, bulk management)\n• Universal media playback control and full in-app live-traffic/navigation stack\n• Automatic SIM-call answering with two-way AI call audio\n• Silent SMS/WhatsApp sending, unattended purchase/booking/payment\n• Force-stopping arbitrary third-party apps or automatically repairing their code\n• Snap Vanish effect injected into other apps' video calls\n• Full child/parent control suite", 11f, Color.rgb(255,196,120)))
        body.addView(missing)
        body.addView(premiumButton("Open feature settings", Color.rgb(75,42,130), Color.WHITE) { showPermissionsSettings() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
        showPremiumPanel("Feature Coverage", "Source-code audit of included and incomplete features", "◈", body)
    }

    private fun showWakeActivationSettings() {
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(4)) }
        val currentMode = prefs.getString("wake_mode", "phrase") ?: "phrase"
        val triggerCard = premiumSection("CHOOSE YOUR WAKE TRIGGER", "Choose one microphone mode. Both use a persistent Android foreground-service notification while listening.")
        val phrase = RadioButton(this).apply { text = "Hey Shraddha — listen for the wake phrase"; setTextColor(Color.rgb(243, 236, 255)); isChecked = currentMode == "phrase"; buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(183, 112, 255)) }
        val clap = RadioButton(this).apply { text = "Double clap — listen for two claps"; setTextColor(Color.rgb(243, 236, 255)); isChecked = currentMode == "clap"; buttonTintList = android.content.res.ColorStateList.valueOf(Color.rgb(183, 112, 255)) }
        val group = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL; addView(phrase); addView(clap) }
        triggerCard.addView(group)
        body.addView(triggerCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        val privacyCard = premiumSection("HOW IT WORKS", "Android requires microphone permission and keeps a visible notification while background listening is active.")
        privacyCard.addView(premiumText("""• Wake listening stops when you disable it, force-stop the app, or revoke microphone access.
• 'Hey Shraddha' uses Android's speech recognition service. Depending on the phone, its recognizer may rely on an installed/online recognition provider.
• Double-clap detection can misfire around loud sharp sounds. Adjust the environment if you choose clap mode.
• Android may show a wake notification instead of automatically opening the app. Tap it to start the conversation; this avoids relying on a blocked background app launch.""", 11f, Color.rgb(190, 180, 216)))
        body.addView(privacyCard)
        val panel = showPremiumPanel("Wake Activation", "Voice activation and tactile 3D UI preferences", "◉", body, "Enable wake listening") {
            val mode = if (clap.isChecked) "clap" else "phrase"
            prefs.edit().putString("wake_mode", mode).putBoolean("wake_start_pending", true).apply()
            requestWakePermissionsAndStart(mode)
        }
        // Keep an obvious stop control inside the body; pressing it stops the persistent service.
        val stopButton = premiumButton("■  Stop background wake", Color.rgb(63, 34, 77), Color.WHITE) { stopWakeService(); say("Background wake listening stopped.") }
        body.addView(stopButton, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(12) })
    }

    private fun requestWakePermissionsAndStart(mode: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("wake_enabled", false).putBoolean("wake_start_pending", false).apply()
            say("Pehle Settings > Permissions & Privacy mein microphone permission allow karein.")
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("wake_enabled", false).putBoolean("wake_start_pending", false).apply()
            say("Pehle Settings > Permissions & Privacy mein notification permission allow karein.")
            return
        }
        startWakeService(mode)
    }

    private fun startWakeService(mode: String) {
        try {
            prefs.edit().putString("wake_mode", mode).putBoolean("wake_enabled", true).putBoolean("wake_start_pending", false).apply()
            val serviceIntent = Intent(this, WakeActivationService::class.java).apply {
                action = WakeActivationService.ACTION_START
                putExtra(WakeActivationService.EXTRA_MODE, mode)
            }
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
            say(if (mode == "clap") "Double-clap wake mode is on. Android's notification will stay visible while it listens." else "Hey Shraddha wake mode is on. Android's notification will stay visible while it listens.")
        } catch (e: Exception) {
            prefs.edit().putBoolean("wake_enabled", false).putBoolean("wake_start_pending", false).apply()
            say("Wake mode couldn't start: ${e.message ?: "check microphone and notification permissions"}")
        }
    }

    private fun stopWakeService() {
        prefs.edit().putBoolean("wake_enabled", false).putBoolean("wake_start_pending", false).apply()
        try { startService(Intent(this, WakeActivationService::class.java).setAction(WakeActivationService.ACTION_STOP)) } catch (_: Exception) {}
    }

    private fun openUrl(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { say("Could not open URL.") } }
    private fun saveNote(note: String) {
        if (note.isBlank()) { say("पहले note लिखें।"); return }
        val old = prefs.getString("notes", "").orEmpty()
        prefs.edit().putString("notes", (old + "\n• " + note).trim()).apply()
        say("Note saved.")
    }
    private fun showNotes() { say(prefs.getString("notes", "अभी कोई notes नहीं हैं।").orEmpty()) }
    override fun onDestroy() {
        wakeMode = false
        handler.removeCallbacksAndMessages(null)
        try { speechRecognizer?.cancel(); speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
        speechInProgress = false
        if (::tts.isInitialized) tts.shutdown()
        try { remoteAudioPlayer?.release() } catch (_: Exception) {}
        remoteAudioPlayer = null
        homeOrb = null; navOrb = null
        super.onDestroy()
    }
}

/** Subtle animated starfield behind every Shraddha screen; decorative only and low contrast for readability. */
private class ShraddhaStarfieldView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
        duration = 7_200L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }
    fun refreshMotionPreference() {
        if (!isAttachedToWindow) { invalidate(); return }
        val motionEnabled = !context.getSharedPreferences("assistant", Context.MODE_PRIVATE).getBoolean("reduce_motion", false)
        if (motionEnabled && !animator.isStarted) animator.start()
        else if (!motionEnabled && animator.isStarted) animator.cancel()
        invalidate()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); refreshMotionPreference() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(w * .15f, h * .2f, w * .7f,
            intArrayOf(Color.argb(16, 128, 70, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        paint.shader = RadialGradient(w * .92f, h * .72f, w * .6f,
            intArrayOf(Color.argb(12, 52, 94, 255), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        for (i in 0 until 46) {
            val x = ((i * 73 + 17) % 101) / 100f * w
            val y = ((i * 47 + 11) % 103) / 102f * h
            val twinkle = (0.5 + 0.5 * kotlin.math.sin(phase + i * 1.71)).toFloat()
            val radius = dpSafe(0.75f + (i % 3) * .45f)
            paint.color = if (i % 4 == 0) Color.argb((20 + twinkle * 70).toInt(), 211, 160, 255)
                else Color.argb((9 + twinkle * 38).toInt(), 145, 134, 214)
            canvas.drawCircle(x, y, radius, paint)
        }
    }
    private fun dpSafe(value: Float): Float = value * resources.displayMetrics.density
}

/** Drawn Shraddha-style orb: radial-lit core, rotating neon rings, specular highlight and state glyph. */
private class ShraddhaOrbView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var currentState = "idle"
    private var currentStyle = "Solar"
    private var rotationDegrees = 0f
    private val ringAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 10_000L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { rotationDegrees = it.animatedValue as Float; invalidate() }
    }
    private fun colors(style: String): IntArray = when (style) {
        "Classic" -> intArrayOf(Color.rgb(60, 177, 255), Color.rgb(108, 74, 245), Color.rgb(24, 21, 69))
        "Galaxy" -> intArrayOf(Color.rgb(223, 78, 255), Color.rgb(81, 39, 238), Color.rgb(20, 10, 48))
        "Lawa" -> intArrayOf(Color.rgb(255, 148, 85), Color.rgb(227, 38, 90), Color.rgb(60, 8, 39))
        "Ocean" -> intArrayOf(Color.rgb(84, 238, 244), Color.rgb(23, 119, 232), Color.rgb(5, 26, 75))
        "Aurora" -> intArrayOf(Color.rgb(80, 255, 193), Color.rgb(120, 76, 255), Color.rgb(17, 20, 61))
        "Glass" -> intArrayOf(Color.rgb(226, 248, 255), Color.rgb(134, 124, 255), Color.rgb(30, 26, 72))
        "Plasma" -> intArrayOf(Color.rgb(255, 73, 255), Color.rgb(82, 81, 255), Color.rgb(25, 12, 73))
        "Pulse" -> intArrayOf(Color.rgb(255, 104, 178), Color.rgb(153, 54, 255), Color.rgb(36, 13, 62))
        "Minimal" -> intArrayOf(Color.rgb(180, 191, 219), Color.rgb(91, 101, 146), Color.rgb(22, 26, 47))
        "Archer" -> intArrayOf(Color.rgb(104, 255, 168), Color.rgb(30, 181, 148), Color.rgb(8, 46, 59))
        else -> intArrayOf(Color.rgb(255, 214, 112), Color.rgb(255, 114, 48), Color.rgb(71, 18, 29))
    }
    fun setAppearance(state: String, style: String) {
        currentState = state
        currentStyle = style
        ringAnimator.duration = when (state) { "listening" -> 1_650L; "speaking" -> 2_800L; else -> if (style == "Minimal") 16_000L else 10_000L }
        refreshMotionPreference()
        invalidate()
    }
    fun refreshMotionPreference() {
        if (!isAttachedToWindow) { invalidate(); return }
        val motionEnabled = !context.getSharedPreferences("assistant", Context.MODE_PRIVATE).getBoolean("reduce_motion", false)
        if (motionEnabled && !ringAnimator.isStarted) ringAnimator.start()
        else if (!motionEnabled && ringAnimator.isStarted) ringAnimator.cancel()
        invalidate()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshMotionPreference()
    }
    override fun onDetachedFromWindow() {
        ringAnimator.cancel()
        super.onDetachedFromWindow()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f; val cy = height / 2f
        val r = size * 0.405f
        val palette = colors(currentStyle)
        // Diffused glow behind the glass orb.
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx, cy, size * 0.49f,
            intArrayOf(Color.argb(if (currentState == "listening") 190 else 142, Color.red(palette[0]), Color.green(palette[0]), Color.blue(palette[0])), Color.argb(45, Color.red(palette[1]), Color.green(palette[1]), Color.blue(palette[1])), Color.TRANSPARENT),
            floatArrayOf(0f, 0.67f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, size * 0.49f, paint)
        paint.shader = null
        // Outer glass shell.
        paint.color = Color.rgb(16, 12, 37)
        canvas.drawCircle(cx, cy, r * 1.04f, paint)
        // Rotating rainbow/neon perimeter.
        canvas.save()
        canvas.rotate(rotationDegrees, cx, cy)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1.7f, size * 0.042f)
        paint.shader = SweepGradient(cx, cy,
            intArrayOf(palette[0], Color.WHITE, palette[1], Color.argb(70, Color.red(palette[0]), Color.green(palette[0]), Color.blue(palette[0])), palette[0]), null)
        canvas.drawCircle(cx, cy, r * 0.99f, paint)
        canvas.restore()
        paint.shader = null
        // Inner sphere with a reflective upper-left light source.
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(cx - r * 0.29f, cy - r * 0.39f, r * 1.42f,
            intArrayOf(Color.WHITE, palette[0], palette[1], palette[2]), floatArrayOf(0f, 0.16f, 0.6f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 0.82f, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = maxOf(1.1f, size * 0.014f)
        paint.color = Color.argb(148, 236, 216, 255)
        canvas.drawCircle(cx, cy, r * 0.82f, paint)
        paint.style = Paint.Style.FILL
        // Small specular crescent for a glass/3D appearance.
        paint.color = Color.argb(100, 255, 255, 255)
        canvas.drawOval(RectF(cx - r * 0.45f, cy - r * 0.59f, cx + r * 0.08f, cy - r * 0.37f), paint)
        // Center glyph switches with state: microphone, listening rings, or speech waveform.
        paint.shader = null
        paint.color = Color.WHITE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        when (currentState) {
            "listening" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = size * 0.026f
                val arc = RectF(cx - r * 0.32f, cy - r * 0.32f, cx + r * 0.32f, cy + r * 0.32f)
                canvas.drawArc(arc, 205f, 130f, false, paint)
                canvas.drawArc(RectF(cx - r * 0.47f, cy - r * 0.47f, cx + r * 0.47f, cy + r * 0.47f), 213f, 114f, false, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, r * 0.11f, paint)
            }
            "speaking" -> {
                paint.style = Paint.Style.FILL
                val heights = floatArrayOf(.20f, .34f, .48f, .29f, .42f, .24f, .36f)
                val barW = size * .035f; val gap = size * .035f
                val total = heights.size * barW + (heights.size - 1) * gap
                var x = cx - total / 2f
                heights.forEachIndexed { index, h ->
                    val hh = size * h * (if (index == 3) 1.08f else 1f)
                    canvas.drawRoundRect(RectF(x, cy - hh / 2f, x + barW, cy + hh / 2f), barW, barW, paint)
                    x += barW + gap
                }
            }
            else -> {
                paint.style = Paint.Style.FILL
                val mic = RectF(cx - r * .105f, cy - r * .36f, cx + r * .105f, cy + r * .12f)
                canvas.drawRoundRect(mic, r * .11f, r * .11f, paint)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = size * .032f
                canvas.drawArc(RectF(cx - r * .27f, cy - r * .20f, cx + r * .27f, cy + r * .33f), 0f, 180f, false, paint)
                canvas.drawLine(cx, cy + r * .32f, cx, cy + r * .51f, paint)
                canvas.drawLine(cx - r * .16f, cy + r * .51f, cx + r * .16f, cy + r * .51f, paint)
            }
        }
        paint.shader = null
    }
}

/** Animated, restrained audio waveform used underneath the central voice orb. */
private class ShraddhaWaveformView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
        duration = 950L
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }
    fun refreshMotionPreference() {
        if (!isAttachedToWindow) { invalidate(); return }
        val motionEnabled = !context.getSharedPreferences("assistant", Context.MODE_PRIVATE).getBoolean("reduce_motion", false)
        if (motionEnabled && !animator.isStarted) animator.start()
        else if (!motionEnabled && animator.isStarted) animator.cancel()
        invalidate()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); refreshMotionPreference() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = 31
        val step = width.toFloat() / count
        val cx = height / 2f
        for (i in 0 until count) {
            val wave = kotlin.math.abs(kotlin.math.sin(phase + i * 0.44f)).toFloat()
            val centerBoost = 1f - kotlin.math.abs((i - (count - 1) / 2f) / ((count - 1) / 2f)) * 0.5f
            val barH = maxOf(2f, height * (0.15f + 0.72f * wave * centerBoost))
            paint.color = if (i % 3 == 0) Color.rgb(225, 153, 255) else Color.rgb(145, 78, 245)
            paint.alpha = (120 + wave * 135).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(RectF(i * step + step * .27f, cx - barH / 2f, i * step + step * .72f, cx + barH / 2f), step, step, paint)
        }
    }
}

object AlertDialogCompat {
    fun show(activity: Activity, title: String, content: android.view.View, save: () -> Unit) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(title).setView(content)
            .setPositiveButton("Save") { _, _ -> save() }.setNegativeButton("Cancel", null).show()
    }
}
