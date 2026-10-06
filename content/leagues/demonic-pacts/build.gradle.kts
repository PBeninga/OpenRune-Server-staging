plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.combat.combatModifiers)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.serverConfig)
    implementation(projects.api.specials)
    implementation(projects.content.leagues.demonicPacts.demonicPactsPack)
    implementation(libs.jackson.dataformat.toml)
    implementation(libs.jackson.module.kotlin)
    integrationImplementation(projects.api.combat.combatModifiers)
    integrationImplementation(projects.api.hitPlugin)
    integrationImplementation(projects.api.pluginCommons)
    integrationImplementation(projects.api.serverConfig)
    integrationImplementation(projects.api.specials)
    integrationImplementation(projects.api.spells)
    integrationImplementation(projects.api.spellsRunes)
    integrationImplementation(projects.api.statsPlugin)
    integrationImplementation(projects.content.leagues.demonicPacts.demonicPactsPack)
    integrationImplementation(libs.jackson.module.kotlin)
    integrationImplementation(libs.rsprot.api)
}
