rootProject.name = "kidstune"

// Backend is a standalone Gradle project included here for monorepo builds.
includeBuild("backend")

// Kids App – standalone Android project with its own Gradle wrapper.
// Use `cd kids-app && ./gradlew assembleDebug` to build.
