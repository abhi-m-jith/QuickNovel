package com.lagradost.quicknovel.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LibraryHelper
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.USER_AGENT
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newSearchResponse

object LibReadProvider : LibReadAPI()

open class LibReadAPI : FreewebnovelAPI() {
    override val name = "LibRead"
    override val mainUrl = "https://libread.com"

    //for some reason, now is freewebnovel
    override val secondUrl = "https://freewebnovel.com"
    override val hasMainPage = true

    override val removeHtml = false

    override val iconId = R.drawable.icon_libread

    override val iconBackgroundId = R.color.libread_header_color
    override val rateLimitTime = 1000L
    override val orderBys = listOf(
        "Latest Release" to "latest-release",
        "Latest Novels" to "latest-novels",
        "Completed Novels" to "completed-novels"
    )

    override var isChapterCountFilterNeeded=true

    override fun FABFilterApplied() {
        lastLoadedPage=1
        seenUrls.clear()
    }
    override fun ResetFiltersandPage() {
        FABFilterApplied()
        ChapterFilter= LibraryHelper.ChapterCountFilter.ALL
    }

    override fun getAcode(url: String): String =
        url.substringAfterLast("/").substringBeforeLast("-")

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val collectedResults = mutableListOf<SearchResponse>()
        var currentPage = if (page <= lastLoadedPage) lastLoadedPage else page
        var pagesFetched = 0 // optional safety cap

        suspend fun fetchPage(pp: Int): Pair<List<SearchResponse>, Boolean> {
            val url = if (tag.isNullOrBlank()) "$mainUrl/sort/${orderBy ?: "latest-release"}/$pp" else "$mainUrl/genre/$tag/$pp"
            val document = app.get(url).document
            val rawItems = document.select("div.ul-list1.ul-list1-2.ss-custom > div.li-row")
            val isLast = rawItems.isNotEmpty()

            val filtered_list=rawItems.mapNotNull { selectt ->
                val h3 = selectt.selectFirst("h3.tit > a") ?: return@mapNotNull null

                val title =h3.attr("title") ?: return@mapNotNull null

                val href = h3.attr("href")
                val chapterCount = selectt.selectFirst("div.item:nth-child(3) span.s1")
                    ?.text()
                    ?.substringBefore(" ")

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
                    posterUrl = fixUrlNull(selectt.selectFirst("div.pic > a > img")?.attr("src"))
                    latestChapter = selectt.select("div.item")[2].selectFirst("> div > a")?.text()
                    totalChapterCount = chapterCount
                }
            }


            lastLoadedPage = pp
            return Pair(filtered_list, isLast)
        }
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

        val url = if (tag.isNullOrBlank()) "$mainUrl/sort/${orderBy ?: "latest-release"}/$lastLoadedPage" else "$mainUrl/genre/$tag/$lastLoadedPage"
        return HeadMainPageResponse(url, collectedResults)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post(
            "$mainUrl/search",
            headers = mapOf(
                "referer" to mainUrl,
                "x-requested-with" to "XMLHttpRequest",
                "content-type" to "application/x-www-form-urlencoded",
                "accept" to "*/*",
                "user-agent" to USER_AGENT
            ),
            data = mapOf("searchkey" to query)
        ).document

        return document.select("div.li-row > div.li > div.con").mapNotNull { h ->
            val h3 = h.selectFirst("div.txt > h3.tit > a") ?: return@mapNotNull null

            newSearchResponse(
                name = h3.attr("title") ?: return@mapNotNull null,
                url = h3.attr("href") ?: return@mapNotNull null
            ) {
                posterUrl = fixUrlNull(h.selectFirst("div.pic img")?.attr("src"))
                //latestChapter = h.select("div.item")[2].selectFirst("> div > a")?.text()
            }
        }
    }

    data class LibReadCommentsResponse(
        @JsonProperty("data") val data: LibReadCommentData? = null
    )

    data class LibReadCommentData(
        @JsonProperty("is_end") val isEnd: Boolean? = null,
        @JsonProperty("data_list") val dataList: List<LibReadCommentItem>? = null
    )

    data class LibReadCommentItem(
        @JsonProperty("content") val content: String? = null,
        @JsonProperty("created_at") val createdAt: String? = null,
        @JsonProperty("user_info") val userInfo: LibReadUserInfo? = null
    )

    data class LibReadUserInfo(
        @JsonProperty("nickname") val nickname: String? = null,
        @JsonProperty("picture") val picture: String? = null
    )

    data class ChaptersResponse(
        @JsonProperty("html") val html: String
    )
}