package com.kaspercito.domain.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kaspercito.domain.data.model.browse.album.Track

@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey(autoGenerate = false)
    val queueId: Long = 0,
    val listTrack: List<Track>,
)