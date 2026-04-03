package at.kidstune.kids.domain.model

data class MockProfile(
    val id: String,
    val name: String,
    val emoji: String
)

val mockProfiles: List<MockProfile> = listOf(
    MockProfile(id = "profile-luna", name = "Luna", emoji = "🐻"),
    MockProfile(id = "profile-max",  name = "Max",  emoji = "🦊")
)
