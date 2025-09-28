package io.github.jmusacchio.kogito.generator.gradle.plugin.tasks;

import io.github.jmusacchio.kogito.generator.gradle.plugin.util.Util;
import org.drools.codegen.common.AppPaths;
import org.drools.codegen.common.DroolsModelBuildContext;
import org.drools.codegen.common.GeneratedFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.CompileClasspath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.kie.kogito.KogitoGAV;
import org.kie.kogito.codegen.api.Generator;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.JavaKogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.QuarkusKogitoBuildContext;
import org.kie.kogito.codegen.api.context.impl.SpringBootKogitoBuildContext;
import org.drools.codegen.common.GeneratedFileWriter;
import org.kie.kogito.codegen.decision.DecisionCodegen;
import org.kie.kogito.codegen.prediction.PredictionCodegen;
import org.kie.kogito.codegen.process.ProcessCodegen;
import org.kie.kogito.codegen.process.persistence.PersistenceGenerator;
import org.kie.kogito.codegen.rules.RuleCodegen;
import io.github.jmusacchio.kogito.generator.gradle.plugin.extensions.KogitoExtension;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public abstract class AbstractKieTask extends DefaultTask {

    protected static final GeneratedFileWriter.Builder GENERATED_FILE_WRITER_BUILDER = GeneratedFileWriter.builder("kogito", "kogito.codegen.resources.directory", "kogito.codegen.sources.directory");

    @org.gradle.api.tasks.Optional
    @Input
    private Map<String, String> properties;

    @InputFiles
    @CompileClasspath
    private File outputDirectory;

    @Internal
    private File baseDir;

//    @org.gradle.api.tasks.Optional
//    @InputFiles
//    @CompileClasspath
//    private File generatedSources;
//
//    @org.gradle.api.tasks.Optional
//    @InputFiles
//    @CompileClasspath
//    private File generatedResources;

    @org.gradle.api.tasks.Optional
    @Input
    private Boolean persistence;

    @org.gradle.api.tasks.Optional
    @Input
    private Boolean generateRules;

    @org.gradle.api.tasks.Optional
    @Input
    private Boolean generateProcesses;

    @org.gradle.api.tasks.Optional
    @Input
    private Boolean generateDecisions;

    @org.gradle.api.tasks.Optional
    @Input
    private Boolean generatePredictions;

    @Input
    protected String projectSourceEncoding;

    @Internal
    private File projectBaseDir;

    @Internal
    private Reflections reflections;

    public AbstractKieTask(KogitoExtension extension) {
        this.projectBaseDir = extension.getProjectBaseDir();
        this.properties = extension.getProperties();
        this.outputDirectory = extension.getOutputDirectory();
        this.baseDir = extension.getBaseDir();
        this.persistence = extension.isPersistence();
        this.generateRules = extension.isGenerateRules();
        this.generateProcesses = extension.isGenerateProcesses();
        this.generateDecisions = extension.isGenerateDecisions();
        this.generatePredictions = extension.isGeneratePredictions();
        this.projectSourceEncoding = extension.getProjectSourceEncoding();
    }

    protected void setSystemProperties(Map<String, String> properties) {
        if (properties != null) {
            getLogger().debug("Additional system properties: " + properties);
            for (Map.Entry<String, String> property : properties.entrySet()) {
                System.setProperty(property.getKey(), property.getValue());
            }
            getLogger().debug("Configured system properties were successfully set.");
        }
    }

    protected KogitoBuildContext discoverKogitoRuntimeContext(ClassLoader classLoader) {
        AppPaths appPaths = AppPaths.fromProjectDir(
                getProjectBaseDir().toPath()
        );
        KogitoBuildContext context = this.contextBuilder()
                .withClassAvailabilityResolver(this::hasClassOnClasspath)
                .withClassSubTypeAvailabilityResolver(this.classSubTypeAvailabilityResolver())
                .withApplicationProperties(appPaths.getResourceFiles())
                .withPackageName(this.appPackageName()).withClassLoader(classLoader)
                .withAppPaths(appPaths)
                .withGAV(new KogitoGAV(
                        getProject().getGroup().toString(),
                        getProject().getName(),
                        getProject().getVersion().toString()
                ))
                .build();
        this.additionalProperties(context);
        return context;
    }

    public Reflections getReflections() {
        if (this.reflections == null) {
            URLClassLoader classLoader = (URLClassLoader) this.projectClassLoader();
            ConfigurationBuilder builder = new ConfigurationBuilder();
            builder.addUrls(classLoader.getURLs());
            builder.addClassLoaders(classLoader);
            builder.setExpandSuperTypes(false);
            this.reflections = new Reflections(builder);
        }

        return this.reflections;
    }

    protected Predicate<Class<?>> classSubTypeAvailabilityResolver() {
        return (clazz) ->
                this.getReflections().getSubTypesOf(clazz).stream().anyMatch((c) ->
                        !c.isInterface() && !Modifier.isAbstract(c.getModifiers())
                );
    }

    protected ClassLoader projectClassLoader() {
        return Util.createProjectClassLoader(
                this.getClass().getClassLoader(),
                getProject(),
                getOutputDirectory(),
                null
        );
    }

    protected String appPackageName() {
        return DroolsModelBuildContext.DEFAULT_PACKAGE_NAME;
    }

    private void additionalProperties(KogitoBuildContext context) {
        this.classToCheckForREST().ifPresent((restClass) -> {
            if (!context.hasClassAvailable(restClass)) {
                this.getLogger().info("Disabling REST generation because class '" + restClass + "' is not available");
                context.setApplicationProperty(DroolsModelBuildContext.KOGITO_GENERATE_REST, "false");
            }
        });
        this.classToCheckForDI().ifPresent((diClass) -> {
            if (!context.hasClassAvailable(diClass)) {
                this.getLogger().info("Disabling dependency injection generation because class '" + diClass + "' is not available");
                context.setApplicationProperty(DroolsModelBuildContext.KOGITO_GENERATE_DI, "false");
            }
        });
        context.setApplicationProperty(Generator.CONFIG_PREFIX + RuleCodegen.GENERATOR_NAME, getGenerateRules().toString());
        context.setApplicationProperty(Generator.CONFIG_PREFIX + ProcessCodegen.GENERATOR_NAME, getGenerateProcesses().toString());
        context.setApplicationProperty(Generator.CONFIG_PREFIX + PredictionCodegen.GENERATOR_NAME, getGeneratePredictions().toString());
        context.setApplicationProperty(Generator.CONFIG_PREFIX + DecisionCodegen.GENERATOR_NAME, getGenerateDecisions().toString());
        context.setApplicationProperty(Generator.CONFIG_PREFIX + PersistenceGenerator.GENERATOR_NAME, getPersistence().toString());
    }

    private KogitoBuildContext.Builder contextBuilder() {
        switch (this.discoverFramework()) {
            case QUARKUS:
                return QuarkusKogitoBuildContext.builder();
            case SPRING:
                return SpringBootKogitoBuildContext.builder();
            default:
                return JavaKogitoBuildContext.builder();
        }
    }

    private Optional<String> classToCheckForREST() {
        switch (this.discoverFramework()) {
            case QUARKUS:
                return Optional.of(QuarkusKogitoBuildContext.QUARKUS_REST);
            case SPRING:
                return Optional.of(SpringBootKogitoBuildContext.SPRING_REST);
            default:
                return Optional.empty();
        }
    }

    private Optional<String> classToCheckForDI() {
        switch (this.discoverFramework()) {
            case QUARKUS:
                return Optional.of(QuarkusKogitoBuildContext.QUARKUS_DI);
            case SPRING:
                return Optional.of(SpringBootKogitoBuildContext.SPRING_DI);
            default:
                return Optional.empty();
        }
    }

    private Framework discoverFramework() {
        if (this.hasDependency("quarkus")) {
            return Framework.QUARKUS;
        } else {
            return this.hasDependency("spring") ? Framework.SPRING : Framework.NONE;
        }
    }

    private boolean hasDependency(String dependency) {
        return getProject()
                .getConfigurations()
                .stream().anyMatch(
                        c -> c.getAllDependencies().stream().anyMatch(d -> d.getName().contains(dependency))
                );
    }

    @Internal
    protected GeneratedFileWriter getGeneratedFileWriter() {
        return GENERATED_FILE_WRITER_BUILDER.build(Path.of(baseDir.getAbsolutePath()));
    }

    private boolean hasClassOnClasspath(String className) {
        try {
            URL[] urls = Util.classpathUrls(getProject()).stream().toArray(URL[]::new);
            try (URLClassLoader cl = new URLClassLoader(urls)) {
                cl.loadClass(className);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    protected void writeGeneratedFiles(Collection<GeneratedFile> generatedFiles) {
        GeneratedFileWriter writer = getGeneratedFileWriter();
        generatedFiles.forEach(generatedFile -> writeGeneratedFile(generatedFile, writer));
    }

    protected void writeGeneratedFile(GeneratedFile generatedFile) {
        writeGeneratedFile(generatedFile, getGeneratedFileWriter());
    }

    protected void writeGeneratedFile(GeneratedFile generatedFile, GeneratedFileWriter writer) {
        this.getLogger().info("Generating: " + generatedFile.relativePath());
        writer.write(generatedFile);
    }

    private enum Framework {
        QUARKUS,
        SPRING,
        NONE;

        Framework() {
        }
    }

    public File getProjectBaseDir() {
        return projectBaseDir;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(File baseDir) {
        this.baseDir = baseDir;
    }

    public String getProjectSourceEncoding() {
        return projectSourceEncoding;
    }

    public void setProjectSourceEncoding(String projectSourceEncoding) {
        this.projectSourceEncoding = projectSourceEncoding;
    }

    public Boolean getPersistence() {
        return persistence;
    }

    public Boolean getGenerateRules() {
        return generateRules;
    }

    public Boolean getGenerateProcesses() {
        return generateProcesses;
    }

    public Boolean getGenerateDecisions() {
        return generateDecisions;
    }

    public Boolean getGeneratePredictions() {
        return generatePredictions;
    }
}
