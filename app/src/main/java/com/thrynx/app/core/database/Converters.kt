package com.thrynx.app.core.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.thrynx.app.core.model.CanvasAspectRatio
import com.thrynx.app.core.model.DownloadState
import com.thrynx.app.core.model.TimelineTrackModel

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTracksList(value: List<TimelineTrackModel>?): String {
        return gson.toJson(value ?: emptyList<TimelineTrackModel>())
    }

    @TypeConverter
    fun toTracksList(value: String?): List<TimelineTrackModel> {
        if (value.isNullOrBlank()) return emptyList()
        val listType = object : TypeToken<List<TimelineTrackModel>>() {}.type
        return gson.fromJson(value, listType)
    }

    @TypeConverter
    fun fromCanvasAspectRatio(value: CanvasAspectRatio?): String {
        return value?.name ?: CanvasAspectRatio.RATIO_16_9.name
    }

    @TypeConverter
    fun toCanvasAspectRatio(value: String?): CanvasAspectRatio {
        return try {
            CanvasAspectRatio.valueOf(value ?: CanvasAspectRatio.RATIO_16_9.name)
        } catch (e: Exception) {
            CanvasAspectRatio.RATIO_16_9
        }
    }

    @TypeConverter
    fun fromDownloadState(value: DownloadState?): String {
        return value?.name ?: DownloadState.PENDING.name
    }

    @TypeConverter
    fun toDownloadState(value: String?): DownloadState {
        return try {
            DownloadState.valueOf(value ?: DownloadState.PENDING.name)
        } catch (e: Exception) {
            DownloadState.PENDING
        }
    }
}
