package com.lagradost.quicknovel.ui.result

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import coil3.load
import coil3.request.crossfade
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lagradost.quicknovel.mvvm.PokedexHelper
import com.lagradost.quicknovel.mvvm.Pokemon_Match

object PokemonDialog {

    private const val MAX_RETRIES = 5
    private const val RETRY_DELAY = 2000L

    fun show(
        context: Context,
        pokemon: Pokemon_Match
    ) {
        val imageUrl = PokedexHelper.getPokemonImage(pokemon.id)

        val imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val progressBar = ProgressBar(context).apply {
            isIndeterminate = true
            visibility = View.VISIBLE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }

        val imageContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                220.dp(context),
                220.dp(context)
            )
            addView(imageView)
            addView(progressBar)
        }

        loadPokemonImage(imageView, progressBar, imageUrl)

        val title = TextView(context).apply {
            text = pokemon.name
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(
                8.dp(context),
                8.dp(context),
                8.dp(context),
                16.dp(context)
            )
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                24.dp(context),
                16.dp(context),
                24.dp(context),
                8.dp(context)
            )

            addView(title)
            addView(imageContainer)
        }

        MaterialAlertDialogBuilder(context)
            .setView(container)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun loadPokemonImage(
        imageView: ImageView,
        progressBar: ProgressBar,
        imageUrl: String,
        retryCount: Int = 0
    ) {
        imageView.load(imageUrl) {
            crossfade(true)

            listener(
                onStart = { _ ->
                    progressBar.visibility = View.VISIBLE
                },
                onSuccess = { _, _ ->
                    progressBar.visibility = View.GONE
                },
                onError = { _, _ ->
                    if (retryCount < MAX_RETRIES) {
                        imageView.postDelayed({
                            if (imageView.isAttachedToWindow) {
                                loadPokemonImage(
                                    imageView,
                                    progressBar,
                                    imageUrl,
                                    retryCount + 1
                                )
                            }
                        }, RETRY_DELAY)
                    } else {
                        progressBar.visibility = View.GONE
                    }
                }
            )
        }
    }

    private fun Int.dp(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}