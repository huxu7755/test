package com.marvis.todoapp.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.marvis.todoapp.data.Task
import com.marvis.todoapp.databinding.ItemTaskBinding
import java.text.SimpleDateFormat
import java.util.*

class TaskAdapter(
    private val onToggle: (Task) -> Unit,
    private val onEdit: (Task) -> Unit
) : ListAdapter<Task, TaskAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(task: Task) {
            binding.tvTitle.text = task.title
            binding.tvDescription.text = task.description.ifEmpty { "暂无描述" }
            binding.cbCompleted.isChecked = task.isCompleted

            // Priority indicator
            val priorityText = when (task.priority) {
                3 -> "🔴 高"
                2 -> "🟡 中"
                else -> "🟢 低"
            }
            binding.tvPriority.text = priorityText

            // Category
            binding.tvCategory.text = task.category

            // Deadline
            if (task.deadline > 0) {
                val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                binding.tvDeadline.text = "截止: ${sdf.format(Date(task.deadline))}"
                binding.tvDeadline.visibility = android.view.View.VISIBLE
            } else {
                binding.tvDeadline.visibility = android.view.View.GONE
            }

            // Strike through if completed
            if (task.isCompleted) {
                binding.tvTitle.paintFlags = binding.tvTitle.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.tvTitle.paintFlags = binding.tvTitle.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            binding.cbCompleted.setOnCheckedChangeListener(null)
            binding.cbCompleted.isChecked = task.isCompleted
            binding.cbCompleted.setOnCheckedChangeListener { _, _ -> onToggle(task) }

            binding.root.setOnClickListener { onEdit(task) }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Task, newItem: Task) = oldItem == newItem
    }
}
