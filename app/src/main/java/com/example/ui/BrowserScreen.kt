package com.example.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.CookieStorage
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.PremiumGold
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Switch
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: TrackerViewModel,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
    initialUrl: String? = null
) {
    var urlInput by remember { mutableStateOf(initialUrl ?: "https://www.google.com") }
    var currentUrl by remember { mutableStateOf(initialUrl ?: "https://www.google.com") }
    var webView: WebView? by remember { mutableStateOf(null) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Encrypted Cookie storage initialization
    val cookieStorage = remember { CookieStorage(context) }
    val settingsStorage = remember { com.example.data.SettingsStorage(context) }
    
    // Banner / UI state variables
    var showPersistenceBanner by remember { mutableStateOf(false) }
    var capturedDomain by remember { mutableStateOf("") }
    
    // Tracker setup interactive states
    var isBottomSheetVisible by remember { mutableStateOf(false) }
    var isInspectingMode by remember { mutableStateOf(false) }
    
    // Dialog setups
    var showSetupDialog by remember { mutableStateOf<SetupDialogData?>(null) }
    var selectedInterval by remember { mutableStateOf(15) }

    val sheetState = rememberModalBottomSheetState()

    // Dismiss session persistence banner automatically after 3.5 seconds
    LaunchedEffect(showPersistenceBanner) {
        if (showPersistenceBanner) {
            delay(3500)
            showPersistenceBanner = false
        }
    }

    // Natively handle Android hardware Back press to navigate WebView history first
    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("browser_screen_root")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Address bar at the top with slate card design
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (webView?.canGoBack() == true) {
                                webView?.goBack()
                            } else {
                                onBackToDashboard()
                            }
                        },
                        modifier = Modifier.testTag("browser_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("url_text_field"),
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                focusManager.clearFocus()
                                val formattedUrl = formatUrl(urlInput)
                                urlInput = formattedUrl
                                currentUrl = formattedUrl
                                webView?.loadUrl(formattedUrl)
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
            }

            // Beautiful interactive session persistence feedback banner
            AnimatedVisibility(
                visible = showPersistenceBanner,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("session_persistence_banner"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Session Persistence Active",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(
                                text = "Seamless Login Integration",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Successfully loaded & encrypted session variables for $capturedDomain",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Target Selector top visual banner overlay
            AnimatedVisibility(
                visible = isInspectingMode,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("inspecting_banner"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tap any element on the webpage to track it.",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                isInspectingMode = false
                                webView?.evaluateJavascript("if (window.removeTrackerInspector) { window.removeTrackerInspector(); }", null)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Inspecting",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            // Standard high-performance WebView with high-security page tracking client
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): android.webkit.WebResourceResponse? {
                                request?.let { req ->
                                    val reqUrl = req.url?.toString() ?: ""
                                    val headers = req.requestHeaders
                                    android.util.Log.d("WebViewSpy", "WebView Success Request: $reqUrl | Headers: $headers")
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                url?.let { pageUrl ->
                                    urlInput = pageUrl
                                    currentUrl = pageUrl
                                    
                                    // Seamless session persistence tracking sequence
                                    android.webkit.CookieManager.getInstance().flush()
                                    val cookies = android.webkit.CookieManager.getInstance().getCookie(pageUrl)
                                    
                                    // Grab the exact current User-Agent dynamically from WebView and store it
                                    val currentUa = settings.userAgentString
                                    if (!currentUa.isNullOrBlank()) {
                                        settingsStorage.saveUserAgent(currentUa)
                                    }
                                    
                                    if (!cookies.isNullOrBlank()) {
                                        cookieStorage.saveCookies(pageUrl, cookies)
                                        com.example.data.PersistentCookieJar.saveWebViewCookiesToDb(context, pageUrl, cookies)
                                        
                                        val host = java.net.URI(pageUrl).host ?: ""
                                        val domain = if (host.startsWith("www.")) host.substring(4) else host
                                        if (domain.isNotEmpty() && domain != "google.com") {
                                            capturedDomain = domain
                                            showPersistenceBanner = true
                                        }
                                    }

                                    // Auto-inject JS helper script on load if inspected mode is saved
                                    if (isInspectingMode) {
                                        view?.let { injectInspectorScript(it) }
                                    }
                                }
                            }
                        }
                        webChromeClient = WebChromeClient()
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            supportZoom()
                            builtInZoomControls = true
                            displayZoomControls = false
                            
                            // Initialize User-Agent persistence on startup
                            val initialUa = userAgentString
                            if (!initialUa.isNullOrBlank()) {
                                settingsStorage.saveUserAgent(initialUa)
                            }
                        }
                        
                        // Add JS Interface safely named WebViewTrackerBridge
                        addJavascriptInterface(object : Any() {
                            @JavascriptInterface
                            fun onElementSelected(cssPath: String, genericPath: String, text: String) {
                                scope.launch(Dispatchers.Main) {
                                    if (isInspectingMode) {
                                        isInspectingMode = false
                                        webView?.evaluateJavascript("if (window.removeTrackerInspector) { window.removeTrackerInspector(); }", null)
                                        
                                        selectedInterval = 15
                                        showSetupDialog = SetupDialogData(
                                            uniqueSelector = cssPath,
                                            genericSelector = genericPath,
                                            textPreview = text,
                                            isWholePage = false
                                        )
                                    }
                                }
                            }
                        }, "WebViewTrackerBridge")

                        loadUrl(currentUrl)
                        webView = this
                    }
                },
                update = { view ->
                    webView = view
                },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .testTag("webview")
            )
        }

        // Floating Action Button pinned to bottom right helper target menu
        FloatingActionButton(
            onClick = {
                isBottomSheetVisible = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("select_element_fab"),
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Select Element to Track",
                modifier = Modifier.padding(16.dp)
            )
        }

        // M3 Bottom Sheet
        if (isBottomSheetVisible) {
            ModalBottomSheet(
                onDismissRequest = { isBottomSheetVisible = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "What do you want to track?",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Option 1: Whole Webpage Tracker
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                isBottomSheetVisible = false
                                showSetupDialog = SetupDialogData(
                                    uniqueSelector = null,
                                    genericSelector = null,
                                    textPreview = "Whole Webpage Code",
                                    isWholePage = true
                                )
                            }
                            .testTag("whole_page_track_option"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Whole Page Icon",
                                tint = IndigoPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Whole Webpage Tracker",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Track the entire webpage code. Notify me if any HTML changes.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option 2: Specific Elements Tracker
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                isBottomSheetVisible = false
                                isInspectingMode = true
                                webView?.let { injectInspectorScript(it) }
                            }
                            .testTag("element_track_option"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Specific Element Icon",
                                tint = PremiumGold,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Specific Elements Tracker",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Select specific elements on the screen to track.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // UNIFIED SETUP DIALOG
        showSetupDialog?.let { setupData ->
            var trackModeByList by remember { mutableStateOf(false) } // false = exact unique, true = generic list
            var isPremiumFilterActive by remember { mutableStateOf(false) }
            var aiConditionPromptInput by remember { mutableStateOf("") }
            var useStealthMode by remember { mutableStateOf(false) }
            
            AlertDialog(
                onDismissRequest = { showSetupDialog = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isPremiumFilterActive) Icons.Default.Star else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isPremiumFilterActive) PremiumGold else IndigoPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (setupData.isWholePage) "Whole Webpage Configuration" else "Scraper Rule Configuration",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Configure parameters to secure background HTML scrape checks and alerting rules.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 1. Text preview
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Preview Extracted Text",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = setupData.textPreview,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // 2. Select specific vs list (only if not whole page)
                        if (!setupData.isWholePage) {
                            Column {
                                Text(
                                    text = "Tracking Strategy",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    // Card A: Specific Unique
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { trackModeByList = false }
                                            .testTag("track_specific_btn"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (!trackModeByList) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
                                        ),
                                        border = BorderStroke(
                                            width = if (!trackModeByList) 2.dp else 1.dp,
                                            color = if (!trackModeByList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("Specific Item", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("Watch only this unique element", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    // Card B: List Siblings
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { trackModeByList = true }
                                            .testTag("track_list_btn"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (trackModeByList) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
                                        ),
                                        border = BorderStroke(
                                            width = if (trackModeByList) 2.dp else 1.dp,
                                            color = if (trackModeByList) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("Class Group List", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text("Watch all similar elements", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Premium AI Toggle
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPremiumFilterActive) PremiumGold.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isPremiumFilterActive) PremiumGold else MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = PremiumGold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Smart AI Filter Gate", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Switch(
                                        checked = isPremiumFilterActive,
                                        onCheckedChange = { isPremiumFilterActive = it },
                                        modifier = Modifier.testTag("premium_switch")
                                    )
                                }
                                if (isPremiumFilterActive) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = aiConditionPromptInput,
                                        onValueChange = { aiConditionPromptInput = it },
                                        placeholder = { Text("e.g. Notify me only if the price is higher than 300") },
                                        singleLine = false,
                                        maxLines = 2,
                                        modifier = Modifier.fillMaxWidth().testTag("ai_condition_prompt_input")
                                    )
                                }
                            }
                        }

                        // 3.5. Stealth Mode Toggle Card
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("stealth_mode_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (useStealthMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (useStealthMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Use Stealth Mode",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Bypass strict bot protection using headless browser",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = useStealthMode,
                                        onCheckedChange = { useStealthMode = it },
                                        modifier = Modifier.testTag("stealth_mode_switch")
                                    )
                                }
                            }
                        }

                        // 4. Frequency Selector
                        FrequencySelector(
                            selectedMinutes = selectedInterval,
                            onMinutesSelected = { selectedInterval = it }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        modifier = Modifier.testTag("confirm_add_rule_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPremiumFilterActive) PremiumGold else MaterialTheme.colorScheme.primary
                        ),
                        onClick = {
                            viewModel.addRule(
                                url = currentUrl,
                                cssSelector = if (setupData.isWholePage) null else (if (trackModeByList) setupData.genericSelector else setupData.uniqueSelector),
                                isPremium = isPremiumFilterActive,
                                aiPrompt = if (isPremiumFilterActive) aiConditionPromptInput else null,
                                checkIntervalMinutes = selectedInterval,
                                isTrackWholePage = setupData.isWholePage,
                                isTrackList = if (setupData.isWholePage) false else trackModeByList,
                                aiCondition = if (isPremiumFilterActive) aiConditionPromptInput else null,
                                initialText = setupData.textPreview,
                                isStealthMode = useStealthMode
                            )
                            showSetupDialog = null
                            Toast.makeText(context, "Tracker added successfully!", Toast.LENGTH_SHORT).show()
                            onBackToDashboard()
                        }
                    ) {
                        Text("Add Tracker Rule")
                    }
                },
                dismissButton = {
                    TextButton(
                        modifier = Modifier.testTag("dismiss_dialog_btn"),
                        onClick = { showSetupDialog = null }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun injectInspectorScript(webView: WebView) {
    val jsCode = """
        (function() {
            if (window.hasTrackerInspector) return;
            window.hasTrackerInspector = true;

            let selectedElement = null;
            let originalBorder = "";
            let originalBackground = "";

            function getCssSelector(el) {
                if (!(el instanceof Element)) return "";
                const path = [];
                while (el && el.nodeType === Node.ELEMENT_NODE) {
                    let selector = el.nodeName.toLowerCase();
                    if (el.id) {
                        if (!/^[0-9]/.test(el.id)) {
                            selector += '#' + el.id;
                            path.unshift(selector);
                            break;
                        }
                    }
                    
                    let sibling = el;
                    let nth = 1;
                    while (sibling = sibling.previousElementSibling) {
                        if (sibling.nodeName.toLowerCase() === el.nodeName.toLowerCase()) {
                            nth++;
                        }
                    }
                    if (nth > 1) {
                        selector += ":nth-of-type(" + nth + ")";
                    }
                    path.unshift(selector);
                    el = el.parentNode;
                }
                return path.join(" > ");
            }

            function getGenericSelector(el) {
                if (!(el instanceof Element)) return "";
                let selector = "";
                if (el.className && typeof el.className === 'string') {
                    const classes = el.className.split(/\s+/).filter(c => c.trim().length > 0 && c.indexOf(':') === -1 && c.indexOf('{') === -1);
                    if (classes.length > 0) {
                        selector = "." + classes.join(".");
                    }
                }
                if (!selector) {
                    selector = el.nodeName.toLowerCase();
                }
                const parent = el.parentNode;
                if (parent && parent instanceof Element) {
                    let parentSel = "";
                    if (parent.className && typeof parent.className === 'string') {
                        const parentClasses = parent.className.split(/\s+/).filter(c => c.trim().length > 0 && c.indexOf(':') === -1);
                        if (parentClasses.length > 0) {
                            parentSel = "." + parentClasses.join(".");
                        }
                    }
                    if (!parentSel) {
                        parentSel = parent.nodeName.toLowerCase();
                    }
                    return parentSel + " > " + selector;
                }
                return selector;
            }

            function handleInteract(e) {
                e.preventDefault();
                e.stopPropagation();

                const el = e.target;
                if (selectedElement) {
                    selectedElement.style.border = originalBorder;
                    selectedElement.style.backgroundColor = originalBackground;
                }

                selectedElement = el;
                originalBorder = el.style.border;
                originalBackground = el.style.backgroundColor;

                // Visual target outline styling: 3px dashed #14B8A6 border with 10% opacity fill
                el.style.border = "3px dashed #14B8A6";
                el.style.backgroundColor = "rgba(20, 184, 166, 0.1)";

                const cssPath = getCssSelector(el);
                const genericPath = getGenericSelector(el);
                const text = el.innerText || el.textContent || "";
                
                if (window.WebViewTrackerBridge) {
                    window.WebViewTrackerBridge.onElementSelected(cssPath, genericPath, text);
                }
            }

            document.addEventListener("click", handleInteract, { capture: true });
            document.addEventListener("touchstart", handleInteract, { capture: true });
            
            window.removeTrackerInspector = function() {
                if (selectedElement) {
                    selectedElement.style.border = originalBorder;
                    selectedElement.style.backgroundColor = originalBackground;
                }
                document.removeEventListener("click", handleInteract, { capture: true });
                document.removeEventListener("touchstart", handleInteract, { capture: true });
                window.hasTrackerInspector = false;
            };
        })();
    """.trimIndent()
    webView.evaluateJavascript(jsCode, null)
}

@Composable
fun FrequencySelector(
    selectedMinutes: Int,
    onMinutesSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        Pair("15m", 15),
        Pair("1h", 60),
        Pair("6h", 360),
        Pair("24h", 1440)
    )
    Column(modifier = modifier) {
        Text(
            text = "Check Frequency",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (label, minutes) ->
                val isSelected = selectedMinutes == minutes
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onMinutesSelected(minutes) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatUrl(input: String): String {
    val clean = input.trim()
    if (clean.startsWith("http://") || clean.startsWith("https://")) {
        return clean
    }
    return "https://$clean"
}

data class SetupDialogData(
    val uniqueSelector: String?,
    val genericSelector: String?,
    val textPreview: String,
    val isWholePage: Boolean = false
)
