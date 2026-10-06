package org.rsmod.api.testing.factory.obj

import dev.openrune.types.ItemServerType

/**
 * Creates standalone [ItemServerType]s for tests. They are not registered in `ServerCacheManager`,
 * so code that looks a type up by id will not find them. Use a real cache type when the code under
 * test does a lookup.
 */
public class TestObjTypeFactory {
    public fun create(id: Int = 0, init: ItemServerType.() -> Unit = {}): ItemServerType =
        ItemServerType(id = id, name = "test_obj_type").apply(init)
}
