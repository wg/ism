// Copyright (C) 2010 - Will Glozer.  All rights reserved.

package com.lambdaworks.jetty;

import org.eclipse.jetty.server.session.AbstractSessionIdManager;
import org.infinispan.Cache;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.util.concurrent.TimeUnit;

/**
 * Jetty {@link org.eclipse.jetty.server.SessionIdManager} that checks for
 * existing sessions in an Infinispan distributed {@link Cache}.
 *
 * @author  Will Glozer
 */
public class InfinispanSessionIdManager extends AbstractSessionIdManager {
    private Cache<String, InfinispanHttpSession> cache;
    private TimeUnit maxIdleUnit;

    /**
     * Create a new instance.
     *
     * @param   cache   The cache to manage session IDs in.
     */
    public InfinispanSessionIdManager(Cache<String, InfinispanHttpSession> cache, TimeUnit maxIdleUnit) {
        this.cache = cache;
        this.maxIdleUnit = maxIdleUnit;
    }

    @Override
    public boolean idInUse(String id) {
        return id != null && cache.containsKey(id);
    }

    @Override
    public void addSession(HttpSession httpSession) {
        InfinispanHttpSession session = (InfinispanHttpSession) httpSession;
        cache.put(session.getId(), session, -1, maxIdleUnit, session.getMaxInactiveInterval(), maxIdleUnit);
    }

    @Override
    public void removeSession(HttpSession httpSession) {
        cache.remove(httpSession.getId());
    }

    @Override
    public void invalidateAll(String id) {
        InfinispanHttpSession session = cache.get(id);
        if (session != null) {
            session.invalidate();
        }
    }

    @Override
    public String getClusterId(String id) {
        return id;
    }

    @Override
    public String getNodeId(String id, HttpServletRequest request) {
        return id;
    }
}
