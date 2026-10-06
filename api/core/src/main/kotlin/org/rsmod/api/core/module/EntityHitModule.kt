package org.rsmod.api.core.module

import com.google.inject.Scopes
import com.google.inject.multibindings.OptionalBinder
import org.rsmod.api.npc.hit.NpcDamageContributor
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.modifier.StandardNpcHitModifier
import org.rsmod.api.npc.hit.processor.NpcHitProcessor
import org.rsmod.api.npc.hit.processor.StandardNpcHitProcessor
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.modifier.StandardPlayerHitModifier
import org.rsmod.api.player.hit.processor.DamageOnlyPlayerHitProcessor
import org.rsmod.api.player.hit.processor.InstantPlayerHitProcessor
import org.rsmod.module.ExtendedModule

public object EntityHitModule : ExtendedModule() {
    override fun bind() {
        newSetBinding<NpcDamageContributor>()
        bindBaseInstance<NpcHitModifier>(StandardNpcHitModifier::class.java)
        bindBaseInstance<NpcHitProcessor>(StandardNpcHitProcessor::class.java)
        OptionalBinder.newOptionalBinder(binder(), PlayerHitModifier::class.java)
            .setDefault()
            .to(StandardPlayerHitModifier::class.java)
            .`in`(Scopes.SINGLETON)
        bindBaseInstance<InstantPlayerHitProcessor>(DamageOnlyPlayerHitProcessor::class.java)
    }
}
