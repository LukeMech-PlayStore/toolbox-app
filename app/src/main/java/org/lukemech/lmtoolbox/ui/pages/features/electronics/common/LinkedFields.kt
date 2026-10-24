package org.lukemech.lmtoolbox.ui.pages.features.electronics.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import org.lukemech.lmtoolbox.features.electronics.common.fieldText
import org.lukemech.lmtoolbox.features.electronics.common.parseValue

/**
 * Fields of which any [count] give the others (Ohm's law: two of U, I, R, P): the [count] typed last
 * are the inputs, the others are computed ([solve]) and shown filled in. Typing into a computed one,
 * or just tapping it, makes it an input in place of the one typed earliest.
 */
class LinkedFields<K>(private val count: Int, initial: List<Pair<K, String>>) {
    private val texts = mutableStateMapOf<K, String>().apply { putAll(initial) }

    /** The inputs, the earliest typed first. */
    private val inputs = mutableStateListOf<K>().apply { addAll(initial.map { it.first }.takeLast(count)) }

    fun isInput(key: K) = key in inputs

    fun text(key: K) = texts[key].orEmpty()

    fun edit(key: K, text: String) {
        texts[key] = text
        inputs.remove(key)
        if (text.isNotBlank()) {
            inputs.add(key)
            while (inputs.size > count) inputs.removeAt(0)
        }
    }

    /**
     * The values of all the fields: the inputs' as typed and, with [count] of them, the others
     * [solver] gives from them (a missing or non-finite one stays empty).
     */
    fun solve(solver: (Map<K, Double>) -> Map<K, Double>): Map<K, Double> {
        val given = inputs.mapNotNull { key -> parseValue(text(key))?.let { key to it } }.toMap()
        if (given.size < count) return given
        return solver(given).filterValues { it.isFinite() } + given
    }

    companion object {
        /**
         * Keeps them as the page is made again (e.g. turned): the texts ("U=10") and the inputs ("@U"), in
         * order, by their keys' names ([key] back from one).
         */
        fun <K> saver(count: Int, key: (String) -> K) = Saver<LinkedFields<K>, ArrayList<String>>(
            save = { fields -> ArrayList(fields.inputs.map { "@$it" } + fields.texts.map { (k, v) -> "$k=$v" }) },
            restore = { saved ->
                LinkedFields<K>(count, emptyList()).apply {
                    saved.forEach { entry ->
                        if (entry.startsWith("@")) inputs.add(key(entry.drop(1)))
                        else texts[key(entry.substringBefore('='))] = entry.substringAfter('=')
                    }
                }
            }
        )
    }
}

/** [LinkedFields] kept for the page (as it's turned too), starting with [initial] as the inputs. */
@Composable
inline fun <reified K : Enum<K>> rememberLinkedFields(count: Int, vararg initial: Pair<K, String>): LinkedFields<K> =
    rememberSaveable(saver = LinkedFields.saver(count) { enumValueOf<K>(it) }) { LinkedFields(count, initial.toList()) }

/**
 * [fields]' [key] as a [UnitField]: an input as typed, or its computed value from [values] (see
 * [LinkedFields.solve]), marked, to [sigFigs] significant figures.
 */
@Composable
fun <K> LinkedField(
    fields: LinkedFields<K>,
    values: Map<K, Double>,
    key: K,
    label: String,
    unit: String = "",
    sigFigs: Int = 4,
) {
    val input = fields.isInput(key)
    val text = if (input) fields.text(key) else values[key]?.let { fieldText(it, unit, sigFigs) }.orEmpty()
    val computed = !input && text.isNotEmpty()
    UnitField(
        label = label,
        value = text,
        onValueChange = { fields.edit(key, it) },
        unit = unit,
        computed = computed,
        onFocus = { if (computed) fields.edit(key, text) }
    )
}
