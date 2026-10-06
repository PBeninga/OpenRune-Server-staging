package org.rsmod.content.leagues.demonicpacts.state

import com.google.inject.ProvidedBy
import com.google.inject.Provider
import jakarta.inject.Inject
import org.rsmod.api.server.config.DemonicPactsYaml
import org.rsmod.api.server.config.ServerConfig

/**
 * The numbers behind the default pact rules, read from `game.yml`'s `gameplay.demonic-pacts`
 * section. The defaults are the Demonic Pacts League's: two points from the tutorial, two free
 * resets and at most 40 points spent. Pacts are off unless the section enables them.
 *
 * - [enabled] is [ConfigPactActivation]'s answer, the default [PactActivation].
 * - [startingPoints] feed [StartingPactPoints], a [PactPointSource].
 * - [freeResets] feed [FreePactResets], a [PactResetSource].
 * - [spendCap] is [DefaultPactSpendCap]'s answer.
 */
@ProvidedBy(PactSettings.ConfigProvider::class)
data class PactSettings(
    val enabled: Boolean = false,
    val startingPoints: Int = DEFAULT_STARTING_POINTS,
    val freeResets: Int = DEFAULT_FREE_RESETS,
    val spendCap: Int = DEFAULT_SPEND_CAP,
) {
    init {
        require(startingPoints >= 0) { "startingPoints must not be negative: $startingPoints" }
        require(freeResets >= 0) { "freeResets must not be negative: $freeResets" }
        require(spendCap >= 0) { "spendCap must not be negative: $spendCap" }
    }

    class ConfigProvider @Inject constructor(private val config: ServerConfig) :
        Provider<PactSettings> {
        override fun get(): PactSettings = from(config.gameplay.demonicPacts)
    }

    companion object {
        const val DEFAULT_STARTING_POINTS: Int = 2
        const val DEFAULT_FREE_RESETS: Int = 2
        const val DEFAULT_SPEND_CAP: Int = 40

        fun from(yaml: DemonicPactsYaml): PactSettings =
            PactSettings(
                enabled = yaml.enabled,
                startingPoints = yaml.startingPoints ?: DEFAULT_STARTING_POINTS,
                freeResets = yaml.freeResets ?: DEFAULT_FREE_RESETS,
                spendCap = yaml.spendCap ?: DEFAULT_SPEND_CAP,
            )
    }
}
