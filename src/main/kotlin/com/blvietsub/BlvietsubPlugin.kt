package com.blvietsub

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class BlvietsubPlugin: Plugin() {
    override fun load(context: Context) {
        // Đăng ký Provider BLVietsub vào hệ thống Cloudstream
        registerMainAPI(BlvietsubProvider())
    }
}
