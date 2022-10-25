package io.smallrye.opentelemetry.tck;

import java.io.File;

import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePaths;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;

public class DeploymentProcessor implements ApplicationArchiveProcessor {
    @Override
    public void process(Archive<?> archive, TestClass testClass) {
        if (archive instanceof WebArchive) {
            WebArchive war = (WebArchive) archive;
            war.addAsWebInfResource(EmptyAsset.INSTANCE, ArchivePaths.create("beans.xml"));
            String[] deps = {
                    // Required for web-fragment and init of the CdiInjectorFactory
                    "org.jboss.resteasy:resteasy-cdi",
            };
            File[] dependencies = Maven.configureResolver()
                    .workOffline()
                    .loadPomFromFile(new File("pom.xml"))
                    .resolve(deps)
                    .withoutTransitivity()
                    .asFile();
            war.addAsLibraries(dependencies);
        }
    }
}
