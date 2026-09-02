package com.lockin.app

import android.app.Application
import com.lockin.app.data.AppDatabase
import com.lockin.app.data.TokenState
import com.lockin.app.notify.Notifier
import com.lockin.app.util.UnlockRepository
import com.lockin.app.worker.WeeklyTokenResetWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class LockInApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val db = AppDatabase.getInstance(this@LockInApplication)
            if (db.tokenDao().get() == null) {
                db.tokenDao().upsert(
                    TokenState(
                        tokensRemaining = UnlockRepository.WEEKLY_TOKENS,
                        weekStartEpochDay = LocalDate.now().toEpochDay()
                    )
                )
            }
        }
        Notifier.ensureChannels(this)
        WeeklyTokenResetWorker.schedule(this)
    }
}
