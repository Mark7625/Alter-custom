package dev.openrune.codec.osrs.impl

import dev.openrune.definition.opcode.DefinitionOpcode
import dev.openrune.definition.opcode.OpcodeDefinitionCodec
import dev.openrune.definition.opcode.OpcodeList
import dev.openrune.definition.opcode.OpcodeType
import dev.openrune.definition.opcode.OpcodeType.BOOLEAN.enumType
import dev.openrune.definition.util.readNullableLargeSmart
import dev.openrune.definition.util.writeNullableLargeSmartCorrect
import dev.openrune.types.HuntModeType
import dev.openrune.types.NpcMode
import dev.openrune.types.hunt.HuntCheckNotTooStrong
import dev.openrune.types.hunt.HuntCondition
import dev.openrune.types.hunt.HuntNobodyNear
import dev.openrune.types.hunt.HuntType
import dev.openrune.types.hunt.HuntVis

class HuntCodec(val custom: Map<Int, HuntModeType>? = emptyMap()) :
    OpcodeDefinitionCodec<HuntModeType>() {

    override val definitionCodec =
        OpcodeList<HuntModeType>().apply {
            add(DefinitionOpcode(1, enumType<HuntType>(), HuntModeType::type))
            add(DefinitionOpcode(2, enumType<HuntVis>(), HuntModeType::checkVis))
            add(
                DefinitionOpcode(
                    3,
                    enumType<HuntCheckNotTooStrong>(),
                    HuntModeType::checkNotTooStrong,
                )
            )

            add(DefinitionOpcode(4, OpcodeType.BOOLEAN, HuntModeType::checkNotBusy))
            add(DefinitionOpcode(5, OpcodeType.BOOLEAN, HuntModeType::findKeepHunting))

            add(DefinitionOpcode(6, enumType<NpcMode>(), HuntModeType::findNewMode))
            add(DefinitionOpcode(7, enumType<HuntNobodyNear>(), HuntModeType::nobodyNear))

            add(DefinitionOpcode(8, OpcodeType.USHORT, HuntModeType::checkNotCombat))
            add(DefinitionOpcode(9, OpcodeType.USHORT, HuntModeType::checkNotCombatSelf))

            add(DefinitionOpcode(10, OpcodeType.BOOLEAN, HuntModeType::checkAfk))

            add(DefinitionOpcode(11, OpcodeType.SHORT, HuntModeType::rate))

            add(npcConditionOpcode(12, HuntModeType::checkNpc, HuntModeType::checkNpc::set))
            add(objConditionOpcode(13, HuntModeType::checkObj, HuntModeType::checkObj::set))
            add(locConditionOpcode(14, HuntModeType::checkLoc, HuntModeType::checkLoc::set))

            add(invConditionOpcode(15, HuntModeType::checkInvObj, HuntModeType::checkInvObj::set))
            add(
                invConditionOpcode(
                    16,
                    HuntModeType::checkInvParam,
                    HuntModeType::checkInvParam::set,
                )
            )

            add(varConditionOpcode(17, HuntModeType::checkVar1, HuntModeType::checkVar1::set))
            add(varConditionOpcode(18, HuntModeType::checkVar2, HuntModeType::checkVar2::set))
            add(varConditionOpcode(19, HuntModeType::checkVar3, HuntModeType::checkVar3::set))
        }

    override fun HuntModeType.createData() {
        if (custom == null) return

        val custom = custom[id] ?: return

        type = custom.type
        checkVis = custom.checkVis
        checkNotTooStrong = custom.checkNotTooStrong
        checkNotCombat = custom.checkNotCombat
        checkNotCombatSelf = custom.checkNotCombatSelf
        checkAfk = custom.checkAfk
        checkNotBusy = custom.checkNotBusy
        findKeepHunting = custom.findKeepHunting
        findNewMode = custom.findNewMode
        nobodyNear = custom.nobodyNear
        rate = custom.rate
        checkInvObj = custom.checkInvObj
        checkInvParam = custom.checkInvParam
        checkLoc = custom.checkLoc
        checkNpc = custom.checkNpc
        checkObj = custom.checkObj
        checkVar1 = custom.checkVar1
        checkVar2 = custom.checkVar2
        checkVar3 = custom.checkVar3
    }

    override fun createDefinition(): HuntModeType = HuntModeType()

    fun <T> varConditionOpcode(
        opcode: Int,
        getter: (T) -> HuntCondition.VarCondition?,
        setter: (T, HuntCondition.VarCondition) -> Unit,
    ): DefinitionOpcode<T> =
        DefinitionOpcode(
            opcode,
            decode = { buf, def, _ ->
                val varp = buf.readShort().toInt()
                val operator =
                    HuntCondition.Operator[buf.readByte().toInt()] ?: error("Invalid Operator")
                val required = buf.readInt()

                setter(def, HuntCondition.VarCondition(varp, operator, required))
            },
            encode = { buf, def ->
                getter(def)?.let {
                    buf.writeShort(it.varp)
                    buf.writeByte(it.operator.id)
                    buf.writeInt(it.required)
                }
            },
            shouldEncode = { getter(it) != null },
        )

    fun <T> invConditionOpcode(
        opcode: Int,
        getter: (T) -> HuntCondition.InvCondition?,
        setter: (T, HuntCondition.InvCondition) -> Unit,
    ): DefinitionOpcode<T> =
        DefinitionOpcode(
            opcode,
            decode = { buf, def, _ ->
                val inv = buf.readShort().toInt()
                val type = buf.readShort().toInt()
                val operator =
                    HuntCondition.Operator[buf.readByte().toInt()] ?: error("Invalid Operator")
                val required = buf.readInt()

                setter(def, HuntCondition.InvCondition(inv, type, operator, required))
            },
            encode = { buf, def ->
                getter(def)?.let {
                    buf.writeShort(it.inv)
                    buf.writeShort(it.type)
                    buf.writeByte(it.operator.id)
                    buf.writeInt(it.required)
                }
            },
            shouldEncode = { getter(it) != null },
        )

    fun <T> npcConditionOpcode(
        opcode: Int,
        getter: (T) -> HuntCondition.NpcCondition?,
        setter: (T, HuntCondition.NpcCondition) -> Unit,
    ): DefinitionOpcode<T> =
        DefinitionOpcode(
            opcode,
            decode = { buf, def, _ ->
                val npc = buf.readNullableLargeSmart()
                val category = buf.readNullableLargeSmart()
                setter(def, HuntCondition.NpcCondition(npc, category))
            },
            encode = { buf, def ->
                getter(def)?.let {
                    buf.writeNullableLargeSmartCorrect(it.npc)
                    buf.writeNullableLargeSmartCorrect(it.category)
                }
            },
            shouldEncode = { getter(it) != null },
        )

    fun <T> locConditionOpcode(
        opcode: Int,
        getter: (T) -> HuntCondition.LocCondition?,
        setter: (T, HuntCondition.LocCondition) -> Unit,
    ): DefinitionOpcode<T> =
        DefinitionOpcode(
            opcode,
            decode = { buf, def, _ ->
                val loc = buf.readNullableLargeSmart()
                val category = buf.readNullableLargeSmart()
                setter(def, HuntCondition.LocCondition(loc, category))
            },
            encode = { buf, def ->
                getter(def)?.let {
                    buf.writeNullableLargeSmartCorrect(it.loc)
                    buf.writeNullableLargeSmartCorrect(it.category)
                }
            },
            shouldEncode = { getter(it) != null },
        )

    fun <T> objConditionOpcode(
        opcode: Int,
        getter: (T) -> HuntCondition.ObjCondition?,
        setter: (T, HuntCondition.ObjCondition) -> Unit,
    ): DefinitionOpcode<T> =
        DefinitionOpcode(
            opcode,
            decode = { buf, def, _ ->
                val obj = buf.readNullableLargeSmart()
                val category = buf.readNullableLargeSmart()
                setter(def, HuntCondition.ObjCondition(obj, category))
            },
            encode = { buf, def ->
                getter(def)?.let {
                    buf.writeNullableLargeSmartCorrect(it.obj)
                    buf.writeNullableLargeSmartCorrect(it.category)
                }
            },
            shouldEncode = { getter(it) != null },
        )
}
