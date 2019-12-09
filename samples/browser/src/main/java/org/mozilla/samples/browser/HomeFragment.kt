package org.mozilla.samples.browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_home.view.*

class HomeFragment(val sessionId: String?) : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        view.new_tab_button.setOnClickListener {
            fragmentManager!!.beginTransaction().apply {
                replace(R.id.container, BrowserActivity.createBrowserFragment(sessionId))
                commit()
            }
        }
        return view
    }
}