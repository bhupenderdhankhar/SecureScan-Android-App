package com.example.securescan.repository

import android.os.Build
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class UserRecord(
    val email     : String = "",
    val createdAt : Long   = System.currentTimeMillis(),
    val lastLogin : Long   = System.currentTimeMillis(),
    val deviceInfo: String = ""
)

@Singleton
class FirestoreRepository @Inject constructor() {

    private val db    = FirebaseFirestore.getInstance()
    private val users = db.collection("users")

    /**
     * Called on every successful login.
     * Creates user document if first time, updates lastLogin otherwise.
     */
    suspend fun recordLogin(email: String): Result<Unit> = runCatching {
        val docId = email.replace(".", "_").replace("@", "__")
        val doc   = users.document(docId).get().await()

        if (doc.exists()) {
            // Update lastLogin
            users.document(docId).update(
                "lastLogin", System.currentTimeMillis()
            ).await()
        } else {
            // First login — create user record
            val record = UserRecord(
                email      = email,
                createdAt  = System.currentTimeMillis(),
                lastLogin  = System.currentTimeMillis(),
                deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
            )
            users.document(docId).set(record, SetOptions.merge()).await()
        }
    }

    suspend fun getUserRecord(email: String): Result<UserRecord?> = runCatching {
        val docId = email.replace(".", "_").replace("@", "__")
        val doc   = users.document(docId).get().await()
        doc.toObject(UserRecord::class.java)
    }
}