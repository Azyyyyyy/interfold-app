package app.interfold.app.ui.model

import app.interfold.app.api.client
import app.interfold.app.BuildInfo
import app.interfold.app.api.model.SNAPINewsArticle
import app.interfold.app.api.model.SNAPIResponse
import app.interfold.app.ui.registerStateHandler
import app.interfold.app.ui.retainStateHandler
import app.interfold.app.utils.ColorSchemeParams
import app.interfold.app.utils.currentPlatform
import app.interfold.app.utils.ioDispatcher
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.http.userAgent
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

interface StealthAppComponent {
  val model: StateFlow<Model>

  @Serializable
  data class Model(
    val articles: ArticleState = ArticleState.Loading
  )

  fun openArticle(url: String, colorSchemeParams: ColorSchemeParams)
  fun loadArticles()

  fun exitStealthMode()

  @Serializable
  sealed interface ArticleState {
    @Serializable
    data object Loading : ArticleState
    @Serializable
    data class Success(val articles: List<SNAPINewsArticle>) : ArticleState
    @Serializable
    data class Error(val message: String) : ArticleState
  }
}

private val snapiHttpBuilder: () -> (HttpRequestBuilder.() -> Unit) = {
  {
    headers {
      header("Content-Type", "application/json")
    }
    userAgent("Interfold ${BuildInfo.DISPLAY_STRING}; ${currentPlatform.displayName}")
  }
}

private suspend fun getSNAPIArticles() =
  client
    .get("https://api.spaceflightnewsapi.net/v4/articles/", snapiHttpBuilder())
    .body<SNAPIResponse>()

internal class StealthAppComponentImpl(
  componentContext: CommonComponentContext,
  val navigateToPINEntry: () -> Unit,
  val navigateToMainApp: (String) -> Unit
) : StealthAppComponent, CommonComponentContext by componentContext {
  val coroutineScope = coroutineScope(coroutineContext + SupervisorJob())
  private val handler = retainStateHandler { StealthAppComponent.Model() }
  override val model = handler.model

  init {
    registerStateHandler(handler)
    loadArticles()
  }

  override fun openArticle(url: String, colorSchemeParams: ColorSchemeParams) {
    platformUtilities.openURL(url, colorSchemeParams)
  }

  override fun loadArticles() {
    coroutineScope.launch {
      try {
        withContext(ioDispatcher) {
          val fetchedArticles = getSNAPIArticles()
          handler.model.tryEmit(
            handler.model.value.copy(
              articles = StealthAppComponent.ArticleState.Success(fetchedArticles.results)
            )
          )
        }
      } catch (e: Throwable) {
        e.printStackTrace()

        handler.model.tryEmit(
          handler.model.value.copy(
            articles = StealthAppComponent.ArticleState.Error("Error fetching news articles!")
          )
        )
      }
    }
  }

  override fun exitStealthMode() {
    if(settings.data.value.tokenIsProtected) {
      navigateToPINEntry()
    } else {
      navigateToMainApp(settings.data.value.token!!)
    }
  }
}