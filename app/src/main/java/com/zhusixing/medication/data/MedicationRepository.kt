package com.zhusixing.medication.data

interface MedicationRepository {
    suspend fun login(baseUrl: String, account: String, password: String): Session
    suspend fun today(session: Session): List<RemindTask>
    suspend fun confirm(session: Session, taskId: Long, action: String, requestId: String): RemindTask
}

class RepositoryException(
    message: String,
    val sessionExpired: Boolean = false,
    val outcomeUnknown: Boolean = false,
) : Exception(message)
