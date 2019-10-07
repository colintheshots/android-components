/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.secureproxy.internal

import android.util.Log
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.service.fxa.manager.FxaAccountManager
import org.json.JSONObject

/**
 * Handler for messages from the background process for the Secure Proxy
 * WebExtension.
 *
 * @property accountManager FxA account manager
 * @property sendCodeIfNeeded authentication callback
 * @property errorHandler error handler callback
 */
internal class SecureProxyBackgroundMessageHandler(
        private val accountManager: FxaAccountManager,
        private val sendCodeIfNeeded: () -> Unit,
        private val errorHandler: (String) -> Unit) : MessageHandler {

    override fun onPortConnected(port: Port) {
        Log.d("proxyStatus", "Port connected.")
        sendCodeIfNeeded()
    }

    override fun onPortMessage(message: Any, port: Port) {
        if (message is JSONObject) {
            when (val error = message.optString(MESSAGE_TYPE)) {
                "" -> {
                    Log.d("proxyState", message.toString())
                }
                ACTION_ERROR_CODE -> {
                    Log.d("proxyError", "Error was $error")
                    errorHandler(error)
                }
                else -> {
                    Log.d("proxyState", message.toString())
                }
            }
        } else {
            Log.d("proxyState", "Message: $message")
        }
    }

    companion object {
        internal const val MESSAGE_TYPE = "type"
        internal const val ACTION_ERROR_CODE = "sendError"
    }
}
