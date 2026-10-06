plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.content.leagues.demonicPacts.demonicPactsPack)
    implementation(libs.jackson.dataformat.toml)
    implementation(libs.jackson.module.kotlin)
    integrationImplementation(projects.content.leagues.demonicPacts.demonicPactsPack)
    integrationImplementation(libs.jackson.module.kotlin)
}
