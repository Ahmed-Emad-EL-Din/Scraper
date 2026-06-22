package com.example.data

import kotlinx.coroutines.flow.Flow

class TrackingRuleRepository(
    private val trackingRuleDao: TrackingRuleDao,
    private val trackingHistoryDao: TrackingHistoryDao
) {
    val allRules: Flow<List<TrackingRule>> = trackingRuleDao.getAllRules()

    suspend fun getRuleById(id: Int): TrackingRule? {
        return trackingRuleDao.getRuleById(id)
    }

    suspend fun insertRule(rule: TrackingRule): Long {
        return trackingRuleDao.insertRule(rule)
    }

    suspend fun updateRule(rule: TrackingRule) {
        trackingRuleDao.updateRule(rule)
    }

    suspend fun deleteRule(rule: TrackingRule) {
        trackingRuleDao.deleteRule(rule)
        trackingHistoryDao.deleteHistoryByRule(rule.id)
    }

    suspend fun deleteRuleById(id: Int) {
        trackingRuleDao.deleteRuleById(id)
        trackingHistoryDao.deleteHistoryByRule(id)
    }

    fun getHistoryByRule(ruleId: Int): Flow<List<TrackingHistory>> {
        return trackingHistoryDao.getHistoryByRule(ruleId)
    }

    suspend fun insertHistory(history: TrackingHistory): Long {
        return trackingHistoryDao.insertHistory(history)
    }
}
