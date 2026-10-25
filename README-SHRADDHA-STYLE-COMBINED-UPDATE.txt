SHRADDHA AI / Shraddha-STYLE ANDROID UPDATE
======================================

OPEN AND BUILD
1. Extract the ZIP.
2. Open `shraddha-ai-premium-branded/android` in Android Studio.
3. Let Gradle sync and install the SDK platform requested by the project if prompted.
4. Select Build > Build APK(s).
5. Test on a non-critical device before enabling Accessibility, background microphone, or notification access.

Shraddha REFERENCE DESIGN
- Home page uses the user-provided Shraddha screenshot as the hero artwork, without replacing the existing app launcher icon.
- Header uses a Shraddha AI wordmark, compact menu and settings button, followed by the character artwork, animated glass/neon microphone orb, animated waveform, prompt composer, and quick actions.
- Floating bottom dock has Home, Chat, Voice, History, and Settings. Buttons and touch targets were enlarged slightly.
- Root and modal dialogs apply Android system-bar insets; the dock is positioned above Android gesture / three-button navigation areas to prevent overlap.
- Shared pages use the same midnight/navy gradient, violet/magenta strokes, rounded elevated cards, 3D press feedback, subtle starfield, and staggered entrance animations.
- The Voice Studio default profile now starts at Leda, 85% speech rate, 65% volume on a fresh install. Existing saved user preferences remain unchanged.
- The live orb can represent idle/listening/speaking states. The voice and orb menus include 12 requested voice names and 11 requested orb style names. Named neural voices depend on compatible Gemini speech access; Android TTS remains the fallback.

BRANDING PRESERVATION
- Existing launcher icons (`mipmap-*/ic_launcher.png`) and original `shraddha_logo.png` / `shraddha_icon_foreground.png` are unchanged byte-for-byte from the prior audit project.
- `shraddha_home_hero.png` is a separate image resource cropped from the screenshot the user supplied. It does not replace the app icon.

FEATURE AREAS IN THE PROJECT
- Providers: OpenAI, Gemini, Claude, Groq, Perplexity and OpenRouter-compatible endpoint; provider APIs, models and account access determine what can run.
- Personality profile: assistant name, persona, response style, emotion, custom instructions.
- Voice Studio: requested 12 voice names, Hindi / English / Hinglish Auto, speed, volume, preview, orb style.
- Orb Studio: Classic, Galaxy, Solar, Lawa, Ocean, Aurora, Glass, Plasma, Pulse, Minimal, Archer.
- Permission dashboard: microphone and Android notification prompts are user-triggered; Accessibility and Notification Access open only after the matching in-app choice and explanation.
- Optional background wake service for “Hey Shraddha” or double clap with persistent notification; battery and notification events depend on Android/OEM background limits.
- App control: launch installed apps, read exposed UI text, fill accessible editable fields, and tap exposed controls after the user enables Accessibility.
- Visual Lens: user-selected photo/camera result, image explanation and approximate color recognition.
- Reading: PDF (first three rendered pages), DOCX, EPUB, RTF, TXT, Markdown, CSV, JSON, HTML and selected images. Scanned PDF OCR, long-book paging and unsupported formats are not universal.
- Learning: math, science, history, chemistry, physics and general medical-science education (not diagnosis/prescribing).
- Maps / search: Google Maps route/search hand-off and browser search hand-off. This is not a full embedded live-traffic/turn-by-turn system.
- Memory / notes / automation: locally saved context and simple opt-in workflows.
- Communications: open dialer/SMS composition, WhatsApp/share-sheet hand-off, and user-reviewed message drafts.

PLATFORM AND SAFETY LIMITS
- Ordinary Android apps cannot force-stop arbitrary third-party apps or freely interact with every app screen; Accessibility only exposes UI elements supported by the target app.
- Sensitive settings are not silently enabled. The user must enable permissions / special access explicitly.
- Sending a message, booking, order, payment, or other external action must remain user-reviewed; no silent unattended transactions are implemented.
- Ordinary apps do not have general access to both sides of cellular call audio, so automatic two-way AI conversation over normal SIM calls is not implemented.
- No claim is made that every possible document, arbitrary screen or every third-party-app workflow works universally.
- API keys in app-local preferences are not a production secret vault. Do not distribute an APK containing real API keys; use a backend proxy for production.

VALIDATION STATUS
- The exact old launcher artwork and old Shraddha logo resources were compared against the prior project and are byte-for-byte identical.
- XML/JSON resources and ZIP integrity are validated.
- Android SDK / Gradle build tools are not available in this environment, so a full Android Studio compile, emulator rendering, and real-phone test have NOT been performed. Build and test with Android Studio before installation/distribution.
