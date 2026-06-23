package com.marvis.todoapp.sync

import android.content.Context
import android.net.Uri
import com.marvis.todoapp.data.Task
import com.marvis.todoapp.data.SubTask
import org.json.JSONArray
import org.json.JSONObject
import java.io.*

object DataExporter {

    fun exportToJson(tasks: List<Task>, subtasks: List<SubTask>): String {
        val json = JSONObject().apply {
            put("export_version", 1)
            put("export_time", System.currentTimeMillis())
            put("tasks", JSONArray().apply {
                for (t in tasks) {
                    put(JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("description", t.description)
                        put("priority", t.priority)
                        put("category", t.category)
                        put("deadline", t.deadline)
                        put("is_completed", t.isCompleted)
                        put("created_at", t.createdAt)
                        put("completed_at", t.completedAt)
                        put("repeat_type", t.repeatType)
                        put("repeat_interval", t.repeatInterval)
                        put("parent_task_id", t.parentTaskId)
                    })
                }
            })
            put("subtasks", JSONArray().apply {
                for (s in subtasks) {
                    put(JSONObject().apply {
                        put("id", s.id)
                        put("task_id", s.taskId)
                        put("content", s.content)
                        put("is_completed", s.isCompleted)
                        put("sort_order", s.sortOrder)
                        put("created_at", s.createdAt)
                        put("completed_at", s.completedAt)
                    })
                }
            })
        }
        return json.toString(2)
    }

    fun writeToUri(context: Context, uri: Uri, json: String) {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(json.toByteArray(Charsets.UTF_8))
        }
    }

    data class ImportData(
        val tasks: List<Task>,
        val subtasks: List<SubTask>
    )

    fun importFromJson(jsonStr: String): ImportData? {
        return try {
            val json = JSONObject(jsonStr)
            val tasksArr = json.getJSONArray("tasks")
            val subtasksArr = json.getJSONArray("subtasks")

            val tasks = (0 until tasksArr.length()).map { i ->
                val obj = tasksArr.getJSONObject(i)
                Task(
                    id = 0, // reset ID for local insert
                    title = obj.getString("title"),
                    description = obj.optString("description", ""),
                    priority = obj.optInt("priority", 1),
                    category = obj.optString("category", "默认"),
                    deadline = obj.optLong("deadline", 0),
                    isCompleted = obj.optBoolean("is_completed", false),
                    createdAt = obj.optLong("created_at", System.currentTimeMillis()),
                    completedAt = obj.optLong("completed_at", 0),
                    repeatType = obj.optInt("repeat_type", 0),
                    repeatInterval = obj.optInt("repeat_interval", 1),
                    parentTaskId = obj.optLong("parent_task_id", 0)
                )
            }

            val subtasks = (0 until subtasksArr.length()).map { i ->
                val obj = subtasksArr.getJSONObject(i)
                SubTask(
                    id = 0,
                    taskId = obj.optLong("task_id", 0),
                    content = obj.getString("content"),
                    isCompleted = obj.optBoolean("is_completed", false),
                    sortOrder = obj.optInt("sort_order", 0),
                    createdAt = obj.optLong("created_at", System.currentTimeMillis()),
                    completedAt = obj.optLong("completed_at", 0)
                )
            }

            ImportData(tasks, subtasks)
        } catch (e: Exception) {
            null
        }
    }
}
