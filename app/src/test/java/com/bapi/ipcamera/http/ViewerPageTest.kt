package com.bapi.ipcamera.http

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerPageTest {
    private val html = ViewerPage.html()

    @Test
    fun referencesStreamEndpoint() {
        assertTrue(html.contains("src=\"/stream\""))
    }

    @Test
    fun fitsImageWithoutDistortion() {
        assertTrue(html.contains("object-fit: contain"))
    }

    @Test
    fun pollsStatusForAspectRatio() {
        assertTrue(html.contains("/status"))
        assertTrue(html.contains("displayWidth"))
        assertTrue(html.contains("displayHeight"))
        assertTrue(html.contains("aspectRatio"))
    }

    @Test
    fun detectsBrowserHtmlRequest() {
        assertTrue(ViewerPage.isHtmlRequest("text/html,application/xhtml+xml"))
        assertTrue(ViewerPage.isHtmlRequest("TEXT/HTML"))
        assertFalse(ViewerPage.isHtmlRequest("image/avif,image/webp,image/*,*/*;q=0.8"))
        assertFalse(ViewerPage.isHtmlRequest(null))
    }
}
