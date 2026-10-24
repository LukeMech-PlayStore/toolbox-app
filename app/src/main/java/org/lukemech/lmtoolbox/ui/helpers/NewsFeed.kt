package org.lukemech.lmtoolbox.ui.helpers

import org.lukemech.lmtoolbox.ui.components.Reveal
import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.lukemech.lmtoolbox.BuildConfig
import org.lukemech.lmtoolbox.R
import org.lukemech.lmtoolbox.helpers.PreferenceHelper
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class NewsItem(
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val actionUrl: String,
    val categories: List<String>,
    val createdOn: String
)

object NewsRepository {
    private const val API_URL = "https://toolbox.lukemech.org/api/news"

    private const val PREFS_CACHE_FILE = "news_cache"
    private const val KEY_JSON_DATA = "json_data"
    private const val KEY_LAST_UPDATE = "last_update_timestamp"
    private const val CACHE_EXPIRATION_MS = 2 * 60 * 60 * 1000L
    // --- Public Methods ---

    fun getCachedNews(context: Context): List<NewsItem> {
        val jsonString = PreferenceHelper.get(context, KEY_JSON_DATA, "", PREFS_CACHE_FILE)
        if (jsonString.isEmpty()) return emptyList()

        return parseJson(jsonString)
    }

    // Returns Nullable List: null = error, empty list = no news, populated list = success
    suspend fun refreshNews(context: Context, forceRefresh: Boolean = false): List<NewsItem>? {
        val lastUpdate = PreferenceHelper.get(context, KEY_LAST_UPDATE, 0L, PREFS_CACHE_FILE)
        val currentTime = System.currentTimeMillis()
        val isExpired = (currentTime - lastUpdate) > CACHE_EXPIRATION_MS

        if (!forceRefresh && !isExpired) {
            return getCachedNews(context)
        }

        return fetchAndSave(context)
    }

    // --- Private Methods ---

    private suspend fun fetchAndSave(context: Context): List<NewsItem>? = withContext(Dispatchers.IO) {
        try {
            // Append Language Parameter
            val language = Locale.getDefault().language // e.g., "en", "pl"
            val urlString = "$API_URL?lang=$language"

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val jsonString = url.readText()
                // Kept even when nothing's left for this version: else the old cache came back each launch
                saveToCache(context, jsonString)
                parseJson(jsonString)
            } else {
                null // Return null on server error
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null // Return null on network error
        }
    }

    private fun saveToCache(context: Context, jsonString: String) {
        PreferenceHelper.put(context, KEY_JSON_DATA, jsonString, PREFS_CACHE_FILE)
        PreferenceHelper.put(context, KEY_LAST_UPDATE, System.currentTimeMillis(), PREFS_CACHE_FILE)
    }

    private fun parseJson(jsonString: String): List<NewsItem> {
        val list = mutableListOf<NewsItem>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                // Optional app version range (inclusive), e.g. "downVtresh": "26.10.13"
                if (!isVersionInRange(obj.optString("downVtresh", ""), obj.optString("upVtresh", ""))) continue

                // Parse categories (supports both "categories": [] and legacy "category": "")
                val categoriesList = mutableListOf<String>()
                val jsonCategories = obj.optJSONArray("categories")

                if (jsonCategories != null) {
                    for (j in 0 until jsonCategories.length()) {
                        categoriesList.add(jsonCategories.optString(j))
                    }
                } else {
                    // Fallback to legacy single category field
                    val singleCat = obj.optString("category", "")
                    if (singleCat.isNotEmpty()) {
                        categoriesList.add(singleCat)
                    } else {
                        categoriesList.add("General")
                    }
                }

                list.add(
                    NewsItem(
                        title = obj.optString("title", "News"),
                        subtitle = obj.optString("subtitle", ""),
                        imageUrl = obj.optString("imageUrl", ""),
                        actionUrl = obj.optString("actionUrl", ""),
                        categories = categoriesList,
                        createdOn = obj.optString("createdOn", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun isVersionInRange(downVtresh: String, upVtresh: String): Boolean {
        val current = parseVersion(BuildConfig.VERSION_NAME) ?: return true
        val min = parseVersion(downVtresh)
        val max = parseVersion(upVtresh)
        if (min != null && compareVersions(current, min) < 0) return false
        if (max != null && compareVersions(current, max) > 0) return false
        return true
    }

    // "26.10.13-2.dbg" -> [26, 10, 13, 2], a same-day release's letter as a number: "26.10.17a.dbg" -> [26, 10, 17, 1];
    // null if there are no numeric parts
    private fun parseVersion(version: String): List<Int>? =
        Regex("\\d+|(?<=\\d)[a-z]\\b").findAll(version)
            .map { it.value.toIntOrNull() ?: (it.value[0] - 'a' + 1) }.toList().ifEmpty { null }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }
}

// 3. UI Component
@Composable
fun NewsFeedSection(
    forceRefreshTrigger: Boolean = false,
    onRefreshFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // 1. INIT STATE: Load cache immediately
    var newsList by remember { mutableStateOf(NewsRepository.getCachedNews(context)) }

    // 2. REFRESH LOGIC
    LaunchedEffect(Unit, forceRefreshTrigger) {
        val freshData = NewsRepository.refreshNews(context, forceRefresh = forceRefreshTrigger)

        // Only update if freshData is NOT null (network success)
        // If network fails, we keep showing the cached data
        if (freshData != null) {
            newsList = freshData
        }

        if (forceRefreshTrigger) {
            onRefreshFinished()
        }
    }

    // 3. Nothing (no cache and no net, or none for this version): the section goes, smoothly as
    // buttons do; the last news stay while it does
    val last = remember { arrayOf(newsList) }
    if (newsList.isNotEmpty()) last[0] = newsList
    val shown = last[0]
    Reveal(visible = newsList.isNotEmpty()) {
        // It's space to what's under it goes with it
        Column {
            NewsSection(shown) { item ->
                if (item.actionUrl.isNotEmpty()) {
                    try {
                        uriHandler.openUri(item.actionUrl)
                    } catch (_: Exception) { }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NewsSection(newsList: List<NewsItem>, onOpen: (NewsItem) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.s_news_feed),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(newsList) { item ->
                NewsCard(item) { onOpen(item) }
            }
        }
    }
}

@Composable
fun NewsCard(item: NewsItem, onClick: () -> Unit) {
    val context = LocalContext.current
    val timeAgo = remember(item.createdOn) { getRelativeTimeSpan(context, item.createdOn) }

    // 1. State to track rendered lines
    var titleLines by remember { mutableIntStateOf(0) }
    var subtitleLines by remember { mutableIntStateOf(0) }

    // 2. Calculate Gradient Start Point
    // The higher the line count, the smaller the float (0.0 is top, 1.0 is bottom).
    // We want the darkness to start earlier (higher up) if there is more text.
    val gradientStartPoint = remember(titleLines, subtitleLines) {
        val totalLines = titleLines + subtitleLines
        when {
            totalLines <= 2 -> 0.65f // Minimal text: Dark starts at 65% down
            totalLines == 3 -> 0.5f  // Medium text: Dark starts at 50% down
            totalLines == 4 -> 0.35f // Heavy text: Dark starts at 35% down
            else -> 0.2f             // Max text (5 lines): Dark starts at 20% down
        }
    }

    Card(
        modifier = Modifier
            .width(280.dp)
            .height(160.dp)
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Background Image
            if (item.imageUrl.isNotEmpty()) {
                Crossfade(targetState = rememberUrlImage(item.imageUrl), label = "newsImage") { image ->
                    if (image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // 2. DYNAMIC Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.75f),     // Top (Fixed)
                                0.25f to Color.Transparent,                 // Clear Zone Start (Fixed)
                                gradientStartPoint to Color.Black.copy(alpha = 0.6f),    // DYNAMIC Clear Zone End
                                1.0f to Color.Black.copy(alpha = 0.95f)     // Bottom (Fixed)
                            )
                        )
                    )
            )

            // --- Date Badge ---
            if (timeAgo.isNotEmpty()) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = timeAgo,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // --- Categories ---
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item.categories.forEach { category ->
                    val isImportant = category.startsWith("!!!")
                    val displayCategory = if (isImportant) category.removePrefix("!!!") else category
                    val containerColor = if (isImportant) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    val contentColor = if (isImportant) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = containerColor.copy(alpha = 0.9f)
                    ) {
                        Text(
                            text = displayCategory.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = contentColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // --- Text Content ---
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                // TITLE
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    // KEY CHANGE: Capture line count
                    onTextLayout = { textLayoutResult ->
                        titleLines = textLayoutResult.lineCount
                    }
                )

                // SUBTITLE
                if (item.subtitle.isNotEmpty()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        // KEY CHANGE: Capture line count
                        onTextLayout = { textLayoutResult ->
                            subtitleLines = textLayoutResult.lineCount
                        }
                    )
                }
            }
        }
    }
}

// --- Helper Functions ---

/**
 * Parses date string "2025-11-19_15:31" and returns formatted string using Resources.
 */
private fun getRelativeTimeSpan(context: Context, dateString: String): String {
    if (dateString.isEmpty()) return ""

    return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm")
        val dateTime = LocalDateTime.parse(dateString, formatter)

        val today = LocalDate.now()
        val date = dateTime.toLocalDate()

        val daysDiff = ChronoUnit.DAYS.between(date, today)
        val timeOnly = dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

        when (daysDiff) {
            0L -> context.getString(R.string.f_news_feed_today, timeOnly)
            1L -> context.getString(R.string.s_news_feed_yesterday, timeOnly)
            else -> context.getString(R.string.s_news_feed_days_ago, daysDiff.toString())
        }
    } catch (_: Exception) {
        ""
    }
}