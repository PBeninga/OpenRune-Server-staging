plugins {
    id("base-conventions")
    id("integration-test-suite")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.guice)
    implementation(projects.api.config)
    implementation(projects.api.player)
    implementation(projects.api.script)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.plugin)
}
