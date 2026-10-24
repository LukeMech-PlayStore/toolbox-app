package org.lukemech.lmtoolbox.features.finance.currencyconv

import org.lukemech.lmtoolbox.ui.components.Reveal
import org.lukemech.lmtoolbox.features.pypkgs.R
import org.lukemech.lmtoolbox.ui.components.SwitchButton
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.lukemech.lmtoolbox.helpers.FeatureUi
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import org.lukemech.lmtoolbox.ui.components.FeatureButton
import org.lukemech.lmtoolbox.ui.pages.features.finance.currencyconv.STATUS_LAST_DOWNLOAD
import org.lukemech.lmtoolbox.ui.pages.features.finance.currencyconv.STATUS_BUILTIN
import org.lukemech.lmtoolbox.ui.pages.features.finance.currencyconv.SOURCE_LATEST
import org.lukemech.lmtoolbox.ui.pages.features.finance.currencyconv.SOURCE_BUILTIN
import org.lukemech.lmtoolbox.ui.pages.features.finance.currencyconv.RatesButton
import org.lukemech.lmtoolbox.ui.pages.features.finance.currencyconv.DEFAULT_TO
import org.lukemech.lmtoolbox.ui.pages.features.finance.currencyconv.DEFAULT_FROM
import org.lukemech.lmtoolbox.ui.pages.features.finance.currencyconv.CurrencyDropdown
import org.lukemech.lmtoolbox.ui.pages.features.finance.currencyconv.CurrencyConverterPage
import org.lukemech.lmtoolbox.ui.pages.features.finance.currencyconv.updateRatesWithToast
import org.lukemech.lmtoolbox.ui.pages.features.finance.currencyconv.listCurrencies

/** The currency converter's pages, and its settings: they use its currency lists (see [CurrencyConverterFeature]). */
@Suppress("unused") // Loaded by its name, CurrencyConverterFeature.uiClass
object CurrencyConverterUi : FeatureUi {
    @Composable
    override fun Content(arguments: Map<String, String?>?) {
        CurrencyConverterPage(arguments?.getOrElse("sharedText") { null })
    }

    override val hasSettingsContent get() = true

    @Composable
    override fun SettingsContent() {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        var defaultCurrencyFrom by remember {
            mutableStateOf(PreferenceHelper.get(context, CurrencyConverterFeature.S_CURRENCY_DEFAULT_FROM, DEFAULT_FROM))
        }
        var defaultCurrencyTo by remember {
            mutableStateOf(PreferenceHelper.get(context, CurrencyConverterFeature.S_CURRENCY_DEFAULT_TO, DEFAULT_TO))
        }
        var source by remember {
            mutableStateOf(PreferenceHelper.get(context, CurrencyConverterFeature.S_CURRENCY_SOURCE, SOURCE_LATEST))
        }

        // Currencies (and date) of the rates the converter will use; reloaded after "Update now"
        var currencyList by remember { mutableStateOf<List<String>>(emptyList()) }
        var ratesDate by remember { mutableStateOf<String?>(null) }
        var isLoadingList by remember { mutableStateOf(false) }
        var isUpdating by remember { mutableStateOf(false) }
        var listReloads by remember { mutableIntStateOf(0) }

        LaunchedEffect(source, listReloads) {
            isLoadingList = true
            val status = if (source == SOURCE_LATEST) STATUS_LAST_DOWNLOAD
            else STATUS_BUILTIN
            // Not worth unpacking its tools for: the current choices do meanwhile
            listCurrencies(context, status, unpack = false)?.takeIf { it.second.isNotEmpty() }?.let {
                ratesDate = it.first.takeIf { date -> date != "Unknown" }
                currencyList = it.second
            }
            isLoadingList = false
        }

        fun selectSource(value: String) {
            source = value
            PreferenceHelper.put(context, CurrencyConverterFeature.S_CURRENCY_SOURCE, value)
        }

        val useLatest = source == SOURCE_LATEST
        fun setLatest(on: Boolean) = selectSource(if (on) SOURCE_LATEST else SOURCE_BUILTIN)

        SwitchButton(
            title = stringResource(R.string.f_CurrencyConvert_use_latest),
            subtitle = stringResource(
                if (useLatest) R.string.f_CurrencyConvert_source_latest_desc else R.string.f_CurrencyConvert_source_builtin_desc
            ),
            icon = Icons.Default.CloudDownload,
            checked = useLatest,
            onCheckedChange = { setLatest(it) }
        )

        // Only the latest rates are downloaded; the built-in ones come with the module
        Reveal(visible = useLatest) {
            Column {
                // As on the converter's page
                RatesButton(
                    date = ratesDate, downloadable = true, busy = isUpdating || isLoadingList,
                    onUpdate = {
                        coroutineScope.launch {
                            isUpdating = true
                            updateRatesWithToast(context)
                            isUpdating = false
                            listReloads++
                        }
                    }
                )
            }
        }

        // A gap before the default currencies (with the rows' own, as wide as a section's)
        Spacer(modifier = Modifier.height(4.dp))

        // Without its tools (or while loading) only the current choices can be picked
        val choices = currencyList.ifEmpty { listOf(defaultCurrencyFrom, defaultCurrencyTo).distinct().sorted() }

        FeatureButton(
            title = stringResource(R.string.f_CurrencyConvert_convFrom),
            icon = Icons.Default.NorthEast,
            buttonContent = {
                CurrencyDropdown(
                    currency = defaultCurrencyFrom,
                    currencies = choices,
                    enabled = !isLoadingList,
                    loading = isLoadingList,
                    onSelect = {
                        defaultCurrencyFrom = it
                        PreferenceHelper.put(context, CurrencyConverterFeature.S_CURRENCY_DEFAULT_FROM, it)
                    },
                    modifier = Modifier.width(130.dp)
                )
            }
        )

        // Quick swap of both defaults
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FilledTonalIconButton(
                onClick = {
                    val from = defaultCurrencyFrom
                    defaultCurrencyFrom = defaultCurrencyTo
                    defaultCurrencyTo = from
                    PreferenceHelper.put(context, CurrencyConverterFeature.S_CURRENCY_DEFAULT_FROM, defaultCurrencyFrom)
                    PreferenceHelper.put(context, CurrencyConverterFeature.S_CURRENCY_DEFAULT_TO, defaultCurrencyTo)
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.SwapVert, contentDescription = stringResource(R.string.f_CurrencyConvert_swap_cd))
            }
        }

        FeatureButton(
            title = stringResource(R.string.f_CurrencyConvert_convTo),
            icon = Icons.Default.SouthEast,
            buttonContent = {
                CurrencyDropdown(
                    currency = defaultCurrencyTo,
                    currencies = choices,
                    enabled = !isLoadingList,
                    loading = isLoadingList,
                    onSelect = {
                        defaultCurrencyTo = it
                        PreferenceHelper.put(context, CurrencyConverterFeature.S_CURRENCY_DEFAULT_TO, it)
                    },
                    modifier = Modifier.width(130.dp)
                )
            }
        )
    }
}
