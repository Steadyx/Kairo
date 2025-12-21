package com.example.kairo.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromAuthors(authors: List<String>): String = authors.joinToString("|||")

    @TypeConverter
    fun toAuthors(raw: String): List<String> = if (raw.isEmpty()) emptyList() else raw.split("|||")
}
