/*
* JBoss, Home of Professional Open Source
* Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
* as indicated by the @authors tag. All rights reserved.
* See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* http://www.apache.org/licenses/LICENSE-2.0
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.jboss.seam.jms.descriptors.hornetq;

import org.jboss.shrinkwrap.descriptor.spi.node.Node;

/**
 * QueueDescriptor - describes a queue in the HornetQJMS xml file.
 *
 * @author <a href="mailto:john.d.ament@gmail.com">John Ament</a>
 */
public class QueueDescriptor {
    private final Node configuration;
    private final HornetQJMSDescriptorImpl parent;
    QueueDescriptor(Node configuration,HornetQJMSDescriptorImpl parent)
    {
        this.configuration = configuration;
        this.parent = parent;
    }
    public QueueDescriptor name(String name)
    {
        configuration.attribute("name",name);
        return this;
    }
    public QueueDescriptor entry(String name)
    {
        configuration.getOrCreate("entry").attribute("name",name);
        return this;
    }
    public QueueDescriptor durable(boolean durable)
    {
        configuration.getOrCreate("durable").text(durable);
        return this;
    }
    public QueueDescriptor filter(String filter)
    {
        configuration.getOrCreate("filter").text(filter);
        return this;
    }
    public HornetQJMSDescriptorImpl parent()
    {
        return parent;
    }
}
