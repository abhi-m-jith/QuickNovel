package com.lagradost.quicknovel.ui.mainpage

import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lagradost.cloudstream4.state.ActionHandler
import com.lagradost.cloudstream4.state.DefaultEffectContainer
import com.lagradost.cloudstream4.state.DefaultStateContainer
import com.lagradost.cloudstream4.state.EffectContainer
import com.lagradost.cloudstream4.state.SingleActiveQuery
import com.lagradost.cloudstream4.state.StateContainer
import com.lagradost.quicknovel.APIRepository
import com.lagradost.quicknovel.BookDownloader2
import com.lagradost.quicknovel.LibraryHelper
import com.lagradost.quicknovel.downloader.BookReader
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.common.SearchResponseAction
import com.lagradost.quicknovel.ui.common.SearchResponseOperation
import com.lagradost.quicknovel.ui.common.updateItem
import com.lagradost.quicknovel.util.Apis.Companion.getApiFromName
import com.lagradost.quicknovel.util.AppUtils.openInBrowser
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException
import kotlin.Boolean

@Immutable
data class MainPageState(
    val openQuery: Boolean = false,
    val filter: FilterState = FilterState(),
    val filterVisual: FilterQueryVisual = FilterQueryVisual(),
    val query: QueryState = QueryState(),
    val dialog: MainPageDialog? = null,
    val apiName: String,
    val isChapterCountFilterNeeded : Boolean = false,
)

@Immutable
data class QueryState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val query: String = "",
    val items: PersistentList<ImmutableSearchResponse> = persistentListOf(),
    val error: Throwable? = null,
    val page: Int = 0,
    val hasMore: Boolean = true,
)

@Immutable
data class FilterState(
    val loading: Boolean = true,
    val items: PersistentList<ImmutableSearchResponse> = persistentListOf(),
    val error: Throwable? = null,
    val url: String = "",
    val query: FilterQuery = FilterQuery(),
)

@Immutable
data class FilterQueryVisual(
    val category: String? = null,
    val orderBy: String? = null,
    val tag: String? = null,
    val chaptercount: String?=null,
)

@Immutable
data class FilterQuery(
    val page: Int = 0,
    val category: Int = 0,
    val orderBy: Int = 0,
    val tag: Int = 0,
    val chapterCount: LibraryHelper.ChapterCountFilter =
        LibraryHelper.ChapterCountFilter.ALL,
)

enum class DialogType {
    Tags, Category, OrderBy,ChapterCount
}

@Immutable
data class MainPageDialog(
    val selected: Int,
    val options: PersistentList<String>,
    val type: DialogType,
)

@Immutable
sealed class MainPageAction {
    data class ResultAction(val action: SearchResponseAction) : MainPageAction()

    data class Search(val data: String) : MainPageAction()
    object Expand : MainPageAction()
    object ExpandSearch : MainPageAction()
    data class OpenInBrowser(val url: String) : MainPageAction()
    object Back : MainPageAction()
    object Dismiss : MainPageAction()
    data class OpenDialog(val type: DialogType) : MainPageAction()
    data class SelectDialog(val type: DialogType, val selected: Int) : MainPageAction()
}

@Immutable
sealed class MainPageEffect {
    data class ErrorLoading(val error: Throwable) : MainPageEffect()
}

class MainPageViewModel2(
    val api: APIRepository,
    initQuery: FilterQuery,
) : ViewModel(), StateContainer<MainPageState> by DefaultStateContainer(
    MainPageState(
        filter = FilterState(query = initQuery), apiName = api.name,isChapterCountFilterNeeded = api.api.isChapterCountFilterNeeded
    )
), EffectContainer<MainPageEffect> by DefaultEffectContainer(), ActionHandler<MainPageAction> {
    companion object {
        fun provideFactory(bundle: Bundle) = viewModelFactory {
            initializer {
                val mainCategory = bundle.getInt("mainCategory", 0)
                val orderBy = bundle.getInt("orderBy", 0)
                val tag = bundle.getInt("tag", 0)
                val apiName = bundle.getString("apiName")!!
                val api = getApiFromName(apiName)
                MainPageViewModel2(
                    api,
                    FilterQuery(page = 0, category = mainCategory, tag = tag, orderBy = orderBy, chapterCount = api.api.ChapterFilter)
                )
            }
        }
    }

    private val expandMutex = Mutex()
    private val searchExpandMutex = Mutex()

    init {
        viewModelScope.launch {
            expand()
        }
    }

    suspend fun expand() = withContext(Dispatchers.IO) {

        expandMutex.lock()
        try {
            val from = state.value.filter.query
            val fromList = state.value.filter.items
            if (from != state.value.filter.query) return@withContext

            val mainCategory = api.mainCategories.getOrNull(from.category)
            val tag = api.tags.getOrNull(from.tag)
            val orderBy = api.orderBys.getOrNull(from.orderBy)
            updateState {
                copy(
                    filterVisual = FilterQueryVisual(
                        category = mainCategory?.first, tag = tag?.first, orderBy = orderBy?.first,chaptercount = from.chapterCount.displayName
                    ), filter = filter.copy(
                        loading = true,
                    )
                )
            }

            val nextPage = from.page + 1
            api.loadMainPageResult(
                page = nextPage,
                mainCategory = mainCategory?.second,
                tag = tag?.second,
                orderBy = orderBy?.second
            ).onFailure { error ->
                if (error is CancellationException) {
                    return@withContext
                }
                updateState {
                    copy(filter = filter.copy(loading = false, error = error))
                }
                postEffect {
                    MainPageEffect.ErrorLoading(error = error)
                }
            }.onSuccess { response ->
                val newList = fromList.addingAll(response.list)
                updateState {
                    // Outdated query, drop it
                    if (this.filter.query != from) {
                        return@updateState copy(filter = filter.copy(loading = false))
                    }
                    copy(
                        filter = filter.copy(
                            loading = false,
                            error = null,
                            items = newList,
                            query = from.copy(page = nextPage),
                            url = response.url
                        )
                    )
                }
            }
        } finally {
            updateState {
                copy(filter = filter.copy(loading = false))
            }
            expandMutex.unlock()
        }
    }

    val searchQuery = SingleActiveQuery(Dispatchers.IO)
    suspend fun search(queryText: String) = searchQuery.launch {

        // Reset API pagination state
        api.api.isLastPage = false
        api.api.isSearching = true

        updateState {
            copy(
                openQuery = true,
                query = query.copy(
                    loading = true,
                    loadingMore = false,
                    query = queryText,
                    items = persistentListOf(),
                    error = null,
                    page = 0,
                    hasMore = true
                )
            )
        }

        try {
            api.searchResult(queryText, 1)
                .onFailure { error ->

                    if (error is CancellationException) {
                        return@launch
                    }

                    updateState {
                        copy(
                            query = query.copy(
                                loading = false,
                                loadingMore = false,
                                error = error
                            )
                        )
                    }

                    postEffect {
                        MainPageEffect.ErrorLoading(error)
                    }
                }
                .onSuccess { response ->

                    updateState {
                        // Ignore outdated search results
                        if (query.query != queryText) {
                            return@updateState this
                        }

                        copy(
                            query = query.copy(
                                items = response.toPersistentList(),
                                loading = false,
                                loadingMore = false,
                                error = null,
                                page = 1,
                                hasMore = response.isNotEmpty() && !api.api.isLastPage
                            )
                        )
                    }
                }
        } finally {
            api.api.isSearching = false
        }
    }

    suspend fun expandSearch() = withContext(Dispatchers.IO) {

        searchExpandMutex.lock()

        try {
            val from = state.value.query

            // Do not load if search is already loading
            if (from.loading ||
                from.loadingMore ||
                !from.hasMore ||
                from.query.isBlank()
            ) {
                return@withContext
            }

            val nextPage = from.page + 1

            // Update loading state
            updateState {
                copy(
                    query = query.copy(
                        loadingMore = true,
                        error = null
                    )
                )
            }

            api.api.isSearching = true

            try {
                api.searchResult(
                    query = from.query,
                    page = nextPage
                ).onFailure { error ->

                    if (error is CancellationException) {
                        return@withContext
                    }

                    updateState {
                        // Ignore results belonging to an old search
                        if (query.query != from.query) {
                            return@updateState this
                        }

                        copy(
                            query = query.copy(
                                loadingMore = false,
                                error = error
                            )
                        )
                    }

                    postEffect {
                        MainPageEffect.ErrorLoading(error)
                    }

                }.onSuccess { response ->

                    updateState {

                        // Search changed while this request was running
                        if (query.query != from.query) {
                            return@updateState copy(
                                query = query.copy(loadingMore = false)
                            )
                        }

                        // Append new results
                        val newList = query.items.addingAll(response)

                        copy(
                            query = query.copy(
                                items = newList,
                                page = nextPage,
                                loadingMore = false,
                                error = null,
                                hasMore = response.isNotEmpty() && !api.api.isLastPage
                            )
                        )
                    }
                }
            } finally {
                api.api.isSearching = false
            }

        } finally {
            searchExpandMutex.unlock()
        }
    }

    override fun onAction(action: MainPageAction) {
        when (action) {
            is MainPageAction.ResultAction -> {
                resultAction(action.action)
            }

            is MainPageAction.Search -> {
                viewModelScope.launch {
                    search(action.data)
                }
            }

            is MainPageAction.Expand -> {
                if (state.value.filter.loading) return
                viewModelScope.launch {
                    expand()
                }
            }
            is MainPageAction.ExpandSearch -> {
                if (state.value.query.loading ||
                    state.value.query.loadingMore ||
                    !state.value.query.hasMore
                ) return

                viewModelScope.launch {
                    expandSearch()
                }
            }

            is MainPageAction.OpenInBrowser -> {
                openInBrowser(action.url)
            }

            is MainPageAction.Back -> {
                updateState { copy(openQuery = false) }
            }

            is MainPageAction.OpenDialog -> {
                val names = when (action.type) {
                    DialogType.Category -> api.mainCategories.map { it.first }
                    DialogType.Tags -> api.tags.map { it.first }
                    DialogType.OrderBy -> api.orderBys.map { it.first }
                    DialogType.ChapterCount -> LibraryHelper.ChapterCountFilter.values().map { it.displayName }
                }
                updateState {
                    copy(
                        dialog = MainPageDialog(
                            selected = when (action.type) {
                                DialogType.Category -> filter.query.category
                                DialogType.Tags -> filter.query.tag
                                DialogType.OrderBy -> filter.query.orderBy
                                DialogType.ChapterCount -> filter.query.chapterCount.ordinal
                            }, options = names.toPersistentList(), type = action.type
                        )
                    )
                }
            }

            is MainPageAction.SelectDialog -> {
                val selectedChapterCount =
                    if (action.type == DialogType.ChapterCount) {
                        LibraryHelper.ChapterCountFilter.values()
                            .getOrElse(action.selected) {
                                LibraryHelper.ChapterCountFilter.ALL
                            }
                    } else {
                        state.value.filter.query.chapterCount
                    }
                val previousChapterCount = state.value.filter.query.chapterCount
                if (action.type == DialogType.ChapterCount) {
                    api.api.ChapterFilter = selectedChapterCount

                    if (previousChapterCount != selectedChapterCount) {
                        api.api.FABFilterApplied()
                    }
                }

                updateState {
                    copy(
                        filter = filter.copy(
                            query = filter.query.copy(
                                tag = if (action.type == DialogType.Tags) action.selected else filter.query.tag,
                                category = if (action.type == DialogType.Category) action.selected else filter.query.category,
                                orderBy = if (action.type == DialogType.OrderBy) action.selected else filter.query.orderBy,
                                chapterCount = selectedChapterCount,
                                page = 0,
                            ), items = persistentListOf(), error = null, url = ""
                        ), dialog = null
                    )
                }
                viewModelScope.launch {
                    expand()
                }
            }

            MainPageAction.Dismiss -> {
                updateState {
                    copy(dialog = null)
                }
            }
        }
    }

    private fun resultAction(action: SearchResponseAction) {
        when (action.operation) {
            SearchResponseOperation.Stream -> {
                viewModelScope.launch {
                    BookReader.streamRead(action.response, generating = {
                        updateState {
                            copy(query = query.copy(items = query.items.updateItem(action.response) {
                                copy(generating = true)
                            }), filter = filter.copy(items = filter.items.updateItem(action.response) {
                                copy(generating = true)
                            }))
                        }
                    })

                    updateState {
                        copy(query = query.copy(items = query.items.updateItem(action.response) {
                            copy(generating = false)
                        }), filter = filter.copy(items = filter.items.updateItem(action.response) {
                            copy(generating = false)
                        }))
                    }
                }
            }

            else -> {
                action.doAction()
            }
        }
    }
}