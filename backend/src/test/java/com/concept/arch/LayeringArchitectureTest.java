package com.concept.arch;

import com.concept.common.BaseTenantEntity;
import com.concept.common.TenantScopedRepository;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Enforces the interface/application/data layering from ADR 0001.
 *
 * <p>The legacy {@code management} god-package has been fully carved up and
 * deleted. The layering rules run over every migrated domain slice plus the
 * {@code shared} kernel ({@link #MIGRATED}). Infrastructure packages that
 * predate and sit outside the slice template — {@code user}, {@code tenant},
 * {@code common}, {@code config}, {@code academics}, {@code announcement}, the
 * dev-only {@code devtools} harness, and the root seeders — are intentionally
 * out of scope for the web/app/data rules.
 *
 * <p>{@link #noCodeDependsOnTheDeletedManagementPackage()} additionally runs
 * over the whole codebase as a tripwire: nothing anywhere may depend on
 * {@code com.concept.management}, so the god-package cannot be resurrected.
 */
class LayeringArchitectureTest {

    private static final JavaClasses MIGRATED = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                    "com.concept.roster..",
                    "com.concept.attendance..",
                    "com.concept.fees..",
                    "com.concept.recognition..",
                    "com.concept.staff..",
                    "com.concept.rewards..",
                    "com.concept.transport.admin..",
                    "com.concept.console..",
                    "com.concept.dashboard..",
                    "com.concept.messaging..",
                    "com.concept.parent..",
                    "com.concept.timetable..",
                    "com.concept.assignment..",
                    "com.concept.student..",
                    "com.concept.tasks..",
                    "com.concept.assessment..",
                    "com.concept.oversight..",
                    "com.concept.curriculum..",
                    "com.concept.teacher..",
                    "com.concept.notification..",
                    "com.concept.export..",
                    "com.concept.academics..",
                    "com.concept.shared.."
            );

    private static final JavaClasses WHOLE_CODEBASE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.concept..");

    @Test
    void interfaceLayerDoesNotTouchTheDataLayer() {
        noClasses().that().resideInAPackage("..web..")
                .should().dependOnClassesThat().resideInAPackage("..data..")
                .allowEmptyShould(true)
                .check(MIGRATED);
    }

    @Test
    void applicationLayerHasNoWebConcerns() {
        noClasses().that().resideInAPackage("..app..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.servlet..", "org.thymeleaf..", "..web..")
                .allowEmptyShould(true)
                .check(MIGRATED);
    }

    @Test
    void dataLayerDependsOnNothingAbove() {
        noClasses().that().resideInAPackage("..data..")
                .should().dependOnClassesThat().resideInAnyPackage("..web..", "..app..")
                .allowEmptyShould(true)
                .check(MIGRATED);
    }

    @Test
    void interfaceLayerHandlesNoPersistenceEntities() {
        // The web layer must receive flat view objects from the application layer,
        // never JPA entities — nothing about storage should reach a controller.
        //
        // academics.data rather than academics..: the package was flat when this
        // rule was written, so naming the whole thing was the only way to name
        // its entities. It now has layers, and banning all of it would ban a
        // controller from reaching its own service.
        noClasses().that().resideInAPackage("..web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.concept.academics.data..", "com.concept.shared.data..")
                .allowEmptyShould(true)
                .check(MIGRATED);
    }

    @Test
    void controllersLiveInTheWebLayer() {
        classes().that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage("..web..")
                .allowEmptyShould(true)
                .check(MIGRATED);
    }

    @Test
    void repositoriesLiveInTheDataLayer() {
        classes().that().haveSimpleNameEndingWith("Repository")
                .should().resideInAPackage("..data..")
                .allowEmptyShould(true)
                .check(MIGRATED);
    }

    @Test
    void domainSlicesAreFreeOfDependencyCycles() {
        // Slices may share the kernel and call across boundaries, but the graph
        // of slice-to-slice dependencies must stay acyclic — no two slices may
        // depend on each other (directly or transitively). This is what keeps
        // the architecture modular horizontally, not just layered vertically.
        slices().matching("com.concept.(*)..")
                .should().beFreeOfCycles()
                .check(MIGRATED);
    }

    @Test
    void noCodeDependsOnTheDeletedManagementPackage() {
        // The legacy god-package is gone; this tripwire runs over the whole
        // codebase so it can never be reintroduced.
        noClasses().that().resideOutsideOfPackage("com.concept.management..")
                .should().dependOnClassesThat().resideInAPackage("com.concept.management..")
                .allowEmptyShould(true)
                .check(WHOLE_CODEBASE);
    }

    @Test
    void tenantScopedRepositoriesAreNeverLookedUpByBareId() {
        // findById(ID) on a repository for a BaseTenantEntity subclass returns
        // a row from ANY school, regardless of the caller's tenant -- exactly
        // the shape of the cross-tenant IDOR bugs fixed repeatedly across this
        // codebase's history. Every such repository extends
        // TenantScopedRepository, which declares findByIdAndTenantId; this
        // rule makes forgetting it a test failure instead of a silent leak.
        //
        // Dev-only seed/harness code is exempt (root com.concept package and
        // devtools/): it runs only under app.dev-mode=true, seeds a fixed set
        // of demo IDs, and is never attacker-reachable.
        noClasses()
                .that().resideOutsideOfPackages("com.concept.devtools..", "com.concept")
                .should(callBareFindByIdOnATenantScopedRepository())
                .allowEmptyShould(true)
                .check(WHOLE_CODEBASE);
    }

    private static ArchCondition<JavaClass> callBareFindByIdOnATenantScopedRepository() {
        // Used only inside noClasses().should(...), which wraps this condition in
        // ArchUnit's NeverCondition -- it relays our events through an inverting
        // decorator. So we must report the bad behaviour as SATISFIED (not
        // violated): NeverCondition flips that into the real, outer violation.
        return new ArchCondition<JavaClass>("call findById(ID) on a tenant-scoped repository") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaCall<?> call : javaClass.getCodeUnitCallsFromSelf()) {
                    JavaClass owner = call.getTarget().getOwner();
                    boolean isTenantScopedRepo = owner.isAssignableTo(TenantScopedRepository.class);
                    boolean isBareFindById = call.getTarget().getName().equals("findById");
                    if (isTenantScopedRepo && isBareFindById) {
                        String message = String.format(
                                "%s calls findById(ID) on %s (use findByIdAndTenantId instead) at %s",
                                javaClass.getSimpleName(), owner.getSimpleName(), call.getSourceCodeLocation());
                        events.add(SimpleConditionEvent.satisfied(call, message));
                    }
                }
            }
        };
    }

    /**
     * Every persisted entity must declare how it is scoped to a school.
     *
     * <p>The bare-findById rule above only sees repositories whose entity
     * extends {@link BaseTenantEntity}. An entity that never opted in is
     * invisible to it -- so the guardrail reported green over exactly the code
     * that had no tenant boundary at all. That is how
     * {@code /api/academic/teacher/pending} came to return every school's
     * submissions to any teacher, and it is the same blindness that let
     * {@code com.concept.billing} sit outside the layering rules unnoticed.
     *
     * <p>So the default is inverted here: an entity is a violation unless it
     * either extends BaseTenantEntity or is named below with a reason. Adding a
     * new entity forces the question rather than deferring it.
     */
    @Test
    void everyEntityIsEitherTenantScopedOrExplicitlyExempt() {
        classes()
                .that().areAnnotatedWith("jakarta.persistence.Entity")
                .and(new DescribedPredicate<JavaClass>("are not explicitly exempt") {
                    @Override
                    public boolean test(JavaClass javaClass) {
                        return !SCOPED_WITHOUT_A_TENANT_COLUMN.contains(javaClass.getSimpleName());
                    }
                })
                .should().beAssignableTo(BaseTenantEntity.class)
                .because("an entity with no tenant column cannot be filtered by school; "
                        + "extend BaseTenantEntity, or add it to SCOPED_WITHOUT_A_TENANT_COLUMN "
                        + "with the reason it is safe")
                .check(WHOLE_CODEBASE);
    }

    /**
     * Entities that carry no tenant column of their own and are deliberately
     * left that way. Each is listed with what scopes it instead; anything added
     * here without a real answer is a cross-tenant leak waiting to be found.
     */
    private static final Set<String> SCOPED_WITHOUT_A_TENANT_COLUMN = Set.of(
            // These two ARE the tenant dimension -- a tenant column on them
            // would be a self-reference.
            "Tenant",
            "AcademicYear",

            // Scoped through the student they hang off. Their repositories
            // expose findByIdAndStudentTenantId / findByStatusAndStudentTenantId
            // and callers must use those; a bare findById returns any school's row.
            "StudentProgress",
            "AcademicSubmission",
            "StudentAssessmentScore",

            // Scoped through its Conversation, the tenant-scoped aggregate root.
            // MessagingService proves conversation access first, then that the
            // message belongs to that conversation.
            "Message",

            // Has tenant_id and academic_year_id, but both nullable, so it
            // cannot extend BaseTenantEntity without a migration. Callers use
            // TeacherTaskRepository.findByIdAndTenantId.
            "TeacherTask",

            // Has tenant_id (not null) but no academic_year_id, so it does not
            // fit BaseTenantEntity as written.
            "TeacherVerification");

    /**
     * Every top-level package under {@code com.concept} is either covered by
     * the layering rules or listed as a deliberate exception.
     *
     * <p>{@link #MIGRATED} is an opt-in list, so a package simply absent from
     * it is unenforced and silent about it. That is how {@code com.concept
     * .billing} accumulated a dependency cycle with {@code fees} while the
     * architecture gate stayed green, and how {@code language} and
     * {@code parentapp} went unnoticed afterwards.
     *
     * <p>This makes the omission itself fail. A new domain package is a
     * violation until someone either slices it web/app/data and adds it to
     * MIGRATED, or records here why it does not fit.
     */
    @Test
    void everyTopLevelPackageIsEitherEnforcedOrKnowinglyExcluded() {
        Set<String> covered = new TreeSet<>();
        for (String pkg : MIGRATED_PACKAGE_NAMES) {
            covered.add(pkg);
        }
        covered.addAll(NOT_SLICED);

        Set<String> unaccounted = new TreeSet<>();
        for (JavaClass javaClass : WHOLE_CODEBASE) {
            String name = javaClass.getPackageName();
            if (!name.startsWith("com.concept")) {
                continue;
            }
            String rest = name.substring("com.concept".length());
            if (rest.isEmpty()) {
                continue; // root: BackendApplication and the dev-only seeders
            }
            String remainder = rest.substring(1);
            int dot = remainder.indexOf('.');
            String top = dot < 0 ? remainder : remainder.substring(0, dot);
            if (!covered.contains(top)) {
                unaccounted.add(top);
            }
        }

        assertTrue(unaccounted.isEmpty(),
                "These packages are covered by no architecture rule and are not listed as "
                        + "exceptions: " + unaccounted + ". Either slice them into web/app/data "
                        + "and add them to MIGRATED, or add them to NOT_SLICED with the reason.");
    }

    /** The bare package names inside {@link #MIGRATED}, for the coverage check above. */
    private static final Set<String> MIGRATED_PACKAGE_NAMES = Set.of(
            "roster", "attendance", "fees", "staff", "rewards", "transport", "console",
            "dashboard", "messaging", "parent", "timetable", "assignment", "student",
            "tasks", "assessment", "oversight", "curriculum", "teacher", "notification",
            "export", "shared", "academics", "recognition");

    /**
     * Packages that do not follow the web/app/data slice template, with the
     * reason. Being on this list is a statement that the omission is known --
     * not that the package is exempt from review.
     */
    private static final Set<String> NOT_SLICED = Set.of(
            // Infrastructure that predates the slice template and is shared by
            // every domain rather than owning one.
            "user", "tenant", "common", "config", "announcement",

            // Dev-only, gated on app.dev-mode=true.
            "devtools",

            // Flat adapter packages around external services (Azure speech and
            // translation) and a read-only SIS projection. No web/app/data split
            // because there is no domain here to split -- if either grows domain
            // logic, slice it and move it into MIGRATED.
            "language", "parentapp");
}
