package app.interfold.glance

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.interfold.app.api.CloudflareAccessCredentials
import app.interfold.app.api.getFrontingAlters
import app.interfold.app.api.model.APIResponse
import app.interfold.app.api.model.MyFrontItem
import app.interfold.app.utils.compressAsWebP
import app.interfold.app.utils.globalSerializer
import app.interfold.util.createSharedPreferences
import app.interfold.util.getSavedSettings
import coil3.imageLoader
import coil3.memory.MemoryCache
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Duration

class FrontWidgetWorker(
  private val context: Context,
  workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {
  companion object {
    private val uniqueWorkName = FrontWidgetWorker::class.java.simpleName

    fun enqueue(context: Context, glanceId: GlanceId, force: Boolean = true) {
      val manager = WorkManager.getInstance(context)
      val requestBuilder = OneTimeWorkRequestBuilder<FrontWidgetWorker>().apply {
        addTag(glanceId.toString())
        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
      }
      // One stable name. Settings.hashCode() changed whenever theme or other
      // fields changed, and REPLACE then cancelled the fetch before it could
      // write widget state. The worker reads the latest settings itself.
      val workPolicy = if (force) {
        ExistingWorkPolicy.REPLACE
      } else {
        ExistingWorkPolicy.KEEP
      }

      manager.enqueueUniqueWork(
        uniqueWorkName,
        workPolicy,
        requestBuilder.build()
      )

      // Temporary workaround to avoid WM provider to disable itself and trigger an
      // app widget update
      manager.enqueueUniqueWork(
        "$uniqueWorkName-workaround",
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<FrontWidgetWorker>().apply {
          setInitialDelay(Duration.ofDays(365))
        }.build()
      )
    }

    /**
     * Cancel any ongoing worker
     */
    fun cancel(context: Context, glanceId: GlanceId) {
      WorkManager.getInstance(context).cancelAllWorkByTag(glanceId.toString())
    }
  }

  override suspend fun doWork(): Result {
    val settings = getSavedSettings(createSharedPreferences(context))
    val token = settings.token
    if (token == null || settings.tokenIsProtected) {
      // Composition already shows the login / PIN screen. Do not retry.
      return Result.success()
    }

    CloudflareAccessCredentials.update(
      jwt = settings.cloudflareAccessJwt,
      nearExpirySkewMinutes = settings.cloudflareAccessNearExpirySkewMinutes,
      apiEndpoint = settings.apiEndpoint,
    )

    return try {
      val currentlyFronting = getFrontingAlters(settings.apiEndpoint, token)
      if (currentlyFronting.isError) {
        updateFrontWidget(currentlyFronting)
        return Result.success()
      }

      // Publish names before avatar work. A failed image must not leave the
      // widget on "Loading alters...".
      val withoutAvatars = currentlyFronting.mapData { items ->
        items.map { it.copy(alter = it.alter.copy(avatarUrl = null)) }
      }
      updateFrontWidget(withoutAvatars)
      try {
        updateFrontWidget(loadImages(currentlyFronting))
      } catch (imageError: Exception) {
        Log.e(uniqueWorkName, "Widget loaded without avatars", imageError)
      }

      Result.success()
    } catch (e: Exception) {
      Log.e(uniqueWorkName, "Error while loading widget data!", e)
      if (isAuthFailure(e)) {
        updateFrontWidget(APIResponse.error("Failed to load fronting alters."))
        Result.failure()
      } else if (runAttemptCount < 3) {
        Result.retry()
      } else {
        updateFrontWidget(APIResponse.error("Failed to load fronting alters."))
        Result.failure()
      }
    }
  }

  private fun isAuthFailure(error: Throwable): Boolean {
    val response = (error as? ResponseException)?.response ?: return false
    return response.status == HttpStatusCode.Unauthorized ||
      response.status == HttpStatusCode.Forbidden
  }

  private fun <T> APIResponse<T>.mapData(transform: (T) -> T): APIResponse<T> =
    if (isError) this else APIResponse.success(transform(ensureSuccess))

  private suspend fun loadImages(frontingAlters: APIResponse<List<MyFrontItem>>): APIResponse<List<MyFrontItem>> = coroutineScope {
    val data = frontingAlters.ensureSuccess

    // Load all images in parallel and wait for all of them to finish
    val newItems =
      data.map { item ->
        async(Dispatchers.IO) {
          if(item.alter.avatarUrl == null) {
            item
          } else {
            try {
              val imageBitmapBase64 = getImageBitmap(item.alter.avatarUrl!!)
              // A failed avatar must not be stored as a URL. The widget
              // decodes this field as base64, and a URL throws in composition.
              if (imageBitmapBase64 == null) {
                item.copy(alter = item.alter.copy(avatarUrl = null))
              } else {
                item.copy(alter = item.alter.copy(avatarUrl = imageBitmapBase64))
              }
            } catch(e: Exception) {
              Log.e("INTERFOLD", "Error while loading image", e)
              item.copy(alter = item.alter.copy(avatarUrl = null))
            }
          }
        }
      }.awaitAll()

    APIResponse.success(newItems)
  }

  private suspend fun updateFrontWidget(frontingAlters: APIResponse<List<MyFrontItem>>) {
    val manager = GlanceAppWidgetManager(context)
    val glanceIds = manager.getGlanceIds(FrontWidget::class.java)
    glanceIds.forEach { glanceId ->
      updateAppWidgetState(context, glanceId) { prefs ->
        prefs[FrontWidget.resultKey] = globalSerializer.encodeToString(frontingAlters)
      }
    }
    FrontWidget().updateAll(context)
  }

  private suspend fun getImageBitmap(url: String, force: Boolean = false): String? {
    return try {
      val request = ImageRequest.Builder(context)
        .data(url)
        .httpHeaders(accessHeaders())
        .build()

      // Request the image to be loaded and throw error if it failed
      with(context.imageLoader) {
        if (force) {
          diskCache?.remove(url)
          memoryCache?.remove(MemoryCache.Key(url))
        }
        val result = execute(request)
        if (result is ErrorResult) {
          throw result.throwable
        }
      }

      val encoded = context.imageLoader.diskCache?.openSnapshot(url)?.use { snapshot ->
        val imageFile = snapshot.data.toFile()
        val decoded = BitmapFactory.decodeFile(imageFile.absolutePath) ?: return@use null
        Base64.encodeToString(decoded.compressAsWebP(), Base64.DEFAULT)
      }
      requireNotNull(encoded) {
        "Couldn't find cached file"
      }
    } catch (e: Exception) {
      Log.e("INTERFOLD", "Error while loading widget image", e)
      null
    }
  }

  private fun accessHeaders(): NetworkHeaders {
    val jwt = CloudflareAccessCredentials.accessJwt ?: return NetworkHeaders.EMPTY
    return NetworkHeaders.Builder()
      .set(CloudflareAccessCredentials.JWT_ASSERTION_HEADER, jwt)
      .set("Cookie", "${CloudflareAccessCredentials.COOKIE_NAME}=$jwt")
      .build()
  }
}