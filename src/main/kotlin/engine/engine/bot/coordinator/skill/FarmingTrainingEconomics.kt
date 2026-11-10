package engine.bot.coordinator.skill

import api.bot.script.AlchemyPolicy
import game.skill.farming.patch.Crop
import game.skill.farming.patch.FarmingPatch
import game.skill.farming.patch.HealthCheckPatch
import game.skill.farming.tree.AbstractTreePatch
import kotlin.math.pow

/**
 * Prices one finite farm visit without valuing delayed crops as guaranteed sale proceeds.
 * Native disease metadata supplies a conservative XP expectation; no patch is planted or advanced for a quote.
 *
 * @author lare96
 */
internal object FarmingTrainingEconomics {

    /**
     * Ignores protection, compost, watering, saved harvest lives and experience multipliers. Counting a disease roll
     * on every growth transition also includes native immune stages, making survival a lower bound. Ordinary plots
     * give their delayed planting award only after harvest; health-check plots instead use their actual check award.
     */
    fun experience(patch: FarmingPatch, crop: Crop): Double {
        if (crop.stages !in 1..64 || listOf(crop.plantXp, crop.harvestXp, crop.checkHealthXp)
                .any { !it.isFinite() || it < 0.0 }) return 0.0
        val checked = patch is AbstractTreePatch || patch is HealthCheckPatch
        val immediate = if (patch.plantXpOnPlanting) crop.plantXp else 0.0
        val delayed = if (!patch.plantXpOnPlanting && !checked) crop.plantXp else 0.0
        val harvests = if (checked) 1 else FarmingPatch.BASE_HARVEST_LIVES
        val future = delayed + crop.harvestXp * harvests + if (checked) crop.checkHealthXp else 0.0
        val survival = (1.0 - crop.baseDiseaseChance.coerceIn(0, 128) / 128.0)
            .pow((crop.stages - 1).coerceAtLeast(0))
        return immediate + future * survival
    }

    /**
     * Counts full consumed-input opportunity cost and every missing reusable tool. The purse must cover the entire
     * missing basket at captured ceilings before acquisition starts, including tools needed by the later harvest.
     */
    fun maximumPurchase(
        requirements: Map<Int, Int>, consumed: Set<Int>, experience: Double,
        owned: (Int) -> Long, unitPrice: (Int) -> Long, purse: Long
    ): Long? {
        if (!experience.isFinite() || experience <= 0.0 || purse < 0L || requirements.size > 32) return null
        var economicCost = 0L
        var purchaseCost = 0L
        for ((id, amount) in requirements) {
            val price = unitPrice(id)
            if (amount < 1 || price < 1L) return null
            val missing = (amount.toLong() - owned(id).coerceAtLeast(0L)).coerceAtLeast(0L)
            val charged = if (id in consumed) amount.toLong() else missing
            if (price > Long.MAX_VALUE / maxOf(charged, missing, 1L)) return null
            val economic = charged * price
            val purchase = missing * price
            if (economicCost > Long.MAX_VALUE - economic || purchaseCost > Long.MAX_VALUE - purchase) return null
            economicCost += economic
            purchaseCost += purchase
        }
        val allowance = (experience * AlchemyPolicy.MAX_TRAINING_GP_PER_XP).toLong()
        return purchaseCost.takeIf { it <= purse && economicCost <= allowance }
    }
}
