package com.lagradost.quicknovel.ui.search


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.databinding.HomeResultGridBinding
import com.lagradost.quicknovel.ui.BaseDiffCallback
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.ui.newSharedPool
import com.lagradost.quicknovel.util.AppUtils.toJson
import com.lagradost.quicknovel.util.UIHelper.hideKeyboard
import com.lagradost.quicknovel.util.UIHelper.setImage

class HomeChildItemAdapter(
    private val viewModel: SearchViewModel,
) :
    NoStateAdapter<SearchResponse>(BaseDiffCallback(itemSame = { a, b ->
        a.url == b.url
    }, contentSame = { a, b ->
        a == b
    })) {

    companion object {
        val sharedPool =
            newSharedPool {
                setMaxRecycledViews(CONTENT, 10)
            }
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            HomeResultGridBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: SearchResponse, position: Int) {
        val binding = holder.view as? HomeResultGridBinding ?: return

        binding.apply {
            imageView.apply {
                setImage(item.image)

                setOnClickListener {
                    viewModel.load(item)
                }

                setOnLongClickListener { view ->
                    hideKeyboard(view)
                    viewModel.showMetadata(item)
                    return@setOnLongClickListener true
                }
            }
            imageText.text = item.name
            val InLibrary=item.bookReadStatus

            android.util.Log.d("AAAAAAAAAAAAAAAAAAAAA",item.name)

            if(InLibrary != null && InLibrary != "NONE")
            {
                libraryOverlay.isVisible = true
                libraryOverlay.text = "$InLibrary"
                chapterCountOverlay.isVisible = false
            }
            else{
                libraryOverlay.isVisible=false

                // Only show chapter count overlay if it's not null/blank
                val chapterCountStr = item.totalChapterCount

                if (!chapterCountStr.isNullOrBlank()) {
                    val chapterCount = chapterCountStr.toIntOrNull()

                    chapterCountOverlay.isVisible = true
                    chapterCountOverlay.text = if (chapterCount != null && chapterCount > 0) {
                        "$chapterCount ch"
                    }
                    else {
                        if(chapterCount==0){
                            chapterCountOverlay.isVisible = false
                        }
                        "$chapterCountStr ch" // fallback to string like "V5 46"
                    }
                } else {
                    chapterCountOverlay.isVisible = false
                }
            }
        }
    }
}
