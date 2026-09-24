package com.blvietsub

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class BLVietsubPlugin: Plugin() {
    override fun load(context: Context) {
        // Đăng ký duy nhất nguồn BLVietsub trên Cloudstream Android TV Box
        registerMainAPI(BLVietsubProvider())
        registerExtractorAPI(SSPlayExtractor())
    }
}
