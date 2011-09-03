// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.jetty;

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.server.Request;
import org.infinispan.Cache;
import org.infinispan.manager.CacheContainer;
import org.infinispan.manager.DefaultCacheManager;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        assertEquals(ism.getSessionCookie(), cookie.getName());
        assertEquals(session.getId(), cookie.getValue());
        assertEquals(ism.getSessionDomain(), cookie.getDomain());
        assertEquals("/", cookie.getPath());
        assertEquals(ism.getMaxCookieAge(), cookie.getMaxAge());
        assertEquals(ism.getHttpOnly(), cookie.isHttpOnly());
        assertFalse(cookie.isSecure());
    }

    public HttpServletRequest req() {
        return new Request();
    }
}
