package app.olauncher.helper

object PromptRepository {

    // 60% Mirror prompts (60)
    private val mirrorPrompts = listOf(
        "Why this app?",
        "Is this intentional?",
        "What are you looking for?",
        "What brought you here?",
        "Is this a choice?",
        "What do you want right now?",
        "Are you here on purpose?",
        "What are you expecting?",
        "Is this what you planned?",
        "What just happened?",
        "Why now?",
        "What do you need?",
        "Is this familiar?",
        "What are you doing?",
        "Notice this moment.",
        "What's pulling you here?",
        "Is this a habit?",
        "What are you seeking?",
        "Where are you going?",
        "Is this a reflex?",
        "What do you actually want?",
        "Are you choosing this?",
        "What matters right now?",
        "Is this the right time?",
        "What are you really after?",
        "Is this a want or a need?",
        "Why this, why now?",
        "What do you expect to find?",
        "Is this intentional use?",
        "What triggered this?",
        "Are you present right now?",
        "What's happening inside?",
        "Is this the plan?",
        "What do you hope to feel?",
        "Are you scrolling or searching?",
        "What does this give you?",
        "Is this how you want to spend this?",
        "What are you here for?",
        "Is this a conscious choice?",
        "What do you actually need?",
        "Are you sure?",
        "What's the reason?",
        "Is this a pattern?",
        "What are you chasing?",
        "Are you filling time?",
        "What's underneath this?",
        "Is this serving you?",
        "What are you feeding?",
        "Are you here or somewhere else?",
        "What brought you to this moment?",
        "Is this automatic?",
        "What do you want to feel?",
        "Are you leading or following?",
        "What's the intention?",
        "Is this deliberate?",
        "What are you trading this for?",
        "Are you in control here?",
        "What's the real pull?",
        "Is this your choice?",
        "What are you moving toward?"
    )

    // 20% Future self prompts (20)
    private val futurePrompts = listOf(
        "Will this matter in five minutes?",
        "Will you feel good after this?",
        "How will you feel in an hour?",
        "Is this the best use of now?",
        "What will you wish you did?",
        "Will this help you later?",
        "Is this building anything?",
        "What does future you think?",
        "Will this add or subtract?",
        "How does this serve tomorrow?",
        "Is this an investment?",
        "What are you creating right now?",
        "Will this still matter tonight?",
        "What will you remember from this?",
        "Is this moving you forward?",
        "Will you be glad you did this?",
        "What are you becoming right now?",
        "Is this the direction you want?",
        "How does this fit your day?",
        "Will this feel worth it?"
    )

    // 10% Avoidance prompts (10)
    private val avoidancePrompts = listOf(
        "What are you avoiding?",
        "What are you not doing right now?",
        "What's waiting for you?",
        "What are you stepping away from?",
        "What feels uncomfortable right now?",
        "What are you postponing?",
        "Is there something harder to face?",
        "What did you leave unfinished?",
        "What's the thing you're not doing?",
        "What are you running from?"
    )

    // 5% Identity nudge prompts (5)
    private val identityPrompts = listOf(
        "Builder?",
        "Warrior?",
        "Monk?",
        "Scholar?",
        "Is this you?"
    )

    // 5% Existential micro-prompts (5)
    private val deepPrompts = listOf(
        "Are you living or passing time?",
        "Is this how you want this moment?",
        "What is this moment worth?",
        "Are you here or just present?",
        "What are you made of right now?"
    )

    private val allPrompts: List<Pair<String, Float>> =
        mirrorPrompts.map { it to 0.60f } +
        futurePrompts.map { it to 0.20f } +
        avoidancePrompts.map { it to 0.10f } +
        identityPrompts.map { it to 0.05f } +
        deepPrompts.map { it to 0.05f }

    private var lastPromptIndex: Int = -1

    fun getRandomPrompt(identityMode: String = ""): String {
        // Weighted random selection
        val rand = Math.random().toFloat()
        val pool = when {
            rand < 0.60f -> mirrorPrompts
            rand < 0.80f -> futurePrompts
            rand < 0.90f -> avoidancePrompts
            rand < 0.95f -> identityPrompts
            else -> deepPrompts
        }

        // Avoid immediate repeat within same pool
        var index = (pool.indices).random()
        if (pool === mirrorPrompts && index == lastPromptIndex) {
            index = (index + 1) % pool.size
        }
        lastPromptIndex = index

        val prompt = pool[index]

        return if (identityMode.isNotEmpty()) {
            "$identityMode: $prompt"
        } else {
            prompt
        }
    }

    fun getAll(): List<String> = allPrompts.map { it.first }
}