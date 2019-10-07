package mozilla.components.feature.secureproxy.view

interface SecureProxyView {

    /**
     * Sets the enabled state of the Secure Proxy feature
     *
     * @param enabled whether to show the feature as enabled or disabled
     */
    fun setEnabled(enabled: Boolean)

    /**
     * Sets the visibility of UI related to the Secure Proxy feature
     *
     * @param available whether to show the feature UI or not
     */
    fun setAvailable(available: Boolean)

    /**
     * Sets the current connected or error state of the proxy
     *
     * @param state an identifier for the current proxy state
     */
    fun setProxyState(state: String)
}