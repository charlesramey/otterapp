package com.example.otterenrichment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for displaying SD card files
 */
class FileAdapter(
    private val onFileClick: (ScallopFile) -> Unit,
    private val onFileSelect: (ScallopFile, Boolean) -> Unit,
    private val isSelectionMode: Boolean
) : ListAdapter<ScallopFile, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_multiple_choice, parent, false)
        return FileViewHolder(view, onFileClick, onFileSelect, isSelectionMode)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class FileViewHolder(
        itemView: View,
        private val onFileClick: (ScallopFile) -> Unit,
        private val onFileSelect: (ScallopFile, Boolean) -> Unit,
        private val isSelectionMode: Boolean
    ) : RecyclerView.ViewHolder(itemView) {

        private val checkbox: CheckBox = itemView.findViewById(android.R.id.text1) as CheckBox

        fun bind(file: ScallopFile) {
            val sizeKb = file.size / 1024.0
            val sizeText = if (sizeKb > 1024) {
                String.format("%.2f MB", sizeKb / 1024)
            } else {
                String.format("%.2f KB", sizeKb)
            }

            checkbox.text = "${file.name} ($sizeText)"
            checkbox.isChecked = file.isSelected

            if (isSelectionMode) {
                checkbox.visibility = View.VISIBLE
                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    onFileSelect(file, isChecked)
                }
            } else {
                checkbox.visibility = View.GONE
                itemView.setOnClickListener {
                    onFileClick(file)
                }
            }
        }
    }
}

class FileDiffCallback : DiffUtil.ItemCallback<ScallopFile>() {
    override fun areItemsTheSame(oldItem: ScallopFile, newItem: ScallopFile): Boolean {
        return oldItem.name == newItem.name
    }

    override fun areContentsTheSame(oldItem: ScallopFile, newItem: ScallopFile): Boolean {
        return oldItem == newItem
    }
}
