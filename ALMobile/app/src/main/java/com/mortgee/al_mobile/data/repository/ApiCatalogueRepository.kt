package com.mortgee.al_mobile.data.repository

import com.mortgee.al_mobile.data.model.CatalogueError
import com.mortgee.al_mobile.data.model.CatalogueException
import com.mortgee.al_mobile.data.model.Page
import com.mortgee.al_mobile.data.model.PropertyListing
import com.mortgee.al_mobile.data.network.CatalogueApiService
import com.mortgee.al_mobile.data.network.model.CatalogueItemDto
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class ApiCatalogueRepository @Inject constructor(
    private val apiService: CatalogueApiService
) : CatalogueRepository {

    override suspend fun getProperties(
        page: Int,
        pageSize: Int,
        sort: String?,
        query: String?
    ): Result<Page<PropertyListing>> {
        return try {
            val response = apiService.getItems(
                page = page,
                pageSize = pageSize,
                sort = sort,
                query = query
            )
            val domainList = response.items.map { it.toDomain() }
            Result.success(
                Page(
                    items = domainList,
                    page = response.page,
                    pageSize = response.pageSize,
                    totalCount = response.totalCount,
                    hasMore = response.hasMore
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            val error = when (e.code()) {
                400, 422 -> CatalogueError.InvalidQuery
                404 -> CatalogueError.NotFound
                else -> CatalogueError.NetworkError
            }
            Result.failure(CatalogueException(error, e))
        } catch (e: IOException) {
            Result.failure(CatalogueException(CatalogueError.NetworkError, e))
        } catch (e: Exception) {
            Result.failure(CatalogueException(CatalogueError.ParseError, e))
        }
    }

    override suspend fun getPropertyDetail(id: String): Result<PropertyListing> {
        return try {
            val response = apiService.getItemDetail(id)
            Result.success(response.toDomain())
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            val error = when (e.code()) {
                400, 422 -> CatalogueError.InvalidQuery
                404 -> CatalogueError.NotFound
                else -> CatalogueError.NetworkError
            }
            Result.failure(CatalogueException(error, e))
        } catch (e: IOException) {
            Result.failure(CatalogueException(CatalogueError.NetworkError, e))
        } catch (e: Exception) {
            Result.failure(CatalogueException(CatalogueError.ParseError, e))
        }
    }

    private fun CatalogueItemDto.toDomain(): PropertyListing {
        return PropertyListing(
            id = id,
            title = title,
            summary = summary,
            description = description,
            price = price,
            currency = currency,
            imageUrl = imageUrl,
            category = category,
            isAvailable = isAvailable,
            location = location,
            propertySizeSqm = propertySizeSqm,
            bedrooms = bedrooms,
            propertyType = propertyType,
            berRating = berRating,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
