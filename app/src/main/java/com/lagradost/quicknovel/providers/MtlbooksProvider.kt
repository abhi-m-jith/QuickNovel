package com.lagradost.quicknovel.providers

import android.util.Log
import com.lagradost.quicknovel.ChapterData
import com.lagradost.quicknovel.ErrorLoadingException
import com.lagradost.quicknovel.HeadMainPageResponse
import com.lagradost.quicknovel.LibraryHelper
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.MainActivity.Companion.app
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.StreamResponse
import com.lagradost.quicknovel.fixUrl
import com.lagradost.quicknovel.fixUrlNull
import com.lagradost.quicknovel.newSearchResponse
import com.lagradost.quicknovel.newStreamResponse
import com.lagradost.quicknovel.providers.WebnovelFanficProvider.Companion.MOBILE_USER_AGENT
import com.lagradost.quicknovel.setStatus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private val chapterContentCache = LruCache<String, String>(20) // Cache last 20 chapter contents
class MtlbooksProvider : MainAPI()
{
    override val name = "MTL Books"
    override val mainUrl = "https://mtlbooks.com"
    override val hasMainPage = true

    override val iconId = R.drawable.icon_mtlbooks

    override val mainCategories = listOf(
        "All" to "",
        "Completed" to "Completed",
        "Ongoing" to "Ongoing",
        "Hiatus" to "Hiatus"
    )
    override val tags = listOf(
        "All" to "",
        "Action" to "Action",
        "Adventure" to "Adventure",
        "Comedy" to "Comedy",
        "Drama" to "Drama",
        "Fantasy" to "Fantasy",
        "Fan-Fiction" to "Fan-Fiction",
        "Faloo" to "Faloo",
        "Historical" to "Historical",
        "Josei" to "Josei",
        "Psychological" to "Psychological",
        "Romance" to "Romance",
        "School Life" to "School+Life",
        "Sci-fi" to "Sci-fi",
        "Shoujo" to "Shoujo",
        "Slice Of Life" to "Slice+Of+Life",
        "Supernatural" to "Supernatural",
        "Urban" to "Urban",
        "Virtual Reality" to "Virtual+Reality",
        "VirtualReality" to "VirtualReality",
        "Xianxia" to "Xianxia",
        "Yaoi" to "Yaoi",
        "Adult" to "Adult",
        "Anime" to "Anime",
        "Billionaire" to "Billionaire",
        "Billionaires" to "Billionaires",
        "BL" to "BL",
        "CEO" to "CEO",
        "Competitive Sports" to "Competitive+Sports",
        "Contemporary Romance" to "Contemporary+Romance",
        "Cooking" to "Cooking",
        "Douluo" to "Douluo",
        "Dragon Ball" to "Dragon+Ball",
        "Eastern Fantasy" to "Eastern+Fantasy",
        "Ecchi" to "Ecchi",
        "Elf" to "Elf",
        "Erciyuan" to "Erciyuan",
        "Fantasy Romance" to "Fantasy+Romance",
        "Game" to "Game",
        "GayRomance" to "GayRomance",
        "Gender Bender" to "Gender+Bender",
        "Gender-Bender" to "Gender-Bender",
        "Harem" to "Harem",
        "Historical Romance" to "Historical+Romance",
        "History" to "History",
        "Hogwarts" to "Hogwarts",
        "Horror" to "Horror",
        "Horror&" to "Horror&",
        "Korean" to "Korean",
        "LGBT" to "LGBT",
        "LGBT+" to "LGBT+",
        "Magic" to "Magic",
        "Magical Realism" to "Magical+Realism",
        "Martial Arts" to "Martial+Arts",
        "Martial-Arts" to "Martial-Arts",
        "Marvel" to "Marvel",
        "Mature" to "Mature",
        "Mecha" to "Mecha",
        "Military" to "Military",
        "Modern" to "Modern",
        "Modern&" to "Modern&",
        "Modern Life" to "Modern+Life",
        "Modern Romance" to "Modern+Romance",
        "ModernRomance" to "ModernRomance",
        "Movies" to "Movies",
        "Mystery" to "Mystery",
        "NA" to "NA",
        "Naruto" to "Naruto",
        "Official Circles" to "Official+Circles",
        "One Piece" to "One+Piece",
        "Other" to "Other",
        "Pokemon" to "Pokemon",
        "Realism" to "Realism",
        "Realistic" to "Realistic",
        "Realistic Fiction" to "Realistic+Fiction",
        "RealisticFiction" to "RealisticFiction",
        "Reincarnation" to "Reincarnation",
        "Romantic" to "Romantic",
        "School-Life" to "School-Life",
        "Science Fiction" to "Science+Fiction",
        "Sci-Fi" to "Sci-Fi",
        "Secret" to "Secret",
        "Seinen" to "Seinen",
        "Shoujo Ai" to "Shoujo+Ai",
        "Shoujo-Ai" to "Shoujo-Ai",
        "Shounen" to "Shounen",
        "Shounen Ai" to "Shounen+Ai",
        "Shounen-Ai" to "Shounen-Ai",
        "Slice of life" to "Slice+of+life",
        "Slice-Of-Life" to "Slice-Of-Life",
        "SliceOfLife" to "SliceOfLife",
        "Smut" to "Smut",
        "Sports" to "Sports",
        "Suspense" to "Suspense",
        "Suspense Thriller" to "Suspense+Thriller",
        "Teen" to "Teen",
        "Terror" to "Terror",
        "Tragedy" to "Tragedy",
        "Two-dimensional" to "Two-dimensional",
        "Urban Life" to "Urban+Life",
        "Urban-Life" to "Urban-Life",
        "Urban Romance" to "Urban+Romance",
        "Video games" to "Video+games",
        "Video Games" to "Video+Games",
        "War" to "War",
        "War&" to "War&",
        "War&Military" to "War&Military",
        "Wuxia" to "Wuxia",
        "Wuxia Xianxia" to "Wuxia+Xianxia",
        "Xuanhuan" to "Xuanhuan",
        "Yuri" to "Yuri"
    )


    override val orderBys = listOf("New" to "recent", "Popular" to "popular", "Updates" to "updated","Chapter" to "chaptercount", "BookMarks" to "bookmarkcount", "Word Count" to "wordcount","Views Today" to "dailyviews", "Views Week" to "weeklyviews", "Views Month" to "monthlyviews","Viewed All" to "views")

    var lastLoadedPage=1
    val minBooksNeeded=11
    val maxPagesToFetch=10
    val apiURL="https://alpha.mtlbooks.com/api/v1/"

    val seenUrls = HashSet<String>()
    data class ChapterInfo(
        val novelSlug: String,
        val chapterSlug: String
    )

    override fun FABFilterApplied() {
        lastLoadedPage=1
        seenUrls.clear()
    }
    override fun ResetFiltersandPage() {
        FABFilterApplied()
        ChapterFilter= LibraryHelper.ChapterCountFilter.ALL
    }

    fun GetAPIdata(url:String,slug:String,page:Int): JSONObject {
        val jsonBody = """{"novel_slug": "$slug","page": $page,"order": "ASC"}"""
        val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", MOBILE_USER_AGENT)
            .addHeader("Origin", "$mainUrl")
            .addHeader("Referer", "$mainUrl")
            .build()

        val client = OkHttpClient()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("MTL BOOKS", "Request failed with code ${response.code}")
                return JSONObject()
            }

            val responseText = response.body?.string()
            // Log.d("MTL BOOKS", "Response: $responseText")

            if (responseText != null) {
                val json = JSONObject(responseText)
                return json.optJSONObject("result")
            }
        }
        return JSONObject()
    }
    fun GetChapterList(data:JSONObject,slug:String): MutableList<ChapterData> {
        val chapters = mutableListOf<ChapterData>()
        val chapterLists = data.getJSONArray("chapter_lists")
        for (i in 0 until chapterLists.length())
        {
            val item = chapterLists.getJSONObject(i)
            val chapterUrl="$mainUrl/novel/$slug/${item.optString("chapter_slug")}"
            val chapterName=item.optString("chapter_title")
            chapters.add(ChapterData(chapterName,chapterUrl))
        }
        return chapters
    }



    fun parseNovelAndChapter(url: String): ChapterInfo? {
        try {
            // Remove query parameters if any
            val cleanedUrl = url.split("?")[0]

            // Split by "/" and get parts
            val parts = cleanedUrl.split("/").filter { it.isNotEmpty() }

            // Expected format: ["https:", "mtlbooks.com", "novel", "novel_slug", "chapter_slug"]
            val novelIndex = parts.indexOf("novel")
            if (novelIndex != -1 && parts.size > novelIndex + 2) {
                val novelSlug = parts[novelIndex + 1]
                val chapterSlug = parts[novelIndex + 2]
                return ChapterInfo(novelSlug, chapterSlug)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null // return null if parsing fails
    }
    fun GetChapterAPIdata(url:String,slug:String,chapter_slug:String): JSONObject {
        val jsonBody = """{"novel_slug": "$slug","chapter_slug": "$chapter_slug"}"""
        val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", MOBILE_USER_AGENT)
            .addHeader("Origin", "$mainUrl")
            .addHeader("Referer", "$mainUrl")
            .build()

        val client = OkHttpClient()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e("MTL BOOKS", "Request failed with code ${response.code}")
                return JSONObject()
            }

            val responseText = response.body?.string()
            // Log.d("MTL BOOKS", "Response: $responseText")

            if (responseText != null) {
                val json = JSONObject(responseText)
                return json.optJSONObject("result")
            }
        }
        return JSONObject()
    }
    fun convertJsonContentToHtml(content: String): String {
        // Split content into paragraphs by double newlines
        val paragraphs = content.split("\n\n")

        // Wrap each paragraph in <p> tags, replacing single \n with <br>
        val htmlParagraphs = paragraphs.map { paragraph ->
            "<p>${paragraph.replace("\n", "<br>")}</p>"
        }

        // Join all paragraphs into one HTML string
        return htmlParagraphs.joinToString("\n")
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        isChapterCountFilterNeeded=true
        val collectedResults = mutableListOf<SearchResponse>()
        var currentPage = if (page <= lastLoadedPage) lastLoadedPage else page
        var pagesFetched = 0 // optional safety cap

        suspend fun fetchPage(p: Int): Pair<List<SearchResponse>, Boolean> {
            val url = "${apiURL}search/?page=${p}&order=${orderBy}&include_genres=${tag}&status=${mainCategory}"
            val response = app.get(url)
            val json=JSONObject(response.text)
            val result=json.getJSONObject("result")
            val items = result.getJSONArray("data")
            val hadAnyRawItems = items.length() > 0
            val filtered = mutableListOf<SearchResponse>()

            for (i in 0 until items.length()) {
                val book = items.getJSONObject(i)
                val slug = book.optString("slug") ?: continue
                val link = fixUrlNull("${apiURL}novels/$slug") ?: continue
                if (!seenUrls.add(link)) continue // skip duplicates

                val chapterCount = book.optString("chaptercount", "0")
                if (!LibraryHelper.isChapterCountInRange(ChapterFilter, chapterCount.toString())) {
                    continue
                }

                val title = book.optString("name", "Untitled")
                val pic=book.optString("thumbnail")
                val cover =
                    "https://wsrv.nl/?url=https://cdn.mtlbooks.com/poster/${pic}&w=300&h=400&fit=cover&output=webp&maxage=3M"


                filtered.add(newSearchResponse(title, link) {
                    posterUrl = cover
                    totalChapterCount = chapterCount
                })
            }


            lastLoadedPage = p

            return Pair(filtered, hadAnyRawItems)
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
        //Log.d("MTL BOOKS","${collectedResults.size}")

        val url = "${apiURL}search/?page=${lastLoadedPage}&order=${orderBy}&include_genres=${tag}&status=${mainCategory}"

        return HeadMainPageResponse(url,collectedResults)


    }


    override suspend fun load(url: String): StreamResponse {
        isOpeningBook=true

        val response = app.get(url)
        val json=JSONObject(response.text)
        val result=json.getJSONObject("result")
        val title = result.optString("name", "Untitled")
        val description = result.optString("description", "")
        val pic=result.optString("thumbnail")
        val cover = "https://wsrv.nl/?url=https://cdn.mtlbooks.com/poster/${pic}&w=300&h=400&fit=cover&output=webp&maxage=3M"
        val status = result.optString("status")
        val slug=result.optString("slug")

        val chapters = mutableListOf<ChapterData>()

        val chapApi="${apiURL}chapters/list"

        val firstdata=GetAPIdata(chapApi,slug,1)

        chapters.addAll(GetChapterList(firstdata,slug))

        val pagination=firstdata.getJSONObject("pagination")
        val totalitems=pagination.optInt("total")
        val limit=pagination.optInt("limit")

        val totalpages=(totalitems + limit - 1) / limit

        for (page in 2..totalpages) {
            chapters.addAll(GetChapterList(GetAPIdata(chapApi,slug,page),slug))
        }

        return newStreamResponse(title, fixUrl(url), chapters) {
            this.posterUrl = cover
            this.synopsis = description
            setStatus(status)
        }
    }

    override suspend fun loadHtml(url: String): String? {
        chapterContentCache[url]?.let {
            Log.d("MTL BOOKS", "Loaded chapter content from cache for $url")
            return it
        }

        val fullUrl = fixUrl(url)
        //Log.d("MTL BOOKS", "Loading Chapter HTML from URL: $fullUrl")

        try {

            val chapterInfo = parseNovelAndChapter(fullUrl)
            val chapAPi="${apiURL}chapters/read"
            val chap= chapterInfo?.let {
                GetChapterAPIdata(chapAPi,
                    it.novelSlug,chapterInfo.chapterSlug)
            }
            val Con= chap?.getJSONObject("chapter")?.optString("content")

           // Log.d("MTL BOOKS", "Chapter HTML: $Con")

            val content=convertJsonContentToHtml(Con.toString())

            if (content != null) {
                Log.d("MTL BOOKS", "Chapter Content Loaded Successfully")
                chapterContentCache[url] = content
                return content
            } else {
                Log.e("MTL BOOKS", "Chapter Content NOT FOUND in expected structure")
            }
        } catch (e: Exception) {
            Log.e("MTL BOOKS", "Failed to load chapter HTML: ${e.message}")
        }

        return null
    }

    override suspend fun search(query: String,page: Int): List<SearchResponse> {

        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val results = mutableListOf<SearchResponse>()

        val url = "${apiURL}search/?q=$encodedQuery&page=$page&order=popular"
        val response = app.get(url)
        val json=JSONObject(response.text)
        val result=json.getJSONObject("result")
        val items = result.getJSONArray("data")
        val pageend=result.getJSONObject("pagination").optInt("totalPages")

        for (i in 0 until items.length()) {
            val book = items.getJSONObject(i)
            val slug = book.optString("slug") ?: continue
            val link = fixUrlNull("${apiURL}novels/$slug") ?: continue

            val chapterCount = book.optString("chaptercount", "0")

            val title = book.optString("name", "Untitled")
            val pic=book.optString("thumbnail")
            val cover =
                "https://wsrv.nl/?url=https://cdn.mtlbooks.com/poster/${pic}&w=300&h=400&fit=cover&output=webp&maxage=3M"


            results.add(newSearchResponse(title, link) {
                posterUrl = cover
                totalChapterCount = chapterCount
            })
        }

        isLastPage = page>=pageend
        Log.e("WEBNOVEL","Current Page: ${lastLoadedPage}")
        return results
    }



}