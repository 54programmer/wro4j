/*
 * Copyright (c) 2008. All rights reserved.
 */
package ro.isdc.wro.manager;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.isdc.wro.config.Context;
import ro.isdc.wro.config.jmx.WroConfiguration;
import ro.isdc.wro.http.DelegatingServletOutputStream;
import ro.isdc.wro.http.HttpHeader;
import ro.isdc.wro.http.UnauthorizedRequestException;
import ro.isdc.wro.manager.factory.NoProcessorsWroManagerFactory;
import ro.isdc.wro.manager.factory.ServletContextAwareWroManagerFactory;
import ro.isdc.wro.model.factory.XmlModelFactory;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.processor.impl.css.CssUrlRewritingProcessor;
import ro.isdc.wro.model.resource.util.CRC32HashBuilder;
import ro.isdc.wro.model.resource.util.MD5HashBuilder;
import ro.isdc.wro.util.WroTestUtils;
import ro.isdc.wro.util.io.UnclosableBufferedInputStream;


/**
 * TestWroManager.java.
 *
 * @author Alex Objelean
 * @created Created on Nov 3, 2008
 */
public class TestWroManager {
  private static final Logger LOG = LoggerFactory.getLogger(TestWroManager.class);
  private WroManager manager;

  @Before
  public void setUp() {
    final Context context = Context.webContext(Mockito.mock(HttpServletRequest.class),
        Mockito.mock(HttpServletResponse.class, Mockito.RETURNS_DEEP_STUBS), Mockito.mock(FilterConfig.class));
    Context.set(context, newConfigWithUpdatePeriodValue(0));
    final WroManagerFactory factory = new ServletContextAwareWroManagerFactory();
    manager = factory.getInstance();
    manager.setModelFactory(getValidModelFactory());
  }

  @After
  public void tearDown() {
    manager.destroy();
    Context.unset();
  }

  @Test
  public void testNoProcessorWroManagerFactory()
      throws IOException {
    final WroManagerFactory factory = new NoProcessorsWroManagerFactory();
    manager = factory.getInstance();
    manager.setModelFactory(getValidModelFactory());
    final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    final HttpServletResponse response = Context.get().getResponse();

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    Mockito.when(response.getOutputStream()).thenReturn(new DelegatingServletOutputStream(out));
    Mockito.when(request.getRequestURI()).thenReturn("/app/g1.css");

    Context.set(Context.webContext(request, response, Mockito.mock(FilterConfig.class)));

    manager.process();
    // compare written bytes to output stream with the content from specified css.
    WroTestUtils.compare(WroTestUtils.getInputStream("classpath:ro/isdc/wro/manager/noProcessorsResult.css"),
        new ByteArrayInputStream(out.toByteArray()));
  }

  /**
   * This test actually works, but it is ignored because of encoding issues (I was unable to find a way to specify the
   * encoding in pom.xml).
   */
  @Ignore
  @Test
  public void testProcessingResourceWithChineseEncoding()
      throws IOException {
    genericProcessAndCompare("/chinese.js", "classpath:ro/isdc/wro/manager/chinese-output.js");
  }

  @Test
  public void testProcessingResourceWithSpecialCharacters()
      throws IOException {
    genericProcessAndCompare("/specialCharacters.js", "classpath:ro/isdc/wro/manager/specialCharacters-output.js");
  }

  @Test
  public void testDuplicatedResourcesShouldBeSkipped()
      throws IOException {
    genericProcessAndCompare("/repeatedResources.js", "classpath:ro/isdc/wro/manager/repeated-out.js");
  }

  @Test
  public void testWildcardDuplicatedResourcesShouldBeSkiped()
      throws IOException {
    genericProcessAndCompare("/wildcardRepeatedResources.js", "classpath:ro/isdc/wro/manager/wildcardRepeated-out.js");
  }

  @Test
  public void testMinimizeAttributeIsFalseOnResource()
      throws IOException {
    genericProcessAndCompare("/resourceMinimizeFalse.js", "classpath:ro/isdc/wro/manager/sample.js");
  }

  @Test
  public void testMinimizeAttributeIsTrueOnResource()
      throws IOException {
    genericProcessAndCompare("/resourceMinimizeTrue.js", "classpath:ro/isdc/wro/manager/sample.min.js");
  }

//  @Test
//  public void testChineseEncoding()
//      throws IOException {
//    genericProcessAndCompare("/chineseEncoding.js", "classpath:ro/isdc/wro/manager/wildcardRepeated-out.js");
//  }

  @Test
  public void testWildcardGroupResources()
      throws IOException {
    genericProcessAndCompare("/wildcardResources.js", "classpath:ro/isdc/wro/manager/wildcard-out.js");
  }


  /**
   * Perform a processing on a group extracted from requestUri and compares with the expectedResourceUri content.
   *
   * @param requestUri contains the group name to process.
   * @param expectedResourceUri the uri of the resource which has the expected content.
   */
  private void genericProcessAndCompare(final String requestUri, final String expectedResourceUri)
      throws IOException, FileNotFoundException {
    final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    final HttpServletResponse response = Context.get().getResponse();

    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    Mockito.when(response.getOutputStream()).thenReturn(new DelegatingServletOutputStream(out));
    Mockito.when(request.getRequestURI()).thenReturn(requestUri);

    Context.set(Context.webContext(request, response, Mockito.mock(FilterConfig.class)));

    manager.process();

    // compare written bytes to output stream with the content from specified css.
    final InputStream expectedInputStream = new UnclosableBufferedInputStream(
        WroTestUtils.getInputStream(expectedResourceUri));
    final InputStream actualInputStream = new BufferedInputStream(new ByteArrayInputStream(out.toByteArray()));
    expectedInputStream.reset();
    WroTestUtils.compare(expectedInputStream, actualInputStream);
    expectedInputStream.close();
    actualInputStream.close();
  }

  @Test
  public void processValidModel()
      throws IOException {
    final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    final HttpServletResponse response = Mockito.mock(HttpServletResponse.class, Mockito.RETURNS_DEEP_STUBS);
    Mockito.when(request.getRequestURI()).thenReturn("/app/g1.css");

    //Test also that ETag header value contains quotes
    Mockito.doAnswer(new Answer<Void>() {
      public Void answer(final InvocationOnMock invocation)
        throws Throwable {
        LOG.debug("Header: " + Arrays.toString(invocation.getArguments()));
        final Object[] arguments = invocation.getArguments();
        if (HttpHeader.ETAG.toString().equals(arguments[0])) {
          final String etagHeaderValue = (String) arguments[1];
          Assert.assertTrue(etagHeaderValue.matches("\".*?\""));
        }
        return null;
      }
    }).when(response).setHeader(Mockito.anyString(), Mockito.anyString());

    Context.set(Context.webContext(request, response, Mockito.mock(FilterConfig.class)));

    manager.process();

  }

  @Test
  public void testReloadCacheCall()
      throws IOException {
    final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getRequestURI()).thenReturn(WroManager.API_RELOAD_CACHE);

    Context.set(Context.webContext(request, Mockito.mock(HttpServletResponse.class, Mockito.RETURNS_DEEP_STUBS),
        Mockito.mock(FilterConfig.class)));
    manager.process();
  }

  @Test
  public void testManagerWithSchedulerAndUpdatePeriodSet()
      throws Exception {
    final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getRequestURI()).thenReturn("/app/g1.css");
    final Context context = Context.webContext(request,
        Mockito.mock(HttpServletResponse.class, Mockito.RETURNS_DEEP_STUBS), Mockito.mock(FilterConfig.class));
    final WroConfiguration config = new WroConfiguration();
    // make it run each 10 millisecond
    config.setModelUpdatePeriod(10);
    config.setCacheUpdatePeriod(10);
    Context.set(context, config);
    manager.process();
    // let scheduler run a while
    Thread.sleep(100);
  }

  @Test
  public void testReloadModelCall()
      throws IOException {
    final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getRequestURI()).thenReturn(WroManager.API_RELOAD_MODEL);

    Context.set(Context.webContext(request, Mockito.mock(HttpServletResponse.class, Mockito.RETURNS_DEEP_STUBS),
        Mockito.mock(FilterConfig.class)));
    manager.process();
  }

  /**
   * @return a {@link XmlModelFactory} pointing to a valid config resource.
   */
  private XmlModelFactory getValidModelFactory() {
    return new XmlModelFactory() {
      @Override
      public InputStream getConfigResourceAsStream() {
        return TestWroManager.class.getResourceAsStream("wro.xml");
      }
    };
  }

  /**
   * Test how manager behaves when the update period value is greater than zero and the scheduler starts.
   *
   * @throws Exception
   */
  @Test
  public void testManagerWhenSchedulerIsStarted()
      throws Exception {
    newConfigWithUpdatePeriodValue(1);
    final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    final HttpServletResponse response = Context.get().getResponse();
    Mockito.when(request.getRequestURI()).thenReturn("/app/g1.css");

    Context.set(Context.webContext(request, response, Mockito.mock(FilterConfig.class)));

    manager.process();
    // allow thread to do its job
    Thread.sleep(500);
  }

  @Test(expected = UnauthorizedRequestException.class)
  public void testProxyUnauthorizedRequest()
      throws Exception {
    processProxyWithResourceId("test");
  }

  private void processProxyWithResourceId(final String resourceId)
      throws IOException {
    final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getParameter(CssUrlRewritingProcessor.PARAM_RESOURCE_ID)).thenReturn(resourceId);
    Mockito.when(request.getRequestURI()).thenReturn(
        CssUrlRewritingProcessor.PATH_RESOURCES + "?" + CssUrlRewritingProcessor.PARAM_RESOURCE_ID + "=" + resourceId);

    Context.set(Context.webContext(request, Mockito.mock(HttpServletResponse.class, Mockito.RETURNS_DEEP_STUBS),
        Mockito.mock(FilterConfig.class)));
    manager.process();
  }

  /**
   * Initialize {@link WroConfiguration} object with cacheUpdatePeriod & modelUpdatePeriod equal with provided argument.
   */
  private WroConfiguration newConfigWithUpdatePeriodValue(final long periodValue) {
    final WroConfiguration config = new WroConfiguration();
    config.setCacheUpdatePeriod(periodValue);
    config.setModelUpdatePeriod(periodValue);
    return config;
  }

  @Test
  public void testCRC32Fingerprint()
      throws Exception {
    manager.setHashBuilder(new CRC32HashBuilder());
    final String path = manager.encodeVersionIntoGroupPath("g3", ResourceType.CSS, true);
    Assert.assertEquals("daa1bb3c/g3.css?minimize=true", path);
  }

  @Test
  public void testMD5Fingerprint()
      throws Exception {
    manager.setHashBuilder(new MD5HashBuilder());
    final String path = manager.encodeVersionIntoGroupPath("g3", ResourceType.CSS, true);
    Assert.assertEquals("42b98f2980dc1366cf1d2677d4891eda/g3.css?minimize=true", path);
  }
}
