package com.marvis.todoapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.marvis.todoapp.data.SubTask
import com.marvis.todoapp.databinding.ActivityTaskDetailBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TaskDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTaskDetailBinding
    private val viewModel: TaskViewModel by viewModels()
    private lateinit var subTaskAdapter: SubTaskAdapter
    private var taskId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTaskDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "任务详情"

        taskId = intent.getLongExtra("task_id", 0)
        if (taskId == 0L) { finish(); return }

        setupSubTaskList()
        setupAddSubTask()
        loadTask()
        observeSubTasks()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupSubTaskList() {
        subTaskAdapter = SubTaskAdapter(
            onToggle = { viewModel.toggleSubTaskCompleted(it) },
            onDelete = { showDeleteSubTaskDialog(it) }
        )
        binding.rvSubTasks.layoutManager = LinearLayoutManager(this)
        binding.rvSubTasks.adapter = subTaskAdapter
    }

    private fun setupAddSubTask() {
        binding.btnAddSubTask.setOnClickListener {
            val content = binding.etSubTaskContent.text.toString().trim()
            if (content.isEmpty()) {
                Toast.makeText(this, "请输入子任务内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val subTask = SubTask(taskId = taskId, content = content)
            viewModel.insertSubTask(subTask)
            binding.etSubTaskContent.text?.clear()
        }
    }

    private fun loadTask() {
        lifecycleScope.launch {
            viewModel.getTaskById(taskId)?.let { task ->
                binding.tvTaskTitle.text = task.title
                binding.tvTaskDesc.text = task.description.ifEmpty { "暂无描述" }
                binding.tvTaskDesc.visibility = if (task.description.isEmpty()) View.GONE else View.VISIBLE

                val priText = when (task.priority) {
                    3 -> "高优先级"
                    2 -> "中优先级"
                    else -> "低优先级"
                }
                binding.tvPriority.text = priText
                binding.tvCategory.text = task.category

                if (task.deadline > 0) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    binding.tvDeadline.text = "截止: ${sdf.format(Date(task.deadline))}"
                    binding.tvDeadline.visibility = View.VISIBLE
                } else {
                    binding.tvDeadline.visibility = View.GONE
                }

                // buttons
                binding.btnEditTask.setOnClickListener {
                    val intent = Intent(this@TaskDetailActivity, AddEditTaskActivity::class.java)
                    intent.putExtra("task_id", taskId)
                    startActivity(intent)
                }

                binding.btnDeleteTask.setOnClickListener {
                    AlertDialog.Builder(this@TaskDetailActivity)
                        .setTitle("删除任务")
                        .setMessage("确定删除「${task.title}」及其所有子任务吗？")
                        .setPositiveButton("删除") { _, _ ->
                            viewModel.delete(task)
                            Toast.makeText(this@TaskDetailActivity, "任务已删除", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
        }
    }

    private fun observeSubTasks() {
        viewModel.getSubTasks(taskId).observe(this) { subTasks ->
            subTaskAdapter.submitList(subTasks)

            val total = subTasks.size
            val completed = subTasks.count { it.isCompleted }
            binding.tvSubTaskProgress.text = "子任务 $completed/$total"

            binding.progressBar.max = if (total > 0) total else 1
            binding.progressBar.progress = completed

            binding.tvEmptySubTask.visibility = if (total == 0) View.VISIBLE else View.GONE
            binding.rvSubTasks.visibility = if (total == 0) View.GONE else View.VISIBLE
        }
    }

    private fun showDeleteSubTaskDialog(subTask: SubTask) {
        AlertDialog.Builder(this)
            .setTitle("删除子任务")
            .setMessage("确定删除「${subTask.content}」吗？")
            .setPositiveButton("删除") { _, _ -> viewModel.deleteSubTask(subTask) }
            .setNegativeButton("取消", null)
            .show()
    }
}
