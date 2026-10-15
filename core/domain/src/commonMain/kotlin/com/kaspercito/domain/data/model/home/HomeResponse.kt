package com.kaspercito.domain.data.model.home

import com.kaspercito.domain.data.model.home.chart.Chart
import com.kaspercito.domain.data.model.mood.Mood
import com.kaspercito.domain.utils.Resource

data class HomeResponse(
    val homeItem: Resource<ArrayList<HomeItem>>,
    val exploreMood: Resource<Mood>,
    val exploreChart: Resource<Chart>,
)