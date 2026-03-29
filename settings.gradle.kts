rootProject.name = "kidstune"

// Backend is a standalone Gradle project included here for monorepo builds.
includeBuild("backend")

// Android apps – each is a standalone Android project with its own Gradle wrapper.
// Use `cd parent-app && ./gradlew assembleDebug` to build individual apps.
includeBuild("parent-app")
