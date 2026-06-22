package com.marvis.todoapp.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.marvis.todoapp.data.TaskDao
import com.marvis.todoapp.databinding.ActivityStatsBinding

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding
    private val viewModel: TaskViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "统计数据"

        observeStats()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun observeStats() {
        viewModel.totalTaskCount.observe(this) { count ->
            binding.tvTotalTasks.text = count.toString()
            updateCompletionRate()
        }

        viewModel.completedTaskCount.observe(this) { count ->
            binding.tvCompletedTasks.text = count.toString()
            updateCompletionRate()
        }

        viewModel.overdueCount.observe(this) { count ->
            binding.tvOverdueTasks.text = count.toString()
        }

        viewModel.categoryStats.observe(this) { stats ->
            val sb = StringBuilder()
            val maxCount = stats.maxOfOrNull { it.count } ?: 1
            for ((index, stat) in stats.withIndex()) {
                val bar = "█".repeat((stat.count.toFloat() / maxCount * 20).toInt().coerceAtLeast(1))
                val prefix = if (index > 0) "\n" else ""
                sb.append("$prefix${stat.category.padEnd(4)} $bar  ${stat.count}")
            }
            binding.tvCategoryStats.text = sb.ifEmpty { "暂无数据" }
        }

        viewModel.priorityStats.observe(this) { stats ->
            val sb = StringBuilder()
            val maxCount = stats.maxOfOrNull { it.count } ?: 1
            val priLabels = mapOf(3 to "高", 2 to "中", 1 to "低")
            for ((index, stat) in stats.withIndex()) {
                val label = priLabels[stat.priority] ?: "?"
                val bar = "█".repeat((stat.count.toFloat() / maxCount * 20).toInt().coerceAtLeast(1))
                val prefix = if (index > 0) "\n" else ""
                sb.append("$prefix${label.padEnd(2)} $bar  ${stat.count}")
            }
            binding.tvPriorityStats.text = sb.ifEmpty { "暂无数据" }
        }
    }

    private fun updateCompletionRate() {
        val total = viewModel.totalTaskCount.value ?: 0
        val completed = viewModel.completedTaskCount.value ?: 0
        val rate = if (total > 0) (completed * 100 / total) else 0
        binding.tvCompletionRate.text = "${rate}%"
        binding.progressCompletion.progress = rate
    }
}
