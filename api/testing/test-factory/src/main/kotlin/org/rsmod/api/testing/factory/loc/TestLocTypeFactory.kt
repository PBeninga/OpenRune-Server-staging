package org.rsmod.api.testing.factory.loc

import dev.openrune.types.ObjectServerType
import org.rsmod.game.loc.LocInfo

/**
 * Creates standalone [ObjectServerType]s (loc types) for tests. They are not registered in
 * `ServerCacheManager`, so a packet handler that looks the type up by id will not see their ops.
 * Use a real cache loc when the test goes through a packet handler.
 */
public class TestLocTypeFactory {
    public fun create(loc: LocInfo, init: ObjectServerType.() -> Unit = {}): ObjectServerType =
        create(loc.id, init)

    public fun create(id: Int = 0, init: ObjectServerType.() -> Unit = {}): ObjectServerType =
        ObjectServerType(id = id, name = "test_loc_type").apply(init)
}
