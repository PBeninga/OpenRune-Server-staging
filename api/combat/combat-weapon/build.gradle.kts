plugins {
    id("base-conventions")
    id("integration-test-suite")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.fastutil)
    implementation(libs.guice)
    implementation(projects.api.combat.combatCommons)
    implementation(projects.api.config)
    implementation(projects.api.generated)

    implementation(projects.api.utils.utilsVars)
    implementation(projects.engine.game)
    implementation(projects.engine.plugin)
}
