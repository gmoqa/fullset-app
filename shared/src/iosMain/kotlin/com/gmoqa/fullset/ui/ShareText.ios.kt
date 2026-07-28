package com.gmoqa.fullset.ui

import androidx.compose.runtime.Composable
import platform.Foundation.NSString
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberTextSharer(): (String) -> Unit = { text ->
    val vc = UIActivityViewController(activityItems = listOf(text as NSString), applicationActivities = null)
    UIApplication.sharedApplication.keyWindow?.rootViewController
        ?.presentViewController(vc, animated = true, completion = null)
}
