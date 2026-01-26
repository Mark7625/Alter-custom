package org.alter.skills.cooking

import org.generated.tables.cooking.CookingRecipesRow

object CookingRecipes {
    val all: List<CookingRecipesRow> = CookingRecipesRow.all()

    val byRaw: Map<Int, CookingRecipesRow> = all.associateBy { it.raw }

    val byCooked: Map<Int, CookingRecipesRow> = all.associateBy { it.cooked }
}
