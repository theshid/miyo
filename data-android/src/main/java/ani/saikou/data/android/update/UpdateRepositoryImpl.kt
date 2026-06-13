package ani.saikou.data.android.update

import android.content.Context
import android.os.Build
import ani.saikou.domain.model.update.InstalledAppInfo
import ani.saikou.domain.model.update.UpdateArtifactRef
import ani.saikou.domain.model.update.UpdateError
import ani.saikou.domain.model.update.UpdateManifest
import ani.saikou.domain.model.update.UpdateProgress
import ani.saikou.domain.repository.UpdateRepository
import ani.saikou.platform.log.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Sole Android-side implementation of [`UpdateRepository`]. Composed of small,
 * separately-tested collaborators ([`ApkValidator`], [`SignerFingerprinter`],
 * [`InstallerLauncher`], etc.). Manifest URL is injected from the :app DI
 * module so it can be overridden via BuildConfig without restructuring.
 *
 * Networking and file I/O run on Dispatchers.IO. CancellationException is
 * never swallowed — partial downloads are deleted, then the exception
 * propagates so the caller can react.
 */
class UpdateRepositoryImpl(
    private val context: Context,
    private val client: HttpClient,
    private val manifestUrl: String,
    fileProviderAuthority: String,
    private val expectedPackage: String,
    private val installedVersionCode: Int,
    private val installedVersionName: String,
    private val logger: Logger,
) : UpdateRepository {
    private val cache = UpdateCache(context)
    private val signer = SignerFingerprinter(context.packageManager)
    private val validator = ApkValidator(expectedPackage)
    private val installer = InstallerLauncher(context, fileProviderAuthority)
    private val unknownSourcesGuard = UnknownSourcesGuard(context)

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    override suspend fun fetchManifest(): UpdateManifest =
        withContext(Dispatchers.IO) {
            // withTimeoutOrNull guards BOTH the headers and the body read.
            // client.get() returns once headers arrive; a slow server that
            // dribbles the body would otherwise hang indefinitely. Using
            // withTimeoutOrNull (instead of withTimeout) keeps the
            // TimeoutCancellationException from short-circuiting the
            // catch-CE branch — external cancellation still propagates.
            val raw: String =
                try {
                    withTimeoutOrNull(MANIFEST_TIMEOUT_MS) {
                        val response: HttpResponse = client.get(manifestUrl)
                        if (!response.status.isSuccess()) {
                            throw UpdateError.ManifestUnreachable(statusCode = response.status.value)
                        }
                        response.body<String>()
                    } ?: throw UpdateError.ManifestUnreachable(statusCode = null)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: UpdateError) {
                    throw e
                } catch (e: IOException) {
                    throw UpdateError.NetworkUnavailable(e)
                } catch (e: Throwable) {
                    throw UpdateError.ManifestUnreachable(statusCode = null, cause = e)
                }
            try {
                json.decodeFromString(UpdateManifestDto.serializer(), raw).toDomain()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: SerializationException) {
                throw UpdateError.ManifestMalformed(e)
            } catch (e: IllegalArgumentException) {
                throw UpdateError.ManifestMalformed(e)
            }
        }

    override suspend fun installedAppInfo(): InstalledAppInfo =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val pkg = context.packageName
            val versionCode =
                runCatching {
                    val info = pm.getPackageInfo(pkg, 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        info.longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        info.versionCode
                    }
                }.getOrDefault(installedVersionCode)
            val versionName =
                runCatching {
                    pm.getPackageInfo(pkg, 0).versionName ?: installedVersionName
                }.getOrDefault(installedVersionName)
            val signerSha = signer.installedFingerprint(pkg)
            InstalledAppInfo(
                packageName = pkg,
                versionCode = versionCode,
                versionName = versionName,
                signerSha256 = signerSha,
            )
        }

    override fun downloadAndPrepare(manifest: UpdateManifest): Flow<UpdateProgress> =
        flow {
            emit(UpdateProgress.Idle)
            val tempFile = cache.tempFileFor(manifest)
            val finalFile = cache.fileFor(manifest)
            if (tempFile.exists()) tempFile.delete()
            try {
                stream(manifest, tempFile) { read, total ->
                    emit(UpdateProgress.Downloading(read, total))
                }

                emit(UpdateProgress.Verifying)

                val computed = Sha256Streamer.hashOf(tempFile)
                if (!computed.equals(manifest.sha256, ignoreCase = true)) {
                    tempFile.delete()
                    emit(UpdateProgress.Failed(UpdateError.ChecksumMismatch))
                    return@flow
                }

                val observed = observeApk(tempFile)
                val installedSigner = signer.installedFingerprint(expectedPackage)
                when (val verdict = validator.validate(observed, manifest, installedSigner)) {
                    is ApkValidator.Result.Failed -> {
                        tempFile.delete()
                        emit(UpdateProgress.Failed(verdict.error))
                        return@flow
                    }
                    ApkValidator.Result.Ok -> Unit
                }

                if (finalFile.exists()) finalFile.delete()
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.delete()
                    emit(UpdateProgress.Failed(UpdateError.DownloadFailed(IOException("rename failed"))))
                    return@flow
                }

                emit(UpdateProgress.Ready(UpdateArtifactRef(finalFile.absolutePath)))
            } catch (ce: CancellationException) {
                tempFile.delete()
                throw ce
            } catch (e: UpdateError) {
                tempFile.delete()
                emit(UpdateProgress.Failed(e))
            } catch (e: IOException) {
                tempFile.delete()
                logger.reportError(AREA, "downloadAndPrepare", e)
                emit(UpdateProgress.Failed(UpdateError.DownloadFailed(e)))
            } catch (e: Throwable) {
                tempFile.delete()
                logger.reportError(AREA, "downloadAndPrepare", e)
                emit(UpdateProgress.Failed(UpdateError.DownloadFailed(e)))
            }
        }.flowOn(Dispatchers.IO)

    override fun isInstallPermissionGranted(): Boolean = unknownSourcesGuard.isPermissionGranted()

    override fun openInstallPermissionSettings() {
        unknownSourcesGuard.openSettings()
    }

    override suspend fun startInstall(artifact: UpdateArtifactRef) =
        withContext(Dispatchers.Main) {
            try {
                installer.launch(File(artifact.token))
            } catch (e: UpdateError) {
                throw e
            } catch (e: Throwable) {
                throw UpdateError.InstallerLaunchFailed(e)
            }
        }

    override suspend fun cleanupStaleArtifacts(current: UpdateManifest?) =
        withContext(Dispatchers.IO) {
            cache.cleanup(current)
        }

    private suspend inline fun stream(
        manifest: UpdateManifest,
        target: File,
        crossinline onProgress: suspend (Long, Long?) -> Unit,
    ) {
        client.prepareGet(manifest.apkUrl).execute { response ->
            if (!response.status.isSuccess()) {
                throw UpdateError.DownloadFailed(IOException("HTTP ${response.status.value}"))
            }
            val total: Long? = response.contentLength().takeIf { it != null && it > 0 }
            val channel: ByteReadChannel = response.bodyAsChannel()
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                var read = 0L
                var lastEmittedAt = 0L
                onProgress(0L, total)
                while (!channel.isClosedForRead) {
                    currentCoroutineContext().ensureActive()
                    val n = channel.readAvailable(buffer, 0, buffer.size)
                    if (n < 0) break
                    if (n > 0) {
                        output.write(buffer, 0, n)
                        read += n
                        val now = System.currentTimeMillis()
                        if (now - lastEmittedAt >= PROGRESS_EMIT_MIN_MS || (total != null && read == total)) {
                            onProgress(read, total)
                            lastEmittedAt = now
                        }
                    }
                }
                output.flush()
                onProgress(read, total)
            }
        }
    }

    private fun observeApk(apk: File): ObservedApkInfo {
        val info =
            try {
                context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            } catch (_: Exception) {
                null
            }
        val versionCode =
            info?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    it.longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    it.versionCode
                }
            }
        return ObservedApkInfo(
            packageName = info?.packageName,
            versionCode = versionCode,
            signerSha256 = signer.apkFingerprint(apk),
        )
    }

    private companion object {
        private const val BUFFER_SIZE = 8 * 1024
        private const val MANIFEST_TIMEOUT_MS = 15_000L
        private const val PROGRESS_EMIT_MIN_MS = 120L
        private const val AREA = "self-update"
    }
}
