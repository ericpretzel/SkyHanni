package at.hannibal2.skyhanni.events.entity

import at.hannibal2.skyhanni.api.event.GenericSkyHanniEvent
import net.minecraft.entity.Entity

class EntityRemovedEvent<T : Entity>(val entity: T) : GenericSkyHanniEvent<T>(entity.javaClass)
