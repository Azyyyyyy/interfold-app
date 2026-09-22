package app.interfold.app.ui.compose.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.interfold.app.ui.compose.components.interfoldLogoVectorPainter
import app.interfold.app.ui.model.LoginComponent
import app.interfold.app.ui.model.ServerHealthStatus
import app.interfold.app.api.LoginMethodsStatus
import app.interfold.app.utils.ColorSchemeParams
import app.interfold.app.utils.compose
import app.interfold.app.utils.composeColorSchemeParams
import app.interfold.app.utils.derive
import app.interfold.app.utils.state
import interfoldapp.shared.resources.Res
import interfoldapp.shared.resources.app_logo
import interfoldapp.shared.resources.apple_logo
import interfoldapp.shared.resources.cancel
import interfoldapp.shared.resources.cloudflare_logo
import interfoldapp.shared.resources.direct_token_login_body
import interfoldapp.shared.resources.direct_token_login_title
import interfoldapp.shared.resources.discord_logo
import interfoldapp.shared.resources.google_logo
import interfoldapp.shared.resources.login
import interfoldapp.shared.resources.login_apple
import interfoldapp.shared.resources.login_cloudflare
import interfoldapp.shared.resources.login_discord
import interfoldapp.shared.resources.login_google
import interfoldapp.shared.resources.login_methods_loading
import interfoldapp.shared.resources.login_methods_none
import interfoldapp.shared.resources.login_methods_retry
import interfoldapp.shared.resources.login_methods_unavailable
import interfoldapp.shared.resources.or_lowercase
import interfoldapp.shared.resources.token
import interfoldapp.shared.resources.welcome_body
import interfoldapp.shared.resources.welcome_title
import org.jetbrains.compose.resources.painterResource

@Composable
fun LoginScreen(
  component: LoginComponent
) {
  val model by component.model.collectAsState()
  val directTokenDialogOpen = model.directTokenDialogOpen
  val serverUrl = model.serverUrl
  val serverHealthStatus = model.serverHealthStatus
  val loginMethods = model.loginMethods
  val loginMethodsStatus = model.loginMethodsStatus

  val settings by component.settings.collectAsState()
  val reduceMotion by derive { settings.reduceMotion }

  Scaffold(
    topBar = {
      ServerUrlTopBar(
        serverUrl = serverUrl,
        healthStatus = serverHealthStatus,
        onUrlChange = component::updateServerUrl,
        onCheckHealth = component::checkServerHealth
      )
    },
    bottomBar = {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Column(
          modifier = Modifier.widthIn(max = 450.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          when (loginMethodsStatus) {
            LoginMethodsStatus.Idle -> {}
            LoginMethodsStatus.Loading -> {
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                  Res.string.login_methods_loading.compose,
                  style = MaterialTheme.typography.bodyMedium,
                )
              }
            }
            LoginMethodsStatus.Failed -> LoginMethodsMessage(
              text = Res.string.login_methods_unavailable.compose,
              onRetry = component::fetchLoginMethods,
            )
            LoginMethodsStatus.Ready -> {
              if (loginMethods.hasAny) {
                LoginMethodsButtons(component, loginMethods)
              } else {
                LoginMethodsMessage(
                  text = Res.string.login_methods_none.compose,
                  onRetry = component::fetchLoginMethods,
                )
              }
            }
          }
          Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.systemBars))
        }
      }
    },
    content = { innerPadding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .consumeWindowInsets(innerPadding)
          .padding(innerPadding)
          .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
          modifier = Modifier.fillMaxHeight().widthIn(max = 450.dp)
        ) {
          Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Image(
              painter = interfoldLogoVectorPainter(animate = !reduceMotion),
              contentDescription = Res.string.app_logo.compose,
              modifier = Modifier.size(128.dp).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = component::incrementDirectTokenLoginTimesPressed
              )
            )
            Spacer(modifier = Modifier.height(24.dp))
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
              Column(
                modifier = Modifier.padding(16.dp)
              ) {
                Row {
                  Text(
                    Res.string.welcome_title.compose,
                    style = MaterialTheme.typography.displaySmall
                  )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  Res.string.welcome_body.compose,
                  style = MaterialTheme.typography.bodyMedium.merge(
                    lineHeight = 1.5.em
                  )
                )
              }
            }
          }
        }
      }

      if (directTokenDialogOpen) {
        DirectTokenLoginDialog(
          onDismissRequest = component::closeDirectTokenDialog,
          logInWithToken = component::logInWithDirectToken
        )
      }
    }
  )
}

@Composable
private fun LoginMethodsMessage(
  text: String,
  onRetry: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
    TextButton(onClick = onRetry) {
      Text(Res.string.login_methods_retry.compose)
    }
  }
}

@Composable
private fun LoginMethodsButtons(
  component: LoginComponent,
  loginMethods: app.interfold.app.api.LoginMethods,
) {
  if (loginMethods.cloudflare) {
    CloudflareLoginButton(component::logInWithCloudflare)
    return
  }

  val showDiscord = loginMethods.discord
  val showGoogle = loginMethods.google
  val showApple = loginMethods.apple

  if (showDiscord) {
    DiscordLoginButton(component::logInWithDiscord)
  }
  if (showDiscord && (showGoogle || showApple)) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      HorizontalDivider()
      Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.surface),
      ) {
        Text(
          Res.string.or_lowercase.compose,
          style = MaterialTheme.typography.labelMedium.copy(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
          ),
          modifier = Modifier.padding(horizontal = 16.dp)
        )
      }
    }
  }
  if (showGoogle || showApple) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
      if (showGoogle) {
        GoogleLoginButton(component::logInWithGoogle, modifier = Modifier.weight(1f).fillMaxHeight())
      }
      if (showGoogle && showApple) {
        Spacer(modifier = Modifier.width(8.dp))
      }
      if (showApple) {
        AppleLoginButton(component::logInWithApple, modifier = Modifier.weight(1f).fillMaxHeight())
      }
    }
  }
}

@Composable
private fun CloudflareLoginButton(logIn: (ColorSchemeParams) -> Unit) {
  val colorSchemeParams = composeColorSchemeParams
  Button(
    onClick = { logIn(colorSchemeParams) },
    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    modifier = Modifier.fillMaxWidth()
  ) {
    Icon(
      painterResource(Res.drawable.cloudflare_logo),
      contentDescription = null,
      modifier = Modifier.size(ButtonDefaults.IconSize),
      tint = Color.Unspecified,
    )
    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
    Text(Res.string.login_cloudflare.compose)
  }
}

@Composable
private fun ServerUrlTopBar(
  serverUrl: String,
  healthStatus: ServerHealthStatus,
  onUrlChange: (String) -> Unit,
  onCheckHealth: () -> Unit
) {
  var dialogOpen by state(false)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .windowInsetsPadding(WindowInsets.statusBars)
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    FilledTonalButton(onClick = { dialogOpen = true }) {
      Text("Edit Server")
    }
    Spacer(modifier = Modifier.width(8.dp))
    ServerHealthStatusIndicator(healthStatus)
  }

  if (dialogOpen) {
    ServerUrlDialog(
      serverUrl = serverUrl,
      healthStatus = healthStatus,
      onUrlChange = onUrlChange,
      onCheckHealth = onCheckHealth,
      onDismiss = { dialogOpen = false }
    )
  }
}

@Composable
private fun ServerUrlDialog(
  serverUrl: String,
  healthStatus: ServerHealthStatus,
  onUrlChange: (String) -> Unit,
  onCheckHealth: () -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Server URL") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = serverUrl,
          onValueChange = onUrlChange,
          label = { Text("Server URL") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          trailingIcon = {
            when (healthStatus) {
              ServerHealthStatus.CHECKING -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
              )
              else -> {}
            }
          }
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(onClick = onCheckHealth) {
            Text("Check Status")
          }
          ServerHealthStatusIndicator(healthStatus)
        }
      }
    },
    confirmButton = {
      Button(onClick = {
        onCheckHealth()
        onDismiss()
      }) {
        Text("Done")
      }
    }
  )
}

@Composable
private fun ServerHealthStatusIndicator(status: ServerHealthStatus) {
  val (text, color) = when (status) {
    ServerHealthStatus.UNKNOWN -> "Not checked" to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    ServerHealthStatus.CHECKING -> "Checking..." to MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    ServerHealthStatus.HEALTHY -> "Server is up" to Color(0xFF4CAF50)
    ServerHealthStatus.DEGRADED -> "Server is degraded" to Color(0xFFFF9800)
    ServerHealthStatus.UNREACHABLE -> "Server is unreachable" to Color(0xFFF44336)
  }
  Text(
    text = text,
    style = MaterialTheme.typography.labelMedium,
    color = color
  )
}

@Composable
private fun GoogleLoginButton(logIn: (ColorSchemeParams) -> Unit, modifier: Modifier = Modifier) {
  val colorSchemeParams = composeColorSchemeParams
  Button(
    onClick = { logIn(colorSchemeParams) },
    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    modifier = modifier
  ) {
    Icon(
      painterResource(Res.drawable.google_logo),
      contentDescription = null,
      modifier = Modifier.size(ButtonDefaults.IconSize)
    )
    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
    Text(Res.string.login_google.compose, maxLines = 2)
  }
}

@Composable
private fun AppleLoginButton(logIn: (ColorSchemeParams) -> Unit, modifier: Modifier = Modifier) {
  val colorSchemeParams = composeColorSchemeParams
  Button(
    onClick = { logIn(colorSchemeParams) },
    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    modifier = modifier
  ) {
    Icon(
      painterResource(Res.drawable.apple_logo),
      contentDescription = null,
      modifier = Modifier.size(ButtonDefaults.IconSize)
    )
    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
    Text(Res.string.login_apple.compose, maxLines = 2)
  }
}

@Composable
private fun DiscordLoginButton(logIn: (ColorSchemeParams) -> Unit) {
  val colorSchemeParams = composeColorSchemeParams

  FilledTonalButton(
    onClick = { logIn(colorSchemeParams) },
    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    modifier = Modifier.fillMaxWidth()
  ) {
    Icon(
      painterResource(Res.drawable.discord_logo),
      contentDescription = null,
      modifier = Modifier.size(ButtonDefaults.IconSize)
    )
    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
    Text(Res.string.login_discord.compose)
  }
}

@Composable
private fun DirectTokenLoginDialog(
  onDismissRequest: () -> Unit,
  logInWithToken: (String) -> Unit
) {
  val focusRequester = remember { FocusRequester() }

  var token by state("")

  AlertDialog(
    onDismissRequest = onDismissRequest,
    title = { Text(Res.string.direct_token_login_title.compose) },
    text = {
      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        item {
          Text(Res.string.direct_token_login_body.compose)
        }
        item {
          TextField(
            value = token,
            onValueChange = {
              if (it.length > 1_000) return@TextField
              token = it
            },
            label = { Text(Res.string.token.compose) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
          )

          LaunchedEffect(focusRequester) {
            focusRequester.requestFocus()
          }
        }
      }
    },
    confirmButton = {
      Button(onClick = { logInWithToken(token) }) {
        Text(Res.string.login.compose)
      }
    },
    dismissButton = {
      Button(onClick = onDismissRequest) {
        Text(Res.string.cancel.compose)
      }
    }
  )
}