package com.nofollow.app.features.tracker.data.sync

import kotlinx.coroutines.sync.Mutex

/**
 * Satu kunci untuk semua request sweep ke Instagram (follower dan following). Dengan begitu dua sweep
 * tidak pernah berjalan bersamaan, apa pun pemicunya (worker, probe, tombol manual, buka app).
 */
class SyncGate {
    val mutex = Mutex()
}