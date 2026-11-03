package com.sidefocus.app

import android.app.Application
import com.sidefocus.app.data.FocusDatabase
import com.sidefocus.app.data.PreferenceStore
import com.sidefocus.app.data.SessionStore

class FocusApplication : Application() {
    val database by lazy { FocusDatabase.open(this) }
    val store by lazy { SessionStore(database.sessions()) }
    val preferences by lazy { PreferenceStore(this) }
}
