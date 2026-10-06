plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.serverConfig)
    implementation(projects.content.leagues.demonicPacts.demonicPactsPack)
    implementation(libs.jackson.dataformat.toml)
    implementation(libs.jackson.module.kotlin)
    integrationImplementation(projects.api.pluginCommons)
    integrationImplementation(projects.api.serverConfig)
    integrationImplementation(projects.content.leagues.demonicPacts.demonicPactsPack)
    integrationImplementation(libs.jackson.module.kotlin)
}
