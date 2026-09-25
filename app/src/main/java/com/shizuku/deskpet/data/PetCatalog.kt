package com.shizuku.deskpet.data

data class PetCharacter(val id: String, val name: String)

object PetCatalog {
    val characters = listOf(
        PetCharacter("perlica", "佩丽卡"),
        PetCharacter("caiye", "彩叶"),
        PetCharacter("asuna", "明日奈"),
        PetCharacter("kaguya", "辉夜"),
        PetCharacter("shiroko", "白子")
    )

    fun get(id: String): PetCharacter = characters.find { it.id == id } ?: characters.first()

    fun assetPath(id: String, state: String): String = "pets/${get(id).id}/$state.webm"
}
