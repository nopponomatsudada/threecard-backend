package com.appmaster.domain.error

/**
 * Base class for all domain errors.
 *
 * Error code conventions (threecard spec):
 * - 1xxx: Infrastructure Errors (network, auth)
 * - 2xxx: Content Validation Errors (theme, best, tag)
 * - 3xxx: Collection Errors
 * - 5xxx: Server Errors
 */
sealed class DomainError(
    val code: String,
    val message: String
) {
    // Infrastructure Errors (1xxx)
    data object NetworkError : DomainError("NETWORK_ERROR", "接続できませんでした")
    data object Unauthorized : DomainError("UNAUTHORIZED", "認証が必要です")
    data object InvalidCredentials : DomainError("INVALID_CREDENTIALS", "メールアドレスまたはパスワードが正しくありません")
    data object InvalidDeviceCredentials : DomainError("INVALID_DEVICE_CREDENTIALS", "デバイス認証に失敗しました")
    data object InvalidRefreshToken : DomainError("INVALID_REFRESH_TOKEN", "セッションの有効期限が切れました。もう一度サインインしてください")
    data object EmailAlreadyExists : DomainError("EMAIL_ALREADY_EXISTS", "このメールアドレスは既に使用されています")

    // Content Validation Errors (2xxx)
    data object ThemeTitleTooLong : DomainError("THEME_TITLE_TOO_LONG", "100文字以内で入力してください")
    data object ThemeDescriptionTooLong : DomainError("THEME_DESCRIPTION_TOO_LONG", "140文字以内で入力してください")
    data object BestItemNameRequired : DomainError("BEST_ITEM_NAME_REQUIRED", "アイテム名を入力してください")
    data object BestItemDescriptionTooLong : DomainError("BEST_ITEM_DESCRIPTION_TOO_LONG", "140文字以内で入力してください")
    data object AlreadyPosted : DomainError("ALREADY_POSTED", "このテーマには既に投稿しています")
    data object TagNotSelected : DomainError("TAG_NOT_SELECTED", "カテゴリタグを選択してください")
    data object BestItemNameTooLong : DomainError("BEST_ITEM_NAME_TOO_LONG", "50文字以内で入力してください")

    // Collection Errors (3xxx)
    data object CollectionLimitReached : DomainError("COLLECTION_LIMIT_REACHED", "無料プランは3個まで。Plusにアップグレードして無制限に。")
    data object CollectionTitleRequired : DomainError("COLLECTION_TITLE_REQUIRED", "コレクション名を入力してください")
    data object DuplicateBookmark : DomainError("DUPLICATE_BOOKMARK", "既に保存済みです")
    data object CollectionTitleTooLong : DomainError("COLLECTION_TITLE_TOO_LONG", "50文字以内で入力してください")

    // Server Errors (5xxx)
    data object ServerError : DomainError("SERVER_ERROR", "エラーが発生しました。時間をおいて再度お試しください")

    // Generic
    data class NotFound(val resource: String) : DomainError("NOT_FOUND", "${resource}が見つかりません")
    data class ValidationError(val detail: String) : DomainError("VALIDATION_ERROR", detail)
}

/**
 * Exception wrapper for DomainError.
 * Use this to throw domain errors that will be caught by StatusPages.
 */
class DomainException(val error: DomainError) : Exception(error.message)
