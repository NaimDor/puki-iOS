package io.github.tomsadeh.puki.ui.common

/** [count] things to pick from, in a random order: [target] and others from [pool], no doubles. */
fun <T> pickChoices(target: T, pool: List<T>, count: Int, id: (T) -> Any): List<T> {
    val others = pool.filter { id(it) != id(target) }.distinctBy(id).shuffled().take((count - 1).coerceAtLeast(0))
    return (listOf(target) + others).shuffled()
}
