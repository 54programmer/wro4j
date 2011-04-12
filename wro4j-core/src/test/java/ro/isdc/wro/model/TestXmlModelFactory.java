/*
 * Copyright (c) 2008. All rights reserved.
 */
package ro.isdc.wro.model;

import java.io.InputStream;
import java.util.List;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.isdc.wro.config.Context;
import ro.isdc.wro.model.factory.WroModelFactory;
import ro.isdc.wro.model.factory.XmlModelFactory;
import ro.isdc.wro.model.group.Group;
import ro.isdc.wro.model.group.RecursiveGroupDefinitionException;
import ro.isdc.wro.model.resource.Resource;

/**
 * TestProcessor.java.
 *
 * @author Alex Objelean
 * @created Created on Nov 3, 2008
 */
public class TestXmlModelFactory {
  private static final Logger LOG = LoggerFactory.getLogger(TestXmlModelFactory.class);
  private WroModelFactory factory;

  @Before
  public void init() {
    final Context context = Context.standaloneContext();
    Context.set(context);
    context.getConfig().setCacheUpdatePeriod(0);
    context.getConfig().setModelUpdatePeriod(0);
  }

  @After
  public void tearDown() {
    factory.destroy();
  }

  @Test(expected=RecursiveGroupDefinitionException.class)
  public void recursiveGroupThrowsException() {
    factory = new XmlModelFactory() {
      @Override
      public InputStream getConfigResourceAsStream() {
        return Thread.currentThread().getContextClassLoader()
            .getResourceAsStream("recursive.xml");
      }
		};
		factory.getInstance();
	}

  @Test
  public void testWithUpdatePeriodSet() {
    Context.get().getConfig().setCacheUpdatePeriod(1);
    Context.get().getConfig().setModelUpdatePeriod(1);
    testSuccessfulCreation();
  }
//
//
//  @Test
//  public void processResourceType() {
//    factory = new XmlModelFactory();
//    factory.getInstance();
//  }

  //TODO use two concurrent calls
  @Test
  public void testTwoConcurrentCreationCalls() {
    testSuccessfulCreation();
    factory.getInstance();
  }

  @Test
  public void testSuccessfulCreation() {
    factory = new XmlModelFactory() {
      @Override
      public InputStream getConfigResourceAsStream() {
        return Thread.currentThread()
          .getContextClassLoader()
          .getResourceAsStream("wro1.xml");
      }
    };
    //the uriLocator factory doesn't have any locators set...
    final WroModel model = factory.getInstance();
    LOG.debug("model: " + model);
  }


  @Test
  public void testMinimizeAttributePresence() {
    factory = new XmlModelFactory() {
      @Override
      public InputStream getConfigResourceAsStream() {
        //get a class relative test resource
        return TestXmlModelFactory.class.getResourceAsStream("wro-minimizeAttribute.xml");
      }
    };
    //the uriLocator factory doesn't have any locators set...
    final WroModel model = factory.getInstance();
    final Group group = model.getGroupByName(model.getGroupNames().get(0));
    final List<Resource> resourceList = group.getResources();
    LOG.debug("resources: " + resourceList);
    Assert.assertEquals(false, resourceList.get(0).isMinimize());
    Assert.assertEquals(true, resourceList.get(1).isMinimize());
    Assert.assertEquals(true, resourceList.get(2).isMinimize());
    LOG.debug("model: " + model);
  }
}
