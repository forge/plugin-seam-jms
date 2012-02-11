package org.jboss.seam.jms.forge;

import org.jboss.forge.parser.JavaParser;
import org.jboss.forge.parser.java.JavaInterface;
import org.jboss.forge.parser.java.SyntaxError;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.parser.xml.Node;
import org.jboss.forge.parser.xml.XMLParser;
import org.jboss.forge.project.Project;
import org.jboss.forge.project.dependencies.Dependency;
import org.jboss.forge.project.dependencies.DependencyBuilder;
import org.jboss.forge.project.facets.DependencyFacet;
import org.jboss.forge.project.facets.JavaSourceFacet;
import org.jboss.forge.project.facets.ResourceFacet;
import org.jboss.forge.project.facets.WebResourceFacet;
import org.jboss.forge.resources.DirectoryResource;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.resources.java.JavaResource;
import org.jboss.forge.shell.*;
import org.jboss.forge.shell.events.PickupResource;
import org.jboss.forge.shell.plugins.*;
import org.jboss.forge.shell.project.ProjectScoped;
import org.jboss.seam.jms.descriptors.hornetq.HornetQJMSDescriptor;
import org.jboss.seam.jms.descriptors.hornetq.QueueDescriptor;
import org.jboss.seam.jms.descriptors.hornetq.TopicDescriptor;
import org.jboss.shrinkwrap.descriptor.api.DescriptorImporter;
import org.jboss.shrinkwrap.descriptor.api.Descriptors;

import javax.enterprise.event.Event;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@Alias("jms")
@RequiresProject
public class JmsPlugin implements Plugin
{

   private static final String JMS_ARTIFACT_ID = "seam-jms";
   private static final String JMS_GROUP_ID = "org.jboss.seam.jms";
   @Inject
   @ProjectScoped
   private Project project;
   @Inject
   private Shell shell;
   @Inject
   @Current
   private JavaResource resource;
   @Inject
   private ShellPrompt prompt;
   @Inject
   private ShellPrintWriter writer;
   @Inject
   private Event<PickupResource> pickUp;

   @DefaultCommand
   public void exampleDefaultCommand(@Option String opt, PipeOut pipeOut)
   {
      pipeOut.println(ShellColor.BLUE, "Use the install command to add a Seam JMS dependency.");
      pipeOut.println(ShellColor.BLUE, "Use the new-interface command to add Observer Method Interfaces.");
      pipeOut.println(ShellColor.BLUE, "Use the new-method command to add an Observer Method to an Interface.");
      pipeOut.println(ShellColor.BLUE, "Use the hornetq-topic command to create or edit hornetq topics.");
      pipeOut.println(ShellColor.BLUE, "Use the hornetq-queue command to create or edit hornetq queues.");
   }

   @SetupCommand
   public void installCommand(PipeOut pipeOut)
   {
      DependencyFacet deps = project.getFacet(DependencyFacet.class);
      String depFilter = String.format("%s:%s:[,]", JMS_GROUP_ID, JMS_ARTIFACT_ID);
      List<Dependency> versions = deps.resolveAvailableVersions(depFilter);
      Dependency version = shell.promptChoiceTyped("Install which version of Seam JMS?", versions);
      deps.setProperty("seam.jms.version", version.getVersion());
      String depString = String.format("%s:%s:${seam.jms.version}", JMS_GROUP_ID, JMS_ARTIFACT_ID);
      DependencyBuilder dep = DependencyBuilder.create(depString);
      deps.addDirectDependency(dep);
   }


    @Command("configure-connectionfactory")
    public void configureConnectionFactory(@Option(name = "name", required = true) String connectionFactoryName)
    {
        ResourceFacet resourceFacet = project.getFacet(ResourceFacet.class);
        FileResource<?> seambeans = resourceFacet.getResource("META-INF/seam-beans.xml");

        Node xml;
        if(seambeans.exists()) {
            xml = XMLParser.parse(seambeans.getResourceInputStream()).getRoot();
        } else {
            xml = XMLParser.parse("<beans xmlns=\"http://java.sun.com/xml/ns/javaee\"\n" +
                    "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                    "    xmlns:s=\"urn:java:ee\"\n" +
                    "    xmlns:jmsi=\"urn:java:org.jboss.seam.jms.inject\"\n" +
                    "    xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://docs.jboss.org/cdi/beans_1_0.xsd\"></beans>");
        }

        Node producerNode = xml.createChild("jmsi:JmsConnectionFactoryProducer");
        producerNode.createChild("s:modifies");
        producerNode.createChild("jmsi:connectionFactoryJNDILocation").text(connectionFactoryName);

        seambeans.createNewFile();
        seambeans.setContents(XMLParser.toXMLInputStream(xml));
    }

   @Command("hornetq-queue")
   public void createQueueDescriptor(
            @Option(name = "name",
                     required = true,
                     help = "The name of the queue",
                     description = "The name of the queue") final String name,
            @Option(name = "durable",
                     required = false,
                     help = "Durable queues have special properties",
                     description = "Whether this is a durable queue or not.") final Boolean durable,
             @Option(name = "filter",
                      required = false,
                      help = "Filters messages.",
                      description = "Whether to filter or not.") final String filter)
   {
      HornetQJMSDescriptor descriptor = getOrCreateDescriptor();
      QueueDescriptor queue = descriptor.queue(name);
      if (durable != null)
         queue.durable(durable);
      if (filter != null)
         queue.filter(filter);
      while (true)
      {
         String entry = shell.prompt("Enter a JNDI Location for this Queue, or leave blank to complete.");
         if (entry == null || entry.length() < 1)
            break;
         else
            queue.entry(entry);
      }
      saveConfig(descriptor);
   }

   @Command("hornetq-topic")
   public void createTopicDescriptor(
            @Option(name = "name",
                     required = true,
                     help = "The name of the topic",
                     description = "The name of the topic") final String name)
   {
      HornetQJMSDescriptor descriptor = getOrCreateDescriptor();
      TopicDescriptor topic = descriptor.topic(name);
      while (true)
      {
         String entry = shell.prompt("Enter a JNDI Location for this Topic, or leave blank to complete.");
         if (entry == null || entry.length() < 1)
            break;
         else
            topic.entry(entry);
      }
      saveConfig(descriptor);
   }

   private HornetQJMSDescriptor getOrCreateDescriptor()
   {
      FileResource<?> fileRes = getConfigFile();
      HornetQJMSDescriptor descriptor = null;
      if (fileRes.exists())
      {
         DescriptorImporter<HornetQJMSDescriptor> importer = Descriptors.importAs(HornetQJMSDescriptor.class);
         descriptor = importer.from(getConfigFile().getResourceInputStream());
      }
      else
      {
         descriptor = Descriptors.create(HornetQJMSDescriptor.class);
      }
      return descriptor;
   }

   private void saveConfig(final HornetQJMSDescriptor descriptor)
   {
      String output = descriptor.exportAsString();
      getConfigFile().setContents(output);
   }

   private FileResource<?> getConfigFile()
   {
      DirectoryResource webRoot = project.getFacet(WebResourceFacet.class).getWebRootDirectory();
      return (FileResource<?>) webRoot.getChild("WEB-INF" + File.separator + "hornetq-jms.xml");
   }

   @Command("new-interface")
   public void interfaceCommand(@PipeIn final InputStream in,
            @Option(required = false,
                     help = "the package in which to build this Interface",
                     description = "source package",
                     type = PromptType.JAVA_PACKAGE,
                     name = "package") final String pckg,
            @Option(required = false,
                     help = "the class definition: surround with quotes",
                     description = "class definition") final String... def)
            throws IOException
   {
      JavaSourceFacet java = project.getFacet(JavaSourceFacet.class);
      JavaInterface ji = null;
      JavaResource jr = null;
      if (def != null)
      {
         String classDef = Strings.join(Arrays.asList(def), " ");
         ji = JavaParser.parse(JavaInterface.class, classDef);
      }
      else if (in != null)
      {
         ji = JavaParser.parse(JavaInterface.class, in);
      }
      else
      {
         throw new RuntimeException("You must provide an interface definition.");
      }

      if (pckg != null)
      {
         ji.setPackage(pckg);
      }

      if (!ji.hasSyntaxErrors())
      {
         java.saveJavaSource(ji);
      }
      else
      {
         writer.println(ShellColor.RED, "Syntax Errors:");
         for (SyntaxError error : ji.getSyntaxErrors())
         {
            writer.println(error.toString());
         }
         writer.println();

         if (prompt.promptBoolean("Your class has syntax errors, create anyway?", true))
         {
            jr = java.saveJavaSource(ji);
         }
      }
      if (jr != null)
         pickUp.fire(new PickupResource(jr));
   }
}
