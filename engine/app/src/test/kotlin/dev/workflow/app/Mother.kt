package dev.workflow.app

/**
 * The Object Mother (t.data.a): one place for the sample data every backend test starts from.
 * Each function returns the `vars` of a run of one sample workflow, with sensible defaults and
 * named overrides. `base` is the running app's URL when a sample calls its own demo providers.
 * The studio's mocks have their own seeded mother (apps/web/src/mocks/mother.ts), by language.
 */
object Mother {
    /** confirmation-approval: a trade confirmation document. */
    fun doc(
        notional: Long = 75_000_000,
        counterparty: String = "ACME",
        requester: String = "a.ops",
        id: String = "D-1",
    ): Map<String, Any?> = mapOf(
        "doc" to mapOf("id" to id, "requester" to requester, "counterparty" to counterparty, "notional" to notional),
    )

    /** trade-booking: a trade to book and match. */
    fun trade(
        ref: String = "T-1",
        quantity: Long = 5_000_000,
        counterparty: String = "ACME",
    ): Map<String, Any?> = mapOf("trade" to mapOf("ref" to ref, "quantity" to quantity, "counterparty" to counterparty))

    /** counterparty-onboarding: a counterparty and the screening provider to call. */
    fun counterparty(
        name: String = "Globex Ltd",
        region: String = "EU",
        base: String,
    ): Map<String, Any?> = mapOf(
        "counterparty" to mapOf("name" to name, "region" to region),
        "services" to mapOf("screening" to "$base/demo/screening"),
    )

    /** clause-change-approval: a clause change, with its size and whether it is regulatory. */
    fun change(
        templates: Int = 14,
        regulatory: Boolean = false,
        base: String,
    ): Map<String, Any?> = mapOf(
        "change" to mapOf(
            "clauseId" to "governing-law", "author" to "a.ops", "productOwner" to "p.owner",
            "affectedTemplates" to templates, "regulatory" to regulatory,
        ),
        "services" to mapOf("publish" to "$base/demo/clause-store/publish"),
    )
}
