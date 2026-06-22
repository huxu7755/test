package com.marvis.todoapp.sync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

object SyncManager {
    private const val PREFS_NAME = "todo_sync_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_LAST_SYNC = "last_sync"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getDeviceId(): String {
        val p = prefs ?: return ""
        var id = p.getString(KEY_DEVICE_ID, null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            p.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getServerUrl(): String = prefs?.getString(KEY_SERVER_URL, "") ?: ""
    fun setServerUrl(url: String) { prefs?.edit()?.putString(KEY_SERVER_URL, url)?.apply() }
    fun getLastSync(): String = prefs?.getString(KEY_LAST_SYNC, "1970-01-01T00:00:00") ?: "1970-01-01T00:00:00"
    fun setLastSync(time: String) { prefs?.edit()?.putString(KEY_LAST_SYNC, time)?.apply() }

    suspend fun registerDevice(deviceName: String): String? = withContext(Dispatchers.IO) {
        try {
            val data = JSONObject().apply { put("device_name", deviceName) }
            val resp = postJson("${getServerUrl()}/api/register", data)
            resp?.getString("device_id")
        } catch (e: Exception) { null }
    }

    data class SyncData(
        val tasks: List<JSONObject>,
        val subtasks: List<JSONObject>,
        val deletedTasks: List<Int>,
        val deletedSubtasks: List<Int>,
        val serverTime: String
    )

    suspend fun pullChanges(): SyncData? = withContext(Dispatchers.IO) {
        try {
            val since = getLastSync()
            val url = "${getServerUrl()}/api/sync/${getDeviceId()}?since=${java.net.URLEncoder.encode(since, "UTF-8")}"
            val resp = getJson(url) ?: return@withContext null

            val tasksArr = resp.optJSONArray("tasks") ?: JSONArray()
            val subtasksArr = resp.optJSONArray("subtasks") ?: JSONArray()
            val delTasksArr = resp.optJSONArray("deleted_tasks") ?: JSONArray()
            val delSubtasksArr = resp.optJSONArray("deleted_subtasks") ?: JSONArray()
            val serverTime = resp.getString("server_time")

            val tasks = (0 until tasksArr.length()).map { tasksArr.getJSONObject(it) }
            val subtasks = (0 until subtasksArr.length()).map { subtasksArr.getJSONObject(it) }
            val delTasks = (0 until delTasksArr.length()).map { delTasksArr.getInt(it) }
            val delSubtasks = (0 until delSubtasksArr.length()).map { delSubtasksArr.getInt(it) }

            if (serverTime.isNotEmpty()) setLastSync(serverTime)
            SyncData(tasks, subtasks, delTasks, delSubtasks, serverTime)
        } catch (e: Exception) { null }
    }

    suspend fun pushChanges(
        tasks: List<JSONObject>,
        subtasks: List<JSONObject>,
        deletedTasks: List<Int>,
        deletedSubtasks: List<Int>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val data = JSONObject().apply {
                put("tasks", JSONArray(tasks))
                put("subtasks", JSONArray(subtasks))
                put("deleted_tasks", JSONArray(deletedTasks))
                put("deleted_subtasks", JSONArray(deletedSubtasks))
            }
            val resp = postJson("${getServerUrl()}/api/sync/${getDeviceId()}/push", data)
            resp != null
        } catch (e: Exception) { false }
    }

    private fun getJson(urlStr: String): JSONObject? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
        }
        return try {
            val text = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            JSONObject(text)
        } finally { conn.disconnect() }
    }

    private fun postJson(urlStr: String, data: JSONObject): JSONObject? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 8000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            OutputStreamWriter(conn.outputStream).use { it.write(data.toString()) }
            val text = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            JSONObject(text)
        } finally { conn.disconnect() }
    }
}
