package com.shraddha.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Base64
import java.io.ByteArrayOutputStream
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
    private var wakeMode = false
    private var awaitingWakeCommand = false
    private lateinit var wakeButton: Button
    private var speechInProgress = false
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("assistant", MODE_PRIVATE)
        tts = TextToSpeech(this, this)

        val ink = Color.rgb(248, 245, 255)
        val muted = Color.rgb(173, 161, 205)
        val violet = Color.rgb(183, 112, 255)
        val bg = Color.rgb(7, 7, 20)
        val panel = Color.rgb(20, 17, 42)
        val panel2 = Color.rgb(31, 22, 61)
        val neon = Color.rgb(125, 68, 255)

        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val pageHost = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(7), dp(8), dp(7), dp(10))
            background = rounded(intArrayOf(Color.rgb(15, 13, 32), Color.rgb(15, 13, 32)), 22)
        }
        shell.addView(pageHost)
        shell.addView(nav, LinearLayout.LayoutParams(-1, dp(72)).apply {
            leftMargin = dp(12); rightMargin = dp(12); bottomMargin = dp(8)
        })

        fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
            TextView(this).apply {
                this.text = text; textSize = size; setTextColor(color)
                if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }

        fun makePage(title: String, subtitle: String): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(24))
            addView(label(title, 24f, ink, true))
            addView(label(subtitle, 13f, muted).apply { setPadding(0, dp(5), 0, dp(16)) })
        }

        fun actionCard(title: String, subtitle: String, icon: String, click: () -> Unit): LinearLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(15), dp(14), dp(14), dp(14))
                background = rounded(intArrayOf(panel2, panel), 18)
                isClickable = true; isFocusable = true; setOnClickListener { click() }
                addView(TextView(this@MainActivity).apply {
                    text = icon; textSize = 23f; gravity = Gravity.CENTER
                    setTextColor(violet); background = rounded(intArrayOf(panel, panel), 14)
                }, LinearLayout.LayoutParams(dp(48), dp(48)))
                val texts = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0)
                    addView(label(title, 15f, ink, true))
                    addView(label(subtitle, 12f, muted).apply { setPadding(0, dp(4), 0, 0) })
                }
                addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
                addView(label("›", 27f, violet))
            }

        fun showPage(page: String) {
            pageHost.removeAllViews()
            val scrollContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 0, 0, dp(12))
            }
            val scroller = ScrollView(this).apply { isFillViewport = true; addView(scrollContent) }
            pageHost.addView(scroller, FrameLayout.LayoutParams(-1, -1))

            when (page) {
                "Home" -> {
                    val top = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(22), dp(16), dp(22), dp(12))
                    }
                    top.addView(ImageView(this).apply {
                        setImageResource(R.drawable.shraddha_logo); scaleType = ImageView.ScaleType.CENTER_CROP
                        background = rounded(intArrayOf(panel, panel), 12); clipToOutline = true
                    }, LinearLayout.LayoutParams(dp(43), dp(43)))
                    val brand = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0)
                        addView(label("SHRADDHA AI", 17f, ink, true))
                        addView(label("YOUR AI COMPANION", 9f, muted))
                    }
                    top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
                    top.addView(TextView(this).apply {
                        text = "⚙"; textSize = 23f; setTextColor(violet)
                        setOnClickListener { showPage("Settings") }
                    })
                    scrollContent.addView(top)

                    scrollContent.addView(label("Hello, I'm Shraddha", 27f, ink, true).apply {
                        gravity = Gravity.CENTER; setPadding(dp(8), dp(10), dp(8), 0)
                    })
                    scrollContent.addView(label("Your personal AI, ready when you are.", 13f, muted).apply {
                        gravity = Gravity.CENTER; setPadding(dp(8), dp(5), dp(8), dp(8))
                    })

                    val art = ImageView(this).apply {
                        setImageResource(R.drawable.shraddha_logo)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        contentDescription = "Shraddha AI character artwork"
                    }
                    scrollContent.addView(art, LinearLayout.LayoutParams(-1, dp(235)).apply {
                        topMargin = dp(3); bottomMargin = dp(3)
                    })

                    val orbWrap = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                        setPadding(0, dp(2), 0, dp(9))
                    }
                    val orb = TextView(this).apply {
                        text = "✦"; textSize = 39f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
                        background = rounded(intArrayOf(Color.rgb(199, 117, 255), Color.rgb(93, 51, 245)), 100)
                        elevation = dp(12).toFloat()
                        setOnClickListener { startSpeech() }
                    }
                    orbWrap.addView(orb, LinearLayout.LayoutParams(dp(94), dp(94)))
                    orbWrap.addView(label("TAP TO TALK", 11f, violet, true).apply {
                        gravity = Gravity.CENTER; setPadding(0, dp(9), 0, 0)
                    })
                    scrollContent.addView(orbWrap)

                    input = EditText(this).apply {
                        hint = "Message Shraddha…"; textSize = 14f; setSingleLine(true)
                        setTextColor(ink); setHintTextColor(Color.rgb(133, 122, 164))
                        setPadding(dp(16), dp(14), dp(16), dp(14))
                        background = rounded(intArrayOf(panel, panel), 24)
                    }
                    scrollContent.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
                        addView(TextView(this@MainActivity).apply {
                            text = "➤"; textSize = 22f; gravity = Gravity.CENTER
                            setTextColor(Color.WHITE); background = rounded(intArrayOf(neon, Color.rgb(87, 48, 218)), 30)
                            setOnClickListener { askAI(input.text.toString()) }
                        }, LinearLayout.LayoutParams(dp(52), dp(52)).apply { leftMargin = dp(8) })
                    }, LinearLayout.LayoutParams(-1, -2).apply {
                        leftMargin = dp(20); rightMargin = dp(20); topMargin = dp(8)
                    })

                    scrollContent.addView(label("Quick actions", 18f, ink, true).apply {
                        setPadding(dp(22), dp(22), dp(22), dp(10))
                    })
                    val quick = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), 0, dp(16), 0)
                    }
                    fun quickCard(title: String, emoji: String, click: () -> Unit) {
                        val c = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                            setPadding(dp(8), dp(13), dp(8), dp(13))
                            background = rounded(intArrayOf(panel2, panel), 16)
                            isClickable = true; setOnClickListener { click() }
                            addView(label(emoji, 23f, violet).apply { gravity = Gravity.CENTER })
                            addView(label(title, 12f, ink, true).apply {
                                gravity = Gravity.CENTER; setPadding(0, dp(7), 0, 0)
                            })
                        }
                        quick.addView(c, LinearLayout.LayoutParams(0, dp(86), 1f).apply {
                            leftMargin = dp(4); rightMargin = dp(4)
                        })
                    }
                    quickCard("Chat", "✧") { showPage("Chat") }
                    quickCard("Create", "✦") { showPage("Create") }
                    quickCard("Write", "✎") { showPage("Write") }
                    scrollContent.addView(quick)
                    scrollContent.addView(label("VOICE ASSISTANT", 10f, violet, true).apply {
                        setPadding(dp(22), dp(16), dp(22), dp(4))
                    })
                    output = label("Ready when you are. Tap the orb to speak.", 13f, muted).apply {
                        setPadding(dp(22), 0, dp(22), dp(12))
                    }
                    scrollContent.addView(output)
                }
                "Chat" -> {
                    scrollContent.addView(makePage("AI Chat", "Ask anything in Hindi or English"))
                    input = EditText(this).apply {
                        hint = "Type your message…"; setTextColor(ink); setHintTextColor(muted)
                        setPadding(dp(15), dp(14), dp(15), dp(14)); background = rounded(intArrayOf(panel, panel), 16)
                    }
                    scrollContent.addView(input, LinearLayout.LayoutParams(-1, -2).apply {
                        leftMargin = dp(20); rightMargin = dp(20)
                    })
                    scrollContent.addView(premiumButton("Send to Shraddha  ↗", neon, Color.WHITE) {
                        askAI(input.text.toString())
                    }, LinearLayout.LayoutParams(-1, dp(52)).apply {
                        leftMargin = dp(20); rightMargin = dp(20); topMargin = dp(10)
                    })
                    scrollContent.addView(label("Recent response", 16f, ink, true).apply {
                        setPadding(dp(22), dp(22), dp(22), dp(8))
                    })
                    scrollContent.addView(label(output.text.toString(), 13f, muted).apply {
                        setPadding(dp(22), 0, dp(22), dp(12))
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
                    input = EditText(this).apply {
                        hint = "What should I write for you?"; minLines = 4; gravity = Gravity.TOP
                        setTextColor(ink); setHintTextColor(muted); setPadding(dp(15), dp(14), dp(15), dp(14))
                        background = rounded(intArrayOf(panel, panel), 16)
                    }
                    scrollContent.addView(input, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(20); rightMargin = dp(20) })
                    scrollContent.addView(premiumButton("Create draft  ✦", neon, Color.WHITE) {
                        askAI("Write this for me: " + input.text.toString())
                    }, LinearLayout.LayoutParams(-1, dp(52)).apply { leftMargin = dp(20); rightMargin = dp(20); topMargin = dp(12) })
                }
                "History" -> {
                    scrollContent.addView(makePage("History", "Your recent activity and saved notes"))
                    scrollContent.addView(actionCard("Saved notes", "Open notes you've saved", "▤") { showNotes() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
                    scrollContent.addView(actionCard("AI memory", "Review your saved assistant memory", "♥") { showMemory() })
                }
                "Settings" -> {
                    scrollContent.addView(makePage("Settings", "Personalize Shraddha AI"))
                    scrollContent.addView(actionCard("AI Providers", "Configure available AI connections", "✧") { showSettings() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
                    scrollContent.addView(actionCard("Voice & personality", "Choose assistant voice and style", "♫") { showPersonaSettings() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
                    scrollContent.addView(actionCard("Phone tools", "Automation, communication and clipboard", "▣") { showPhoneToolsPage() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
                    scrollContent.addView(actionCard("Memory & notes", "Manage notes and AI memory", "♥") { showMemory() }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
                }
            }
        }

        val navItems = listOf(
            Triple("Home", "⌂", "Home"),
            Triple("Chat", "◌", "Chat"),
            Triple("Voice", "✦", "Voice"),
            Triple("History", "◷", "History"),
            Triple("Settings", "⚙", "Settings")
        )
        navItems.forEach { item ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                isClickable = true; isFocusable = true
                addView(TextView(this@MainActivity).apply {
                    text = item.second; textSize = if (item.first == "Voice") 24f else 20f
                    gravity = Gravity.CENTER; setTextColor(if (item.first == "Voice") Color.WHITE else muted)
                    if (item.first == "Voice") background = rounded(intArrayOf(Color.rgb(190, 103, 255), neon), 40)
                }, LinearLayout.LayoutParams(if (item.first == "Voice") dp(46) else -1, if (item.first == "Voice") dp(42) else dp(31)))
                addView(TextView(this@MainActivity).apply {
                    text = item.first; textSize = 9f; gravity = Gravity.CENTER
                    setTextColor(if (item.first == "Home") violet else muted)
                })
                setOnClickListener {
                    if (item.first == "Voice") startSpeech() else showPage(item.third)
                }
            }
            nav.addView(cell, LinearLayout.LayoutParams(0, -1, 1f))
        }
        showPage("Home")
        setContentView(shell)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        }
    }

    private fun showPhoneToolsPage() {
        AlertDialog.Builder(this)
            .setTitle("Phone Tools")
            .setItems(arrayOf("Automation rules", "Calls, SMS & WhatsApp", "Clipboard AI")) { _, which ->
                when (which) {
                    0 -> showAutomation()
                    1 -> showCommunicationTools()
                    2 -> summarizeClipboard()
                }
            }.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(colors: IntArray, radius: Int): GradientDrawable = GradientDrawable(
        if (colors.size > 1) GradientDrawable.Orientation.TL_BR else GradientDrawable.Orientation.LEFT_RIGHT,
        colors
    ).apply { cornerRadius = dp(radius).toFloat() }

    private fun premiumButton(label: String, color: Int, textColor: Int, action: () -> Unit): Button = Button(this).apply {
        text = label; setTextColor(textColor); textSize = 13f; isAllCaps = false
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        background = rounded(intArrayOf(color, color), 16)
        stateListAnimator = null; elevation = dp(2).toFloat(); setOnClickListener { action() }
    }

    private fun premiumTool(label: String, textColor: Int, action: () -> Unit): TextView = TextView(this).apply {
        text = label; textSize = 14f; setTextColor(textColor); gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), 0, dp(14), 0); background = rounded(intArrayOf(Color.rgb(29, 21, 51), Color.rgb(29, 21, 51)), 15)
        setOnClickListener { action() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            say("Microphone permission मिल गई। सुनने के लिए Tap to listen दबाएँ।")
        } else if (requestCode == 10) {
            say("माइक्रोफ़ोन अनुमति के बिना voice command चालू नहीं हो सकती। आप बाद में अनुमति देकर इसे शुरू कर सकते हैं।")
        }
    }
    private fun btn(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }
    override fun onInit(status: Int) { ttsReady = status == TextToSpeech.SUCCESS; if (ttsReady) { tts.language = Locale("hi", "IN"); tts.setPitch(0.98f) } }
    private fun say(text: String) {
        output.text = text
        if (ttsReady) {
            val speed = prefs.getInt("speech_speed", 100).coerceIn(60, 160) / 100f
            val volume = prefs.getInt("voice_volume", 100).coerceIn(0, 100) / 100f
            tts.setSpeechRate(speed)
            val bundle = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, bundle, "assistant-reply")
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
        return "You are Shraddha, a Hindi-English AI voice assistant. $persona $stylePrompt Emotional expression preference: $emotion. $emotionalDirection Recognize the user's emotional cues cautiously from their words; ask rather than assume when uncertain. Be empathetic, but be honest that you are an AI and these are expressive communication styles, not literal human feelings. Follow the user's language. Relevant user-provided memory: $memory. Recent Visual Lens observations (locally saved): ${prefs.getString("lens_memory", "").orEmpty()}. Treat observations as time-specific, not guaranteed current facts. Do not claim to perform actions unless they were actually completed. For risky or external actions, ask for confirmation."
    }

    private fun showPersonaSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 8, 18, 8) }
        val personaOptions = arrayOf("Normal", "Girlfriend", "Wife", "Teacher", "Friendly", "Funny", "Companion", "Developer", "Hungry", "Nautanki", "Custom")
        val styleOptions = arrayOf("Balanced", "Short", "Detailed", "Bullet points", "Conversational")
        val emotionOptions = arrayOf("Natural", "Khush 😊", "Pyaar se 💗", "Shaant 😌", "Joshila 😄", "Dukhi 😢", "Gussa 😠", "Rona / emotional 😭", "Nautanki 🎭", "Motivational 💪")
        val emotion = Spinner(this).apply { adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, emotionOptions); setSelection(emotionOptions.indexOf(prefs.getString("emotion_style", "Natural")).coerceAtLeast(0)) }
        val persona = Spinner(this).apply { adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, personaOptions); setSelection(personaOptions.indexOf(prefs.getString("persona", "Normal")).coerceAtLeast(0)) }
        val style = Spinner(this).apply { adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, styleOptions); setSelection(styleOptions.indexOf(prefs.getString("response_style", "Balanced")).coerceAtLeast(0)) }
        val custom = EditText(this).apply { hint = "Custom AI personality / instructions"; setText(prefs.getString("custom_persona", "")); minLines = 2 }
        val speedLabel = TextView(this).apply { text = "Speech speed: ${prefs.getInt("speech_speed", 100)}%" }
        val speed = SeekBar(this).apply { max = 100; progress = (prefs.getInt("speech_speed", 100) - 60).coerceIn(0, 100); setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { speedLabel.text = "Speech speed: ${progress + 60}%" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }) }
        val volumeLabel = TextView(this).apply { text = "Voice volume: ${prefs.getInt("voice_volume", 100)}%" }
        val volume = SeekBar(this).apply { max = 100; progress = prefs.getInt("voice_volume", 100).coerceIn(0, 100); setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { volumeLabel.text = "Voice volume: $progress%" }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }) }
        box.addView(TextView(this).apply { text = "AI mode" }); box.addView(persona)
        box.addView(TextView(this).apply { text = "Response style" }); box.addView(style)
        box.addView(TextView(this).apply { text = "Emotion / expression (AI acting style)" }); box.addView(emotion)
        box.addView(custom); box.addView(speedLabel); box.addView(speed); box.addView(volumeLabel); box.addView(volume)
        AlertDialogCompat.show(this, "Personality & Voice", box) {
            prefs.edit().putString("persona", persona.selectedItem.toString())
                .putString("response_style", style.selectedItem.toString())
                .putString("emotion_style", emotion.selectedItem.toString())
                .putString("custom_persona", custom.text.toString())
                .putInt("speech_speed", speed.progress + 60)
                .putInt("voice_volume", volume.progress).apply()
            say("AI personality and voice settings saved.")
        }
    }

    private fun showAutomation() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 8, 18, 8) }
        val info = TextView(this).apply { text = "Enable simple command shortcuts. Actions run only when you invoke them; external or sensitive actions require confirmation." }
        val autoSearch = CheckBox(this).apply { text = "Route spoken 'search …' commands to web search"; isChecked = prefs.getBoolean("auto_search", true) }
        val autoNotes = CheckBox(this).apply { text = "Save spoken commands starting with 'note' as notes"; isChecked = prefs.getBoolean("auto_notes", true) }
        box.addView(info); box.addView(autoSearch); box.addView(autoNotes)
        AlertDialogCompat.show(this, "Automation Rules", box) {
            prefs.edit().putBoolean("auto_search", autoSearch.isChecked)
                .putBoolean("auto_notes", autoNotes.isChecked).apply()
            say("Android automation preferences saved.")
        }
    }
    private fun startSpeech() {
        if (speechInProgress) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
            return
        }
        startInAppRecognition(isVoiceMode = false)
    }

    private fun startInAppRecognition(isVoiceMode: Boolean) {
        if (speechInProgress || isFinishing || isDestroyed) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            output.text = "Is phone par speech recognition service available nahi hai."
            wakeMode = false
            wakeButton.text = "🎙 Start listening"
            return
        }
        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechInProgress = true
            if (isVoiceMode) {
                wakeButton.text = "🎙 Listening — tap to stop"
                output.text = "Shraddha sun rahi hai… boliye."
            } else output.text = "Sun rahi hoon… boliye."

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { if (isVoiceMode) output.text = "Aapki baat samajh rahi hoon…" }
                override fun onError(error: Int) {
                    speechInProgress = false
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                    if (isVoiceMode) {
                        wakeMode = false
                        wakeButton.text = "🎙 Start listening"
                    }
                    output.text = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Awaaz samajh nahi aayi. Dobara mic dabayein."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Awaaz nahi mili. Dobara mic dabayein."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission allow karein."
                        else -> "Voice recognition ruk gayi. Dobara mic dabayein."
                    }
                }
                override fun onResults(results: Bundle?) {
                    speechInProgress = false
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                    if (isVoiceMode) {
                        wakeMode = false
                        wakeButton.text = "🎙 Start listening"
                    }
                    if (spoken.isNotBlank()) {
                        input.setText(spoken)
                        output.text = "Aapne kaha: $spoken"
                        routeCommand(spoken)
                    } else output.text = "Kuch sunai nahi diya. Dobara mic dabayein."
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            speechInProgress = false
            speechRecognizer?.destroy()
            speechRecognizer = null
            wakeMode = false
            wakeButton.text = "🎙 Start listening"
            output.text = "Voice start nahi ho payi. Dobara try karein."
        }
    }

    @Deprecated("Activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == lensCode) {
            if (resultCode == RESULT_OK) {
                val bitmap = data?.extras?.get("data") as? Bitmap
                if (bitmap == null) say("फोटो नहीं मिली। दोबारा Visual Lens खोलकर कोशिश करें。") else analyzeLensPhoto(bitmap)
            } else say("फोटो नहीं ली गई। Visual Lens बंद है।")
        }
    }

    private fun toggleWakeMode() {
        if (wakeMode || speechInProgress) {
            wakeMode = false
            speechInProgress = false
            awaitingWakeCommand = false
            try { speechRecognizer?.cancel(); speechRecognizer?.destroy() } catch (_: Exception) {}
            speechRecognizer = null
            wakeButton.text = "🎙 Start listening"
            output.text = "Listening band hai."
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
            return
        }
        wakeMode = true
        startInAppRecognition(isVoiceMode = true)
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
            else -> askAI(text)
        }
    }
    private fun askAI(prompt: String) {
        if (prompt.isBlank()) { say("पहले अपना सवाल लिखें या बोलें।"); return }
        val provider = prefs.getString("provider", "OpenAI") ?: "OpenAI"
        val keys = listOf(
            prefs.getString("api_key_1", "").orEmpty(),
            prefs.getString("api_key_2", "").orEmpty(),
            prefs.getString("api_key_3", "").orEmpty(),
            prefs.getString("api_key", "").orEmpty()
        ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val model = prefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"
        val base = (prefs.getString("base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1").trimEnd('/')
        if (keys.isEmpty()) { say("Settings में पहले AI provider और API key भरें।"); return }
        say("Shraddha सोच रही है…")
        thread {
            var finalAnswer: String? = null
            var lastError = "Unknown API error"
            for ((index, key) in keys.withIndex()) {
                var conn: HttpURLConnection? = null
                try {
                    val endpoint = when (provider) {
                        "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
                        else -> "$base/chat/completions"
                    }
                    conn = URL(endpoint).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"; conn.connectTimeout = 15000; conn.readTimeout = 30000
                    conn.setRequestProperty("Authorization", "Bearer $key")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    val body = JSONObject().put("model", model).put("messages", org.json.JSONArray()
                        .put(JSONObject().put("role", "system").put("content", buildSystemPrompt()))
                        .put(JSONObject().put("role", "user").put("content", prompt))).toString()
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code in 200..299) {
                        val obj = JSONObject(response)
                        finalAnswer = obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                        break
                    } else {
                        lastError = "API error $code: ${try { JSONObject(response).optJSONObject("error")?.optString("message") ?: response } catch (_: Exception) { response }}"
                        // Try the next configured key for authentication, quota, or rate-limit errors.
                        if (code !in listOf(401, 403, 429)) break
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "Network error"
                    if (index == keys.lastIndex) break
                } finally { conn?.disconnect() }
            }
            runOnUiThread { say(finalAnswer ?: "AI connection failed. $lastError") }
        }
    }
    private fun showSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 10, 20, 10) }
        val provider = EditText(this).apply { hint = "Provider: OpenAI / Groq / Compatible"; setText(prefs.getString("provider", "OpenAI")) }
        val key1 = EditText(this).apply { hint = "API Key 1 (primary)"; setText(prefs.getString("api_key_1", prefs.getString("api_key", ""))); inputType = 129 }
        val key2 = EditText(this).apply { hint = "API Key 2 (backup if key 1 fails)"; setText(prefs.getString("api_key_2", "")); inputType = 129 }
        val key3 = EditText(this).apply { hint = "API Key 3 (backup if keys 1–2 fail)"; setText(prefs.getString("api_key_3", "")); inputType = 129 }
        val model = EditText(this).apply { hint = "Model"; setText(prefs.getString("model", "gpt-4o-mini")) }
        val base = EditText(this).apply { hint = "Base URL (OpenAI-compatible)"; setText(prefs.getString("base_url", "https://api.openai.com/v1")) }
        listOf(provider, key1, key2, key3, model, base).forEach { box.addView(it) }
        AlertDialogCompat.show(this, "AI Provider & API Keys", box) {
            prefs.edit().putString("provider", provider.text.toString().trim())
                .putString("api_key_1", key1.text.toString().trim())
                .putString("api_key_2", key2.text.toString().trim())
                .putString("api_key_3", key3.text.toString().trim())
                .putString("api_key", key1.text.toString().trim())
                .putString("model", model.text.toString().trim())
                .putString("base_url", base.text.toString().trim()).apply()
            say("Settings saved on this phone. API keys are stored locally.")
        }
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
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 8, 18, 8) }
        val number = EditText(this).apply { hint = "Phone number (opens dialer)"; inputType = 3 }
        val message = EditText(this).apply { hint = "SMS / WhatsApp message"; minLines = 2 }
        val whatsapp = CheckBox(this).apply { text = "Compose WhatsApp message instead of SMS" }
        box.addView(number); box.addView(message); box.addView(whatsapp)
        AlertDialogCompat.show(this, "Calls & Messages", box) {
            if (whatsapp.isChecked) {
                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; setPackage("com.whatsapp"); putExtra(Intent.EXTRA_TEXT, message.text.toString()) }
                try { startActivity(intent) } catch (_: Exception) { say("WhatsApp नहीं मिला; पहले app install करें।") }
            } else if (number.text.isNotBlank() && message.text.isNotBlank()) {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number.text.toString())))
                intent.putExtra("sms_body", message.text.toString())
                try { startActivity(intent) } catch (_: Exception) { say("SMS app नहीं खुल सकी।") }
            } else if (number.text.isNotBlank()) dialNumber(number.text.toString())
            else say("Phone number या message भरें।")
        }
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

    private fun launchCamera() {
        val key = prefs.getString("api_key", "").orEmpty()
        if (key.isBlank()) { say("Visual Lens के लिए पहले Settings में vision-capable AI provider की API key सेट करें।"); return }
        try {
            val intent = Intent("android.media.action.IMAGE_CAPTURE")
            if (intent.resolveActivity(packageManager) == null) { say("इस फोन में camera app उपलब्ध नहीं है।"); return }
            say("कैमरा खोल रही हूँ। आसपास की चीज़ें समझने के लिए फोटो लें। फोटो आपके चुने AI provider को analysis के लिए भेजी जाएगी।")
            startActivityForResult(intent, lensCode)
        } catch (_: Exception) { say("इस फोन में camera app उपलब्ध नहीं है।") }
    }

    private fun analyzeLensPhoto(bitmap: Bitmap) {
        val key = prefs.getString("api_key", "").orEmpty()
        val model = prefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"
        val base = (prefs.getString("base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1").trimEnd('/')
        val provider = prefs.getString("provider", "OpenAI") ?: "OpenAI"
        val endpoint = when (provider) {
            "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
            else -> "$base/chat/completions"
        }
        say("Visual Lens फोटो समझ रही है…")
        thread {
            try {
                val scaled = Bitmap.createScaledBitmap(bitmap, minOf(bitmap.width, 1024), (bitmap.height * (minOf(bitmap.width, 1024).toFloat() / bitmap.width)).toInt().coerceAtLeast(1), true)
                val bytes = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 78, bytes)
                val imageData = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                val content = org.json.JSONArray()
                    .put(JSONObject().put("type", "text").put("text", "You are Shraddha Visual Lens. Describe visible surroundings, objects, text/signs, layout, useful context, and uncertainty in Hindi or Hinglish. Do not guess hidden facts or claim certainty about identities. If people appear, describe only visible non-sensitive scene details; do not identify them. Give a concise answer and mention that this is a single photo, not continuous live vision."))
                    .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$imageData")))
                val messages = org.json.JSONArray()
                    .put(JSONObject().put("role", "system").put("content", buildSystemPrompt()))
                    .put(JSONObject().put("role", "user").put("content", content))
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"; conn.connectTimeout = 20000; conn.readTimeout = 60000
                conn.setRequestProperty("Authorization", "Bearer $key")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val body = JSONObject().put("model", model).put("messages", messages).toString()
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
                val response = stream.bufferedReader().use { it.readText() }
                val obj = JSONObject(response)
                val answer = if (conn.responseCode in 200..299) obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content") else "Vision API error ${conn.responseCode}: ${obj.optString("error", response)}"
                runOnUiThread {
                    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 8, 18, 8) }
                    val description = EditText(this).apply { setText(answer); minLines = 5; maxLines = 12 }
                    val remember = CheckBox(this).apply { text = "इस observation को Shraddha की local memory में सेव करें"; isChecked = true }
                    box.addView(TextView(this).apply { text = "Lens ने क्या देखा? ज़रूरत हो तो जवाब edit करें।" })
                    box.addView(description); box.addView(remember)
                    android.app.AlertDialog.Builder(this).setTitle("👁 Visual Lens").setView(box)
                        .setPositiveButton("Done") { _, _ ->
                            val finalText = description.text.toString().trim()
                            say(finalText)
                            if (remember.isChecked && finalText.isNotBlank()) {
                                val old = prefs.getString("lens_memory", "").orEmpty()
                                val entry = "[${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(java.util.Date())}] $finalText"
                                prefs.edit().putString("lens_memory", (old + "\n" + entry).trim().takeLast(12000)).apply()
                            }
                        }.setNegativeButton("Discard", null).show()
                }
            } catch (e: Exception) { runOnUiThread { say("Visual Lens error: ${e.message}. जाँचें कि चुना model image input support करता है।") } }
        }
    }

    private fun summarizeClipboard() {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = manager.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
        if (text.isBlank()) say("Clipboard खाली है। पहले text copy करें।") else askAI("इस copied text को संक्षेप में समझाओ:\n$text")
    }

    private fun showMemory() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 8, 18, 8) }
        val memory = EditText(this).apply { hint = "Things Shraddha should remember"; setText(prefs.getString("ai_memory", "")); minLines = 4 }
        box.addView(memory)
        val lensMemory = EditText(this).apply { hint = "Visual Lens observations (editable local memory)"; setText(prefs.getString("lens_memory", "")); minLines = 4 }
        box.addView(TextView(this).apply { text = "Visual Lens memory" }); box.addView(lensMemory)
        AlertDialogCompat.show(this, "AI Memory (local to this phone)", box) {
            prefs.edit().putString("ai_memory", memory.text.toString()).putString("lens_memory", lensMemory.text.toString()).apply()
            say("Memory इस फोन पर सेव हो गई।")
        }
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
        super.onDestroy()
    }
}

object AlertDialogCompat {
    fun show(activity: Activity, title: String, content: android.view.View, save: () -> Unit) {
        android.app.AlertDialog.Builder(activity).setTitle(title).setView(content)
            .setPositiveButton("Save") { _, _ -> save() }.setNegativeButton("Cancel", null).show()
    }
}
