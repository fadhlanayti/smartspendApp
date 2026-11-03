package com.smartspend.app.core.model

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER
}

enum class AccountType {
    BANK,
    EWALLET,
    CASH,
    CARD,
    OTHER
}

enum class CaptureSource {
    AUTO,
    MANUAL
}

enum class ReviewStatus {
    CONFIRMED,
    NEEDS_REVIEW
}

enum class AmountFormat {
    ID_DOT_COMMA,
    US_COMMA_DOT,
    AUTO
}

enum class DebtDirection {
    OWE,   // Saya berutang (bayar)
    OWED   // Orang berutang ke saya (tagih)
}

enum class DebtStatus {
    ACTIVE,
    PAID,
    CANCELLED
}

enum class InstallmentPeriod {
    WEEKLY,
    MONTHLY
}

enum class NeedLevel {
    NEED,
    WANT
}

enum class PurchasePlanStatus {
    WAITING,
    READY,
    BOUGHT,
    SKIPPED,
    EXPIRED
}

enum class PeriodFilter {
    THIS_WEEK,
    THIS_MONTH,
    THREE_MONTHS,
    CUSTOM
}
