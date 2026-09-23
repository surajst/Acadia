package com.concept.rewards;

import com.concept.rewards.app.RewardView;
import com.concept.rewards.app.RewardsService;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rewards catalogue had no server-side bounds and no way to take an entry
 * back out: a reward saved at -50 XP was permanent, and redeeming it added XP
 * rather than spending it. These pin the price bounds, the tenant scoping on
 * the two new mutations, and the fact that a bad entry can now be withdrawn.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
class RewardsCatalogTest {

    @Autowired private RewardsService rewardsService;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;

    private UUID tenantId;
    private UUID yearId;
    private UUID otherTenantId;

    @BeforeEach
    void setup() {
        yearId = null;
        tenantId = newSchool("Silverbrook");
        otherTenantId = newSchool("Elsewhere");
    }

    private UUID newSchool(String name) {
        UUID tid = UUID.randomUUID();
        UUID yid = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(tid);
        tenant.setName(name);
        tenant.setSubdomain(name.toLowerCase() + "-" + tid.toString().substring(0, 8));
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantRepository.saveAndFlush(tenant);

        AcademicYear year = new AcademicYear();
        year.setId(yid);
        year.setTenantId(tid);
        year.setName("2026-27");
        year.setStartDate(LocalDate.of(2026, 4, 1));
        year.setEndDate(LocalDate.of(2027, 3, 31));
        year.setCurrent(true);
        academicYearRepository.saveAndFlush(year);

        if (yearId == null) {
            yearId = yid;
        }
        return tid;
    }

    private UUID create(int xpCost) {
        return rewardsService.createReward("Homework Pass", "Skip one homework", xpCost,
                "T", 5, tenantId, yearId, null);
    }

    /** P0-6, the reported case. */
    @Test
    void aNegativeXpCostIsRefused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> create(-50));
        assertTrue(e.getMessage().toLowerCase().contains("at least"),
                "the message should say what the rule is, got: " + e.getMessage());
        assertTrue(rewardsService.listInventory(tenantId).isEmpty(),
                "nothing may be saved when the price is refused");
    }

    /** Zero costs nothing, which is the same fault one step along. */
    @Test
    void aZeroXpCostIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> create(0));
    }

    @Test
    void negativeStockIsRefused() {
        assertThrows(IllegalArgumentException.class, () ->
                rewardsService.createReward("Bad stock", "", 10, "T", -1, tenantId, yearId, null));
    }

    @Test
    void anOrdinaryRewardIsStillAccepted() {
        UUID id = create(50);
        List<RewardView> inventory = rewardsService.listInventory(tenantId);
        assertEquals(1, inventory.size());
        assertEquals(50, inventory.get(0).xpCost());
        assertEquals(id, inventory.get(0).id());
    }

    /** The other half of P0-6: a bad reward has to be removable. */
    @Test
    void aRewardCanBeWithdrawn() {
        UUID id = create(50);
        rewardsService.deleteReward(id, tenantId, null);
        assertTrue(rewardsService.listInventory(tenantId).isEmpty());
    }

    @Test
    void aRewardCanBeAmended() {
        UUID id = create(50);
        rewardsService.updateReward(id, "Homework Pass", "Skip one homework", 75, "T", 3, tenantId, null);

        RewardView after = rewardsService.listInventory(tenantId).get(0);
        assertEquals(75, after.xpCost());
        assertEquals(3, after.inventoryCount());
    }

    /** An amendment must not be a way around the price rule either. */
    @Test
    void anAmendmentCannotPriceARewardBelowOne() {
        UUID id = create(50);
        assertThrows(IllegalArgumentException.class, () ->
                rewardsService.updateReward(id, "Homework Pass", "", -5, "T", 3, tenantId, null));
        assertEquals(50, rewardsService.listInventory(tenantId).get(0).xpCost(),
                "a refused amendment must leave the reward as it was");
    }

    /**
     * Both mutations take an id straight from a URL, so they have to be scoped
     * to the caller's school -- this is the repository change earning its place.
     */
    @Test
    void anotherSchoolsRewardIsNotVisibleToDeleteOrEdit() {
        UUID id = create(50);

        assertThrows(IllegalArgumentException.class, () ->
                rewardsService.deleteReward(id, otherTenantId, null));
        assertThrows(IllegalArgumentException.class, () ->
                rewardsService.updateReward(id, "Hijacked", "", 1, "T", 1, otherTenantId, null));

        assertEquals(1, rewardsService.listInventory(tenantId).size(),
                "the owning school's reward must survive both attempts");
    }
}
