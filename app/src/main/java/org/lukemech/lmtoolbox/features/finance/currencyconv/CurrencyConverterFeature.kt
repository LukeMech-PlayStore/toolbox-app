package org.lukemech.lmtoolbox.features.finance.currencyconv

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PriceCheck
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.FeatureCategory
import org.lukemech.lmtoolbox.helpers.ModuleFeature

/** Currency converter: its pages (and settings) are in the pypkgs module (features/pypkgs, its finance/currencyconv). */
object CurrencyConverterFeature : ModuleFeature(
    route = "currencyconv",
    titleStringResId = R.string.fp_CurrencyConvert,
    keywordsResId = R.string.fk_CurrencyConvert,
    descriptionResId = R.string.fd_CurrencyConvert,
    category = FeatureCategory.Finance,
    moduleIcon = Icons.Default.PriceCheck,
    linuxExtensions = listOf("pypkgs"),
    uiClass = "org.lukemech.lmtoolbox.features.finance.currencyconv.CurrencyConverterUi",
    // Its scripts and the downloaded rates next to them
    scriptDir = "currencyconv",
) {
    const val S_DSHKEY = "CurrencyConv_!ShareTo"
    const val S_CURRENCY_DEFAULT_FROM = "currencyconv_default_from"
    const val S_CURRENCY_DEFAULT_TO = "currencyconv_default_to"
    const val S_CURRENCY_SOURCE = "currencyconv_source"

    override val preferenceKeys = listOf(S_DSHKEY, S_CURRENCY_DEFAULT_FROM, S_CURRENCY_DEFAULT_TO, S_CURRENCY_SOURCE)
}
