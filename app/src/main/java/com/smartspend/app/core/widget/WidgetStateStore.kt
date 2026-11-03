package com.smartspend.app.core.widget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.widgetDataStore by preferencesDataStore(name = "smartspend_widget_prefs")

data class WidgetSnapshot(
    val todayExpense: Long = 0L,
    val remainingDailyBudget: Long = 0L,
    val monthExpense: Long = 0L,
    val monthBudgetLimit: Long = 0L,
    val isHideBalance: Boolean = false,
    val topCategoriesJson: String = "[]",
    val recentTransactionsJson: String = "[]",
    val accountBalancesJson: String = "[]",
    val debtsDueCount: Int = 0
)

object WidgetStateStore {
    val KEY_TODAY_EXPENSE = longPreferencesKey("today_expense")
    val KEY_REMAINING_DAILY_BUDGET = longPreferencesKey("remaining_daily_budget")
    val KEY_MONTH_EXPENSE = longPreferencesKey("month_expense")
    val KEY_MONTH_BUDGET_LIMIT = longPreferencesKey("month_budget_limit")
    val KEY_IS_HIDE_BALANCE = booleanPreferencesKey("is_hide_balance")
    val KEY_TOP_CATEGORIES = stringPreferencesKey("top_categories")
    val KEY_RECENT_TRANSACTIONS = stringPreferencesKey("recent_transactions")
    val KEY_ACCOUNT_BALANCES = stringPreferencesKey("account_balances")
    val KEY_DEBTS_DUE_COUNT = stringPreferencesKey("debts_due_count")

    fun getSnapshotFlow(context: Context): Flow<WidgetSnapshot> {
        return context.widgetDataStore.data.map { prefs ->
            WidgetSnapshot(
                todayExpense = prefs[KEY_TODAY_EXPENSE] ?: 0L,
                remainingDailyBudget = prefs[KEY_REMAINING_DAILY_BUDGET] ?: 0L,
                monthExpense = prefs[KEY_MONTH_EXPENSE] ?: 0L,
                monthBudgetLimit = prefs[KEY_MONTH_BUDGET_LIMIT] ?: 0L,
                isHideBalance = prefs[KEY_IS_HIDE_BALANCE] ?: false,
                topCategoriesJson = prefs[KEY_TOP_CATEGORIES] ?: "[]",
                recentTransactionsJson = prefs[KEY_RECENT_TRANSACTIONS] ?: "[]",
                accountBalancesJson = prefs[KEY_ACCOUNT_BALANCES] ?: "[]",
                debtsDueCount = (prefs[KEY_DEBTS_DUE_COUNT] ?: "0").toIntOrNull() ?: 0
            )
        }
    }

    suspend fun getSnapshot(context: Context): WidgetSnapshot {
        return getSnapshotFlow(context).first()
    }

    suspend fun updateSnapshot(context: Context, snapshot: WidgetSnapshot) {
        context.widgetDataStore.edit { prefs ->
            prefs[KEY_TODAY_EXPENSE] = snapshot.todayExpense
            prefs[KEY_REMAINING_DAILY_BUDGET] = snapshot.remainingDailyBudget
            prefs[KEY_MONTH_EXPENSE] = snapshot.monthExpense
            prefs[KEY_MONTH_BUDGET_LIMIT] = snapshot.monthBudgetLimit
            prefs[KEY_IS_HIDE_BALANCE] = snapshot.isHideBalance
            prefs[KEY_TOP_CATEGORIES] = snapshot.topCategoriesJson
            prefs[KEY_RECENT_TRANSACTIONS] = snapshot.recentTransactionsJson
            prefs[KEY_ACCOUNT_BALANCES] = snapshot.accountBalancesJson
            prefs[KEY_DEBTS_DUE_COUNT] = snapshot.debtsDueCount.toString()
        }
    }

    suspend fun toggleHideBalance(context: Context) {
        context.widgetDataStore.edit { prefs ->
            val current = prefs[KEY_IS_HIDE_BALANCE] ?: false
            prefs[KEY_IS_HIDE_BALANCE] = !current
        }
    }
}
