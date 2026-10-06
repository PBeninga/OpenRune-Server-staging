plugins {
    id("base-conventions")
    id("integration-test-suite")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.guice)
    implementation(projects.api.npc)
    implementation(projects.api.player)
    implementation(projects.api.random)
    implementation(projects.api.script)
    implementation(projects.engine.events)
    implementation(projects.engine.game)
    implementation(projects.engine.map)
    implementation(projects.engine.plugin)
    testImplementation(projects.api.core)
    testImplementation(projects.engine.annotations)
    testImplementation(projects.engine.module)
    integrationImplementation(projects.api.combat.combatCommons)
    integrationImplementation(projects.api.combat.combatFormulas)
    integrationImplementation(projects.api.combat.combatManager)
    integrationImplementation(projects.api.combat.combatWeapon)
    integrationImplementation(projects.api.config)
    integrationImplementation(projects.api.hitPlugin)
    integrationImplementation(projects.api.objCharges)
    integrationImplementation(projects.api.random)
    integrationImplementation(projects.api.specials)
    integrationImplementation(projects.api.spells)
    integrationImplementation(projects.api.spellsRunes)
    integrationImplementation(projects.api.statsPlugin)
    // The combat scripts are `internal`, so they are only needed on the runtime classpath.
    integrationRuntimeOnly(projects.api.combat.combatScripts)
}
