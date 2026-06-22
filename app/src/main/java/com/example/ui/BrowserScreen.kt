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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: TrackerViewModel,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var urlInput by remember { mutableStateOf("https://www.google.com") }
    var currentUrl by remember { mutableStateOf("https://www.google.com") }
    var webView: WebView? by remember { mutableStateOf(null) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Encrypted Cookie storage initialization
    val cookieStorage = remember { CookieStorage(context) }
    
    // Banner / UI state variables
    var showHeistBanner by remember { mutableStateOf(false) }
    var capturedDomain by remember { mutableStateOf("") }
    
    // Tracker setup interactive states
    var isBottomSheetVisible by remember { mutableStateOf(false) }
    var isInspectingMode by remember { mutableStateOf(false) }
    var isPremiumOptionSelected by remember { mutableStateOf(false) }
    
    // Dialog setups
    var showFreeConfirmBySelector by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showPremiumPromptBySelector by remember { mutableStateOf<Pair<String, String>?>(null) }
    var premiumPromptInput by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState()

    // Dismiss heist banner automatically after 3.5 seconds
    LaunchedEffect(showHeistBanner) {
        if (showHeistBanner) {
            delay(3500)
            showHeistBanner = false
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

            // Beautiful interactive cookie heist feedback banner
            AnimatedVisibility(
                visible = showHeistBanner,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("cookie_heist_banner"),
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
                            contentDescription = "Cookies Extracted",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(
                                text = "Session Cookies Secured",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Successfully captured & encrypted session variables for $capturedDomain",
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
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                url?.let { pageUrl ->
                                    urlInput = pageUrl
                                    currentUrl = pageUrl
                                    
                                    // Cookie heist extracting sequence
                                    val cookieManager = CookieManager.getInstance()
                                    val cookies = cookieManager.getCookie(pageUrl)
                                    
                                    if (!cookies.isNullOrBlank()) {
                                        cookieStorage.saveCookies(pageUrl, cookies)
                                        
                                        val host = java.net.URI(pageUrl).host ?: ""
                                        val domain = if (host.startsWith("www.")) host.substring(4) else host
                                        if (domain.isNotEmpty() && domain != "google.com") {
                                            capturedDomain = domain
                                            showHeistBanner = true
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
                        }
                        
                        // Add JS Interface safely named WebViewTrackerBridge
                        addJavascriptInterface(object : Any() {
                            @JavascriptInterface
                            fun onElementSelected(cssPath: String, text: String) {
                                scope.launch(Dispatchers.Main) {
                                    if (isInspectingMode) {
                                        isInspectingMode = false
                                        webView?.evaluateJavascript("if (window.removeTrackerInspector) { window.removeTrackerInspector(); }", null)
                                        
                                        if (isPremiumOptionSelected) {
                                            showPremiumPromptBySelector = Pair(cssPath, text)
                                        } else {
                                            showFreeConfirmBySelector = Pair(cssPath, text)
                                        }
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

                    // Option 1 (Free)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                isBottomSheetVisible = false
                                isInspectingMode = true
                                isPremiumOptionSelected = false
                                webView?.let { injectInspectorScript(it) }
                            }
                            .testTag("free_track_option"),
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
                                contentDescription = "Standard Eye Icon",
                                tint = IndigoPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Standard Tracker",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Notify on any change",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Option 2 (Premium)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                isBottomSheetVisible = false
                                isInspectingMode = true
                                isPremiumOptionSelected = true
                                webView?.let { injectInspectorScript(it) }
                            }
                            .testTag("premium_track_option"),
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
                                contentDescription = "Premium Gold Icon",
                                tint = PremiumGold,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Smart AI Tracker",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Set conditions & get summaries",
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

        // CONFIRM DIALOG FOR FREE OPTION
        showFreeConfirmBySelector?.let { selected ->
            val cssPath = selected.first
            val text = selected.second
            AlertDialog(
                onDismissRequest = { showFreeConfirmBySelector = null },
                title = { Text("Confirm Standard Tracker") },
                text = {
                    Column {
                        Text("Do you want to track this element?")
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Value: $text",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Selector: $cssPath",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        onClick = {
                            viewModel.addRule(
                                url = currentUrl,
                                cssSelector = cssPath,
                                isPremium = false,
                                aiPrompt = null
                            )
                            showFreeConfirmBySelector = null
                            Toast.makeText(context, "Tracker added!", Toast.LENGTH_SHORT).show()
                            onBackToDashboard()
                        }
                    ) {
                        Text("Add Tracker")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFreeConfirmBySelector = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // CONFIRM DIALOG FOR PREMIUM OPTION
        showPremiumPromptBySelector?.let { selected ->
            val cssPath = selected.first
            val text = selected.second
            AlertDialog(
                onDismissRequest = { showPremiumPromptBySelector = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = PremiumGold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Rules Setup")
                    }
                },
                text = {
                    Column {
                        Text("Enter the AI condition rule to summarize this element:")
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = premiumPromptInput,
                            onValueChange = { premiumPromptInput = it },
                            placeholder = { Text("e.g., Notify me if the price goes above 1000") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("premium_condition_input"),
                            singleLine = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PremiumGold,
                                focusedLabelColor = PremiumGold
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Selected text value: $text",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = PremiumGold),
                        onClick = {
                            viewModel.addRule(
                                url = currentUrl,
                                cssSelector = cssPath,
                                isPremium = true,
                                aiPrompt = premiumPromptInput
                            )
                            showPremiumPromptBySelector = null
                            premiumPromptInput = ""
                            Toast.makeText(context, "Smart Premium Tracker added!", Toast.LENGTH_SHORT).show()
                            onBackToDashboard()
                        }
                    ) {
                        Text("Save Smart Tracker")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPremiumPromptBySelector = null }) {
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

            function handleClick(e) {
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

                // Visual target outline styling: 3px dashed #14B8A6 border with a 10% opacity fill (rgba(20, 184, 166, 0.1))
                el.style.border = "3px dashed #14B8A6";
                el.style.backgroundColor = "rgba(20, 184, 166, 0.1)";

                const cssPath = getCssSelector(el);
                const text = el.innerText || el.textContent || "";
                
                // Invoke Android Native Bridge Callback
                if (window.WebViewTrackerBridge) {
                    window.WebViewTrackerBridge.onElementSelected(cssPath, text);
                }
            }

            document.addEventListener("click", handleClick, { capture: true });
            
            window.removeTrackerInspector = function() {
                if (selectedElement) {
                    selectedElement.style.border = originalBorder;
                    selectedElement.style.backgroundColor = originalBackground;
                }
                document.removeEventListener("click", handleClick, { capture: true });
                window.hasTrackerInspector = false;
            };
        })();
    """.trimIndent()
    webView.evaluateJavascript(jsCode, null)
}

private fun formatUrl(input: String): String {
    val clean = input.trim()
    if (clean.startsWith("http://") || clean.startsWith("https://")) {
        return clean
    }
    return "https://$clean"
}
