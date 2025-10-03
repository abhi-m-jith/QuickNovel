package com.lagradost.quicknovel.providers

import android.util.Log
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.HeadMainPageResponse
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

class WuxiaBoxProvider :  MainAPI() {
    override val name = "WuxiaBox"
    override val mainUrl = "https://www.wuxiabox.com"
    override val iconId = R.drawable.icon_wuxiabox

    override val hasMainPage = true

    override val mainCategories = listOf(
        "All" to "all",
        "Completed" to "Completed",
        "Ongoing" to "Ongoing"
    )
    override val tags = listOf(
        "All" to "all",
        "Fan-Fiction" to "fan-fiction",
        "Faloo" to "faloo",
        "Action" to "action",
        "Adventure" to "adventure",
        "Comedy" to "comedy",
        "Contemporary Romance" to "contemporary-romance",
        "Drama" to "drama",
        "Eastern Fantasy" to "eastern-fantasy",
        "Fantasy" to "fantasy",
        "Fantasy Romance" to "fantasy-romance",
        "Gender Bender" to "gender-bender",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Josei" to "josei",
        "Lolicon" to "lolicon",
        "Magical Realism" to "magical-realism",
        "Martial Arts" to "martial-arts",
        "Mecha" to "mecha",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "School Life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shounen" to "shounen",
        "Shounen Ai" to "shounen-ai",
        "Slice of Life" to "slice-of-life",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Tragedy" to "tragedy",
        "Video Games" to "video-games",
        "Wuxia" to "wuxia",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
        "Yaoi" to "yaoi",
        "Two-dimensional" to "two-dimensional",
        "Erciyuan" to "erciyuan",
        "Game" to "game",
        "Military" to "military",
        "Urban Life" to "urban-life",
        "Yuri" to "yuri",
        "Chinese" to "chinese",
        "Japanese" to "japanese",
        "Hentai" to "hentai",
        "Isekai" to "isekai",
        "Magic" to "magic",
        "Shoujo Ai" to "shoujo-ai",
        "Urban" to "urban",
        "Virtual Reality" to "virtual-reality",
        "Wuxia Xianxia" to "wuxia_xianxia",
        "Official Circles" to "official_circles",
        "Science Fiction" to "science_fiction",
        "Suspense Thriller" to "suspense_thriller",
        "Travel Through Time" to "travel_through_time"
    )

    override val orderBys = listOf("New" to "newstime", "Popular" to "onclick", "Updates" to "lastdotime")

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        var p=page;
        if(p>0){
            p--;
        }
        val url = "$mainUrl/list/${tag}/${mainCategory}-${orderBy}-${p}.html"
        val document = app.get(url, timeout = 60).document

        return HeadMainPageResponse(
            url,
            list = document.select("li.novel-item").mapNotNull { select ->
                val node = select.selectFirst("a[title]") ?: return@mapNotNull null
                val href = node.attr("href") ?: return@mapNotNull null
                val title = node.attr("title") ?: node.selectFirst("h4.novel-title")?.text() ?: return@mapNotNull null

                val chapterText = select.select("div.novel-stats span")
                    .firstOrNull { it.text().contains("Chapters", true) }
                    ?.text()
                    ?: ""
                val chapterCount = Regex("""\d+""").find(chapterText)?.value

                val cover = select.selectFirst("img")?.let {
                    it.attr("data-src").ifBlank { it.attr("src") }
                }

                newSearchResponse(
                    name = title,
                    url = href
                ) {
                    posterUrl = fixUrlNull(cover)
                    totalChapterCount=chapterCount
                }
            }
        )
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document


        val title = document.selectFirst("h1.novel-title")?.text()?.trim() ?: ""
        val author = document.selectFirst("div.author [itemprop=author]")?.text()?.trim() ?: ""
        val synopsis = document.selectFirst("meta[itemprop=description]")?.attr("content")
            ?: document.selectFirst("div.summary, div.desc, #intro")?.text()
            ?: ""

        val cover = document.selectFirst("div.fixed-img img")?.let {
            it.attr("data-src").ifBlank { it.attr("src") }
        }


        val status = document.selectFirst("div.header-stats strong:matches(Ongoing|Completed)")?.text()


        // Extract bookId from the novel URL
        val bookId = url.substringAfterLast("/").substringBefore(".html")


        val chapters = mutableListOf<ChapterData>()

        var currentPage = 0
        while (true) {
            val chaptersPageUrl = "$mainUrl/e/extend/fy.php?page=$currentPage&wjm=$bookId&X-Requested-With=XMLHttpRequest&_=${System.currentTimeMillis()}"

            val doc = app.get(chaptersPageUrl).document
            val chapterElements = doc.select("ul.chapter-list li")

            if (chapterElements.isEmpty()) break

            val pageChapters = chapterElements.mapNotNull { li ->
                val link = li.selectFirst("a") ?: return@mapNotNull null
                val url = fixUrl(link.attr("href"))
                val title = link.selectFirst("strong.chapter-title")?.text()?.trim().orEmpty()

                newChapterData(
                    name = title,
                    url = url,
                )
            }

            chapters.addAll(pageChapters)
            currentPage++
        }





        return newStreamResponse(title,fixUrl(url), chapters) {
            this.author = author
            this.posterUrl = fixUrlNull(cover)
            this.synopsis = synopsis
            setStatus(status)
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val fullUrl = fixUrl(url)
        Log.d("WuxiaBoxProvider", "Loading Chapter HTML from URL: $fullUrl")

        return try {
            val document = app.get(fullUrl).document

            // Select the actual chapter text container
            val contentElement = document.selectFirst("div.chapter-content")

            // Remove ad placeholders/images
            contentElement?.select("img[src*=disable-blocker.jpg], script, div[align=center]")?.forEach { it.remove() }

            // Get the HTML string for display
            val content = contentElement?.html()
            if (content != null) {
                Log.d("WuxiaBoxProvider", "Chapter Content Loaded Successfully")
                content
            } else {
                Log.e("WuxiaBoxProvider", "Chapter Content NOT FOUND")
                null
            }
        } catch (e: Exception) {
            Log.e("WuxiaBoxProvider", "Failed to load chapter HTML: ${e.message}")
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {


        var currentPage = 0
        val results = mutableListOf<SearchResponse>()
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")

        val response = app.post(
            "$mainUrl/e/search/index.php",
            data = mapOf(
                "show" to "title",
                "tempid" to "1",
                "tbname" to "news",
                "keyboard" to encodedQuery // your search keyword
            )
        )

        // The server redirects with url result/?searchid=XXXXX
        val redirectValue = response.url

        // Regex to pull out the digits after searchid=
        val searchId = Regex("searchid=(\\d+)").find(redirectValue)?.groupValues?.get(1)

        while (true) {
            val url = "$mainUrl/e/search/result/index.php?page=$currentPage&searchid=$searchId"
            val document = app.get(url, timeout = 60).document

            val pageResults = document.select("li.novel-item").mapNotNull { element ->
                val node = element.selectFirst("a[title]") ?: return@mapNotNull null
                val href = fixUrl(node.attr("href"))
                val title = node.attr("title")
                    .ifBlank { element.selectFirst("h4.novel-title")?.text() }
                    ?: return@mapNotNull null

                val cover = element.selectFirst("img")?.let {
                    it.attr("data-src").ifBlank { it.attr("src") }
                }

                val chapterText = element.select("div.novel-stats span")
                    .firstOrNull { it.text().contains("Chapters", true) }
                    ?.text().orEmpty()
                val chapterCount = Regex("""\d+""").find(chapterText)?.value

                newSearchResponse(title, href) {
                    posterUrl = fixUrlNull(cover)
                    totalChapterCount = chapterCount
                }
            }

            if (pageResults.isEmpty()) break

            results.addAll(pageResults)
            currentPage++
        }

        return results


    }

}