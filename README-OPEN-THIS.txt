SHRADDHA AI - DEVELOPER CONTACTS + AI VISION UPDATE

OPEN THIS FOLDER IN ANDROID STUDIO:
  android/

WHAT CHANGED
- Settings > About & Support > Developer Details: Sharad Patel, 8120727987, dial/SMS/WhatsApp draft/copy-number actions.
- Phone Tools > Calls & Messages: Search / choose a contact using Android's native contact picker. The app does not request broad READ_CONTACTS permission.
- Every call/SMS/WhatsApp action starts with user review. Calls open the dialer. SMS opens a prepared SMS draft. WhatsApp opens a prepared wa.me chat and the user taps Send in WhatsApp. Silent send is not claimed or implemented.
- Settings > AI Vision API: separate provider, model and key for OpenAI, Gemini, Claude, Groq and Compatible/OpenRouter-compatible provider. Provider base URLs are defined in source and are not shown as editable fields.
- Visual Lens uses the dedicated vision profile first, then can fall back to the matching chat provider key.
- Settings > Feature Enable Guide documents what is active, what requires opt-in, and what needs separate integration.

BUILD
1. Open android/ in Android Studio.
2. Sync Gradle.
3. Build > Build APK(s).
4. Install on a test phone and test AI chat/vision with your own API key.

HONEST STATUS
The source and manifests were statically checked in this environment, but a full Android Gradle build and real-device test were not run. API vision compatibility depends on selecting a model that accepts image inputs and on that provider key/account having access.

SECURITY / AUTOMATION NOTES
- Contact selection uses Android's native picker. No READ_CONTACTS permission is required by this implementation.
- Call uses ACTION_DIAL; it does not place calls silently.
- SMS and WhatsApp actions use prefilled composer/chat flows and require user send confirmation.
- Accessibility and notification-listener components are intentionally not registered in the sideload-safe manifest. To re-enable, they need clear user-facing disclosure, proper manifest registration and explicit user-granted Android special access; an app screen toggle alone cannot enable unregistered components.
