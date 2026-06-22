package com.marvis.todoapp.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY priority DESC, deadline ASC, createdAt DESC")
    fun getActiveTasks(): LiveData<List<Task>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY completedAt DESC")
    fun getCompletedTasks(): LiveData<List<Task>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 1")
    suspend fun getCompletedTasksList(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): Task?

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND category = :category ORDER BY priority DESC, deadline ASC")
    fun getTasksByCategory(category: String): LiveData<List<Task>>

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND priority = :priority ORDER BY deadline ASC")
    fun getTasksByPriority(priority: Int): LiveData<List<Task>>

    @Query("SELECT DISTINCT category FROM tasks ORDER BY category")
    fun getAllCategories(): LiveData<List<String>>

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasks(): LiveData<List<Task>>

    @Query("SELECT * FROM subtasks ORDER BY id ASC")
    fun getAllSubTasks(): LiveData<List<SubTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE tasks SET isCompleted = :completed, completedAt = :completedAt WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean, completedAt: Long = if (completed) System.currentTimeMillis() else 0)

    @Query("SELECT * FROM tasks WHERE isCompleted = 0 AND (deadline > 0 AND deadline < :now) ORDER BY deadline ASC")
    fun getOverdueTasks(now: Long = System.currentTimeMillis()): LiveData<List<Task>>

    // SubTask queries
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sortOrder ASC, id ASC")
    fun getSubTasks(taskId: Long): LiveData<List<SubTask>>

    @Query("SELECT COUNT(*) FROM subtasks WHERE taskId = :taskId")
    fun getSubTaskCount(taskId: Long): LiveData<Int>

    @Query("SELECT COUNT(*) FROM subtasks WHERE taskId = :taskId AND isCompleted = 1")
    fun getCompletedSubTaskCount(taskId: Long): LiveData<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTask(subTask: SubTask): Long

    @Update
    suspend fun updateSubTask(subTask: SubTask)

    @Delete
    suspend fun deleteSubTask(subTask: SubTask)

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteSubTaskById(id: Long)

    @Query("UPDATE subtasks SET isCompleted = :completed, completedAt = :completedAt WHERE id = :id")
    suspend fun setSubTaskCompleted(id: Long, completed: Boolean, completedAt: Long)

    // Statistics
    @Query("SELECT COUNT(*) FROM tasks")
    fun getTotalTaskCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 1")
    fun getCompletedTaskCount(): LiveData<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 0 AND deadline > 0 AND deadline < :now")
    fun getOverdueCount(now: Long = System.currentTimeMillis()): LiveData<Int>

    @Query("SELECT category, COUNT(*) as count FROM tasks GROUP BY category ORDER BY count DESC")
    fun getCategoryStats(): LiveData<List<CategoryStat>>

    @Query("SELECT priority, COUNT(*) as count FROM tasks GROUP BY priority ORDER BY priority DESC")
    fun getPriorityStats(): LiveData<List<PriorityStat>>

    data class CategoryStat(val category: String, val count: Int)
    data class PriorityStat(val priority: Int, val count: Int)
}
