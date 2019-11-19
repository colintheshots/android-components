/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.secureproxy

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import mozilla.components.browser.session.SessionManager
import mozilla.components.concept.engine.Engine
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.concept.fetch.Client
import mozilla.components.concept.sync.AuthType
import mozilla.components.concept.sync.OAuthAccount
import mozilla.components.service.fxa.manager.FxaAccountManager
import mozilla.components.service.location.MozillaLocationService
import mozilla.components.support.test.any
import mozilla.components.support.test.argumentCaptor
import mozilla.components.support.test.eq
import mozilla.components.support.test.mock
import mozilla.components.support.test.robolectric.testContext
import mozilla.components.support.webextensions.WebExtensionController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SecureProxyFeatureTest {

    private lateinit var context: Context
    private lateinit var client: Client
    private lateinit var accountManager: FxaAccountManager
    private lateinit var sessionManager: SessionManager

    @Before
    fun setup() {
        context = spy(testContext)
        client = mock()
        accountManager = mock()
        sessionManager = mock()

        WebExtensionController.installedExtensions.clear()
    }

    @Test
    fun `launch secure proxy feature install only once`() {
        val locationService = mock<MozillaLocationService>()
        val engine = mock<Engine>()
        val account = mock<OAuthAccount>()
        val ext = mock<WebExtension>()
        `when`(sessionManager.engine).thenReturn(engine)
        `when`(accountManager.authenticatedAccount()).thenReturn(null)
        val secureProxyFeature = spy(SecureProxyFeature(context, client, accountManager, sessionManager, locationService))

        secureProxyFeature.onApplicationStartup()
        WebExtensionController.installedExtensions[SecureProxyFeature.SECURE_PROXY_EXTENSION_ID] = ext
        secureProxyFeature.onResume()
        `when`(accountManager.authenticatedAccount()).thenReturn(account)
        secureProxyFeature.onAuthenticated(account, AuthType.Signin)

        val onSuccess = argumentCaptor<((WebExtension) -> Unit)>()
        val onError = argumentCaptor<((String, Throwable) -> Unit)>()
        verify(accountManager, times(1)).register(any())
        verify(sessionManager.engine, times(1)).installWebExtension(
                eq(SecureProxyFeature.SECURE_PROXY_EXTENSION_ID),
                eq(SecureProxyFeature.SECURE_PROXY_EXTENSION_URL),
                eq(true),
                onSuccess.capture(),
                onError.capture()
        )

        onSuccess.value.invoke(mock())

        // Already installed, should not try to install again.
        secureProxyFeature.onResume()
        verify(accountManager, times(1)).register(any())
        verify(sessionManager.engine, times(1)).installWebExtension(
                eq(SecureProxyFeature.SECURE_PROXY_EXTENSION_ID),
                eq(SecureProxyFeature.SECURE_PROXY_EXTENSION_URL),
                eq(true),
                any(),
                any()
        )
    }

    @Test
    fun `onResume will attempt to install the extension only if no port is connected`() {
        val locationService = mock<MozillaLocationService>()
        val engine = mock<Engine>()
        `when`(sessionManager.engine).thenReturn(engine)
        val ext = mock<WebExtension>()
        val controller = mock<WebExtensionController>()

        WebExtensionController.installedExtensions[SecureProxyFeature.SECURE_PROXY_EXTENSION_ID] = ext
        val secureProxyFeature = spy(SecureProxyFeature(context, client, accountManager, sessionManager, locationService))
        secureProxyFeature.extensionController = controller

        secureProxyFeature.onResume()

        verify(controller, times(1)).registerBackgroundMessageHandler(any(), any())
        verify(controller, times(1)).install(engine)

        `when`(controller.portConnected(null)).thenReturn(true)
        secureProxyFeature.onResume()

        verify(controller, times(1)).registerBackgroundMessageHandler(any(), any())
        verify(controller, times(1)).install(engine)
    }

    @Test
    fun `shouldShowUI allows US country code, disallows IR`() = runBlockingTest {
        val locationService = mock<MozillaLocationService>()
        val secureProxyFeature = spy(SecureProxyFeature(context, client, accountManager, sessionManager, locationService))

        `when`(locationService.fetchRegion()).thenReturn(MozillaLocationService.Region("US","United States"))
        assertTrue(secureProxyFeature.shouldShowUI())

        `when`(locationService.fetchRegion()).thenReturn(MozillaLocationService.Region("IR","Iran"))
        assertFalse(secureProxyFeature.shouldShowUI())
    }

    @Test
    fun `shouldShowUI returns false when fetchRegion returns null`() = runBlockingTest {
        val locationService = mock<MozillaLocationService>()
        val secureProxyFeature = spy(SecureProxyFeature(context, client, accountManager, sessionManager, locationService))

        `when`(locationService.fetchRegion()).thenReturn(null)
        assertFalse(secureProxyFeature.shouldShowUI())
    }
}
