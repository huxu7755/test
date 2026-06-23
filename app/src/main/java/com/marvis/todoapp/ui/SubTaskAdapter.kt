package com.marvis.todoapp.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.marvis.todoapp.data.SubTask
import com.marvis.todoapp.databinding.ItemSubtaskBinding
import java.text.SimpleDateFormat
import java.util.*

class SubTaskAdapter(
    private val onToggle: (SubTask) -> Unit,
    private val onDelete: (SubTask) -> Unit
) : ListAdapter<SubTask, SubTaskAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSubtaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemSubtaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(subTask: SubTask) {
            binding.cbDone.isChecked = subTask.isCompleted
            binding.tvContent.text = subTask.content

            // Always show created time
            val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            binding.tvCreatedTime.text = sdf.format(Date(subTask.createdAt))

            if (subTask.isCompleted) {
                binding.tvContent.paintFlags = binding.tvContent.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                binding.tvContent.alpha = 0.5f
                if (subTask.completedAt > 0) {
                    binding.tvCompletedTime.text = "完成: ${sdf.format(Date(subTask.completedAt))}"
                    binding.tvCompletedTime.visibility = android.view.View.VISIBLE
                } else {
                    binding.tvCompletedTime.visibility = android.view.View.GONE
                }
            } else {
                binding.tvContent.paintFlags = binding.tvContent.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.tvContent.alpha = 1f
                binding.tvCompletedTime.visibility = android.view.View.GONE
            }

            binding.cbDone.setOnCheckedChangeListener(null)
            binding.cbDone.isChecked = subTask.isCompleted
            binding.cbDone.setOnCheckedChangeListener { _, _ -> onToggle(subTask) }

            binding.btnDelete.setOnClickListener { onDelete(subTask) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<SubTask>() {
        override fun areItemsTheSame(oldItem: SubTask, newItem: SubTask) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: SubTask, newItem: SubTask) = oldItem == newItem
    }
}
