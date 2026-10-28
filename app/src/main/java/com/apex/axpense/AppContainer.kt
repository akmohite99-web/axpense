package com.apex.axpense

import android.content.Context
import com.apex.axpense.data.AxpenseDatabase
import com.apex.axpense.data.DataRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Creates the app's shared objects once, so the ViewModel, the backup worker and the
 * notification listener all use the same database, Firebase handles and repository.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val database: AxpenseDatabase by lazy { AxpenseDatabase.getDatabase(appContext) }
    val repository: DataRepository by lazy { DataRepository(appContext, database, auth, firestore) }
}

/** The app-wide [AppContainer], from any Context. */
val Context.container: AppContainer
    get() = (applicationContext as App).container
