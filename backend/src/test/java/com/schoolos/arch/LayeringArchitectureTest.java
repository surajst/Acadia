package com.schoolos.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces the interface/application/data layering from ADR 0001.
 *
 * <p>Scoped to migrated domains only (currently {@code roster}) so it stays
 * green while the legacy {@code management} package is carved up. As each
 * domain is refactored onto the {@code <domain>.web/app/data} template, add its
 * package here. The rules use {@code allowEmptyShould(true)} so they pass
 * vacuously until the target packages exist.
 */
class LayeringArchitectureTest {

    private static final JavaClasses MIGRATED = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(
                    "com.schoolos.roster.."
                    // add "com.schoolos.fees..", "com.schoolos.attendance..", ... as migrated
            );

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
}
