rootProject.name = "kidstune"

// Backend is a standalone Gradle project included here for monorepo builds.
// Android modules (kids-app, parent-app, shared) will be added in later phases.
includeBuild("backend")
