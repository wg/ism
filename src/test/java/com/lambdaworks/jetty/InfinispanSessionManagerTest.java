// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.jetty;

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.server.Request;
import org.infinispan.Cache;
import org.infinispan.manager.CacheContainer;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.*;

import javax.servlet.http.*;

import java.util.concurrent.*;

import static org.junit.Assert.*;

public class InfinispanSessionManagerTest {
    private CacheContainer cacheManager;
    private Cache<String, InfinispanHttpSession> cache;
    private InfinispanSessionManager ism;

    @Before
    public void setUp() throws Exception {
        cacheManager = new DefaultCacheManager();
        cache = cacheManager.getCache("cache");

        ism = new InfinispanSessionManager(cache);
        ism.setMaxInactiveInterval(100);

        ism.start();
    }

    @After
    public void tearDown() throws Exception {
        cacheManager.stop();
    }

    @Test
    public void newHttpSession() throws Exception {
        HttpSession session = ism.newHttpSession(req());
        String id = session.getId();
        assertEquals(session, cache.get(id));
        assertTrue(session.isNew());
    }

    @Test
    public void getHttpSession() throws Exception {
        HttpSession session = ism.newHttpSession(req());
        String id = session.getId();
        assertEquals(session, ism.getHttpSession(id));
    }

    @Test
    public void getSessionCookie() throws Exception {
        HttpSession session = ism.newHttpSession(req());
        HttpCookie cookie = ism.getSessionCookie(session, "/", false);
        assertEquals(ism.getSessionCookieConfig().getName(), cookie.getName());
        assertEquals(session.getId(), cookie.getValue());
        assertEquals(ism.getSessionCookieConfig().getDomain(), cookie.getDomain());
        assertEquals("/", cookie.getPath());
        assertEquals(ism.getSessionCookieConfig().getMaxAge(), cookie.getMaxAge());
        assertEquals(ism.getHttpOnly(), cookie.isHttpOnly());
        assertFalse(cookie.isSecure());
    }

    @Test
    public void sessionExpires() throws Exception {
        ism.setMaxInactiveInterval(1, TimeUnit.MICROSECONDS);
        HttpSession session = ism.newHttpSession(req());
        Thread.sleep(1);
        assertNull(ism.getHttpSession(session.getId()));
    }

    @Test(timeout = 100)
    public void sessionDestroyedViaInvalidate() throws Exception {
        HttpSessionAdapter adapter = new HttpSessionAdapter();
        ism.addEventListener(adapter);
        HttpSession session = ism.newHttpSession(req());
        assertEquals(session, adapter.created.take());
        session.invalidate();
        assertEquals(session, adapter.destroyed.take());
    }

    public HttpServletRequest req() {
        return new Request();
    }

    private static class HttpSessionAdapter implements HttpSessionListener {
        BlockingQueue<HttpSession> created = new LinkedBlockingQueue<HttpSession>();
        BlockingQueue<HttpSession> destroyed = new LinkedBlockingQueue<HttpSession>();

        @Override
        public void sessionCreated(HttpSessionEvent e) {
            created.add(e.getSession());
        }

        @Override
        public void sessionDestroyed(HttpSessionEvent e) {
            destroyed.add(e.getSession());
        }
    }
}
