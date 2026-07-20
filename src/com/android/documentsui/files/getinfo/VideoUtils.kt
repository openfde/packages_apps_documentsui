/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui.files.getinfo

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.media.MediaMetadata
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.android.documentsui.R
import com.android.documentsui.base.Shared
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Helper functions to parse video metadata. */
object VideoUtils {
    private const val TAG = "GetInfoVideoUtils"

    // Constant to avoid using magic numbers throughout.
    private const val ONE_MEGAPIXEL = 1_000_000f

    /** A data class to hold video location coordinates. */
    data class Coordinates(val latitude: Float, val longitude: Float)

    suspend fun parseVideoData(context: Context, tags: Bundle): List<ListItem> = buildList {
        addAll(parseDimensions(context, tags))

        getVideoCoords(tags)?.let { coords ->
            add(createCoordinateItem(context, coords))

            getAddress(context, coords)?.let { address ->
                add(SharedUtils.createInfo(context, R.string.metadata_address, address))
            }
        }

        // Duration is usually supplied as a Long, however, occasionally it can be an Integer. Fall
        // back to Integer in the case Long returns 0.
        var millis = tags.getLong(MediaMetadata.METADATA_KEY_DURATION)
        if (millis == 0L) {
            millis = tags.getInt(MediaMetadata.METADATA_KEY_DURATION).toLong()
        }

        if (millis > 0) {
            add(
                SharedUtils.createInfo(
                    context,
                    R.string.metadata_duration,
                    DateUtils.formatElapsedTime(millis / 1000L),
                )
            )
        }
    }

    private fun getVideoCoords(data: Bundle): Coordinates? {
        if (
            !data.containsKey(Shared.METADATA_VIDEO_LATITUDE) ||
                !data.containsKey(Shared.METADATA_VIDEO_LONGITUDE)
        ) {
            return null
        }

        // Latitude and longitude is normally in the bundle as a double, however, it can
        // occasionally be a float. Use Double.NaN as the value if the key existed but had the wrong
        // type.
        val lat =
            data
                .getDouble(Shared.METADATA_VIDEO_LATITUDE, Double.NaN)
                .takeIf { !it.isNaN() }
                ?.toFloat() ?: data.getFloat(Shared.METADATA_VIDEO_LATITUDE)
        val lon =
            data
                .getDouble(Shared.METADATA_VIDEO_LONGITUDE, Double.NaN)
                .takeIf { !it.isNaN() }
                ?.toFloat() ?: data.getFloat(Shared.METADATA_VIDEO_LONGITUDE)

        if (lat == 0.0f && lon == 0.0f) {
            return null
        }

        return Coordinates(lat, lon)
    }

    /**
     * Extract the width and height if they exist and identify the megapixels of the image (required
     * as the final value in the dimensions format, after width and height)
     */
    fun parseDimensions(context: Context, tags: Bundle): List<ListItem> = buildList {
        val width = getIntTag(tags, ExifInterface.TAG_IMAGE_WIDTH)
        val height = getIntTag(tags, ExifInterface.TAG_IMAGE_LENGTH)

        if (width <= 0 || height <= 0) {
            return@buildList
        }

        val megaPixels = (width * height) / ONE_MEGAPIXEL
        add(
            ListItem.Info(
                context.getString(R.string.metadata_dimensions),
                context.getString(R.string.metadata_dimensions_format, width, height, megaPixels),
            )
        )
    }

    /** Returns an Info item with the coordinates. */
    fun createCoordinateItem(context: Context, coords: Coordinates): ListItem.Info {
        return ListItem.Info(
            context.getString(R.string.metadata_coordinates),
            context.getString(
                R.string.metadata_coordinates_format,
                coords.latitude,
                coords.longitude,
            ),
        )
    }

    /** Uses the Geocoder to convert coordinates into a street address. */
    suspend fun getAddress(context: Context, coords: Coordinates): String? {
        if (!Geocoder.isPresent()) {
            return null
        }

        return try {
            val geocoder = Geocoder(context)

            // In Android T a new async version of the Geocoder was introduced, so prefer that
            // version when it's available.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        coords.latitude.toDouble(),
                        coords.longitude.toDouble(),
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                val result = addresses.firstOrNull()?.let { formatAddress(it) }
                                continuation.resume(result)
                            }

                            override fun onError(errorMessage: String?) {
                                // Log the error and resume with null so the coroutine finishes!
                                Log.w(TAG, "Failed getFromLocation: $errorMessage")
                                continuation.resume(null)
                            }
                        },
                    )
                }
            } else {
                // In versions prior to Android T, the Geocoder only has synchronous API calls,
                // fallback to those as we still support Android S, S_V2 and T.
                @Suppress("DEPRECATION")
                val addresses =
                    geocoder.getFromLocation(
                        coords.latitude.toDouble(),
                        coords.longitude.toDouble(),
                        1,
                    )
                addresses?.firstOrNull()?.let { formatAddress(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve address", e)
            null
        }
    }

    /**
     * Some tags are Integers but the standard is a bit ambiguous whether to store it as a String.
     * So to avoid getting no value, try an Int first then try a String after that and parse it into
     * an Int.
     */
    private fun getIntTag(tags: Bundle, key: String): Int {
        return tags.getInt(key).takeIf { it > 0 } ?: tags.getString(key)?.toIntOrNull() ?: 0
    }

    /** Take an `Address` and return a formatted string. */
    private fun formatAddress(address: Address): String {
        return when {
            address.maxAddressLineIndex >= 0 -> {
                (0..address.maxAddressLineIndex).joinToString(separator = "\n") {
                    address.getAddressLine(it) ?: ""
                }
            }
            address.locality != null -> address.locality
            address.subAdminArea != null -> address.subAdminArea
            address.adminArea != null -> address.adminArea
            else -> address.countryName ?: ""
        }
    }
}
