package com.fer.projekt.service

object CancellationManager {
    @Volatile private var cancelled = false
    private val lock = Any()
    private val suppliersStateSaved: MutableSet<String> = mutableSetOf()

    fun isCancelled() = cancelled

    fun cancel() {
        cancelled = true
    }

    fun reset() {
        synchronized(lock) {
            cancelled = false
            suppliersStateSaved.clear()
        }
    }

    fun saveStateOnceForSupplier(jplagRunId: String, saveState: () -> Unit) {
        synchronized(lock) {
            if (!suppliersStateSaved.contains(jplagRunId)) {
                saveState()
                suppliersStateSaved.add(jplagRunId)
            }
        }
    }
}
