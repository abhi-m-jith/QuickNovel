package com.lagradost.quicknovel.providers

import android.util.Log
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LibraryHelper
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.setStatus
import org.jsoup.Jsoup
import kotlin.math.roundToInt

class NovelFireProvider :  MainAPI() {
    override val name = "NovelFire"
    override val mainUrl = "https://novelfire.net"
    override val iconId = R.drawable.icon_novelfire

    override val hasMainPage = true

    override val mainCategories = listOf(
        "All" to "status-all",
        "Completed" to "status-completed",
        "Ongoing" to "status-ongoing"
    )
    override val orderBys =
        listOf("New" to "sort-new", "Popular" to "sort-popular", "Updates" to "sort-latest-release")

    override val tags = listOf(
        "All" to "all",
        "Action" to "action",
        "Adult" to "adult",
        "Adventure" to "adventure",
        "Anime" to "anime",
        "Arts" to "arts",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Eastern" to "eastern",
        "Ecchi" to "ecchi",
        "Fan-fiction" to "fan-fiction",
        "Fantasy" to "fantasy",
        "Game" to "game",
        "Gender-bender" to "gender-bender",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Isekai" to "isekai",
        "Josei" to "josei",
        "Lgbt" to "lgbt",
        "Magic" to "magic",
        "Magical-realism" to "magical-realism",
        "Manhua" to "manhua",
        "Martial-arts" to "martial-arts",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Military" to "military",
        "Modern-life" to "modern-life",
        "Movies" to "movies",
        "Mystery" to "mystery",
        "Other" to "other",
        "Psychological" to "psychological",
        "Realistic-fiction" to "realistic-fiction",
        "Reincarnation" to "reincarnation",
        "Romance" to "romance",
        "School-life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shoujo-ai" to "shoujo-ai",
        "Shounen" to "shounen",
        "Shounen-ai" to "shounen-ai",
        "Slice-of-life" to "slice-of-life",
        "Smut" to "smut",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "System" to "system",
        "Tragedy" to "tragedy",
        "Urban" to "urban",
        "Urban-life" to "urban-life",
        "Video-games" to "video-games",
        "War" to "war",
        "Wuxia" to "wuxia",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri"
    )

    var lastLoadedPage=1
    val minBooksNeeded=11
    val maxPagesToFetch=10

    val seenUrls = HashSet<String>()

    override fun FABFilterApplied() {
        lastLoadedPage=1
        seenUrls.clear()
    }
    override fun ResetFiltersandPage() {
        FABFilterApplied()
        ChapterFilter=LibraryHelper.ChapterCountFilter.ALL
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        isOpeningBook=true
        isChapterCountFilterNeeded=true
        val collectedResults = mutableListOf<SearchResponse>()
        var currentPage = if (page <= lastLoadedPage) lastLoadedPage else page
        var pagesFetched = 0 // optional safety cap

        suspend fun fetchPage(p: Int): Pair<List<SearchResponse>, Boolean> {
            val url = "$mainUrl/genre-${tag}/${orderBy}/${mainCategory}/all-novel?page=$p"
            val document = app.get(url).document
            val rawItems = document.select("li.novel-item")
            val isLast = rawItems.isNotEmpty()

            val filtered_list=rawItems.mapNotNull { select ->
                val node = select.selectFirst("a[title]") ?: return@mapNotNull null
                val href = node.attr("href") ?: return@mapNotNull null
                val title = node.attr("title") ?: node.selectFirst("h4.novel-title")?.text() ?: return@mapNotNull null

                val chapterText = select.selectFirst(".novel-stats")?.ownText() ?: ""
                val chapterCount = Regex("""\d+""").find(chapterText)?.value

                // Apply filter
                if (!LibraryHelper.isChapterCountInRange(ChapterFilter, chapterCount.toString())) {
                    return@mapNotNull null
                }

                // Skip duplicates across loads
                if (!seenUrls.add(href)) {
                    return@mapNotNull null
                }

                newSearchResponse(
                    name = title,
                    url = href
                ) {
                    posterUrl = fixUrlNull(
                        select.selectFirst("img")?.attr("data-src")
                            ?: select.selectFirst("img")?.attr("src")
                    )
                    totalChapterCount = chapterCount
                }
            }


            lastLoadedPage = p
            return Pair(filtered_list, isLast)
        }




        // Keep fetching until we have enough or reach a reasonable limit
        while (collectedResults.size < minBooksNeeded && pagesFetched < maxPagesToFetch) {
            val (pageResults, hadRawItems) = fetchPage(currentPage)

            // If there were no raw items on this page, it's the end — break
            if (!hadRawItems) break

            // Add whatever passed the filters (could be empty) and continue to next page if needed
            if (pageResults.isNotEmpty()) {
                collectedResults.addAll(pageResults)
            }

            // Prepare for next iteration
            currentPage++
            pagesFetched++
        }
        //Log.d("NOVELFIRE","${collectedResults.size}")

        val url = "$mainUrl/genre-${tag}/${orderBy}/${mainCategory}/all-novel?page=${lastLoadedPage}"

        return HeadMainPageResponse(url,collectedResults)


    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        // Extract title
        val title = document.selectFirst("h1.novel-title")?.text() ?: ""

        // Extract author
        val author = document.selectFirst("div.author a[itemprop=author]")?.text() ?: ""

        // Extract description/synopsis
        val synopsis = document.selectFirst("meta[itemprop=description]")?.attr("content") ?: ""

        // Extract book ID from URL to construct image URL
        val bookId = url.substringAfterLast("/book/").substringBefore("?").substringBefore("/")
        val constructedPosterUrl = "https://novelfire.net/server-1/$bookId.jpg"

        val chapters = mutableListOf<ChapterData>()

        var currentPage = 1
        var fetchedAll = false

        while (!fetchedAll) {
            val chaptersPageUrl = "$mainUrl/book/$bookId/chapters?page=$currentPage"

            // Get the raw HTML response
            val response = app.get(chaptersPageUrl)
            val html = response.text
            val document = Jsoup.parse(html)

            // Select all chapter <li> elements
            val chapterElements = document.select("ul.chapter-list > li")

            if (chapterElements.isEmpty()) {
                fetchedAll = true
            } else {
                val pageChapters = chapterElements.mapNotNull { li ->
                    val aTag = li.selectFirst("a") ?: return@mapNotNull null
                    val title = aTag.attr("title")?.trim() ?: return@mapNotNull null
                    val url = aTag.attr("href")?.trim() ?: return@mapNotNull null

                    newChapterData(title, fixUrl(url))
                }

                chapters.addAll(pageChapters)

                // Check if there is a "next" page button
                val hasNext = document.selectFirst("li.page-item > a[rel=next]") != null
                if (hasNext) {
                    currentPage++
                } else {
                    fetchedAll = true
                }
            }
        }



        return newStreamResponse(title,fixUrl(url), chapters) {
            this.author = author
            this.posterUrl = fixUrlNull(constructedPosterUrl)
            this.synopsis = synopsis
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val fullUrl = fixUrl(url)
        Log.d("NovelFireProvider", "Loading Chapter HTML from URL: $fullUrl")

        return try {
            val document = app.get(fullUrl).document
            val contentElement = document.selectFirst("div#content")
            contentElement?.select("img[src*=disable-blocker.jpg]")?.forEach { it.remove() }

            val content = contentElement?.html()
            if (content != null) {
                Log.d("NovelFireProvider", "Chapter Content Loaded Successfully")
                content
            } else {
                Log.e("NovelFireProvider", "Chapter Content NOT FOUND")
                null
            }
        } catch (e: Exception) {
            Log.e("NovelFireProvider", "Failed to load chapter HTML: ${e.message}")
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {


        var currentPage = 1
        var fetchedAll = false
        val results = mutableListOf<SearchResponse>()
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

        while (!fetchedAll) {
            val url = "$mainUrl/search/?keyword=$encodedQuery&page=$currentPage"
            val document = app.get(url).document

            val searchList = document.select("ul.novel-list.horizontal.col2.chapters").firstOrNull()
            val novels = searchList?.select("li.novel-item") ?: emptyList()

            if (novels.isEmpty()) {
                fetchedAll = true
                break
            }

            for (element in novels) {
                val anchor = element.selectFirst("a") ?: continue
                val title = anchor.attr("title").trim()
                val novelUrl = anchor.absUrl("href")
                val coverUrl = anchor.selectFirst("img")?.absUrl("src") ?: ""

                // Extract chapter count
                val chapterStat = anchor.select("div.novel-stats").firstOrNull { stat ->
                    stat.selectFirst("i.icon-book-open") != null
                }
                val chapterText = chapterStat?.text()?.trim() ?: ""
                val chapterCount = Regex("(\\d+)").find(chapterText)?.value

                results.add(
                    newSearchResponse(title,fixUrl(novelUrl)){
                        posterUrl=coverUrl
                        totalChapterCount=chapterCount
                    }
                )
            }

            // Check if "Next ›" pagination exists
            val hasNext = document.select("ul.pagination li.page-item > a.page-link")
                .any { it.text().contains("›") }

            if (hasNext) {
                currentPage++
            } else {
                fetchedAll = true
            }
        }

        return results


    }


}