package mozilla.components.feature.secureproxy.view

internal class SecureProxyPresenter(
    private val view: SecureProxyView
) {

    fun show() {
        view.setAvailable(true)
    }

    fun hide() {
        view.setAvailable(false)
    }

    fun error(error: String) {
        view.setProxyState(error) // TODO Use a dedicated error state
    }

    fun connected() {
        view.setProxyState("connected") // TODO Use a sealed class
    }

    fun disconnected() {
        view.setProxyState("not_connected") // TODO Use a sealed class
    }

    fun enable() {
        view.setEnabled(true)
    }

    fun disable() {
        view.setEnabled(false)
    }

}