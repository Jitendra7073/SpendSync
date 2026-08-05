package com.example.spendsync.ui.holds

import com.example.spendsync.data.remote.model.HoldDto

internal data class PersonHoldSummary(
    val personName: String,
    val netAmount: Double,
    val holdCount: Int,
)

/**
 * owed_to_me holds add to a person's net amount, owed_by_me holds subtract —
 * same signed-sum convention already used for Home's Hold Money aggregate.
 */
internal fun groupHoldsByPerson(holds: List<HoldDto>): List<PersonHoldSummary> {
    return holds
        .groupBy { it.personName }
        .map { (name, personHolds) ->
            val net = personHolds.sumOf { hold ->
                val amt = hold.amount.toDoubleOrNull() ?: 0.0
                if (hold.direction == "owed_to_me") amt else -amt
            }
            PersonHoldSummary(personName = name, netAmount = net, holdCount = personHolds.size)
        }
        .sortedBy { it.personName }
}
