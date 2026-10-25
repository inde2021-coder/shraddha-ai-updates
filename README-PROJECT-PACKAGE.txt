SHRADDHA AI — ANDROID PROJECT
============================

OPEN AND BUILD
1. Extract the package.
2. In Android Studio open the `android` folder.
3. Let Gradle sync and install the Android SDK Platform 35 if Android Studio prompts.
4. Run Build > Build APK(s).
5. Test on a non-critical device before using Accessibility, background microphone or notification features.

CURRENT SCREEN / DESIGN AUDIT
- Home, Chat, Voice, History and Settings use the shared dark-space / purple-neon styling.
- Feature panels use rounded elevated cards, consistent headings, 3D-style press feedback and the same dark theme.
- The original Shraddha launcher artwork and home logo are preserved from the previous project.
- Settings contains Permissions & Privacy, Feature Coverage & Diagnostics, AI Providers, Voice & Personality, Phone Tools, Memory, Wake & Orb, App & Task Control, and Study / Books / Maps.
- Provider Base URLs stay in code; the Settings form does not display a Base URL field.

PERMISSIONS & PRIVACY
- Nothing requests runtime microphone or notification permission on app launch.
- Runtime microphone and Android 13+ notification permission prompts are issued only when the user taps the corresponding button inside Settings > Permissions & Privacy.
- Accessibility and Notification Access are special Android settings; they open only after the user taps the matching row in that dashboard, and the user must manually switch the service on.
- App-control and notification pages link back to the in-app permission dashboard instead of jumping to sensitive settings automatically.
- Notification listener only checks that a notification event occurred; it does not read notification title/body text.
- Camera is handed off to the system camera app; photos/files are selected through Android's picker. No broad camera or storage permission is declared.
- SMS/call actions use system composers/dialer/share sheet; user reviews and confirms external actions.

FEATURES WITH SOURCE-LEVEL IMPLEMENTATION
- Providers: OpenAI, Gemini, Claude, Groq, Perplexity, and OpenRouter-compatible endpoint; check current provider key/model eligibility with the relevant account.
- Assistant name, Hindi / English / Hinglish Auto, personality presets, response style, emotion, speech speed/volume.
- Named voice selection: Leda, Kore, Aoede, Zephyr, Autonoe, Despina, Erinome, Laomedeia, Achernar, Gacrux, Sulafat, Callirrhoe. Named provider voices require compatible Gemini speech API/model/account; device TTS is fallback.
- Orb choices: Classic, Galaxy, Solar, Lawa, Ocean, Aurora, Glass, Plasma, Pulse, Minimal, Archer. Orb styles and state animations work while the app UI is visible.
- Optional foreground wake service for “Hey Shraddha” or double clap with a persistent notification; platform/OEM restrictions can interrupt it.
- Optional notification event wake prompt, low battery alert, and periodic full-charge check.
- Installed-app launching, selected-screen text reading, editable-field filling and visible-label tap when Accessibility is manually enabled; controls vary by target app.
- Visual Lens for a selected photo or camera result, approximate center-region color recognition with RGB/HEX, and AI photo explanation.
- Book/text extraction: PDF (first 3 rendered pages), DOCX, EPUB, RTF, TXT, Markdown, CSV, JSON, HTML and selected images. Archive/document input has size limits. Scanned text depends on vision provider/image quality.
- Maths, science, history, chemistry, physics and medical-science educational prompts; Maps search/directions; local notes/memory; clipboard summary; browser search hand-off.
- Communication helper drafts SMS/WhatsApp text and shares chosen files; user chooses recipient and confirms sending.

KNOWN LIMITS — NOT PRESENTED AS COMPLETE
- No universal full-screen OCR/screen capture: Accessibility only reads accessible UI text after manual enable; protected/custom-rendered screens may not expose content.
- No full e-book library/pagination, scanned-PDF OCR pipeline, or general file-manager operations such as moving/renaming files.
- No native sourced web search with citations, in-app image-generation/editing pipeline, or full turn-by-turn/live-traffic map stack.
- No silent SMS/WhatsApp sending, unattended booking/payment/order completion, force-stop of arbitrary apps, or automatic repair of third-party apps.
- No automatic two-way AI conversation over regular SIM call audio; Android restricts cellular call audio capture and call automation.
- The requested Snap Vanish effect cannot be injected into every other video-call app; it requires supported call SDK integration.
- API keys are stored in app-local preferences, not a production secret vault. Do not publish this project or APK with real keys. A production release should use a backend secret vault/proxy.

VALIDATION STATUS
- XML and JSON resources are parsed/validated, ZIP integrity is checked, permission flows are source-audited, and original icon files are byte-for-byte preserved.
- This environment does not have Android SDK/Gradle build tools set up, so a full Android Studio compile and real-phone run have NOT been verified. Build and test with Android Studio before installation/distribution.
