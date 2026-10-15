package com.kaspercito.domain.data.model.searchResult

import com.kaspercito.domain.data.type.SearchResultType

data class SearchSuggestions(
    val queries: List<String>,
    val recommendedItems: List<SearchResultType>,
)