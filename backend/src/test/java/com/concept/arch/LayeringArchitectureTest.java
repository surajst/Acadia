package com.concept.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
        noClasses().that().resideInAPackage("..web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.concept.academics..", "com.concept.shared.data..")
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
    void noCodeDependsOnTheDeletedManagementPackage() {
        // The legacy god-package is gone; this tripwire runs over the whole
        // codebase so it can never be reintroduced.
        noClasses().that().resideOutsideOfPackage("com.concept.management..")
                .should().dependOnClassesThat().resideInAPackage("com.concept.management..")
                .allowEmptyShould(true)
                .check(WHOLE_CODEBASE);
    }
}
