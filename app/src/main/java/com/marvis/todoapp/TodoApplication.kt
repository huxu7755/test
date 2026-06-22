package com.marvis.todoapp

import android.app.Application
import com.marvis.todoapp.data.TaskDatabase

class TodoApplication : Application() {
    val database: TaskDatabase by lazy { TaskDatabase.getDatabase(this) }
}
