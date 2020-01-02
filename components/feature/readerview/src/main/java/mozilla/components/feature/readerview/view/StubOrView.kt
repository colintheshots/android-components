/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.readerview.view

import android.view.ViewStub

fun <T> lazyInflated(stubOrView: StubOrView<T>, shouldBeInflated: () -> Boolean = {false}): Lazy<T?> = SynchronizedLazyInflatedViewImpl(stubOrView, shouldBeInflated)

private class SynchronizedLazyInflatedViewImpl<out T>(stubOrView: StubOrView<T>, val shouldBeInflated: () -> Boolean = {false}, lock: Any? = null) : Lazy<T?> {
    @Volatile private var _value: T? = if (stubOrView is StubOrView.RealView) stubOrView.view else null
    @Volatile private var _stub: ViewStub? = if (stubOrView is StubOrView.Stub) stubOrView.stub else null

    // final field is required to enable safe publication of constructed instance
    private val lock = lock ?: this

    override val value: T?
        get() {
            val v1 = _value
            if (v1 !== null) {
                return v1
            }

            return synchronized(lock) {
                val v2 = _value
                if (v2 !== null) v2 else {
                    @Suppress("UNCHECKED_CAST") val typedValue = if (shouldBeInflated()) {
                        _stub?.inflate()!! as T
                    } else null
                    _value = typedValue
                    if (_value != null) {
                        _stub = null
                    }
                    typedValue
                }
            }
        }

    override fun isInitialized(): Boolean = _value !== null

    override fun toString(): String = if (isInitialized()) value.toString() else "Lazy-inflated value not initialized yet."
}

sealed class StubOrView<V> {
    data class Stub<V>(val stub: ViewStub) : StubOrView<V>()
    data class RealView<V>(val view: V) : StubOrView<V>()
}
