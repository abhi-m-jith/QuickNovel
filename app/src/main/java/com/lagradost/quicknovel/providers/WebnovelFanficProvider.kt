package com.lagradost.quicknovel.providers

import android.net.Uri
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LibraryHelper
import com.lagradost.quicknovel.LoadResponse
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ReleaseStatus
import com.lagradost.quicknovel.Score
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.UserReview
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newChapterData
import com.lagradost.quicknovel.newReview
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.providers.NovelFireAPI.AjaxChapterRoot
import com.lagradost.quicknovel.setStatus
import com.lagradost.quicknovel.util.AppUtils.toJson
import com.lagradost.quicknovel.utils.SessionCookieProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.jsoup.Jsoup
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import org.json.JSONObject
import org.jsoup.select.Elements
import kotlin.coroutines.CoroutineContext

private var cachedCookies: String? = null
private var cachedCsrfToken: String? = null

object WebnovelFanficProvider : WebnovelFanficAPI()

open class WebnovelFanficAPI : MainAPI(){
    override val name = "Webnovel (Fanfic)"
    override val mainUrl = "https://m.webnovel.com"
    override val iconId = R.drawable.icon_webnovel
    override val rateLimitTime = 500L
    override val usesCloudFlareKiller = true
    override val hasMainPage = true
    override val hasReviews = true

    companion object {
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.5735.131 Mobile Safari/537.36"
    }

    override val tags = listOf(
        "All" to "fanfic",
        "Anime & Comics" to "fanfic-anime-comics",
        "Video Games" to "fanfic-video-games",
        "Celebrities" to "fanfic-celebrities",
        "Music & Bands" to "fanfic-music-bands",
        "Movies" to "fanfic-movies",
        "Book & Literature" to "fanfic-book-literature",
        "TV" to "fanfic-tv",
        "Theater" to "fanfic-theater",
        "Others" to "fanfic-others"
    )

    override val orderBys = listOf(
        "Popular" to "1",
        "Recommended" to "2",
        "Most Collections" to "3",
        "Rating" to "4",
        "Updated" to "5"
    )

    override val mainCategories = listOf(
        "All" to "0",
        "Ongoing" to "1",
        "Completed" to "2",
    )

    private suspend fun ensureCookiesLoaded() {
        if (cachedCookies != null && cachedCsrfToken != null) return

        val cookies = SessionCookieProvider.getValidCookie(MainActivity.context)
        val csrfToken = Regex("_csrfToken=([^;]+)").find(cookies)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("CSRF token not found in cookies")

        cachedCookies = cookies
        cachedCsrfToken = csrfToken
    }

    private fun getCategoryIdFromTag(tag: String): String {
        return when (tag) {
            "fanfic-anime-comics" -> "81006"
            "fanfic-video-games" -> "81005"
            "fanfic-celebrities" -> "81002"
            "fanfic-music-bands" -> "81003"
            "fanfic-movies" -> "81007"
            "fanfic-book-literature" -> "81001"
            "fanfic-tv" -> "81008"
            "fanfic-theater" -> "81004"
            "fanfic-others" -> "81009"
            "fanfic" -> "0"
            else -> "0"
        }
    }

    override var isChapterCountFilterNeeded=true
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
        ChapterFilter= LibraryHelper.ChapterCountFilter.ALL
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        ensureCookiesLoaded()

        val slug = tag ?: "fanfic"
        val categoryId = getCategoryIdFromTag(slug)

        var currentPage = if (page <= lastLoadedPage) lastLoadedPage else page
        val collectedResults = mutableListOf<SearchResponse>()
        var pagesFetched = 0

        fun fetchPage(p: Int,Retry:Boolean=false): Pair<List<SearchResponse>, Boolean> {
            val apiUrl =
                "$mainUrl/go/pcm/category/categoryAjax?_csrfToken=$cachedCsrfToken&language=en&categoryId=$categoryId&categoryType=4&orderBy=$orderBy&pageIndex=$p&bookStatus=$mainCategory"

            val client = OkHttpClient()
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("User-Agent", MOBILE_USER_AGENT)
                .addHeader("Referer", "$mainUrl/stories/$slug")
                .addHeader("Cookie", cachedCookies!!)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw ErrorLoadingException("Empty response")

            if(!body.startsWith("{") || body.startsWith("<!DOCTYPE"))
            {
                if(Retry)
                {
                    Log.e("WEBNOVEL", "Non-JSON response detected, skipping page.")
                    return Pair(emptyList(), false)
                }
                else
                {
                    Log.e("WEBNOVEL", "Non-JSON response detected, Retrying Once after 3 second.")
                    Thread.sleep(3000)
                    return  fetchPage(p,true)
                }
            }
            else
            {
                Log.w("WEBNOVEL","Load Success !! Current Page: ${currentPage} ${collectedResults.size}")

                val json = JSONObject(body)
                val items = json.getJSONObject("data").getJSONArray("items")
                val hadAnyRawItems = items.length() > 0

                //logLong("WEBNOVEL", items.toString())

                if(!hadAnyRawItems)
                {
                    showToast("No More Books !!")
                }

                val filtered = mutableListOf<SearchResponse>()

                for (i in 0 until items.length()) {
                    val book = items.getJSONObject(i)
                    val bookId = book.optString("bookId") ?: continue
                    val link = fixUrlNull("$mainUrl/book/$bookId") ?: continue
                    if (!seenUrls.add(link)) continue // skip duplicates

                    val chapterCount = book.optString("chapterNum", "0")
                    if (!LibraryHelper.isChapterCountInRange(ChapterFilter, chapterCount.toString())) {
                        continue
                    }

                    val title = book.optString("bookName", "Untitled")
                    val cover =
                        "https://book-pic.webnovel.com/bookcover/$bookId?imageMogr2/thumbnail/180x|imageMogr2/format/webp|imageMogr2/quality/70!"


                    filtered.add(newSearchResponse(title, link) {
                        posterUrl = cover
                        totalChapterCount = chapterCount
                    })
                }

                //Log.d("WEBNOVEL","Before: ${items.length()}  Filtered: ${filtered.size}")
                lastLoadedPage = p

                return Pair(filtered, hadAnyRawItems)
            }


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

        val url = "$mainUrl/genre-${tag}/${orderBy}/${mainCategory}/all-novel?page=${lastLoadedPage}"

        return HeadMainPageResponse(url,collectedResults)
    }


    override suspend fun load(url: String): LoadResponse {
        ensureCookiesLoaded()

        val bookId = url.substringAfterLast("/")

        // Fetch book info from API
        val detailUrl = "$mainUrl/go/pcm/book/get-book-detail?_csrfToken=$cachedCsrfToken&bookId=$bookId"
        val infoRequest = Request.Builder()
            .url(detailUrl)
            .addHeader("User-Agent", MOBILE_USER_AGENT)
            .addHeader("Cookie", cachedCookies!!)
            .build()
        Log.d("WebNovel","${detailUrl}")

        val client = OkHttpClient()
        val infoResponse = client.newCall(infoRequest).execute()
        val infoBody = infoResponse.body?.string() ?: throw ErrorLoadingException("Empty book info")

        val bookInfo = JSONObject(infoBody)
            .getJSONObject("data")
            .getJSONObject("bookInfo")

        val recommendInfo= JSONObject(infoBody)
            .getJSONObject("data")
            .getJSONArray("recommendListItems")

        val title = bookInfo.optString("bookName", "Untitled")
        val author = bookInfo.optString("authorName", "Unknown")
        val description = bookInfo.optString("description", "")
        val views=bookInfo.optString("pvNum","0")
        val starscore=bookInfo.optString("totalScore","0")
        val totalvotes=bookInfo.optString("voters","0")
        val cover = "https://book-pic.webnovel.com/bookcover/$bookId?imageMogr2/thumbnail/180x|imageMogr2/format/webp|imageMogr2/quality/70!"
        val status = when (bookInfo.optInt("actionStatus", 0)) {
            30 -> ReleaseStatus.Ongoing
            50 -> ReleaseStatus.Completed
            else -> ReleaseStatus.Paused
        }

        val catalogUrl = "$mainUrl/book/$bookId/catalog"
        val catalogRequest = Request.Builder()
            .url(catalogUrl)
            .addHeader("User-Agent", MOBILE_USER_AGENT)
            .addHeader("Cookie", cachedCookies!!)
            .build()

        val catalogResponse = client.newCall(catalogRequest).execute()
        val catalogHtml = catalogResponse.body?.string() ?: throw ErrorLoadingException("Empty catalog page")

        val doc = Jsoup.parse(catalogHtml)
        val chapterLinks = doc.select("a.lh24.db.oh.fs14.clearfix.g_row.pr.pt8.pb8")

        val chapters = getChapters(chapterLinks)


        return newStreamResponse(title, fixUrl(url), chapters) {
            this.author = author
            this.posterUrl = fixUrlNull(cover)
            this.synopsis = description
            this.status= status

            this.views = views.let {
                if (it.contains("k", true)) it.replace("k", "", true).toFloatOrNull()
                    ?.times(1000)?.roundToInt()
                else if (it.contains("m", true)) it.replace("m", "", true).toFloatOrNull()
                    ?.times(1000000)?.roundToInt()
                else it.toIntOrNull()
            }

            this.peopleVoted =totalvotes.replace(Regex("[^0-9]"), "").trim().toIntOrNull() ?: 0
            score = Score.from5(starscore)
            related = getRelated(recommendInfo)
            reviewData = bookId
        }
    }

    open suspend fun getChapters(chapterLinks : Elements): List<ChapterData> {

        val chapters = mutableListOf<ChapterData>()
        for ((i, el) in chapterLinks.withIndex()) {
            val chapterUrl = el.attr("href") // Already absolute URL
            val chapterName = el.selectFirst("strong.styles_chapter_name__lQv69 > span")
                ?.text()?.trim() ?: "Chapter ${i + 1}"

            chapters.add(ChapterData(chapterName,chapterUrl))
        }

        return  chapters
    }

    suspend fun getRelated(recommendInfo: JSONArray): List<SearchResponse> {
        val responselist=mutableListOf<SearchResponse>()

        for (i in 0 until recommendInfo.length()) {
            val book = recommendInfo.optJSONObject(i) ?: continue

            val bookID = book.optString("bookId") ?: continue
            val link = fixUrlNull("$mainUrl/book/$bookID") ?: continue

            val chapterCount = book.optString("totalChapterNum", "0")
            val title = book.optString("bookName", "Untitled")
            val cover =
                "https://book-pic.webnovel.com/bookcover/$bookID?imageMogr2/thumbnail/180x|imageMogr2/format/webp|imageMogr2/quality/70!"

            responselist.add(newSearchResponse(title, link) {
                posterUrl = cover
                totalChapterCount = chapterCount
            })
        }

        return  responselist;
    }



    override suspend fun loadReviews(url: String, page: Int, data: String?): List<UserReview> {
        val id = data ?: return emptyList()
        val realUrl = "$mainUrl/go/pcm/bookReview/get-reviews?_csrfToken=$cachedCsrfToken&bookId=$id&pageIndex=$page&pageSize=20&orderBy=1&novelType=0"
        val infoRequest = Request.Builder()
            .url(realUrl)
            .addHeader("User-Agent", MOBILE_USER_AGENT)
            .addHeader("Cookie", cachedCookies!!)
            .build()
        val client = OkHttpClient()
        val infoResponse = client.newCall(infoRequest).execute()
        val infoBody = infoResponse.body?.string() ?: throw ErrorLoadingException("Empty book info")


        val reviewInfo=JSONObject(infoBody)
            .getJSONObject("data")
            .getJSONArray("bookReviewInfos")

        val Reviewlist=mutableListOf<UserReview>()

        for (i in 0 until reviewInfo.length()) {
            val review = reviewInfo.getJSONObject(i)
            val userid = review.optString("userId") ?: continue
            val usename = review.optString("userName","")
            val revdate = review.optString("createTimeFormat", "")
            val avatar = "https://user-pic.webnovel.com/userheadimg/$userid/100.jpg"

            val isSpoiler = review.optString("leakFlag", "0") == "1"
            val reviewTxt = review.optString("content", "")

            Reviewlist.add( newReview(reviewTxt) {
                username = usename
                date = revdate
                avatarUrl = fixUrlNull(avatar)
                containsSpoilers = isSpoiler
            })

        }

        return Reviewlist
    }

    override suspend fun loadHtml(url: String): String? {
        val fullUrl = fixUrl(url)
        Log.d("WebnovelFanficProvider", "Loading Chapter HTML from URL: $fullUrl")

        try {
            val document = app.get(fullUrl).document
            val contentElement = document.selectFirst("div.cha-content[data-report-l1='3'] div.cha-words")
            val content = contentElement?.html()

            if (content != null) {
                Log.d("WebnovelFanficProvider", "Chapter Content Loaded Successfully")
                return content
            } else {
                Log.e("WebnovelFanficProvider", "Chapter Content NOT FOUND in expected structure")
            }
        } catch (e: Exception) {
            Log.e("WebnovelFanficProvider", "Failed to load chapter HTML: ${e.message}")
        }

        return null
    }

    override suspend fun search(query: String,page: Int): List<SearchResponse> {
        ensureCookiesLoaded()
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val results = mutableListOf<SearchResponse>()
        val nextPage = fetchSearchPage(encodedQuery, page)
        results.addAll(nextPage.first)
        isLastPage = nextPage.second
        Log.e("WEBNOVEL","Current Page: ${lastLoadedPage}")
        return results
    }
    private fun fetchSearchPage(encodedQuery: String, pageIndex: Int,Retry:Boolean=false): Pair<List<SearchResponse>, Boolean> {
        val url = "$mainUrl/go/pcm/search/result?_csrfToken=$cachedCsrfToken&pageIndex=$pageIndex&type=fanfic&keywords=$encodedQuery"

        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", MOBILE_USER_AGENT)
            .addHeader("Referer", "$mainUrl/fanfic-search?keywords=$encodedQuery")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("Cookie", cachedCookies!!)
            .build()

        val response = OkHttpClient().newCall(request).execute()
        val body = response.body?.string() ?: return Pair(emptyList(), true)

        if(!body.startsWith("{") || body.startsWith("<!DOCTYPE"))
        {
            if(Retry)
            {
                Log.e("WEBNOVEL", "Non-JSON response detected, skipping page.")
                return Pair(emptyList(), false)
            }
            else
            {
                Log.e("WEBNOVEL", "Non-JSON response detected, Retrying Once after 3 second.")
                Thread.sleep(3000)
                return  fetchSearchPage(encodedQuery,pageIndex,true)
            }
        }
        else{
            Log.w("WEBNOVEL","Load Success !! Query: ${encodedQuery} Current Page: ${pageIndex} ")
            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: return Pair(emptyList(), true)
            val fanficData = data.optJSONObject("fanficBookInfo") ?: return Pair(emptyList(), true)
            val books = fanficData.optJSONArray("fanficBookItems") ?: return Pair(emptyList(), true)

            val results = mutableListOf<SearchResponse>()
            for (i in 0 until books.length()) {
                val book = books.getJSONObject(i)
                val id = book.optString("bookId") ?: continue
                val title = book.optString("bookName", "Untitled")
                val cover = "https://book-pic.webnovel.com/bookcover/$id?imageMogr2/thumbnail/180x|imageMogr2/format/webp|imageMogr2/quality/70!"
                val link = "$mainUrl/book/$id"
                val chapterCount=book.optString("chapterNum","0")

                results.add( newSearchResponse(title, fixUrl(link)) {
                    this.posterUrl = cover;
                    this.totalChapterCount=chapterCount
                })

            }

            val isLastPage = fanficData.optInt("isLast", 1) == 1
            Log.d("TEST PAGE","isLast : ${fanficData.optInt("isLast")}  ${isLastPage}")

            return Pair(results, isLastPage)
        }


    }

}