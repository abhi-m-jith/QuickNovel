package com.lagradost.quicknovel.ui.result

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ReadActivityViewModel
import com.lagradost.quicknovel.mvvm.BookHelper
import com.lagradost.quicknovel.mvvm.Replacer_Data
import com.lagradost.quicknovel.ui.reader.ReplaceWordsAdapter

class ReplaceWordsBottomSheet : BottomSheetDialogFragment() {

    private lateinit var bookKey: String
    private val readViewModel: ReadActivityViewModel by activityViewModels()
    private var editingIndex: Int? = null
    private lateinit var adapter: ReplaceWordsAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bookKey = requireArguments().getString(ARG_BOOK_KEY)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_book_replacer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        //BookHelper.load(requireContext(), bookKey)
        //android.util.Log.d("BOOK HELPER","${BookHelper.All_Words.count()}")

        val btnAdd = view.findViewById<MaterialButton>(R.id.btn_add_word)
        val btnDelete = view.findViewById<MaterialButton>(R.id.btn_delete_selected)
        val btnSave = view.findViewById<MaterialButton>(R.id.save_words)

        val inputWord = view.findViewById<EditText>(R.id.input_word)
        val inputReplacement = view.findViewById<EditText>(R.id.input_replacement)

        btnDelete.visibility = View.GONE
        btnAdd.text = getString(R.string.add)
        btnAdd.visibility=View.VISIBLE

        adapter = ReplaceWordsAdapter(BookHelper.All_Words, onItemClick = { item, index ->

            if(index==-1)
            {
                editingIndex=null
                inputWord.setText("")
                inputReplacement.setText("")
                btnAdd.text = getString(R.string.add)
                btnDelete.visibility = View.GONE
                return@ReplaceWordsAdapter
            }

            editingIndex = index
            inputWord.setText(item.word)
            inputReplacement.setText(item.replacement_Word)

            btnAdd.text = getString(R.string.update)
            btnDelete.visibility = View.VISIBLE
        }
        )

        val recycler = view.findViewById<RecyclerView>(R.id.replacer_list)
        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(context)

        btnAdd.setOnClickListener {
            val w = inputWord.text?.toString()?.trim().orEmpty()
            val r = inputReplacement.text?.toString()?.trim().orEmpty()
            if (w.isEmpty() || r.isEmpty()) return@setOnClickListener

            val newItem = Replacer_Data(w, r)
            val index = editingIndex
            if(index!=null)
            {
                adapter.updateItem(index,newItem)
                editingIndex = null
                adapter.clearSelection()
                btnAdd.text = getString(R.string.add)
                btnDelete.visibility = View.GONE
            }
            else
            {
                adapter.addItem(newItem)
            }



            inputWord.setText("")
            inputReplacement.setText("")
        }

        btnDelete.setOnClickListener {
            val index = editingIndex
            if (index != null) {
                adapter.deleteItem(index)
                editingIndex = null

                btnDelete.visibility = View.GONE
                btnAdd.text = getString(R.string.add)

                inputWord.setText("")
                inputReplacement.setText("")
            }
        }

        btnSave.setOnClickListener {
            if(BookHelper.All_Words.isNotEmpty())
            {
                //BookHelper.All_Words.forEach { BookHelper.addItem(it) }
                BookHelper.save(requireContext(), bookKey)
                showToast(getString(R.string.reload_chapter_format).format(""))
                readViewModel.reloadChapter()
                //BookHelper.clearAll()
            }
            dismiss()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        BookHelper.CheckAllWords()
        super.onDismiss(dialog)
    }

    companion object {
        private const val ARG_BOOK_KEY = "book_key"

        fun newInstance(bookKey: String) = ReplaceWordsBottomSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_BOOK_KEY, bookKey)
            }
        }
    }
}
