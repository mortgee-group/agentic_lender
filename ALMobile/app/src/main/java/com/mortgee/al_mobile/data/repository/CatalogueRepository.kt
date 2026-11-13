package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.model.Page
import com.mortgee.al_mobile.data.model.PropertyListing

interface CatalogueRepository {
    suspend fun getProperties(
        page: Int = 1,
        pageSize: Int = 20,
        sort: String? = "createdAt,desc",
        query: String? = null
    ): Result<Page<PropertyListing>>

    suspend fun getPropertyDetail(
        id: String
    ): Result<PropertyListing>
}
