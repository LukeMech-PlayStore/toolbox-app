package org.lukemech.lmtoolbox.ui.pages.features.finance.currencyconv

import org.lukemech.lmtoolbox.ui.components.fadeExit
import org.lukemech.lmtoolbox.ui.components.fadeEnter
import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.ui.components.REVEAL_MS
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.ui.platform.LocalFocusManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ContactSupport
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.R as AppR
import org.lukemech.lmtoolbox.features.finance.currencyconv.parseSharedAmount
import org.lukemech.lmtoolbox.features.pypkgs.R
import org.lukemech.lmtoolbox.features.finance.currencyconv.CurrencyConverterFeature.S_CURRENCY_DEFAULT_FROM
import org.lukemech.lmtoolbox.features.finance.currencyconv.CurrencyConverterFeature.S_CURRENCY_DEFAULT_TO
import org.lukemech.lmtoolbox.features.finance.currencyconv.CurrencyConverterFeature.S_CURRENCY_SOURCE
import org.lukemech.lmtoolbox.linux.LinuxScripts
import org.lukemech.lmtoolbox.linux.LinuxTasks
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.components.PageHeading
import org.lukemech.lmtoolbox.features.finance.currencyconv.CurrencyConverterFeature
import org.lukemech.lmtoolbox.features.finance.currencyconv.EcbRates
import org.lukemech.lmtoolbox.ui.components.InputField
import org.lukemech.lmtoolbox.ui.components.RowColors
import org.lukemech.lmtoolbox.tasks.AppTasks

@Composable
fun CurrencyConverterPage(sharedValue: String?) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // What was shared ("100 USD to EUR"): the number goes in the field, the currencies are picked once the rates have them
    val shared = remember { sharedValue?.let(::parseSharedAmount) }
    var amount by remember { mutableStateOf(shared?.amount ?: "1.0") }
    var fromCurrency by remember { mutableStateOf(PreferenceHelper.get(context, S_CURRENCY_DEFAULT_FROM, DEFAULT_FROM)) }
    var toCurrency by remember { mutableStateOf(PreferenceHelper.get(context, S_CURRENCY_DEFAULT_TO, DEFAULT_TO)) }
    var result by remember { mutableStateOf<String?>(null) }
    var isConverting by remember { mutableStateOf(false) }

    var initPhase by remember { mutableStateOf("loading_currencies") }
    var currencyList by remember { mutableStateOf<List<String>>(emptyList()) }
    var dbDate by remember { mutableStateOf<String?>(null) }
    var isUpdating by remember { mutableStateOf(false) }
    val popupDismissedState = remember { mutableStateOf(true) }

    // Latest: the last downloaded rates (bundled ones before the first download); nothing is downloaded here
    val useLatest = remember { PreferenceHelper.get(context, S_CURRENCY_SOURCE, SOURCE_LATEST) == SOURCE_LATEST }
    val ratesStatus = if (useLatest) STATUS_LAST_DOWNLOAD else STATUS_BUILTIN

    // Currencies and date of the rates convert.py uses
    suspend fun loadRates() {
        try {
            val parsed = listCurrencies(context, ratesStatus)
            if (parsed != null) {
                dbDate = parsed.first
                currencyList = parsed.second

                if (currencyList.isEmpty()) {
                    // Told in the one place errors are, the bottom bar
                    AppTasks.reportFailure(CurrencyConverterFeature, "", "No currencies in the rates")
                    initPhase = "error"
                } else {
                    // The shared currencies as from and to, once (a single one as from, the sides swapped if they'd match)
                    if (initPhase != "ready") shared?.currencies?.filter { it in currencyList }?.takeIf { it.isNotEmpty() }?.let { codes ->
                        val previousFrom = fromCurrency
                        fromCurrency = codes[0]
                        if (codes.size > 1) toCurrency = codes[1]
                        else if (toCurrency == codes[0]) toCurrency = previousFrom
                    }
                    initPhase = "ready"
                }
            } else {
                // Its command's failure: the bottom bar tells
                initPhase = "error"
            }
        } catch (e: CancellationException) {
            // The page left while loading: nothing went wrong
            throw e
        } catch (e: Exception) {
            AppTasks.reportFailure(CurrencyConverterFeature, "", e.message ?: e.javaClass.simpleName, e.stackTraceToString())
            initPhase = "error"
        }
    }

    LaunchedEffect(Unit) {
        loadRates()
        // Outdated latest rates update themselves when there's a connection (once per opening)
        val date = dbDate
        if (useLatest && date != null && EcbRates.isOutdated(date) && isOnline(context)) {
            isUpdating = true
            updateRates(context)
            loadRates()
            isUpdating = false
        }
    }

    fun update() {
        if (isUpdating) return
        coroutineScope.launch {
            isUpdating = true
            updateRatesWithToast(context)
            loadRates()
            isUpdating = false
        }
    }

    // Converting (by the button, the keyboard's Done or a swap) puts the keyboard away
    val focusManager = LocalFocusManager.current
    fun convert() {
        focusManager.clearFocus()
        if (amount.isBlank()) return
        coroutineScope.launch {
            isConverting = true
            result = null

            val cmd = listOf(
                "python3", LinuxScripts.script(context, "features/currencyconv/convert.py"),
                // A decimal comma ("1,5") as convert.py's float() takes it
                ratesStatus, amount.replace(',', '.'), fromCurrency, toCurrency
            )
            val execResult = LinuxTasks.run(context, cmd, CurrencyConverterFeature)
            isConverting = false

            // Failed: the bottom bar tells (the task's, its log a tap away)
            if (execResult.isSuccess) {
                result = execResult.stdout.lines().firstOrNull { it.isNotBlank() } ?: "Error"
            }
        }
    }

    val showPrompt = !useLatest
    val ready = initPhase == "ready"
    val loading = initPhase == "loading_currencies"

    if (showPrompt && !popupDismissedState.value) {
        AlertDialog(
            onDismissRequest = { popupDismissedState.value = true },
            confirmButton = {
                Button(onClick = { popupDismissedState.value = true }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            title = { Text(stringResource(R.string.f_CurrencyConvert_builtin_title)) },
            text = { Text(stringResource(R.string.f_CurrencyConvert_builtin_msg)) }
        )
    }

    // Like the editors: the form above, its button kept at the bottom
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            PageHeading(stringResource(AppR.string.fp_CurrencyConvert)) {
                // Loading or updating rates shows in the buttons' spinners (and the bottom bar), not a bar of its own
                Reveal(visible = showPrompt) {
                    Column {
                        FeatureButton(
                            title = stringResource(R.string.f_CurrencyConvert_prompt_builtin),
                            onClick = { popupDismissedState.value = false },
                            icon = Icons.Default.SyncDisabled,
                            icon2 = Icons.AutoMirrored.Default.ContactSupport
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    // Names above the fields, as in the QR generator's forms
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.f_CurrencyConvert_amount), style = MaterialTheme.typography.labelLarge)
                        InputField(
                            value = amount,
                            onValueChange = { typed -> amount = typed.filter { it.isDigit() || it == '.' || it == ',' } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                if (ready && !isConverting) convert()
                            }),
                            leadingIcon = { Icon(Icons.Default.Payments, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.f_CurrencyConvert_from), style = MaterialTheme.typography.labelLarge)
                            CurrencyDropdown(
                                currency = fromCurrency,
                                currencies = currencyList,
                                enabled = ready,
                                loading = loading,
                                onSelect = { fromCurrency = it }
                            )
                        }

                        // Swapping converts again (not while a conversion runs)
                        IconButton(
                            onClick = {
                                if (isConverting) return@IconButton
                                val temp = fromCurrency
                                fromCurrency = toCurrency
                                toCurrency = temp
                                convert()
                            },
                            enabled = ready,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.f_CurrencyConvert_swap_cd))
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.f_CurrencyConvert_to), style = MaterialTheme.typography.labelLarge)
                            CurrencyDropdown(
                                currency = toCurrency,
                                currencies = currencyList,
                                enabled = ready,
                                loading = loading,
                                onSelect = { toCurrency = it }
                            )
                        }
                    }
                }

                // The result, in the app's cards
                Reveal(visible = result != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Text(
                                stringResource(R.string.f_CurrencyConvert_result),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${result.orEmpty()} $toCurrency",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

            }
        }

        // At the bottom, as every tool's buttons: which rates it converts with (tap to update), then convert
        RatesButton(
            date = dbDate, downloadable = useLatest, busy = loading || isUpdating,
            onUpdate = { update() }
        )
        Button(
            onClick = { convert() },
            enabled = ready && !isConverting && amount.isNotBlank(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp).height(56.dp)
        ) {
            if (isConverting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(Icons.Default.CurrencyExchange, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.f_CurrencyConvert_conv), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

const val DEFAULT_FROM = "USD"
const val DEFAULT_TO = "EUR"

/** [S_CURRENCY_SOURCE] values: ECB rates downloaded on launch, or the ones bundled with the module. */
const val SOURCE_LATEST = "latest"
const val SOURCE_BUILTIN = "builtin"

/** Rates status of [SOURCE_BUILTIN]; the scripts use the bundled rates for it. */
const val STATUS_BUILTIN = "BUILTIN"

/** Rates status of [SOURCE_LATEST] without downloading: the last download if there is one. */
const val STATUS_LAST_DOWNLOAD = "OFFLINE_HAS_DB"

/** Whether the device has a connection to the internet now. */
private fun isOnline(context: Context): Boolean {
    val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/**
 * Downloads the latest ECB rates; returns UPDATED, OFFLINE_HAS_DB, OFFLINE_NO_DB or ERROR.
 * Keeps downloading if the page is left, so the next visit has fresh rates.
 */
suspend fun updateRates(context: Context): String {
    val cmd = listOf("python3", LinuxScripts.script(context, "features/currencyconv/update_rates.py"))
    val result = LinuxTasks.runInBackground(context, cmd, CurrencyConverterFeature)
    return result.stdout.lines().firstOrNull { it.isNotBlank() } ?: "ERROR"
}

/**
 * Date of the rates, there from the start and spinning while they load or update (like the
 * tools' versions in the settings); the same on the page and in the settings. Tapping it
 * downloads the latest ones ([downloadable]: not the built-in ones, the sync icon says so),
 * red once the ECB has newer ones than these.
 */
@Composable
fun RatesButton(date: String?, downloadable: Boolean, busy: Boolean, onUpdate: () -> Unit) {
    val shown = date?.takeIf { it != "Unknown" }
    val outdated = downloadable && shown != null && EcbRates.isOutdated(shown)
    FeatureButton(
        title = stringResource(R.string.f_CurrencyConvert_data_label),
        subtitle = when {
            shown == null -> "-"
            outdated -> stringResource(R.string.f_CurrencyConvert_data_outdated, shown)
            else -> shown
        },
        titleSubtitleSwap = true,
        colors = if (outdated) RowColors.alert else RowColors.default,
        icon = if (downloadable) Icons.Default.Sync else Icons.Default.Event,
        buttonContent = {
            AnimatedVisibility(visible = busy, enter = fadeEnter, exit = fadeExit) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.primary)
            }
        },
        onClick = { if (downloadable && !busy) onUpdate() }
    )
}

/** Downloads the latest rates ([updateRates]), saying so in a toast once they are (a failure, the bottom bar tells); whether they were updated. */
suspend fun updateRatesWithToast(context: Context): Boolean {
    val updated = updateRates(context) == "UPDATED"
    if (updated) Toast.makeText(context, context.getString(R.string.f_CurrencyConvert_updated), Toast.LENGTH_SHORT).show()
    return updated
}

/**
 * Date and sorted currency codes of the rates [status] points to (the ones convert.py uses), or
 * null; also null while its tools aren't unpacked, unless they're to be ([unpack]).
 */
suspend fun listCurrencies(context: Context, status: String, unpack: Boolean = true): Pair<String, List<String>>? {
    val cmd = listOf("python3", LinuxScripts.script(context, "features/currencyconv/list_currencies.py"), status)
    val line = LinuxTasks.run(context, cmd, CurrencyConverterFeature, installExtensions = unpack)
        .stdout.lines().firstOrNull { it.contains("|") } ?: return null
    val (date, codes) = line.split("|", limit = 2)
    return date to codes.split(",").map { it.trim() }.filter { it.isNotEmpty() }.sorted()
}

/** Field showing [currency] that opens a list of [currencies], each with its name in the app's language. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrencyDropdown(
    modifier: Modifier = Modifier,
    label: String? = null,
    currency: String,
    currencies: List<String>,
    enabled: Boolean = true,
    loading: Boolean = false,
    onSelect: (String) -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { expanded = it }, modifier = modifier) {
        InputField(
            value = currency,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = label?.let { { Text(it) } },
            trailingIcon = {
                // The list is still loading: a spinner, fading into the arrow once it's there
                Crossfade(loading, animationSpec = tween(REVEAL_MS), label = "trailing") { l ->
                    if (l) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
                }
            },
            textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth()
        )
        // Wider than the field, so the names fit
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            matchAnchorWidth = false,
            modifier = Modifier.widthIn(min = 220.dp)
        ) {
            currencies.forEach { code ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(code, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                            currencyName(code, locale)?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    onClick = {
                        onSelect(code)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

private fun currencyName(code: String, locale: Locale): String? =
    runCatching { Currency.getInstance(code).getDisplayName(locale) }.getOrNull()?.takeIf { it != code }
