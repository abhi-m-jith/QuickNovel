package com.lagradost.quicknovel.ui.reader

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.mvvm.Replacer_Data
import org.json.JSONObject
import androidx.core.graphics.toColorInt

class ReplaceWordsAdapter(
    private val items: MutableList<Replacer_Data>,
    private val onItemClick: (Replacer_Data, Int) -> Unit
) : RecyclerView.Adapter<ReplaceWordsAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val word: TextView = view.findViewById(R.id.text_word)
        val replacement: TextView = view.findViewById(R.id.text_replacement)
    }

    private var selectedIndex: Int? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_replacer_word, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val item = items[position]

        holder.word.text = item.word
        holder.replacement.text = item.replacement_Word

        // 🔹 Highlight editing item
        val isSelected = selectedIndex == position
        holder.itemView.isSelected = isSelected
        holder.itemView.isActivated = isSelected
        if (isSelected) {
            // Set your selection color (e.g., a semi-transparent gray or red)
            holder.itemView.setBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.semiWhite))
        } else {
            // Restore the ripple effect
            val outValue = android.util.TypedValue()
            holder.itemView.context.theme.resolveAttribute(
                android.R.attr.selectableItemBackground,
                outValue,
                true
            )
            holder.itemView.setBackgroundResource(outValue.resourceId)
        }

        // row click for editing
        holder.itemView.setOnClickListener {
            if (selectedIndex == position) {
                selectedIndex = null
                notifyItemChanged(position)
                onItemClick(item, -1) // deselect
            } else {
                val old = selectedIndex
                selectedIndex = position
                old?.let { notifyItemChanged(it) }
                notifyItemChanged(position)
                onItemClick(item, position)

                android.util.Log.d("BOOK HELPER","Selected: $position  index: $selectedIndex")
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun addItem(item: Replacer_Data) {
        if(!items.contains(item))
        {
            items.add(item)
            notifyItemInserted(items.size - 1)
            android.util.Log.d("BOOK HELPER","Item Added: $item")
        }
    }
    fun updateItem(index: Int, item: Replacer_Data) {
        items[index] = item
        notifyItemChanged(index)
    }


    fun deleteItem(index: Int) {
        android.util.Log.d("BOOK HELPER","Item Deleting at: $index  Size:${items.size}")
        items.removeAt(index)
        selectedIndex = null
        notifyDataSetChanged()
        android.util.Log.d("BOOK HELPER","Item Deleted at: $index  Size:${items.size}")
    }

    fun clearSelection() {
        selectedIndex = null
        notifyDataSetChanged()
    }
}
