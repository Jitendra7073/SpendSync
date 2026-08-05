package com.example.spendsync.ui.holds

import com.example.spendsync.data.remote.model.HoldDto
import org.junit.Assert.assertEquals
import org.junit.Test

private fun hold(
    id: String,
    personName: String,
    amount: String,
    direction: String,
    status: String = "pending",
) = HoldDto(
    id = id,
    userId = "u1",
    transactionId = "t-$id",
    direction = direction,
    personName = personName,
    amount = amount,
    expectedReturnDate = "2026-08-10T00:00:00.000Z",
    status = status,
    settledAt = null,
    createdAt = "2026-08-01T00:00:00.000Z",
    updatedAt = null,
)

class HoldGroupingTest {

    @Test
    fun `groups multiple holds for the same person and sums signed amounts`() {
        val holds = listOf(
            hold(id = "1", personName = "Manish", amount = "500", direction = "owed_to_me"),
            hold(id = "2", personName = "Manish", amount = "200", direction = "owed_to_me"),
        )

        val result = groupHoldsByPerson(holds)

        assertEquals(1, result.size)
        assertEquals("Manish", result[0].personName)
        assertEquals(700.0, result[0].netAmount, 0.001)
        assertEquals(2, result[0].holdCount)
    }

    @Test
    fun `nets owed_to_me and owed_by_me for the same person`() {
        val holds = listOf(
            hold(id = "1", personName = "Rahul", amount = "1000", direction = "owed_to_me"),
            hold(id = "2", personName = "Rahul", amount = "400", direction = "owed_by_me"),
        )

        val result = groupHoldsByPerson(holds)

        assertEquals(1, result.size)
        assertEquals(600.0, result[0].netAmount, 0.001)
        assertEquals(2, result[0].holdCount)
    }

    @Test
    fun `separates different people into separate groups`() {
        val holds = listOf(
            hold(id = "1", personName = "Manish", amount = "500", direction = "owed_to_me"),
            hold(id = "2", personName = "Rahul", amount = "300", direction = "owed_by_me"),
        )

        val result = groupHoldsByPerson(holds).associateBy { it.personName }

        assertEquals(2, result.size)
        assertEquals(500.0, result.getValue("Manish").netAmount, 0.001)
        assertEquals(-300.0, result.getValue("Rahul").netAmount, 0.001)
    }

    @Test
    fun `returns an empty list for no holds`() {
        assertEquals(emptyList<PersonHoldSummary>(), groupHoldsByPerson(emptyList()))
    }

    @Test
    fun `sorts groups alphabetically by person name`() {
        val holds = listOf(
            hold(id = "1", personName = "Zara", amount = "100", direction = "owed_to_me"),
            hold(id = "2", personName = "Amit", amount = "100", direction = "owed_to_me"),
        )

        val result = groupHoldsByPerson(holds)

        assertEquals(listOf("Amit", "Zara"), result.map { it.personName })
    }
}
