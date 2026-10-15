package com.kaspercito.kotlinytmusicscraper.models.response

import com.kaspercito.kotlinytmusicscraper.models.Continuation
import com.kaspercito.kotlinytmusicscraper.models.MusicResponsiveListItemRenderer
import com.kaspercito.kotlinytmusicscraper.models.MusicShelfRenderer
import com.kaspercito.kotlinytmusicscraper.models.Tabs
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val contents: Contents?,
    val continuationContents: ContinuationContents?,
) {
    @Serializable
    data class Contents(
        val tabbedSearchResultsRenderer: Tabs?,
    )

    @Serializable
    data class ContinuationContents(
        val musicShelfContinuation: MusicShelfContinuation,
    ) {
        @Serializable
        data class MusicShelfContinuation(
            val contents: List<Content>,
            val continuations: List<Continuation>?,
        ) {
            @Serializable
            data class Content(
                val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?,
                val musicMultiRowListItemRenderer: MusicShelfRenderer.Content.MusicMultiRowListItemRenderer?,
            )
        }
    }
}