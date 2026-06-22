package com.marvis.todoapp

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.marvis.todoapp.databinding.ActivityMainBinding
import com.marvis.todoapp.sync.DataExporter
import com.marvis.todoapp.sync.SyncManager
import com.marvis.todoapp.ui.TaskDetailActivity
import com.marvis.todoapp.ui.FilterMode
import com.marvis.todoapp.ui.TaskAdapter
import com.marvis.todoapp.ui.TaskViewModel
import com.marvis.todoapp.ui.StatsActivity
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: TaskViewModel by viewModels()
    private lateinit var adapter: TaskAdapter

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { exportToUri(it) } }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importFromUri(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        setupObservers()
        setupFab()
    }

    private fun setupRecyclerView() {
        adapter = TaskAdapter(
            onToggle = { task -> viewModel.toggleCompleted(task) },
            onEdit = { task -> openTaskDetail(task.id) }
        )
        binding.rvTasks.layoutManager = LinearLayoutManager(this)
        binding.rvTasks.adapter = adapter
    }

    private fun setupObservers() {
        viewModel.filteredTasks.observe(this) { tasks ->
            adapter.submitList(tasks)
            binding.tvEmpty.visibility = if (tasks.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.tvTaskCount.text = "共 ${tasks.size} 项"
        }

        viewModel.filterMode.observe(this) { mode ->
            binding.chipAll.isChecked = mode == FilterMode.ALL
            binding.chipCompleted.isChecked = mode == FilterMode.COMPLETED
            binding.chipOverdue.isChecked = mode == FilterMode.OVERDUE
        }
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddEditTaskActivity::class.java))
        }
    }

    private fun openTaskDetail(taskId: Long) {
        val intent = Intent(this, TaskDetailActivity::class.java)
        intent.putExtra("task_id", taskId)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(com.marvis.todoapp.R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            com.marvis.todoapp.R.id.action_filter_category -> {
                showCategoryFilter()
                true
            }
            com.marvis.todoapp.R.id.action_filter_priority -> {
                showPriorityFilter()
                true
            }
            com.marvis.todoapp.R.id.action_delete_completed -> {
                showDeleteCompletedDialog()
                true
            }
            com.marvis.todoapp.R.id.action_stats -> {
                startActivity(Intent(this, StatsActivity::class.java))
                true
            }
            com.marvis.todoapp.R.id.action_export -> {
                exportLauncher.launch("TodoApp_backup_${System.currentTimeMillis()}.json")
                true
            }
            com.marvis.todoapp.R.id.action_import -> {
                importLauncher.launch(arrayOf("application/json", "*/*"))
                true
            }
            com.marvis.todoapp.R.id.action_sync_config -> {
                showSyncConfigDialog()
                true
            }
            com.marvis.todoapp.R.id.action_sync_now -> {
                doSync()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun onFilterChipClicked(view: android.view.View) {
        val mode = when (view.id) {
            R.id.chipAll -> FilterMode.ALL
            R.id.chipOverdue -> FilterMode.OVERDUE
            R.id.chipCompleted -> FilterMode.COMPLETED
            else -> FilterMode.ALL
        }
        viewModel.setFilterMode(mode)
    }

    private fun showCategoryFilter() {
        viewModel.categories.observe(this) { cats ->
            val items = listOf("全部") + cats
            AlertDialog.Builder(this)
                .setTitle("按分类筛选")
                .setItems(items.toTypedArray()) { _, which ->
                    if (which == 0) {
                        viewModel.setFilterMode(FilterMode.ALL)
                    } else {
                        viewModel.setFilterCategory(cats[which - 1])
                        viewModel.setFilterMode(FilterMode.CATEGORY)
                    }
                }
                .show()
        }
    }

    private fun showPriorityFilter() {
        val priorities = arrayOf("全部", "低优先级", "中优先级", "高优先级")
        AlertDialog.Builder(this)
            .setTitle("按优先级筛选")
            .setItems(priorities) { _, which ->
                if (which == 0) {
                    viewModel.setFilterMode(FilterMode.ALL)
                } else {
                    viewModel.setFilterPriority(which)
                    viewModel.setFilterMode(FilterMode.PRIORITY)
                }
            }
            .show()
    }

    private fun showDeleteCompletedDialog() {
        AlertDialog.Builder(this)
            .setTitle("删除已完成任务")
            .setMessage("确定要删除所有已完成的任务吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val completed = viewModel.getCompletedTasksList()
                    completed.forEach { viewModel.delete(it) }
                    Toast.makeText(this@MainActivity, "已删除 ${completed.size} 个已完成任务", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportToUri(uri: android.net.Uri) {
        lifecycleScope.launch {
            val tasks = viewModel.allTasks.value ?: emptyList()
            val subtasks = viewModel.allSubtasks.value ?: emptyList()
            val json = DataExporter.exportToJson(tasks, subtasks)
            DataExporter.writeToUri(this@MainActivity, uri, json)
            Toast.makeText(this@MainActivity, "数据已导出", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFromUri(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    ?: return@launch
                val data = DataExporter.importFromJson(json)
                    ?: run {
                        Toast.makeText(this@MainActivity, "文件格式不正确", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                viewModel.importAll(data.tasks, data.subtasks)
                Toast.makeText(this@MainActivity, "导入成功: ${data.tasks.size} 个任务, ${data.subtasks.size} 个子任务", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSyncConfigDialog() {
        SyncManager.init(this)
        val input = EditText(this).apply {
            setText(SyncManager.getServerUrl())
            hint = "例如 http://192.168.1.100:3000"
        }
        AlertDialog.Builder(this)
            .setTitle("多端同步服务器地址")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val url = input.text.toString().trim()
                SyncManager.setServerUrl(url)
                Toast.makeText(this, "同步服务器地址已保存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doSync() {
        SyncManager.init(this)
        val serverUrl = SyncManager.getServerUrl()
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "请先设置同步服务器地址", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // Pull
                val data = SyncManager.pullChanges()
                if (data != null) {
                    viewModel.mergeSync(data)
                }
                // Push
                val allTasks = viewModel.allTasks.value ?: emptyList()
                val allSubtasks = viewModel.allSubtasks.value ?: emptyList()
                val tasksJson = allTasks.map { task ->
                    org.json.JSONObject().apply {
                        put("local_id", task.id)
                        put("title", task.title)
                        put("description", task.description)
                        put("priority", task.priority)
                        put("category", task.category)
                        put("deadline", task.deadline)
                        put("is_completed", task.isCompleted)
                        put("created_at", task.createdAt)
                        put("completed_at", task.completedAt)
                        put("repeat_type", task.repeatType)
                        put("repeat_end_date", task.repeatEndDate)
                        put("parent_task_id", task.parentTaskId)
                    }
                }
                val subtasksJson = allSubtasks.map { s ->
                    org.json.JSONObject().apply {
                        put("local_id", s.id)
                        put("task_local_id", s.taskId)
                        put("content", s.content)
                        put("is_completed", s.isCompleted)
                        put("sort_order", s.sortOrder)
                        put("created_at", s.createdAt)
                        put("completed_at", s.completedAt)
                    }
                }
                SyncManager.pushChanges(tasksJson, subtasksJson, emptyList(), emptyList())
                Toast.makeText(this@MainActivity, "同步完成", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "同步失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
