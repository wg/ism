// Copyright (C) 2010 - Will Glozer.  All rights reserved.

package com.lambdaworks.jetty;

import org.eclipse.jetty.server.session.AbstractSessionIdManager;
import org.infinispan.Cache;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.util.concurrent.TimeUnit;

/**
 * Jetty 7.x {@link org.eclipse.jetty.server.SessionIdManager} that checks for
 * existing sessions in an <a href="http://www.jboss.org/infinispan">Infinispan</a>
 * distributed {@link Cache}.
 *
 * @author  Will Glozer
 */
public class InfinispanSessionIdManager extends AbstractSessionIdManager {
    private Cache<String, InfinispanHttpSession> cache;

    /**
     * Create a new instance.
     *
     * @param   cache   The cache to manage session IDs in.
     */
    public InfinispanSessionIdManager(Cache<String, InfinispanHttpSession> cache) {
        this.cache = cache;
    }

    public boolean idInUse(String id) {
        return id != null && cache.containsKey(id);
    }

    public void addSession(HttpSession httpSession) {
        InfinispanHttpSession session = (InfinispanHttpSession) httpSession;
        cache.put(session.getId(), session, -1, TimeUnit.SECONDS, session.getMaxInactiveInterval(), TimeUnit.SECONDS);
    }

    public void removeSession(HttpSession httpSession) {
        cache.remove(httpSession.getId());
    }

    public void invalidateAll(String id) {
        InfinispanHttpSession session = cache.get(id);
        if (session != null) {
            session.invalidate();
        }
    }

    public String getClusterId(String id) {
        return id;
    }

    public String getNodeId(String id, HttpServletRequest request) {
        return id;
    }
}
