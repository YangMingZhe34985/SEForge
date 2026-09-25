package com.ustb.seforge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter().importPackages("com.ustb.seforge");

    @Test
    void modelClientsStayInsideAiInfrastructure() {
        noClasses().that().resideOutsideOfPackage("..ai.infrastructure..")
                .should().dependOnClassesThat().haveNameMatching(
                        "dev\\.langchain4j\\.model\\.openai\\.OpenAi(ChatModel|StreamingChatModel|EmbeddingModel).*" )
                .check(classes);
    }

    @Test
    void controllersDoNotAccessRepositories() {
        ArchRule rule = noClasses().that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().resideInAnyPackage("..repository..");
        rule.check(classes);
    }

    @Test
    void providerSdksStayInsideInfrastructure() {
        ArchRule rule = noClasses().that().resideOutsideOfPackage("..infrastructure..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.langchain4j.model.openai..", "dev.langchain4j.store.embedding.milvus..",
                        "io.minio..", "io.milvus..");
        rule.check(classes);
    }

    @Test
    void domainDoesNotDependOnWebOrInfrastructure() {
        ArchRule rule = noClasses().that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..", "org.springframework.security..", "..infrastructure..");
        rule.check(classes);
    }
}
