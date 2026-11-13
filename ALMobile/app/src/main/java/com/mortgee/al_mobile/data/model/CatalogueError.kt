package com.mortgee.al_mobile.data.model

sealed interface CatalogueError {
    data object NetworkError : CatalogueError
    data object InvalidQuery : CatalogueError
    data object NotFound : CatalogueError
    data object ParseError : CatalogueError
    data class Unknown(val cause: Throwable) : CatalogueError
}

class CatalogueException(
    val error: CatalogueError,
    cause: Throwable? = null
) : Exception("Catalogue operation failed with error: $error", cause)
