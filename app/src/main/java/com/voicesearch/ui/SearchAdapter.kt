package com.voicesearch.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.voicesearch.databinding.ItemResultCardBinding
import com.voicesearch.model.SearchResult

class SearchAdapter(
    private val onItemClick: (SearchResult) -> Unit
) : RecyclerView.Adapter<SearchAdapter.ResultViewHolder>() {

    private val items = mutableListOf<SearchResult>()

    fun submitList(newItems: List<SearchResult>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val binding = ItemResultCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        val holder = ResultViewHolder(binding, onItemClick)
        // Focus change animation: set once in onCreateViewHolder, not on every bind
        binding.root.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.root.animate()
                    .scaleX(1.08f)
                    .scaleY(1.08f)
                    .setDuration(150)
                    .start()
            } else {
                binding.root.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .start()
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ResultViewHolder(
        private val binding: ItemResultCardBinding,
        private val onItemClick: (SearchResult) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(result: SearchResult) {
            binding.title.text = result.title

            if (!result.posterUrl.isNullOrBlank()) {
                binding.poster.load(result.posterUrl) {
                    crossfade(true)
                }
            }

            binding.root.setOnClickListener {
                onItemClick(result)
            }
        }
    }
}
