
package com.lagradost.quicknovel.mvvm

import android.content.Context
import com.lagradost.quicknovel.R
import org.json.JSONObject
import java.util.regex.Pattern

data class Pokemon_Data(
    val id: String,
    val names: List<String>
)

data class Pokemon_Match(
    val name: String,
    val id: String,
    val start: Int,
    val end: Int
)

object PokedexHelper {

    private var pokemonList: List<Pokemon_Data> = emptyList()

    private var cachedPattern: Pattern? = null

    private val pokemonMap =
        mutableMapOf<String, Pokemon_Data>()

    private var isLoaded = false

    fun load(context: Context) {

        if (isLoaded) return

        val jsonString = context.resources
            .openRawResource(R.raw.pokedex)
            .bufferedReader()
            .use { it.readText() }

        val pokedex = JSONObject(jsonString)
            .getJSONArray("pokedex")

        val list = mutableListOf<Pokemon_Data>()

        for (i in 0 until pokedex.length()) {

            val item = pokedex.getJSONObject(i)

            val namesArray = item.getJSONArray("names")

            val names = mutableListOf<String>()

            for (j in 0 until namesArray.length()) {
                val name = namesArray.getString(j).trim()

                if (name.isNotEmpty() &&
                    names.none { it.equals(name, ignoreCase = true) }
                ) {
                    names.add(name)
                }
            }

            list.add(
                Pokemon_Data(
                    id = item.getString("id"),
                    names = names
                )
            )
        }

        pokemonList = list

        pokemonMap.clear()

        // Add every name and alias to the lookup map.
        pokemonList.forEach { pokemon ->
            pokemon.names.forEach { name ->
                val key = name.lowercase()
                if (!pokemonMap.containsKey(key)) {
                    pokemonMap[key] = pokemon
                }
            }
        }

        // Collect all unique names.
        // Longest names first to avoid partial matches.
        val allNames = pokemonList
            .flatMap { it.names }
            .distinctBy { it.lowercase() }
            .sortedByDescending { it.length }

        val regex = allNames.joinToString("|") {
            Pattern.quote(it)
        }

        cachedPattern = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])(?:$regex)(?![\\p{L}\\p{N}_])",
            Pattern.CASE_INSENSITIVE
        )

        isLoaded = true
    }

    fun findPokemon(story: String): List<Pokemon_Match> {

        if (!isLoaded || cachedPattern == null) {
            return emptyList()
        }

        val matcher = cachedPattern!!.matcher(story)

        val results = mutableListOf<Pokemon_Match>()

        while (matcher.find()) {

            val matchedName = matcher.group()

            val pokemon = pokemonMap[matchedName.lowercase()]
                ?: continue

            // Get the original display name from the JSON.
            val canonicalName = pokemon.names.firstOrNull {
                it.equals(matchedName, ignoreCase = true)
            } ?: matchedName

            results.add(
                Pokemon_Match(
                    name = canonicalName,
                    id = pokemon.id,
                    start = matcher.start(),
                    end = matcher.end()
                )
            )
        }

        return results
    }

    fun getPokemonImage(slug: String): String {
        return "https://raw.githubusercontent.com/HybridShivam/Pokemon/master/assets/images/$slug.png"
    }
}
