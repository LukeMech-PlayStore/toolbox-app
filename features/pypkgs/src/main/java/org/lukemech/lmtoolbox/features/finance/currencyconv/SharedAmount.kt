package org.lukemech.lmtoolbox.features.finance.currencyconv

/**
 * What a shared text says about money: its [amount] (a plain number, "1234.56") and the
 * [currencies] it names, in order ("100 USD to EUR" -> USD, EUR). Candidates only: a code counts once
 * the rates have it.
 */
data class SharedAmount(val amount: String?, val currencies: List<String>)

/** Symbols as the codes they stand for, the longer first ("US$" before "$"). */
private val SYMBOLS = listOf(
    "US$" to "USD", "R$" to "BRL", "C$" to "CAD", "A$" to "AUD", "NZ$" to "NZD", "HK$" to "HKD", "S$" to "SGD",
    "zł" to "PLN", "Kč" to "CZK", "Ft" to "HUF", "lei" to "RON", "лв" to "BGN",
    "$" to "USD", "€" to "EUR", "£" to "GBP", "¥" to "JPY", "₹" to "INR", "₴" to "UAH", "₽" to "RUB",
    "₺" to "TRY", "₩" to "KRW", "₪" to "ILS", "₱" to "PHP", "฿" to "THB",
)

/** A number, its thousands grouped by spaces, dots or commas ("1 234,56", "1,234.56", "12.5"). */
private val NUMBER = Regex("""\d{1,3}(?:[ \u00A0\u202F.,]\d{3})+(?:[.,]\d+)?|\d+(?:[.,]\d+)?""")

/** Three letters on their own ("usd", "EUR"). */
private val CODE = Regex("""(?<!\p{L})\p{L}{3}(?!\p{L})""")

/** [text]'s amount and currencies (see [SharedAmount]). */
fun parseSharedAmount(text: String): SharedAmount {
    val found = ArrayList<Pair<Int, String>>()
    val taken = BooleanArray(text.length)
    for ((symbol, code) in SYMBOLS) {
        var from = 0
        while (true) {
            val at = text.indexOf(symbol, from, ignoreCase = symbol.any { it.isLetter() })
            if (at < 0) break
            if ((at until at + symbol.length).none { taken[it] }) {
                found.add(at to code)
                (at until at + symbol.length).forEach { taken[it] = true }
            }
            from = at + symbol.length
        }
    }
    CODE.findAll(text).filter { m -> m.range.none { taken[it] } }.forEach { found.add(it.range.first to it.value.uppercase()) }

    val amount = NUMBER.find(text)?.value?.let(::plainNumber)
    return SharedAmount(amount, found.sortedBy { it.first }.map { it.second }.distinct())
}

/**
 * [number] with its grouping gone and a dot as its decimal point. With both a dot and a comma, the last
 * is the decimal one; one alone is a decimal point unless three digits follow it once (not after a 0:
 * "1,500" is 1500, "0,500" a half) or it repeats ("1.000.000").
 */
private fun plainNumber(number: String): String {
    val n = number.replace(Regex("""[ \u00A0\u202F]"""), "")
    val dot = n.lastIndexOf('.')
    val comma = n.lastIndexOf(',')
    val decimal = when {
        dot >= 0 && comma >= 0 -> maxOf(dot, comma)
        dot < 0 && comma < 0 -> -1
        else -> {
            val at = maxOf(dot, comma)
            val separator = n[at]
            val grouping = n.count { it == separator } > 1 || (n.length - at - 1 == 3 && n.substring(0, at) != "0")
            if (grouping) -1 else at
        }
    }
    return n.filterIndexed { i, c -> c.isDigit() || i == decimal }.replace(',', '.')
}
