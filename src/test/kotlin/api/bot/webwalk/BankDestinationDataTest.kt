package api.bot.webwalk

import api.bot.webwalk.data.WebWalkDataException
import api.bot.webwalk.data.WebWalkLoader
import api.bot.webwalk.generate.BankLinks
import api.bot.webwalk.generate.BankSeed
import api.bot.webwalk.generate.BankStands
import api.bot.webwalk.generate.GeneratedBank
import api.bot.webwalk.generate.GenerationReport
import api.bot.webwalk.generate.WebWalkWriter
import api.bot.webwalk.model.BankDefinition
import api.bot.webwalk.model.BankLink
import api.bot.webwalk.model.NodeKind
import api.bot.webwalk.model.WebWalkNode
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.PlannerCosts
import api.bot.webwalk.plan.WalkEstimator
import api.bot.webwalk.plan.WebWalkPlanner
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.Position
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.*

/**
 * Checks banking NPC metadata through generation, JSON validation and ordinary bank route selection.
 *
 * @author lare96
 */
class BankDestinationDataTest {

    private lateinit var luna: MockedStatic<Luna>

    @BeforeEach fun settings() {
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        luna = mockStatic(Luna::class.java)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
    }

    @AfterEach fun restoreSettings() { luna.close() }

    @Test fun existingPositionalConstructorsRemainBoothBanks() {
        val booth = Position(3208, 3200)
        val stand = Position(3207, 3200)
        val links = listOf(BankLink("root", 4))
        assertNull(BankDefinition("booth", booth, stand, links).npcId)
        assertNull(GeneratedBank("booth", booth, stand, links).npcId)
        assertNull(BankSeed("booth", booth, listOf(stand)).npcId)
    }

    @Test fun generatorPreservesNpcIdentityAndSpawnAnchorThroughStandSelectionAndLinking() {
        val anchor = Position(3208, 3200)
        val rejected = Position(3209, 3200)
        val stand = Position(3207, 3200)
        val report = GenerationReport()
        val seeds = listOf(BankSeed("npc_bank", anchor, listOf(rejected, stand), npcId = 494))
        val stands = BankStands.generate(seeds, { if (it == stand) 2 else 0 }, report)
        val root = WebWalkNode("root", Position(3200, 3200), NodeKind.WAYPOINT)
        val banks = BankLinks.generate(stands, listOf(root), { _, _ -> true }, { _, _ -> 4 }, 56, 6, report)

        assertEquals(1, banks.size)
        assertEquals(494, banks.single().npcId)
        assertEquals(anchor, banks.single().booth)
        assertEquals(stand, banks.single().stand)
        assertEquals(listOf(BankLink("root", 4)), banks.single().links)
        val loaded = load(WebWalkWriter.banks(banks)).banks.single()
        assertEquals(494, loaded.npcId)
        assertEquals(anchor, loaded.booth)
        assertEquals(stand, loaded.stand)
        assertEquals(banks.single().links, loaded.links)
    }

    @Test fun legacyBankJsonStillLoadsAndWritingItDoesNotAddAnNpcField() {
        val graph = load("""[{"id":"legacy","booth":[3208,3200],"pos":[3207,3200],"links":[{"node":"root","cost":4}]}]""")
        val bank = graph.banks.single()
        assertNull(bank.npcId)
        val generated = GeneratedBank(bank.id, bank.booth, bank.stand, bank.links)
        val written = WebWalkWriter.banks(listOf(generated))
        assertFalse(written.contains("npcId"))
        assertEquals(bank, load(written).banks.single())
    }

    @Test fun mixedNpcAndBoothBanksRoundTripInStableIdOrder() {
        val npc = GeneratedBank("npc", Position(3208, 3200), Position(3207, 3200), listOf(BankLink("root", 4)), 494)
        val booth = GeneratedBank("booth", Position(3216, 3200), Position(3215, 3200), listOf(BankLink("root", 8)))
        val written = WebWalkWriter.banks(listOf(npc, booth))
        assertEquals(written, WebWalkWriter.banks(listOf(booth, npc)))
        val loaded = load(written).banks
        assertEquals(listOf("booth", "npc"), loaded.map { it.id })
        assertNull(loaded[0].npcId)
        assertEquals(494, loaded[1].npcId)
        assertEquals(npc.booth, loaded[1].booth)
    }

    @Test fun npcDefinitionZeroIsAccepted() {
        assertEquals(0, load(npcBank("0")).banks.single().npcId)
    }

    @Test fun aPresentNpcIdMustBeANonnegativeWholeNumber() {
        for (invalid in listOf("-1", "1.5", "2147483648", "\"494\"", "true", "[]", "null")) {
            val exception = assertThrows(WebWalkDataException::class.java) { load(npcBank(invalid)) }
            assertTrue(exception.problems.any { it.contains("banks.jsonc[0].npcId") }, invalid)
        }
    }

    @Test fun npcBanksRetainAdjacentSamePlaneStandValidation() {
        for (stand in listOf("[3208,3200]", "[3210,3200]", "[3207,3200,1]")) {
            val exception = assertThrows(WebWalkDataException::class.java) {
                load(npcBank("494", stand = stand))
            }
            assertTrue(exception.problems.any { it.contains("banks.jsonc[0].pos") }, stand)
        }
    }

    @Test fun npcBanksStillNeedValidUniqueLinksToExistingNodes() {
        val invalidLinks = listOf("[]", """[{"node":"unknown","cost":4}]""",
            """[{"node":"root","cost":4},{"node":"root","cost":5}]""")
        for (links in invalidLinks) {
            assertThrows(WebWalkDataException::class.java) { load(npcBank("494", links = links)) }
        }
    }

    @Test fun routeCostCanChooseAnNpcBankOverAGeometricallyCloserBooth() {
        val graph = load("""[
            {"id":"near_booth","booth":[3210,3200],"pos":[3209,3200],"links":[{"node":"root","cost":20}]},
            {"id":"far_npc","npcId":494,"booth":[3300,3200],"pos":[3299,3200],"links":[{"node":"root","cost":7}]}
        ]""")
        val estimator = WalkEstimator { from, to -> if (from == to) 0 else null }
        val planner = WebWalkPlanner(graph, estimator, PlannerCosts(legPenalty = 0.0))
        val plan = requireNotNull(planner.planToNearestBank(Position(3200, 3200), CapabilitySnapshot(), refine = false))
        assertEquals(Position(3299, 3200), plan.destination)
        assertEquals(7.0, plan.cost)
        assertEquals(494, graph.banks.single { it.stand == plan.destination }.npcId)
    }

    @Test fun addingNpcBanksDoesNotPreventChoosingACheaperBooth() {
        val graph = load("""[
            {"id":"booth","booth":[3210,3200],"pos":[3209,3200],"links":[{"node":"root","cost":5}]},
            {"id":"npc","npcId":494,"booth":[3220,3200],"pos":[3219,3200],"links":[{"node":"root","cost":30}]}
        ]""")
        val estimator = WalkEstimator { from, to -> if (from == to) 0 else null }
        val planner = WebWalkPlanner(graph, estimator, PlannerCosts(legPenalty = 0.0))
        val plan = requireNotNull(planner.planToNearestBank(Position(3200, 3200), CapabilitySnapshot(), refine = false))
        assertEquals(Position(3209, 3200), plan.destination)
        assertEquals(5.0, plan.cost)
    }

    private fun load(banks: String) = WebWalkLoader.fromSources(mapOf(
        WebWalkLoader.WALK_GRAPH to """{"waypoints":[{"id":"root","pos":[3200,3200]}],"edges":[]}""",
        WebWalkLoader.BANKS to banks))

    private fun npcBank(npcId: String, stand: String = "[3207,3200]",
                        links: String = """[{"node":"root","cost":4}]"""): String =
        """[{"id":"npc","npcId":$npcId,"booth":[3208,3200],"pos":$stand,"links":$links}]"""
}
