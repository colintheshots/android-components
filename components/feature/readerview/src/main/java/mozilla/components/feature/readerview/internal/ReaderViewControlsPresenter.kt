/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.readerview.internal

import androidx.core.view.isVisible
import mozilla.components.feature.readerview.ReaderViewFeature
import mozilla.components.feature.readerview.view.ReaderViewControlsView
import mozilla.components.feature.readerview.view.StubOrView
import mozilla.components.feature.readerview.view.lazyInflated

/**
 * Presenter implementation that will update the view whenever the feature is started.
 */
internal class ReaderViewControlsPresenter(
    stubOrView: StubOrView<ReaderViewControlsView>,
    private val config: ReaderViewFeature.Config
) {

    private var controlsShouldBeInflated = false
    private val view: ReaderViewControlsView? by lazyInflated(stubOrView) {controlsShouldBeInflated}
    private var interactor: ReaderViewControlsInteractor? = null

    /**
     * Sets the initial state of the ReaderView controls and makes the controls visible.
     */
    fun show() {
        controlsShouldBeInflated = true
        interactor =  ReaderViewControlsInteractor(view!!, config)
        view!!.apply {
            interactor!!.start()
            setColorScheme(config.colorScheme)
            setFont(config.fontType)
            setFontSize(config.fontSize)
            showControls()
        }
    }

    /**
     * Checks whether or not the ReaderView controls are visible.
     */
    fun areControlsVisible(): Boolean {
        return view?.asView()?.isVisible ?: false
    }

    /**
     * Hides the controls.
     */
    fun hide() {
        interactor?.stop()
        view?.hideControls()
    }
}
