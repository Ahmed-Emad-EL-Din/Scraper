🔲 PROMPT 3: The JavaScript Bridge, Settings, & Advanced Tracking UI
Context: Now we will make the WebView interactive so the user can visually select elements on the webpage to track, add tracking for whole webpages, and set up the AI configuration. Add Jsoup (org.jsoup:jsoup:1.17.2) to dependencies.

AI Settings Configuration:

Add a "Settings" screen or dialog accessible from the Tracker Dashboard.

Include a text input for the user to provide their own Gemini API Key.

Include a dropdown menu to select the AI Model (e.g., "gemini-1.5-flash" for speed, "gemini-1.5-pro" for complex reasoning).

API Key Validation (Test Connection): When the user adds their API key (either automatically on text input pause or via a "Test Key" button), make a lightweight test API call to Gemini (e.g., prompt: "Reply with exactly the word 'Hello'").

If the API replies successfully, display a green success message or Toast: "Successful AI Connection".

If an error occurs (e.g., invalid key, quota exceeded), catch the exception and display a red error message or Toast: "Connection Failed: $$Error Reason$$".

Save these preferences securely in EncryptedSharedPreferences.

The Tracking Menu (Bottom Sheet):
When the user clicks the FAB in the WebView, slide up a BottomSheetDialogFragment.

Option 1: Whole Webpage Tracker "Track the entire webpage code. Notify me if any HTML changes." (Saves a rule with isTrackWholePage = true).

Option 2: Specific Elements Tracker "Select specific elements on the screen to track." (Triggers the visual inspector below).

Sync Frequency: Provide a dropdown or radio group for how often to check in the background: 15 Minutes, 1 Hour, 6 Hours, 24 Hours.

Premium Toggle: A Gold "Star" toggle to add a custom AI condition (e.g., "Notify me only if the price of gold increased more than 1000").

The Visual Element Inspector (JS Injection - Chrome DevTools Style):
If the user selects "Specific Elements Tracker":

Inject a JS script via webView.evaluateJavascript().

Show a native Android top banner (#1E293B background): "Tap any element on the webpage to track it."

The injected JS must act like the Chrome Developer Tools Inspector. It must intercept touchstart and click, run e.preventDefault(), and inject a 3px dashed #14B8A6 border with a 10% opacity fill on the touched element.

Smart Hierarchy Detection: The JS must generate a reliable CSS path. If the clicked element shares a class structure with siblings (e.g., multiple grade rows), pass a generic selector back to Android that captures all similar elements.

Saving the Rule:

Save the tracking rule to the Room DB. The entity must include url, cssSelector, syncFrequency, isTrackList, isTrackWholePage, lastKnownText (or HTML), and the aiCondition if applicable.

Goal for this prompt: A fully working UI with an API settings menu, a bottom sheet offering whole-page or specific-element tracking, and a Chrome-like visual JS inspector for selecting elements.

🔲 PROMPT 4: The Background Worker & "WhatsApp-Style" Notifications
Context: The final phase. We will build the background engine that checks websites silently. Notifications must be high-priority, system-level alerts (like a WhatsApp message), and must open to a detailed view when tapped.

The Background WorkManager:

Create a PeriodicWorkRequest dynamically based on the user's chosen frequency (minimum 15 minutes). Must require NetworkType.CONNECTED.

Execution Loop: Read all rules from Room DB. Group by URL to minimize network requests.

Execute OkHttp GET requests using the URL. You MUST inject the cookies saved in Phase 2 into the header (Cookie: [saved_cookies]) to bypass login screens.

Parsing & Extraction:

Parse the response HTML using Jsoup.

If isTrackWholePage == true: Compare the raw doc.body().html() with the saved HTML.

If isTrackWholePage == false and isTrackList == false: Extract new_text = doc.select(savedCssSelector).text().

If isTrackWholePage == false and isTrackList == true: Extract all matching elements into a HashMap<String, String> and convert to JSON.

AI Summarization & Logic (If AI Condition/Premium is active):
If the rule has an AI condition, make an API call to Gemini using the user's saved API Key and chosen Model.

Prompt: "Old Content: '{old_content}'. New Content: '{new_content}'. User Condition: '{aiCondition}'. If the condition is NOT met, reply 'IGNORE'. If met, reply 'TRIGGER: $$Generate a short, 1-sentence summary of what exactly changed or is new$$'."

If AI replies "IGNORE", silently update the DB without vibrating the phone.

Real, High-Priority System Notifications:
Do NOT just show a card in the app. You must trigger a real Android Notification.

Use NotificationChannel with IMPORTANCE_HIGH so it pops up at the top of the user's screen (Heads-up notification) and makes a sound/vibrates, exactly like a WhatsApp message.

Notification Text: Use the AI's summary (e.g., "New grade added for Math: 18/20") or a generated plain-text diff (e.g., "Changed from '16/20' to '18/20'").

Tap for Details (PendingIntent):

Attach a PendingIntent to the notification.

When the user taps the notification, open the app to a "Change Details" Screen.

This screen must clearly show the "Old Data" vs. the "New Data" (highlighting what changed) and provide the AI summary if available.

Background Resiliency:

Ensure proper Android 13+ POST_NOTIFICATIONS permissions are requested.

If OkHttp returns a 401 Unauthorized, pause the specific WorkManager and fire a Notification: "Session Expired. Please open the app and log in again."

Goal for this prompt: A background scraper that respects cookies, uses the user's custom Gemini API key/model, and fires high-priority, heads-up notifications summarizing the change. Tapping the notification must open a detailed comparison view inside the app.

🔲 PROMPT 5: Dashboard Finalization, Change History, & System Resilience
Context: This final polish phase focuses on app usability and background stability. We will implement the Tracker Dashboard, the detailed diff screen, rule management, and ensure Android's strict battery optimizations don't kill our background tracker.

The "Change Details" Screen (Notification Target):
When the user taps a notification, open this dedicated screen.

Header: Display the AI Summary in an Indigo (#6366F1) highlight box (if available).

The Diff View: Display the "Old Data" and "New Data" side-by-side or stacked. Use a simple diff text styling (e.g., deleted text has a red background with strikethrough, newly added text has a green background).

Action Button: Include a "View Live on Website" button that opens the WebView and navigates directly to the URL.

Finalizing the Tracker Dashboard (Screen A):
Replace the blank state from Phase 1. Add a RecyclerView mapping the Room DB rules to visual cards.

Card UI: Each card should show the URL hostname, the sync frequency (e.g., "Checks every 1 hr"), and the "Last Checked" timestamp.

Rule Management: Implement swipe-to-delete. If a user swipes a card away, delete it from the Room DB and cancel its associated WorkManager task.

Pause Toggle: Add a Switch on each card to pause/resume tracking without deleting the rule entirely.

Change History Database:

Create a new Room Entity: TrackingHistory (id, ruleId, oldText, newText, timestamp).

Every time the WorkManager detects a change, insert a record here.

Add a "History" button to the Tracker Dashboard cards so the user can view a list of past changes for that specific tracker.

Battery & Boot Resilience (Crucial for Scrapers):

Boot Receiver: Register a BroadcastReceiver for Intent.ACTION_BOOT_COMPLETED. When the phone restarts, query the Room DB for active rules and re-enqueue the WorkManager periodic tasks.

Ignore Battery Optimizations: Android Doze mode will kill frequent background checks. Add a prompt in the Settings screen requesting REQUEST_IGNORE_BATTERY_OPTIMIZATIONS so the user can whitelist the app and ensure reliable background notifications.

Goal for this prompt: A polished app experience where users can manage their trackers, view visual text differences, check historical changes, and run the app reliably without being killed by Android's battery manager.

🔲 PROMPT 6: Advanced Scraping, Error Recovery, & App Polish
Context: Real-world websites update their code frequently and often rely heavily on JavaScript. This phase adds failsafes for broken trackers, a fallback for JS-heavy sites, and final UI polish.

JS-Heavy Website Fallback (Hidden WebView):

Add a boolean to the Room DB Rule: requiresJS.

In the Bottom Sheet (Prompt 3), add a checkbox: "Requires JavaScript to load (Slower)".

If requiresJS == true, the WorkManager cannot use OkHttp. Instead, it must launch a Headless (invisible) WebView on the main thread, inject the cookies, wait for onPageFinished, wait 2000ms for JS frameworks to render, extract the HTML, and then destroy the WebView.

Broken Selector Detection & Alerts:

Websites change their UI. If the WorkManager searches for a cssSelector and gets null or an empty string for 3 consecutive checks, pause the rule.

Fire a specific Notification: "Tracker Broken: The layout for $$URL$$ seems to have changed. Tap to re-select the element."

Tapping this notification opens the WebView directly to that URL with the Visual Inspector pre-activated so the user can click the new element.

Data Export / Import:

Add an "Export Data" button in the Settings screen.

Generate a JSON file containing all active Rules and their configurations.

Use the Android Storage Access Framework (ACTION_CREATE_DOCUMENT) so the user can save this backup file to their phone. Allow importing it to restore rules if they change phones.

UI Polish & Dark Mode:

Ensure the entire app respects the system Dark Mode.

Map the Slate Off-White (#F8FAFC) to a Deep Slate (#0F172A) in dark mode, and map the White Surface (#FFFFFF) to Dark Surface (#1E293B).

Add a smooth fade-in animation when the user transitions from the Dashboard (Screen A) to the Browser (Screen B).

Goal for this prompt: An enterprise-grade scraper that can handle modern React/Angular apps, automatically detects when websites change their layouts, allows data backups, and looks visually polished in both light and dark modes.

🔲 PROMPT 7: Branding, Packaging, & Production "Secrets"
Context: The final step before building the release APK. We need to set the official brand identity for "2binventor" and configure the project to survive real-world Android production environments (Play Store policies, obfuscation, etc.).

Package Name & Branding:

Set the application package name strictly to: com.twobinventor.webmonitor (or similar, confirm final suffix with me).

Use the provided image file (ChatGPT Image Jun 22, 2026, 06_52_28 PM.png) to generate the Android Adaptive Icons (ic_launcher and ic_launcher_round).

Use Android Studio's Image Asset Studio. The image features a white browser window and a cyan target on a blue background. Ensure the blue background scales correctly as the "Background Layer" so the icon looks perfect across all Android shapes (Circle, Squircle, Teardrop).

ProGuard & R8 Obfuscation Rules (CRITICAL):

Web scraping relies heavily on reflection and JavaScript interfaces. When building the Release APK, Android will shrink and obfuscate the code. This will break the app if not handled.

Add custom ProGuard rules to keep the @JavascriptInterface methods safe: -keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }

Add ProGuard rules to keep Jsoup models and Room DB entities from being obfuscated, otherwise database crashes will occur in production.

Play Store Policy Compliance:

Background Data: Google Play strictly limits background execution. Ensure the REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is requested properly with a clear, user-friendly dialogue explaining why it is needed for tracking, or Google will reject the app.

Javascript Injection Security: Google Play scans for malicious JS. The JavaScript injected in Phase 3 MUST be strictly local (bundled directly in the app's Kotlin string/raw folder) and NEVER downloaded from a remote server. This prevents the app from being flagged for "downloading executable code."

Terminology: In user-facing text and Play Store descriptions, never use terms like "cookie stealing." Use enterprise terminology like "Session Persistence" or "Seamless Login Integration."

Goal for this prompt: Refactor the package name to the 2binventor brand, implement the official logo as an adaptive icon, and secure the release build with the necessary ProGuard rules and policy compliance safeguards.

🔲 PROMPT 8: Background Robustness, Quick Settings Tile, & Smart Notifications
Context: We are now refining the background engine to make it robust, prevent infinite network hangs, add quick-access system controls, and improve the notification payload for the user. Please implement the following 4 features exactly as specified:

Quick Settings Panel Tile (TileService):

Create an Android TileService so the user can add a toggle tile to their Android Quick Settings drop-down menu (the menu with WiFi, Bluetooth, etc.).

The Tile should be named "Web Monitor".

State Logic: If the tracker is active, the Tile should be lit/active. If tapped, it should toggle the WorkManager on or off.

Foreground Service & "Silent" Tracking Notification:

To prevent the Android OS from killing the background task, the WorkManager must use setForegroundAsync() (ForegroundInfo).

Display a "Silent" / low-priority persistent system notification in the drop-down menu while the service is active (e.g., "Tracking Started - Website monitoring successfully configured for: $$URL$$"). This should sit silently in the background without making a sound.

This guarantees the WorkManager executes reliably without buzzing the user's phone just for performing routine background checks.

Robust Network Timeouts (Fixing Infinite Hangs):

Currently, the app hangs infinitely if a website is slow or unresponsive. You MUST add strict timeouts to both OkHttp and Jsoup.

OkHttp: Add .connectTimeout(15, TimeUnit.SECONDS), .readTimeout(15, TimeUnit.SECONDS), and .writeTimeout(15, TimeUnit.SECONDS) to the OkHttpClient builder.

Jsoup: Ensure the Jsoup connection includes .timeout(15000).

If a timeout occurs, catch the exception cleanly, log it, and gracefully let the WorkManager retry on the next cycle without freezing the app.

User-Selectable Intervals & Strict Diff Notifications:

Interval Selection UI: Add a dropdown/spinner in the app settings allowing the user to choose their tracking interval: 15 minutes (Android minimum), 30 minutes, 1 hour, 6 hours, or 12 hours. Update the PeriodicWorkRequest to respect this setting.

Plain Text Diffing (Free Version): When comparing the old_text and new_text, ensure you are extracting pure text using Jsoup.parse(html).text(). DO NOT compare raw HTML code.

The "No Change, No Alert" Rule: The code MUST include a strict logic gate. If old_text == new_text, do absolutely nothing. Do NOT send an alert notification.

Notification Formatting: ONLY if a change is detected, trigger an IMPORTANCE_HIGH notification that drops down from the top menu and alerts the user. It must show exactly what changed. For example, the notification body must read: "Update: Changed from $$Old Text$$ to $$New Text$$". Do not just say "Website updated."

🔲 PROMPT 9: Anti-Bot Evasion, 409 Errors, & Stealth Tracking
Context: Some highly secure websites (especially those with CAPTCHAs) block standard background HTTP requests. They return HTTP 409 (Conflict) or force a page refresh loop because they detect the background worker is not a real browser. We need to upgrade the tracking engine to be 100% stealthy.

Please implement the following 4 features exactly as specified:

The Full Header Heist (User-Agent Sync):

Stealing cookies is no longer enough. The website's anti-bot system is checking the browser's identity.

In Phase 2, capture the exact User-Agent string of the Android WebView.

When the OkHttp WorkManager makes a background request, it MUST explicitly inject the exact same User-Agent, Accept-Language, and Accept headers as the WebView.

Automated 409 Error & Refresh Handling:

If the OkHttp background request receives an HTTP 409, 403, or gets stuck in a refresh loop (HTML returned contains meta-refresh or JS redirects), catch this immediately.

DO NOT fail or pause the rule. Instead, trigger the "Headless WebView Fallback" (from Phase 6) as an automatic retry mechanism.

Upgrading the Headless WebView (The Ultimate Bypass):

For sites with aggressive CAPTCHAs, OkHttp will never work. We must upgrade the requiresJS Hidden WebView logic to be a full "Stealth Browser".

Ensure the WorkManager's hidden WebView is configured with: settings.javaScriptEnabled = true, settings.domStorageEnabled = true, and settings.databaseEnabled = true.

Inject the saved cookies using CookieManager before calling loadUrl.

Crucial: You must wait for the page to fully load and settle (detecting when the CAPTCHA/refresh loop finishes) before extracting the HTML using evaluateJavascript.

Manual "Stealth Mode" Toggle:

In the Bottom Sheet tracking menu (Prompt 3), add a toggle switch: "Use Stealth Mode (Bypass strict bot protection)".

If the user checks this box, the app skips OkHttp entirely for that specific URL. It will ALWAYS use the Headless WebView to check for updates in the background, ensuring it organically passes Javascript and CAPTCHA checks just like a real human browser.

🔲 PROMPT 10: CSRF Protection Bypass & 419 Error Handling
Context: Modern website frameworks (such as Laravel) implement strict Cross-Site Request Forgery (CSRF) protection. When sessions expire or background GET/POST requests lack a valid CSRF token, the server throws an HTTP 419 (Page Expired) error. We must implement dynamic CSRF extraction and recovery.

Please implement the following 4 features exactly as specified:

Catching the 419 Error Code:

Update the OkHttp client inside the WorkManager to explicitly watch for HTTP response code 419.

If a 419 Page Expired error occurs, DO NOT freeze or skip the sync. This indicates the session cookie died or the server demands a fresh CSRF token.

Dynamic CSRF Token Extraction:

Before making restricted background API requests, OkHttp must first fetch the webpage HTML and parse it using Jsoup.

Hunt for standard CSRF meta tags. Use Jsoup to select meta[name="csrf-token"] and extract the content attribute.

If found, append this token to all subsequent OkHttp network headers (e.g., Header("X-CSRF-TOKEN", extractedToken)).

Automated Session Recovery (The Stealth Re-Auth):

If OkHttp catches a 419 error, the static saved cookies are likely invalid.

Immediately trigger an automated recovery via the "Headless WebView Fallback" (from Phase 9).

Load the webpage invisibly in the WebView, let it execute its internal Javascript to negotiate a fresh session with the server, and extract the new, updated cookies using CookieManager. Save these new cookies back to EncryptedSharedPreferences and retry the request.

The Fail-Safe Notification:

Create a retry counter for the 419 recovery process. If the Headless WebView fails to resolve the 419 error after 2 consecutive attempts, the session is completely dead (requiring manual human login).

Pause the specific tracker rule in Room DB.

Fire a localized, user-friendly notification: "Session Expired. Please open the app and log in to $$Website URL$$ to resume background tracking."

🔲 PROMPT 11: Advanced 419 Debugging & Deep Header Synchronization
Context: The standard CSRF bypass in Phase 10 is failing, meaning the server has advanced protection. We need to implement exact error catching and deep header synchronization to find out exactly why the 419 error is happening and force OkHttp to perfectly mimic the WebView.

Please implement the following 4 features exactly as specified:

Exact 419 Error Catching & Verbose Logging:

When OkHttp receives a 419 error, you MUST log the exact conditions. Write an interceptor that prints the ENTIRE request and response to Android Logcat.

Logcat must show: The exact URL, all Request Headers sent by OkHttp, the exact String of cookies injected, and the full response body HTML returned by the 419 error. This is critical for the developer to see what is missing.

Deep Header Sync (Referer, Origin, X-Requested-With):

The 419 error often happens because strict Laravel/Web frameworks check where the request came from.

You must add these exact headers to the OkHttp Builder:

.addHeader("X-Requested-With", "XMLHttpRequest")

.addHeader("Origin", "[Extract the base URL of the website]")

.addHeader("Referer", "[Extract the exact URL of the website]")

WebView Request Interception (The Spy Method):

We need to spy on the real WebView to see what headers it naturally sends that OkHttp is missing.

Inside the main WebViewClient, override the shouldInterceptRequest method.

Add a Log.d("WebViewSpy", ...) that prints out all request.requestHeaders whenever the real WebView loads the webpage successfully. This will allow the developer to compare the real WebView headers against the failing OkHttp headers.

Strict Cookie Flushing (Fixing Desync):

A major cause of 419 errors is that CookieManager has new cookies, but they haven't been flushed to disk when OkHttp tries to read them.

Force a strict sync: Call CookieManager.getInstance().flush() immediately inside the WebView's onPageFinished event BEFORE extracting and saving the cookies to SharedPreferences.

Create a dedicated CookieJar implementation for OkHttp that reads directly from CookieManager.getInstance().getCookie(url) in real-time, rather than relying on stale SharedPreferences data.
