package org.rsmod.api.testing.factory

import org.rsmod.api.testing.factory.entity.TestPathingEntityFactory
import org.rsmod.api.testing.factory.inv.TestInvFactory
import org.rsmod.api.testing.factory.loc.TestLocFactory
import org.rsmod.api.testing.factory.loc.TestLocTypeFactory
import org.rsmod.api.testing.factory.map.TestCollisionFactory
import org.rsmod.api.testing.factory.npc.TestNpcFactory
import org.rsmod.api.testing.factory.npc.TestNpcTypeFactory
import org.rsmod.api.testing.factory.obj.TestObjFactory
import org.rsmod.api.testing.factory.obj.TestObjTypeFactory
import org.rsmod.api.testing.factory.player.TestPlayerFactory

/* Entity factory properties */
public val entityFactory: TestPathingEntityFactory
    get() = TestPathingEntityFactory()

/* Inv factory properties */
public val invFactory: TestInvFactory
    get() = TestInvFactory()

/* Loc factory properties */
public val locFactory: TestLocFactory
    get() = TestLocFactory()

public val locTypeFactory: TestLocTypeFactory
    get() = TestLocTypeFactory()

/* Map factory properties */
public val collisionFactory: TestCollisionFactory
    get() = TestCollisionFactory()

/* Npc factory properties */
public val npcFactory: TestNpcFactory
    get() = TestNpcFactory()

public val npcTypeFactory: TestNpcTypeFactory
    get() = TestNpcTypeFactory()

/* Obj factory properties */
public val objFactory: TestObjFactory
    get() = TestObjFactory()

public val objTypeFactory: TestObjTypeFactory
    get() = TestObjTypeFactory()

/* Player factory properties */
public val playerFactory: TestPlayerFactory
    get() = TestPlayerFactory()
