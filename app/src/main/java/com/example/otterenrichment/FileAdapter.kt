package com.example.otterenrichment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
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
            .inflate(R.layout.item_file, parent, false)
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

        private val checkedTextView: CheckedTextView = itemView.findViewById(android.R.id.text1) as CheckedTextView
        private val defaultCheckMark = checkedTextView.checkMarkDrawable

        fun bind(file: ScallopFile) {
            val sizeKb = file.size / 1024.0
            val sizeText = if (sizeKb > 1024) {
                String.format("%.2f MB", sizeKb / 1024)
            } else {
                String.format("%.2f KB", sizeKb)
            }

            checkedTextView.text = "${file.name} ($sizeText)"
            checkedTextView.isChecked = file.isSelected

            if (isSelectionMode) {
                checkedTextView.checkMarkDrawable = defaultCheckMark
                // In multiple choice mode, click toggles selection
                itemView.setOnClickListener {
                    val newChecked = !checkedTextView.isChecked
                    checkedTextView.isChecked = newChecked
                    onFileSelect(file, newChecked)
                }
            } else {
                checkedTextView.checkMarkDrawable = null
                // In normal mode, click triggers action (download)
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
