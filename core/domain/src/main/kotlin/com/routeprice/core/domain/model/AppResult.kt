package com.routeprice.core.domain.model

/** Erros de domínio, sem detalhes técnicos nem dados pessoais. */
sealed interface DomainError {
    data object Offline : DomainError
    data object MissingApiKey : DomainError
    data object RouteNotFound : DomainError
    data object AddressNotFound : DomainError
    data object LocationPermissionDenied : DomainError
    data object LocationUnavailable : DomainError
    data object QuotaOrKeyRejected : DomainError
    data class Http(val code: Int) : DomainError
    data object Timeout : DomainError
    data object Unknown : DomainError
}

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: DomainError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value
