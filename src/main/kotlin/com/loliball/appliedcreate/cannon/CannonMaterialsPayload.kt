package com.loliball.appliedcreate.cannon

import com.loliball.appliedcreate.AppliedCreate
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.handling.IPayloadContext

data class RequestCannonMaterialsPayload(val page: Int, val refresh: Boolean) : CustomPacketPayload {
    override fun type() = TYPE
    fun handle(context: IPayloadContext) {
        context.enqueueWork {
            val menu = context.player().containerMenu as? MEBlueprintCannonMenu ?: return@enqueueWork
            if (!menu.stillValid(context.player())) return@enqueueWork
            menu.materialPage = page.coerceIn(0, (menu.contentHolder.materials.size - 1).coerceAtLeast(0) / 7)
            if (refresh) menu.contentHolder.requestChecklist()
            menu.sendMaterials()
        }
    }
    companion object {
        val TYPE = CustomPacketPayload.Type<RequestCannonMaterialsPayload>(ResourceLocation.fromNamespaceAndPath(AppliedCreate.MOD_ID, "request_cannon_materials"))
        val STREAM_CODEC: StreamCodec<ByteBuf, RequestCannonMaterialsPayload> = object : StreamCodec<ByteBuf, RequestCannonMaterialsPayload> {
            override fun encode(buf: ByteBuf, value: RequestCannonMaterialsPayload) { buf.writeInt(value.page); buf.writeBoolean(value.refresh) }
            override fun decode(buf: ByteBuf) = RequestCannonMaterialsPayload(buf.readInt(), buf.readBoolean())
        }
    }
}

data class CannonMaterialsPayload(val menuId: Int, val pos: BlockPos, val page: Int, val pages: Int,
                                 val loading: Boolean, val incomplete: Boolean,
                                 val rows: List<MEBlueprintCannonBlockEntity.Material>) : CustomPacketPayload {
    override fun type() = TYPE
    fun handle(context: IPayloadContext) {
        context.enqueueWork {
            val menu = context.player().containerMenu as? MEBlueprintCannonMenu ?: return@enqueueWork
            if (menu.containerId == menuId && menu.contentHolder.blockPos == pos) menu.materialSnapshot = this
        }
    }
    companion object {
        val TYPE = CustomPacketPayload.Type<CannonMaterialsPayload>(ResourceLocation.fromNamespaceAndPath(AppliedCreate.MOD_ID, "cannon_materials"))
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CannonMaterialsPayload> = object : StreamCodec<RegistryFriendlyByteBuf, CannonMaterialsPayload> {
            override fun encode(buf: RegistryFriendlyByteBuf, value: CannonMaterialsPayload) {
                buf.writeVarInt(value.menuId); buf.writeBlockPos(value.pos); buf.writeVarInt(value.page); buf.writeVarInt(value.pages)
                buf.writeBoolean(value.loading); buf.writeBoolean(value.incomplete); buf.writeVarInt(value.rows.size)
                for (row in value.rows) {
                    ItemStack.STREAM_CODEC.encode(buf, row.stack); buf.writeVarLong(row.required); buf.writeVarLong(row.available); buf.writeBoolean(row.damage)
                }
            }
            override fun decode(buf: RegistryFriendlyByteBuf): CannonMaterialsPayload {
                val menuId = buf.readVarInt(); val pos = buf.readBlockPos(); val page = buf.readVarInt(); val pages = buf.readVarInt()
                val loading = buf.readBoolean(); val incomplete = buf.readBoolean(); val size = buf.readVarInt()
                require(size in 0..7)
                val rows = List(size) { MEBlueprintCannonBlockEntity.Material(ItemStack.STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarLong(), buf.readBoolean()) }
                return CannonMaterialsPayload(menuId, pos, page, pages, loading, incomplete, rows)
            }
        }
    }
}
