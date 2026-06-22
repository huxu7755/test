package com.marvis.todoapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val priority: Int = 1,       // 1=低 2=中 3=高
    val category: String = "默认",
    val deadline: Long = 0,      // 截止日期时间戳（毫秒），0=无截止
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0,
    // 循环任务字段
    val repeatType: Int = 0,     // 0=不重复 1=每天 2=每周 3=每月 4=每年
    val repeatEndDate: Long = 0, // 重复截止日期，0=无限重复
    val parentTaskId: Long = 0   // 父任务ID（自动生成的循环实例记录来源），0=原始任务
)

enum class RepeatType(val code: Int, val label: String) {
    NONE(0, "不重复"),
    DAILY(1, "每天"),
    WEEKLY(2, "每周"),
    MONTHLY(3, "每月"),
    YEARLY(4, "每年");

    companion object {
        fun fromCode(code: Int) = entries.find { it.code == code } ?: NONE
    }
}
