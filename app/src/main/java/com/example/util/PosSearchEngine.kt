package com.example.util

import com.example.data.model.InventoryItem
import com.example.data.model.ItemType
import com.example.data.model.Sale
import com.example.data.model.SaleStatus
import com.example.data.model.SaleType
import org.json.JSONArray
import java.text.Normalizer
import java.util.Locale
import kotlin.math.ceil

/**
 * Pre-normalized entry for sub-millisecond POS catalog filtering during busy queues.
 */
data class IndexedInventoryEntry(
    val item: InventoryItem,
    val normalizedName: String,
    val normalizedCode: String,
    val normalizedCategory: String,
    val combinedSearchText: String
)

/**
 * High-speed, accent-insensitive, multi-token product search and quick-pick engine
 * designed specifically for fast checkout in Punto de Venta (TPV).
 */
object PosSearchEngine {

    private val DIACRITICS_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        val decomposed = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
        return DIACRITICS_REGEX.replace(decomposed, "").lowercase(Locale.ROOT)
    }

    fun buildIndex(items: List<InventoryItem>): List<IndexedInventoryEntry> {
        return items
            .asSequence()
            .filter { !it.isWaste }
            .map { item ->
                val normName = normalize(item.name)
                val normCode = normalize(item.code)
                val normCat = normalize(item.category)
                IndexedInventoryEntry(
                    item = item,
                    normalizedName = normName,
                    normalizedCode = normCode,
                    normalizedCategory = normCat,
                    combinedSearchText = "$normCode $normName $normCat"
                )
            }
            .toList()
    }

    /**
     * Filters and ranks inventory items by precision:
     * 1. Exact barcode / SKU match
     * 2. Barcode prefix match
     * 3. Product name prefix match
     * 4. Multi-token match across code, name, and category
     */
    fun search(
        index: List<IndexedInventoryEntry>,
        rawQuery: String,
        filterType: ItemType? = null
    ): List<InventoryItem> {
        val normQuery = normalize(rawQuery)

        if (normQuery.isEmpty()) {
            return index
                .asSequence()
                .filter { filterType == null || it.item.type == filterType }
                .sortedWith(
                    compareByDescending<IndexedInventoryEntry> {
                        it.item.type == ItemType.SERVICIO || it.item.stock > 0.0
                    }.thenBy { it.normalizedName }
                )
                .map { it.item }
                .toList()
        }

        val tokens = normQuery.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        val scored = ArrayList<Pair<Int, IndexedInventoryEntry>>()

        for (entry in index) {
            if (filterType != null && entry.item.type != filterType) continue

            var allTokensMatch = true
            var score = 0

            for (token in tokens) {
                if (!entry.combinedSearchText.contains(token)) {
                    allTokensMatch = false
                    break
                }
                score += when {
                    entry.normalizedCode == token -> 300
                    entry.normalizedCode.startsWith(token) -> 180
                    entry.normalizedName.startsWith(token) -> 140
                    entry.normalizedName.contains(" $token") -> 110
                    entry.normalizedName.contains(token) -> 70
                    else -> 30
                }
            }

            if (!allTokensMatch) continue

            // Exact & prefix boosts on full query
            if (entry.normalizedCode == normQuery && normQuery.isNotEmpty()) {
                score += 2000
            } else if (entry.normalizedCode.startsWith(normQuery)) {
                score += 900
            }

            if (entry.normalizedName == normQuery) {
                score += 800
            } else if (entry.normalizedName.startsWith(normQuery)) {
                score += 450
            }

            // Availability boost so in-stock items rank above out-of-stock items
            if (entry.item.type == ItemType.SERVICIO || entry.item.stock > 0.0) {
                score += 50
            }

            scored.add(score to entry)
        }

        scored.sortWith(
            compareByDescending<Pair<Int, IndexedInventoryEntry>> { it.first }
                .thenBy { it.second.normalizedName }
        )

        return scored.map { it.second.item }
    }

    /**
     * Checks if a scanned barcode or submitted query uniquely matches a SKU/barcode
     * for instant 1-step addition to the cart.
     */
    fun findExactBarcodeMatch(items: List<InventoryItem>, rawCode: String): InventoryItem? {
        val clean = rawCode.trim()
        if (clean.isEmpty()) return null
        return items.firstOrNull { !it.isWaste && it.code.equals(clean, ignoreCase = true) }
    }

    /**
     * Computes the most-sold products/services from real historical sales so cashiers
     * can ring up high-frequency items in a single tap.
     */
    fun computeTopSellingItems(
        items: List<InventoryItem>,
        sales: List<Sale>,
        limit: Int = 8
    ): List<InventoryItem> {
        val activeItems = items.filter { !it.isWaste }
        if (activeItems.isEmpty()) return emptyList()

        val salesCounts = HashMap<String, Double>()
        for (sale in sales) {
            if (sale.type != SaleType.VENTA || sale.status == SaleStatus.CANCELADA) continue
            if (sale.itemsJson.isBlank()) continue
            try {
                val arr = JSONArray(sale.itemsJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = normalize(obj.optString("name", ""))
                    val qty = obj.optDouble("quantity", 1.0)
                    if (name.isNotEmpty()) {
                        salesCounts[name] = (salesCounts[name] ?: 0.0) + qty
                    }
                }
            } catch (_: Exception) {
                // Ignore malformed historical JSON rows safely
            }
        }

        return activeItems
            .sortedWith(
                compareByDescending<InventoryItem> { item ->
                    salesCounts[normalize(item.name)] ?: 0.0
                }.thenByDescending { item ->
                    item.type == ItemType.SERVICIO || item.stock > 0.0
                }.thenBy { it.name }
            )
            .take(limit)
    }

    /**
     * Suggests practical cash bill combinations >= total for 1-tap checkout calculation.
     */
    fun suggestCashBills(total: Double): List<Double> {
        if (total <= 0.0) return emptyList()
        val candidates = linkedSetOf<Double>()
        val denominations = listOf(20.0, 50.0, 100.0, 200.0, 500.0, 1000.0)

        // Exact next multiple of 10 or 50 if total isn't already clean
        val nextTen = ceil(total / 10.0) * 10.0
        if (nextTen > total) candidates.add(nextTen)

        val nextFifty = ceil(total / 50.0) * 50.0
        if (nextFifty > total) candidates.add(nextFifty)

        val nextHundred = ceil(total / 100.0) * 100.0
        if (nextHundred > total) candidates.add(nextHundred)

        for (bill in denominations) {
            if (bill > total) {
                candidates.add(bill)
            }
        }

        return candidates.sorted().take(4)
    }
}
