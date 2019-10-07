/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.secureproxy

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import mozilla.components.browser.session.SessionManager
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.sync.AccountObserver
import mozilla.components.concept.sync.AuthType
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.feature.secureproxy.internal.SecureProxyAuthFetcher
import mozilla.components.feature.secureproxy.internal.SecureProxyBackgroundMessageHandler
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.service.location.MozillaLocationService
import mozilla.components.support.ktx.android.content.PreferencesHolder
import mozilla.components.support.ktx.android.content.booleanPreference
import mozilla.components.support.webextensions.WebExtensionController
import org.json.JSONObject

/**
 * Feature implementation that provides an interface to a secure proxy
 * WebExtension through sending native messages to a background page
 * interface.
 *
 * @property context Android application context
 * @property client the network Client implementation
 * @property accountManager FxA AccountManager instance
 * @property sessionManager SessionManager instance
 * @property locationService MozillaLocationService instance to obtain geo information
 * @property environment enum value of the current Environment
 * @property errorHandler error callback
 */
class SecureProxyFeature(
    private val context: Context,
    private val client: Client,
    private val accountManager: FxaAccountManager,
    private val sessionManager: SessionManager,
    private val locationService: MozillaLocationService,
    private val environment: Environment = Environment.DEV,
    private val errorHandler: (String) -> Unit = {}
) : AccountObserver {

    @VisibleForTesting
    internal var extensionController =
        WebExtensionController(SECURE_PROXY_EXTENSION_ID, SECURE_PROXY_EXTENSION_URL)

    val config = ProxyConfig()

    private val scope = MainScope()

    @VisibleForTesting
    internal val messageHandler by lazy {
        SecureProxyBackgroundMessageHandler(accountManager, ::sendCodeIfNeeded, errorHandler)
    }

    private val authFetcher by lazy {
        SecureProxyAuthFetcher(environment, client) { message ->
            extensionController.sendBackgroundMessage(message)
        }
    }

    /**
     * Method to call when resuming the app to ensure the extension is installed and an auth
     * code has been obtained
     */
    fun onResume() {
        installExtensionIfNeeded()
        sendCodeIfNeeded()
    }

    /**
     * Registers this feature to receive accountManager callbacks when the user logs in or out
     */
    fun onApplicationStartup() {
        accountManager.register(this)
        installExtensionIfNeeded()
    }

    /**
     * Helper method for consuming apps to decide if they should show proxy-related UI items
     *
     * @return true if the received country code is contained in the allowed list of codes
     */
    suspend fun shouldShowUI() : Boolean {
        return (locationService.fetchRegion()?.countryCode) in allowedRegionCodes
    }

    override fun onAuthenticated(account: OAuthAccount, authType: AuthType) {
        super.onAuthenticated(account, authType)

        if (authType == AuthType.Existing) return

        installExtensionIfNeeded()
        sendCodeIfNeeded()
    }

    override fun onLoggedOut() {
        super.onLoggedOut()
        extensionController.disconnectPort(null)
    }

    @VisibleForTesting
    internal fun sendEnabled(enabled: Boolean) {
        // TODO We shouldn't get here from the UI if no account is authenticated, but handle it
        extensionController.sendBackgroundMessage(createSendEnabledMessage(enabled))
    }

    @VisibleForTesting
    internal fun installExtensionIfNeeded() {
        if (!extensionController.portConnected(null)) {
            extensionController.registerBackgroundMessageHandler(messageHandler)
            extensionController.install(sessionManager.engine)
        }
    }

    @VisibleForTesting
    internal fun sendCodeIfNeeded() {
        // TODO Attempt to re-use code if we have one in memory and grab a new one if it fails
        if (config.enabled) {
            accountManager.authenticatedAccount()?.let { account ->
                Log.d("proxyAuth", "--- Sending auth code ---")
                scope.launch {
                    authFetcher.sendCode(account)
                }
            }
        }
    }

    private val allowedRegionCodes: Set<String> get() {
        return when (environment) {
            Environment.DEV -> setOf("US")
            Environment.PRODUCTION -> setOf("US")
        }
    }

    @VisibleForTesting
    inner class ProxyConfig : PreferencesHolder {
        override val preferences: SharedPreferences
            get() = context.getSharedPreferences(SHARED_PREF_NAME, Context.MODE_PRIVATE)

        var enabled by booleanPreference(KEY_PROXY_ENABLED, false) { new: Boolean ->
            sendEnabled(new)
            sendCodeIfNeeded()
        }
    }

    @VisibleForTesting
    companion object {
        internal const val SECURE_PROXY_EXTENSION_ID = "mozacSecureProxy"
        internal const val SECURE_PROXY_EXTENSION_URL = "resource://android/assets/extensions/secureproxy/"

        // Constants for storing the reader mode config in shared preferences
        const val SHARED_PREF_NAME = "mozac_feature_secure_proxy"
        const val KEY_PROXY_ENABLED = "mozac-secureproxy-enabled"

        internal const val ACTION_MESSAGE_KEY = "action"
        internal const val ACTION_ERROR_KEY = "error"
        internal const val ACTION_VALUE = "value"
        internal const val ACTION_SEND_CODE = "sendCode"
        internal const val ACTION_ENABLED_CODE = "sendEnabled"
        internal const val ACTION_VALUE_STATUS_CODE = "statusCode"
        internal const val ACTION_VALUE_AUTH_CODE = "authCode"

        private fun createSendEnabledMessage(enabled: Boolean): JSONObject {
            return JSONObject()
                    .put(ACTION_MESSAGE_KEY, ACTION_ENABLED_CODE)
                    .put(ACTION_VALUE, enabled.toString())
        }

        @Suppress("unused")
        enum class Environment {
            DEV, PRODUCTION
        }
    }
}
