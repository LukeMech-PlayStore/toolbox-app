package org.lukemech.lmtoolbox.features.electronics.series_parallel

import kotlin.math.abs
import org.lukemech.lmtoolbox.features.electronics.common.ESeries

/** Pairs of [series] standard values that [combine] to within 2% of [target], closest first. */
internal fun findPairs(series: ESeries.Series, target: Double, combine: (Double, Double) -> Double): List<Pair<Double, Double>> {
    val candidates = ESeries.valuesIn(series, target / 100, target * 100)
    val out = ArrayList<Pair<Double, Double>>()
    for (i in candidates.indices) for (j in i until candidates.size) {
        val value = combine(candidates[i], candidates[j])
        if (abs(value - target) / target < 0.02) out.add(candidates[i] to candidates[j])
    }
    return out.sortedBy { abs(combine(it.first, it.second) - target) }.take(6)
}
