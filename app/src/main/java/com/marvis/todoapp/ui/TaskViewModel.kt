package com.marvis.todoapp.ui

import android.app.Application
import androidx.lifecycle.*
import com.marvis.todoapp.data.RepeatType
import com.marvis.todoapp.data.SubTask
import com.marvis.todoapp.data.Task
import com.marvis.todoapp.data.TaskDao
import com.marvis.todoapp.data.TaskDatabase
import com.marvis.todoapp.sync.SyncManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.*

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val taskDao = TaskDatabase.getDatabase(application).taskDao()

    val activeTasks: LiveData<List<Task>> = taskDao.getActiveTasks()
    val completedTasks: LiveData<List<Task>> = taskDao.getCompletedTasks()
    val categories: LiveData<List<String>> = taskDao.getAllCategories()
    val allTasks: LiveData<List<Task>> = taskDao.getAllTasks()
    val allSubtasks: LiveData<List<SubTask>> = taskDao.getAllSubTasks()
    val totalTaskCount: LiveData<Int> = taskDao.getTotalTaskCount()
    val completedTaskCount: LiveData<Int> = taskDao.getCompletedTaskCount()
    val overdueCount: LiveData<Int> = taskDao.getOverdueCount()
    val categoryStats: LiveData<List<TaskDao.CategoryStat>> = taskDao.getCategoryStats()
    val priorityStats: LiveData<List<TaskDao.PriorityStat>> = taskDao.getPriorityStats()

    private val _filterMode = MutableLiveData(FilterMode.ALL)
    val filterMode: LiveData<FilterMode> = _filterMode

    private val _filterCategory = MutableLiveData<String?>(null)
    val filterCategory: LiveData<String?> = _filterCategory

    private val _filterPriority = MutableLiveData<Int?>(null)
    val filterPriority: LiveData<Int?> = _filterPriority

    val filteredTasks: LiveData<List<Task>> = _filterMode.switchMap { mode ->
        when (mode) {
            FilterMode.ALL -> activeTasks
            FilterMode.CATEGORY -> _filterCategory.switchMap { cat ->
                if (cat != null) taskDao.getTasksByCategory(cat)
                else activeTasks
            }
            FilterMode.PRIORITY -> _filterPriority.switchMap { pri ->
                if (pri != null) taskDao.getTasksByPriority(pri)
                else activeTasks
            }
            FilterMode.OVERDUE -> taskDao.getOverdueTasks()
            FilterMode.COMPLETED -> completedTasks
        }
    }

    fun setFilterMode(mode: FilterMode) { _filterMode.value = mode }
    fun setFilterCategory(category: String?) { _filterCategory.value = category }
    fun setFilterPriority(priority: Int?) { _filterPriority.value = priority }

    fun insert(task: Task) = viewModelScope.launch { taskDao.insert(task) }
    fun update(task: Task) = viewModelScope.launch { taskDao.update(task) }
    fun delete(task: Task) = viewModelScope.launch { taskDao.delete(task) }
    suspend fun getCompletedTasksList(): List<Task> = taskDao.getCompletedTasksList()

    fun toggleCompleted(task: Task) = viewModelScope.launch {
        taskDao.setCompleted(task.id, !task.isCompleted)
        // 如果是循环任务且被标记为完成，自动生成下一个实例
        if (!task.isCompleted && task.repeatType > 0) {
            createNextRepeatInstance(task)
        }
    }

    private suspend fun createNextRepeatInstance(task: Task) {
        val cal = Calendar.getInstance()
        val nextDeadline = if (task.deadline > 0) {
            cal.timeInMillis = task.deadline
            when (task.repeatType) {
                RepeatType.DAILY.code -> cal.add(Calendar.DAY_OF_MONTH, 1)
                RepeatType.WEEKLY.code -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                RepeatType.MONTHLY.code -> cal.add(Calendar.MONTH, 1)
                RepeatType.YEARLY.code -> cal.add(Calendar.YEAR, 1)
            }
            val next = cal.timeInMillis
            if (task.repeatEndDate > 0 && next > task.repeatEndDate) 0L else next
        } else 0L

        val nextTask = Task(
            title = task.title,
            description = task.description,
            priority = task.priority,
            category = task.category,
            deadline = nextDeadline,
            repeatType = task.repeatType,
            repeatEndDate = task.repeatEndDate,
            parentTaskId = task.id
        )
        taskDao.insert(nextTask)
    }

    suspend fun getTaskById(id: Long): Task? = taskDao.getTaskById(id)

    // SubTask operations
    fun getSubTasks(taskId: Long): LiveData<List<SubTask>> = taskDao.getSubTasks(taskId)
    fun getSubTaskCount(taskId: Long): LiveData<Int> = taskDao.getSubTaskCount(taskId)
    fun getCompletedSubTaskCount(taskId: Long): LiveData<Int> = taskDao.getCompletedSubTaskCount(taskId)

    fun insertSubTask(subTask: SubTask) = viewModelScope.launch { taskDao.insertSubTask(subTask) }
    fun updateSubTask(subTask: SubTask) = viewModelScope.launch { taskDao.updateSubTask(subTask) }
    fun deleteSubTask(subTask: SubTask) = viewModelScope.launch { taskDao.deleteSubTask(subTask) }
    fun toggleSubTaskCompleted(subTask: SubTask) = viewModelScope.launch {
        val newCompleted = !subTask.isCompleted
        val completedAt = if (newCompleted) System.currentTimeMillis() else 0L
        taskDao.setSubTaskCompleted(subTask.id, newCompleted, completedAt)
    }

    fun importAll(tasks: List<Task>, subTasks: List<SubTask>) = viewModelScope.launch {
        for (t in tasks) taskDao.insert(t)
        for (s in subTasks) taskDao.insertSubTask(s)
    }

    fun mergeSync(data: SyncManager.SyncData) = viewModelScope.launch {
        // Remove deleted items locally
        for (id in data.deletedTasks) {
            taskDao.deleteById(id.toLong())
        }
        for (id in data.deletedSubtasks) {
            taskDao.deleteSubTaskById(id.toLong())
        }
        // Upsert tasks
        for (obj in data.tasks) {
            val localId = obj.getLong("local_id")
            val existing = taskDao.getTaskById(localId)
            val task = Task(
                id = localId,
                title = obj.getString("title"),
                description = obj.optString("description", ""),
                priority = obj.optInt("priority", 1),
                category = obj.optString("category", "默认"),
                deadline = obj.optLong("deadline", 0),
                isCompleted = obj.optInt("is_completed", 0) == 1,
                createdAt = obj.optLong("created_at", System.currentTimeMillis()),
                completedAt = obj.optLong("completed_at", 0),
                repeatType = obj.optInt("repeat_type", 0),
                repeatEndDate = obj.optLong("repeat_end_date", 0),
                parentTaskId = obj.optLong("parent_task_id", 0)
            )
            if (existing != null) taskDao.update(task) else taskDao.insert(task)
        }
        // Upsert subtasks
        for (obj in data.subtasks) {
            val localId = obj.getLong("local_id")
            taskDao.deleteSubTaskById(localId) // remove old first
            val sub = SubTask(
                id = localId,
                taskId = obj.optLong("task_local_id", 0),
                content = obj.optString("content", ""),
                isCompleted = obj.optInt("is_completed", 0) == 1,
                sortOrder = obj.optInt("sort_order", 0),
                createdAt = obj.optLong("created_at", System.currentTimeMillis()),
                completedAt = obj.optLong("completed_at", 0)
            )
            taskDao.insertSubTask(sub)
        }
    }
}

enum class FilterMode { ALL, CATEGORY, PRIORITY, OVERDUE, COMPLETED }
