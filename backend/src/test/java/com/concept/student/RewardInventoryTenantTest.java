package com.concept.student;

import com.concept.rewards.data.RewardItem;
import com.concept.rewards.data.RewardItemRepository;
import com.concept.tenant.AcademicYear;
import com.concept.tenant.AcademicYearRepository;
import com.concept.tenant.Tenant;
import com.concept.tenant.TenantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The reward catalogue a student is shown must be their own school's.
 *
 * Both student dashboards built it with {@code rewardItemRepository.findAll()},
 * so every child saw every school's rewards -- the same findAll/findById-without-
 * tenant class of leak this codebase has had to fix repeatedly. Redemption was
 * always guarded ({@code findByIdAndTenantId}); it was the listing that leaked.
 */
@SpringBootTest
@TestPropertySource(properties = "app.dev-mode=true")
@Transactional
public class RewardInventoryTenantTest {

    @Autowired private RewardItemRepository rewardItemRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private AcademicYearRepository academicYearRepository;

    /** A tenant and its current academic year -- RewardItem requires both. */
    private record School(UUID tenantId, UUID yearId) {}

    private School newTenant(String name) {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        tenant.setName(name);
        tenant.setSubdomain("ri-" + tenantId.toString().substring(0, 8));
        tenant.setActive(true);
        tenant.setCreatedAt(Instant.now());
        tenantRepository.saveAndFlush(tenant);

        AcademicYear year = new AcademicYear();
        UUID yearId = UUID.randomUUID();
        year.setId(yearId);
        year.setTenantId(tenantId);
        year.setName("2026-27");
        year.setStartDate(LocalDate.of(2026, 4, 1));
        year.setEndDate(LocalDate.of(2027, 3, 31));
        year.setCurrent(true);
        academicYearRepository.saveAndFlush(year);
        return new School(tenantId, yearId);
    }

    private RewardItem reward(School school, String title) {
        RewardItem item = new RewardItem();
        item.setId(UUID.randomUUID());
        item.setTenantId(school.tenantId());
        item.setAcademicYearId(school.yearId());
        item.setTitle(title);
        item.setXpCost(100);
        item.setInventoryCount(5);
        return rewardItemRepository.saveAndFlush(item);
    }

    @Test
    void aSchoolsRewardCatalogueIsScopedToThatSchool() {
        School schoolA = newTenant("School A");
        School schoolB = newTenant("School B");

        reward(schoolA, "A: Canteen voucher");
        reward(schoolB, "B: Library pass");

        List<RewardItem> forA = rewardItemRepository.findByTenantId(schoolA.tenantId());

        assertTrue(forA.stream().anyMatch(r -> "A: Canteen voucher".equals(r.getTitle())),
                "school A should see its own reward");
        assertTrue(forA.stream().noneMatch(r -> "B: Library pass".equals(r.getTitle())),
                "school A must not see school B's reward");
        assertTrue(forA.stream().allMatch(r -> schoolA.tenantId().equals(r.getTenantId())),
                "every row returned must belong to the asking tenant");
    }

    @Test
    void findAllWouldHaveLeakedAcrossTenants() {
        School schoolA = newTenant("School A");
        School schoolB = newTenant("School B");
        reward(schoolA, "A: Canteen voucher");
        reward(schoolB, "B: Library pass");

        // Pins why the dashboards may not go back to findAll(): it genuinely
        // returns both schools' rows, so the tenant filter is the only thing
        // standing between a child and another school's catalogue.
        List<RewardItem> everything = rewardItemRepository.findAll();
        assertTrue(everything.stream().anyMatch(r -> schoolA.tenantId().equals(r.getTenantId())));
        assertTrue(everything.stream().anyMatch(r -> schoolB.tenantId().equals(r.getTenantId())));
    }
}
