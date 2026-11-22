package com.nofollow.app.features.update.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.nofollow.app.core.common.AppError
import com.nofollow.app.core.common.AppResult
import com.nofollow.app.features.update.data.remote.GitHubReleaseApi
import com.nofollow.app.features.update.domain.model.AppUpdate
import com.nofollow.app.features.update.domain.model.AppVersion
import com.nofollow.app.features.update.domain.model.UpdateConfig
import com.nofollow.app.features.update.domain.repository.UpdateRepository

class UpdateRepositoryImpl(
    private val context: Context,
    private val api: GitHubReleaseApi,
    private val config: UpdateConfig,
) : UpdateRepository {

    override fun installedVersion(): AppVersion {
        val name = try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0).versionName
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        // Tidak mungkin gagal untuk app sendiri; 0.0.0 membuat rilis apa pun dianggap lebih baru.
        return AppVersion.parseOrNull(name) ?: AppVersion(0, 0, 0)
    }

    override suspend fun fetchLatestVersion(): AppResult<AppVersion> = when (val result = api.latestTag()) {
        is AppResult.Failure -> result
        is AppResult.Success ->
            AppVersion.parseOrNull(result.data)?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.Unknown("tag rilis tidak dikenali: ${result.data}"))
    }

    override fun updateFor(version: AppVersion): AppUpdate {
        val releaseUrl = config.releasePageUrl(version)
        // SUPPORTED_ABIS terurut dari yang paling disukai HP; ambil yang pertama yang APK-nya kita rilis.
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in config.supportedAbis }
        // ABI tidak dikenal (mis. emulator x86): arahkan ke halaman rilis, user pilih file sendiri.
        val downloadUrl = abi?.let(config::apkDownloadUrl) ?: releaseUrl
        return AppUpdate(version, downloadUrl, releaseUrl)
    }
}