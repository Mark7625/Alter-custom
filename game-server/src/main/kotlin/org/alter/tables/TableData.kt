package org.alter.tables

import org.alter.game.util.DbHelper

interface TableData<T> {
    fun convert(table: DbHelper): T
}

