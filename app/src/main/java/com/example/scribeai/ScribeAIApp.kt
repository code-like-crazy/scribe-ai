package com.example.scribeai

import android.app.Application
import com.example.scribeai.data.AppDatabase

class ScribeAIApp : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
}