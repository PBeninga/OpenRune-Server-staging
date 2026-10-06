package org.rsmod.api.testing.factory.inv

import org.rsmod.game.inv.Inventory

public class TestInvFactory {
    /** Creates an [Inventory] of the cache inv type [internal], for example `"inv.bank"`. */
    public fun create(internal: String): Inventory = Inventory.create(internal)

    public fun createInv(): Inventory = Inventory.create("inv.inv")

    public fun createWorn(): Inventory = Inventory.create("inv.worn")
}
