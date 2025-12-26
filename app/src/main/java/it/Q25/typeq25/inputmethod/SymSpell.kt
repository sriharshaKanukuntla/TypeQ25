package it.srik.TypeQ25.inputmethod

/**
 * SymSpell implementation for fast spell checking using delete-based approach.
 * Based on the SymSpell algorithm - significantly faster than traditional edit distance.
 * 
 * Pre-computes character deletions for dictionary words to enable O(n*d) lookup
 * instead of O(m*n*dict_size) for traditional Levenshtein distance.
 */
class SymSpell(
    private val maxEditDistance: Int = 2,
    private val prefixLength: Int = 7
) {

    data class SuggestItem(
        val term: String,
        val distance: Int,
        val frequency: Int
    )

    // term -> frequency
    private val dictionary: MutableMap<String, Int> = mutableMapOf()
    // delete -> list of terms that produced this delete
    private val deletes: MutableMap<String, MutableList<String>> = mutableMapOf()

    /**
     * Adds a word to the dictionary with its frequency.
     * Generates all delete variations up to maxEditDistance.
     */
    fun addWord(term: String, frequency: Int) {
        if (term.isEmpty()) return
        val existing = dictionary[term]
        if (existing == null || frequency > existing) {
            dictionary[term] = frequency
        }

        val key = term.take(prefixLength)
        generateDeletes(key, maxEditDistance) { delete ->
            deletes.getOrPut(delete) { mutableListOf() }.let { bucket ->
                if (!bucket.contains(term)) bucket.add(term)
            }
        }
    }

    /**
     * Looks up spelling suggestions for the input word.
     * Returns suggestions sorted by edit distance, frequency, and length.
     */
    fun lookup(input: String, maxSuggestions: Int = 8): List<SuggestItem> {
        if (input.isEmpty()) return emptyList()
        val suggestions = mutableListOf<SuggestItem>()
        val suggestionSet = HashSet<String>()
        val consideredDeletes = HashSet<String>()

        val inputPrefix = input.take(prefixLength)
        val queue: ArrayDeque<String> = ArrayDeque()
        queue.add(inputPrefix)
        consideredDeletes.add(inputPrefix)

        fun addSuggestion(term: String, distance: Int) {
            val freq = dictionary[term] ?: 0
            val item = SuggestItem(term, distance, freq)
            if (suggestionSet.add(term)) {
                suggestions.add(item)
            } else {
                // update if higher frequency with same term/distance
                val idx = suggestions.indexOfFirst { it.term == term }
                if (idx >= 0 && suggestions[idx].frequency < freq) {
                    suggestions[idx] = item
                }
            }
        }

        // Check for direct hit
        dictionary[input]?.let { freq ->
            addSuggestion(input, 0)
        }

        while (queue.isNotEmpty()) {
            val candidate = queue.removeFirst()
            val distance = inputPrefix.length - candidate.length
            if (distance > maxEditDistance) continue

            // candidate is a delete; see if it maps to any terms
            deletes[candidate]?.forEach { suggestionTerm ->
                val editDistance = damerauDistanceLimited(input, suggestionTerm, maxEditDistance)
                if (editDistance >= 0 && editDistance <= maxEditDistance) {
                    addSuggestion(suggestionTerm, editDistance)
                }
            }

            // enqueue next deletes
            if (distance < maxEditDistance) {
                for (i in candidate.indices) {
                    val delete = candidate.removeRange(i, i + 1)
                    if (consideredDeletes.add(delete)) {
                        queue.add(delete)
                    }
                }
            }
        }

        // Sort by distance asc, frequency desc, length asc and trim
        return suggestions.sortedWith(
            compareBy<SuggestItem> { it.distance }
                .thenByDescending { it.frequency }
                .thenBy { it.term.length }
        ).take(maxSuggestions)
    }

    /**
     * Generates all possible deletes for a term up to the given distance.
     */
    private fun generateDeletes(term: String, distance: Int, emit: (String) -> Unit) {
        fun recurse(current: String, d: Int) {
            if (d == 0) return
            for (i in current.indices) {
                val deleted = current.removeRange(i, i + 1)
                emit(deleted)
                recurse(deleted, d - 1)
            }
        }
        recurse(term, distance)
    }

    /**
     * Calculates Damerau-Levenshtein distance with early termination.
     * Handles insertions, deletions, substitutions, and transpositions.
     * Returns -1 if distance exceeds maxDistance.
     */
    private fun damerauDistanceLimited(a: String, b: String, maxDistance: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > maxDistance) return -1
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        val prevPrev = IntArray(b.length + 1)

        for (i in 1..a.length) {
            curr[0] = i
            var minRow = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(
                    prev[j] + 1,        // deletion
                    curr[j - 1] + 1,    // insertion
                    prev[j - 1] + cost  // substitution
                )
                // Handle transposition (e.g., "teh" -> "the")
                if (i > 1 && j > 1 &&
                    a[i - 1] == b[j - 2] &&
                    a[i - 2] == b[j - 1]
                ) {
                    value = minOf(value, prevPrev[j - 2] + 1)
                }
                curr[j] = value
                if (value < minRow) minRow = value
            }
            // Early termination if entire row exceeds maxDistance
            if (minRow > maxDistance) return -1
            // Rotate arrays for next iteration
            for (k in 0..b.length) {
                prevPrev[k] = prev[k]
                prev[k] = curr[k]
            }
        }
        return if (prev[b.length] <= maxDistance) prev[b.length] else -1
    }

    /**
     * Returns the current dictionary size.
     */
    fun size(): Int = dictionary.size

    /**
     * Clears all stored data.
     */
    fun clear() {
        dictionary.clear()
        deletes.clear()
    }
}
