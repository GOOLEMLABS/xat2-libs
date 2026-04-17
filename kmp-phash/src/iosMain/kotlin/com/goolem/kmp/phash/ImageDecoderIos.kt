@file:OptIn(ExperimentalForeignApi::class)

package com.goolem.kmp.phash

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceGray
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage

private const val MAX_DIMENSION = 4096u

public actual fun decodeToGrayscale32x32(imageBytes: ByteArray): ByteArray? {
    val nsData = imageBytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.toULong())
    }
    val image   = UIImage(data = nsData) ?: return null
    val cgImage = image.CGImage          ?: return null

    val srcW = CGImageGetWidth(cgImage)
    val srcH = CGImageGetHeight(cgImage)
    if (srcW == 0uL || srcH == 0uL) return null
    if (srcW > MAX_DIMENSION || srcH > MAX_DIMENSION) return null

    val size = 32
    val out  = ByteArray(size * size)

    out.usePinned { pinned ->
        val colorSpace = CGColorSpaceCreateDeviceGray()
        val context = CGBitmapContextCreate(
            data             = pinned.addressOf(0),
            width            = size.toULong(),
            height           = size.toULong(),
            bitsPerComponent = 8u,
            bytesPerRow      = size.toULong(),
            space            = colorSpace,
            bitmapInfo       = CGImageAlphaInfo.kCGImageAlphaNone.value,
        )
        CGColorSpaceRelease(colorSpace)
        if (context == null) return null
        CGContextDrawImage(context, CGRectMake(0.0, 0.0, size.toDouble(), size.toDouble()), cgImage)
        CFRelease(context)
    }
    return out
}
