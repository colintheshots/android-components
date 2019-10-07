package mozilla.components.feature.secureproxy.internal

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.fetch.Request
import mozilla.components.concept.fetch.isSuccess
import mozilla.components.concept.sync.AccessType
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.feature.secureproxy.SecureProxyFeature
import mozilla.components.feature.secureproxy.SecureProxyFeature.Companion.Environment
import org.json.JSONObject

/**
 * Authentication handlers for Secure Proxy feature
 *
 * @property client http concept fetch client
 */
class SecureProxyAuthFetcher(
    private val environment: Environment,
    private val client: Client,
    private val backgroundMessageSender: (JSONObject) -> Unit
) {

    internal suspend fun sendCode(account: OAuthAccount): JSONObject {
        return withContext(Dispatchers.IO) {
            when (val stateResult = getFPNState(environment)) {
                is AuthResult.Success -> {
                    val statusCode = stateResult.value
                    val authCode = getCode(account, statusCode)
                            ?: return@withContext createErrorReturn("Auth code is null")
                    val authCodeMessage = createSendCodeMessage(statusCode, authCode)
                    backgroundMessageSender(authCodeMessage)
                    Log.d("proxyAuth", "received auth code - $authCodeMessage")
                    return@withContext authCodeMessage
                }
                is AuthResult.Failure -> {
                    return@withContext createErrorReturn("FPN state request failed: ${stateResult.errorMessage}")
                }
            }
        }
    }

    private fun getFPNState(environment: Environment): AuthResult {
        val request = Request(getFPNStatePath(environment), headers = null)
        val response = client.fetch(request)
        return response.use { r ->
            when {
                r.isSuccess -> {
                    r.body.useBufferedReader {
                        val bodyJson = JSONObject(it.readText())
                        AuthResult.Success(bodyJson.getString(STATE_TOKEN_KEY))
                    }
                }
                else -> AuthResult.Failure(r.status.toString())
            }
        }
    }

    private fun getCode(account: OAuthAccount, state: String): String? =
            account.authorizeOAuthCode(FPN_CLIENT_ID, FPN_SCOPES, state, accessType = AccessType.OFFLINE)

    private fun getEndpointURL(environment: Environment): String {
        return when (environment) {
            Environment.DEV -> {
                DEV_FPN_ENDPOINT_URL
            }
            Environment.PRODUCTION -> {
                PROD_FPN_ENDPOINT_URL
            }
        }
    }

    private fun getFPNStatePath(environment: Environment) : String =
            getEndpointURL(environment) + FPN_STATE_PATH

    sealed class AuthResult {
        data class Success(val value: String) : AuthResult()
        data class Failure(val errorMessage: String) : AuthResult()
    }

    companion object {
        private const val PROD_FPN_ENDPOINT_URL = "https://fpn.firefox.com/"
        private const val DEV_FPN_ENDPOINT_URL = "https://guardian-dev.herokuapp.com/"
        private const val FPN_STATE_PATH = "browser/oauth/state"
        private const val FPN_CLIENT_ID = "565585c1745a144d"
        private val FPN_SCOPES = arrayOf("profile", "https://identity.mozilla.com/apps/secure-proxy")
        private const val STATE_TOKEN_KEY = "state_token"

        private fun createSendCodeMessage(statusCode: String?, authCode: String?): JSONObject {
            return JSONObject()
                    .put(SecureProxyFeature.ACTION_MESSAGE_KEY, SecureProxyFeature.ACTION_SEND_CODE)
                    .put(SecureProxyFeature.ACTION_VALUE_STATUS_CODE, statusCode)
                    .put(SecureProxyFeature.ACTION_VALUE_AUTH_CODE, authCode)
        }

        private fun createErrorReturn(errorMessage: String): JSONObject {
            return JSONObject().put(SecureProxyFeature.ACTION_ERROR_KEY, errorMessage)
        }
    }
}