package it.srik.TypeQ25

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for emoji RecyclerView.
 * Optimized for performance using classic RecyclerView.
 */
class EmojiRecyclerViewAdapter(
    private val emojis: List<String>,
    private val onEmojiClick: (String) -> Unit
) : RecyclerView.Adapter<EmojiRecyclerViewAdapter.EmojiViewHolder>() {

    class EmojiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val emojiText: TextView = itemView.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        val holder = EmojiViewHolder(view)
        
        // Configure TextView to center emoji
        holder.emojiText.apply {
            textSize = 28.8f // 24 * 1.2 (20% larger)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 0)
            minHeight = (48 * parent.context.resources.displayMetrics.density).toInt() // 40 * 1.2
            minWidth = (48 * parent.context.resources.displayMetrics.density).toInt() // 40 * 1.2
        }
        
        // Click listener setup
        view.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION && position < emojis.size) {
                onEmojiClick(emojis[position])
            }
        }
        
        return holder
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        holder.emojiText.text = emojis[position]
    }

    override fun getItemCount(): Int = emojis.size
}
