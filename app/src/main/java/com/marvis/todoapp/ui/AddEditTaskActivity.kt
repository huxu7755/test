package com.marvis.todoapp.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.marvis.todoapp.data.RepeatType
import com.marvis.todoapp.data.Task
import com.marvis.todoapp.databinding.ActivityAddEditTaskBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddEditTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditTaskBinding
    private val viewModel: TaskViewModel by viewModels()
    private var taskId: Long = 0
    private var selectedDeadline: Long = 0L
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupPrioritySpinner()
        setupCategorySpinner()
        setupDeadlinePicker()
        setupRepeatTypeSpinner()
        setupSaveButton()
        setupDeleteButton()

        taskId = intent.getLongExtra("task_id", 0)
        if (taskId > 0) {
            isEditMode = true
            supportActionBar?.title = "编辑任务"
            loadTask()
        } else {
            supportActionBar?.title = "新建任务"
            binding.btnDelete.visibility = android.view.View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupPrioritySpinner() {
        val priorities = arrayOf("低", "中", "高")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, priorities)
        binding.spPriority.adapter = adapter
        binding.spPriority.setSelection(0) // default low
    }

    private fun setupCategorySpinner() {
        viewModel.categories.observe(this) { cats ->
            val allCats = if (cats.contains("默认")) cats.toList() else listOf("默认") + cats
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, allCats)
            binding.spCategory.adapter = adapter
        }
    }

    private fun setupDeadlinePicker() {
        binding.btnPickDeadline.setOnClickListener {
            val cal = Calendar.getInstance()
            if (selectedDeadline > 0) cal.timeInMillis = selectedDeadline

            DatePickerDialog(this, { _, year, month, day ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, day)

                TimePickerDialog(this, { _, hour, minute ->
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, minute)
                    selectedDeadline = cal.timeInMillis
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    binding.tvDeadlineDisplay.text = sdf.format(Date(selectedDeadline))
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()

            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.btnClearDeadline.setOnClickListener {
            selectedDeadline = 0L
            binding.tvDeadlineDisplay.text = "未设置截止日期"
        }
    }

    private fun setupRepeatTypeSpinner() {
        val labels = RepeatType.entries.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        binding.spRepeatType.adapter = adapter

        binding.spRepeatType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                binding.tilRepeatInterval.visibility =
                    if (position == RepeatType.CUSTOM.code) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSaveButton() {
        binding.btnSave.setOnClickListener {
            val title = binding.etTitle.text.toString().trim()
            if (title.isEmpty()) {
                binding.tilTitle.error = "标题不能为空"
                return@setOnClickListener
            }

            val description = binding.etDescription.text.toString().trim()
            val priority = binding.spPriority.selectedItemPosition + 1

            // Get category from spinner text (handle custom input)
            val categoryInput = binding.etCustomCategory.text.toString().trim()
            val category = if (categoryInput.isNotEmpty()) categoryInput
            else binding.spCategory.selectedItem?.toString() ?: "默认"

            val repeatType = binding.spRepeatType.selectedItemPosition
            val repeatInterval = if (repeatType == RepeatType.CUSTOM.code) {
                binding.etRepeatInterval.text.toString().toIntOrNull() ?: 1
            } else 1

            val task = Task(
                id = taskId,
                title = title,
                description = description,
                priority = priority,
                category = category,
                deadline = selectedDeadline,
                repeatType = repeatType,
                repeatInterval = repeatInterval
            )

            if (isEditMode) {
                viewModel.update(task)
                Toast.makeText(this, "任务已更新", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.insert(task)
                Toast.makeText(this, "任务已创建", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }

    private fun setupDeleteButton() {
        binding.btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("删除任务")
                .setMessage("确定要删除这个任务吗？")
                .setPositiveButton("删除") { _, _ ->
                    lifecycleScope.launch {
                        viewModel.getTaskById(taskId)?.let { viewModel.delete(it) }
                        Toast.makeText(this@AddEditTaskActivity, "任务已删除", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun loadTask() {
        lifecycleScope.launch {
            viewModel.getTaskById(taskId)?.let { task ->
                binding.etTitle.setText(task.title)
                binding.etDescription.setText(task.description)
                binding.spPriority.setSelection(task.priority - 1)
                selectedDeadline = task.deadline
                binding.spRepeatType.setSelection(task.repeatType)

                if (task.repeatType == RepeatType.CUSTOM.code) {
                    binding.etRepeatInterval.setText(task.repeatInterval.toString())
                    binding.tilRepeatInterval.visibility = View.VISIBLE
                }

                if (task.deadline > 0) {
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    binding.tvDeadlineDisplay.text = sdf.format(Date(task.deadline))
                }

                // Set category spinner
                val catAdapter = binding.spCategory.adapter
                if (catAdapter != null) {
                    for (i in 0 until catAdapter.count) {
                        if (catAdapter.getItem(i) == task.category) {
                            binding.spCategory.setSelection(i)
                            return@launch
                        }
                    }
                }
            }
        }
    }
}
