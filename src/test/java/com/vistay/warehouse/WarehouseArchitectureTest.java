package com.vistay.warehouse;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.vistay.warehouse", importOptions = {ImportOption.DoNotIncludeTests.class})
public class WarehouseArchitectureTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_adapters =
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..adapter..");

    @ArchTest
    static final ArchRule services_should_be_in_service_package =
            classes()
                    .that().haveSimpleNameEndingWith("Service")
                    .should().resideInAPackage("..application.service..");

    @ArchTest
    static final ArchRule repositories_should_be_in_repository_package =
            classes()
                    .that().haveSimpleNameEndingWith("Repository")
                    .should().resideInAPackage("..domain.repository..");
}

