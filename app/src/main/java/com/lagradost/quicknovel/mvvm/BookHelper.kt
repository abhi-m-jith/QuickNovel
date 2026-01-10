package com.lagradost.quicknovel.mvvm

import android.content.Context
import com.lagradost.quicknovel.DataStore.clearBookReplacers
import com.lagradost.quicknovel.DataStore.loadBookReplacers
import com.lagradost.quicknovel.DataStore.saveBookReplacers
import java.util.regex.Matcher
import java.util.regex.Pattern

object BookHelper {

    var current_book_ID: String = ""
        private set

    private val _allWords = mutableListOf<Replacer_Data>()
    private val _ogWords = mutableListOf<Replacer_Data>()

    val All_Words: MutableList<Replacer_Data>
        get() = _allWords

    private var cachedPattern: Pattern? = null
    private var cachedMap: Map<String, String> = emptyMap()
    private var cachedKeyHash: Int = 0

    fun CheckAllWords()
    {
        if (_allWords != _ogWords) {
            resetLoad()
        }
    }

    fun addItem(item: Replacer_Data) {
        _allWords.add(item)
    }

    fun deleteItem(item: Replacer_Data) {
        _allWords.remove(item)
    }

    fun clearAll() {
        _allWords.clear()
        _ogWords.clear()
    }

    private fun ogLoad()
    {
        _ogWords.clear()
        _ogWords.addAll(_allWords)
    }

    private fun resetLoad()
    {
        _allWords.clear()
        _allWords.addAll(_ogWords)
    }

    fun load(context: Context, bookKey: String) {
        clearAll()
        current_book_ID = bookKey
        android.util.Log.d("BOOK HELPER","LOADING BOOK DATA: Current: $current_book_ID  BKEY:$bookKey")
        _allWords.addAll(context.loadBookReplacers(bookKey))
        ogLoad()
    }

    fun save(context: Context,bookKey: String) {
        if (current_book_ID.isNotEmpty() && current_book_ID == bookKey) {
            context.saveBookReplacers(current_book_ID, _allWords)
            ogLoad()
            android.util.Log.d("BOOK HELPER","SAVING BOOK DATA  ${All_Words.count()}")
        }
    }

    fun clearBook(context: Context, bookKey: String) {
        context.clearBookReplacers(bookKey)
        if (current_book_ID == bookKey) {
            android.util.Log.d("BOOK HELPER","CLEARING BOOK DATA")
            current_book_ID = ""
            clearAll()
        }
    }

    fun ReplaceText(inputText: String): String {
        android.util.Log.d("BOOK HELPER","REPLACE CALLED")
        val rules = All_Words
        if (rules.isEmpty()) return inputText
        android.util.Log.d("BOOK HELPER","RULES OK")

        // Detect changes to avoid rebuilding regex every time
        val hash = rules.joinToString("|") { "${it.word}->${it.replacement_Word}" }.hashCode()
        if (cachedPattern == null || hash != cachedKeyHash) {

            cachedKeyHash = hash

            cachedMap = rules
                .filter { it.word.isNotBlank() }
                .associate { it.word.lowercase() to it.replacement_Word }

            if (cachedMap.isEmpty()) return inputText

            val regex = cachedMap.keys.joinToString("|") {
                "\\b${Pattern.quote(it)}\\b"
            }

            cachedPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        }

        android.util.Log.d("BOOK HELPER","PATTERN OK")

        val matcher = cachedPattern!!.matcher(inputText)
        val result = StringBuffer(inputText.length)

        while (matcher.find()) {
            val replacement = cachedMap[matcher.group().lowercase()] ?: matcher.group()
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement))
        }
        matcher.appendTail(result)

        android.util.Log.d("BOOK HELPER","REPLACE COMPLETE")

        return result.toString()
    }

}

data class Replacer_Data(
    var word: String = "",
    var replacement_Word: String = ""
)
