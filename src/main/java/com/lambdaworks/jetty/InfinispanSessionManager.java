// Copyright (C) 2010 - Will Glozer.  All rights reserved.

package com.lambdaworks.jetty;

import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.server.SessionIdManager;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.LazyList;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.infinispan.Cache;
import org.infinispan.notifications.Listener;
import org.infinispan.notifications.cachelistener.annotation.*;
import org.infinispan.notifications.cachelistener.event.CacheEntryCreatedEvent;
import org.infinispan.notifications.cachelistener.event.CacheEntryEvent;

import javax.servlet.http.*;
import java.util.EventListener;
import java.util.concurrent.TimeUnit;

/**
 * Jetty 7.x {@link SessionManager} that stores sessions in an
 * <a href="http://www.jboss.org/infinispan">Infinispan</a> distributed
 * {@link Cache}.
 *
 * All objects stored in the {@link InfinispanHttpSession} must implement
 * {@link java.io.Serializable} and also have a no-arg constructor.
 *
 * @author  Will Glozer
 */
@Listener(sync = false)
public class InfinispanSessionManager extends AbstractLifeCycle implements SessionManager {
    private Cache<String, InfinispanHttpSession> cache;

    private SessionIdManager idManager;
    private SessionHandler handler;
    private ContextHandler.Context context;

    private int maxIdleTime;
    private boolean secureCookies = false;
    private boolean httpOnly = false;
    private String sessionCookieName = __DefaultSessionCookie;
    private String sessionIdPathParameterName = __DefaultSessionIdPathParameterName;
    private String sessionIdPathParameterNamePrefix = ";"+ sessionIdPathParameterName + "=";
    private String sessionDomain = __DefaultSessionDomain;
    private String sessionPath;
    private int maxCookieAge = -1;
    private boolean checkRemoteSessionId;

    protected Object listeners;
    protected Object attributeListeners;

    /**
     * Create a new instance.
     *
     * @param   cache   The cache to manage sessions in.
     */
    public InfinispanSessionManager(Cache<String, InfinispanHttpSession> cache) {
        this.idManager = new InfinispanSessionIdManager(cache);
        this.cache = cache;
        cache.addListener(this);
    }

    public HttpSession getHttpSession(String id) {
        return cache.get(id);
    }

    public HttpSession newHttpSession(HttpServletRequest request) {
        String id = idManager.newSessionId(request, -1L);
        InfinispanHttpSession session = new InfinispanHttpSession(id, maxIdleTime);
        session.restore(cache, context);

        cache.put(id, session, -1, TimeUnit.SECONDS, maxIdleTime, TimeUnit.SECONDS);

        return session;
    }

    public boolean getSecureCookies() {
        return secureCookies;
    }

    public boolean getHttpOnly() {
        return httpOnly;
    }

    public int getMaxInactiveInterval() {
        return maxIdleTime;
    }

    public void setMaxInactiveInterval(int interval) {
        this.maxIdleTime = interval;
    }

    public void setSessionHandler(SessionHandler handler) {
        this.handler = handler;
    }

    public void addEventListener(EventListener listener) {
        if (listener instanceof HttpSessionListener)
            listeners = LazyList.add(listeners, listener);
        if (listener instanceof HttpSessionAttributeListener)
            attributeListeners = LazyList.add(attributeListeners, listener);
    }

    public void removeEventListener(EventListener listener) {
        if (listener instanceof HttpSessionListener)
            listeners = LazyList.remove(listeners, listener);
        if (listener instanceof HttpSessionAttributeListener)
            attributeListeners = LazyList.remove(attributeListeners, listener);
    }

    public void clearEventListeners() {
        listeners = null;
        attributeListeners = null;
    }

    public HttpCookie getSessionCookie(HttpSession session, String contextPath, boolean requestIsSecure) {
        String path = (sessionPath == null) ? contextPath : sessionPath;

        HttpCookie cookie = new HttpCookie(
            sessionCookieName,
            session.getId(),
            sessionDomain,
            (path == null || path.isEmpty()) ? "/" : path,
            maxCookieAge,
            httpOnly,
            requestIsSecure && secureCookies);

        return cookie;
    }

    public SessionIdManager getIdManager() {
        return idManager;
    }

    public void setIdManager(SessionIdManager idManager) {
        this.idManager = idManager;
    }

    public boolean isValid(HttpSession session) {
        return ((InfinispanHttpSession) session).isValid();
    }

    public String getNodeId(HttpSession session) {
        return session.getId();
    }

    public String getClusterId(HttpSession session) {
        return session.getId();
    }

    public HttpCookie access(HttpSession httpSession, boolean secure) {
        HttpCookie cookie = null;

        InfinispanHttpSession session = (InfinispanHttpSession) httpSession;
        long now = System.currentTimeMillis();

        session.access(now);
        long cookieCreatedAt = session.getCookieCreatedAt();

        if (getMaxCookieAge() > 0 && now >= cookieCreatedAt) {
            session.setCookieCreatedAt(now);
            String contextPath = (context == null) ? "/" : context.getContextPath();
            cookie = getSessionCookie(session, contextPath, secure);
        }

        return cookie;
    }

    public void complete(HttpSession httpSession) {
        InfinispanHttpSession session = (InfinispanHttpSession) httpSession;
        if (session.isValid() && session.isModified()) {
            long maxIdleTime = session.getMaxInactiveInterval();
            cache.replace(session.getId(), session, -1, TimeUnit.SECONDS, maxIdleTime, TimeUnit.SECONDS);
        }
    }

    public void setSessionCookie(String sessionCookieName) {
        this.sessionCookieName = sessionCookieName;
    }

    public String getSessionCookie() {
        return sessionCookieName;
    }

    public void setSessionIdPathParameterName(String sessionIdPathParameterName) {
        this.sessionIdPathParameterName = sessionIdPathParameterName;
    }

    public String getSessionIdPathParameterName() {
        return sessionIdPathParameterName;
    }

    public String getSessionIdPathParameterNamePrefix() {
        return sessionIdPathParameterNamePrefix;
    }

    public void setSessionDomain(String sessionDomain) {
        this.sessionDomain = sessionDomain;
    }

    public String getSessionDomain() {
        return sessionDomain;
    }

    public void setSessionPath(String sessionPath) {
        this.sessionPath = sessionPath;
    }

    public String getSessionPath() {
        return sessionPath;
    }

    public void setMaxCookieAge(int maxCookieAge) {
        this.maxCookieAge = maxCookieAge;
    }

    public int getMaxCookieAge() {
        return maxCookieAge;
    }

    public boolean isUsingCookies() {
        return true;
    }

    public boolean isCheckingRemoteSessionIdEncoding() {
        return checkRemoteSessionId;
    }

    public void setCheckingRemoteSessionIdEncoding(boolean remote) {
        this.checkRemoteSessionId = remote;
    }

    @Override
    public void doStart() throws Exception {
        context = ContextHandler.getCurrentContext();
        if (!idManager.isStarted()) {
            idManager.start();
        }
        super.doStart();
    }

    @CacheEntryCreated
    public void cacheEntryCreated(CacheEntryCreatedEvent e) {
        InfinispanHttpSession session = cache.get(e.getKey());

        if (session != null) {
            if (!e.isOriginLocal()) session.restore(cache, context);

            if (listeners != null) {
                HttpSessionEvent event = new HttpSessionEvent(session);
                for (int i = 0; i < LazyList.size(listeners); i++) {
                    ((HttpSessionListener) LazyList.get(listeners, i)).sessionCreated(event);
                }
            }
        }
    }

    @CacheEntryRemoved
    @CacheEntryEvicted
    public void cacheEntryRemoved(CacheEntryEvent e) {
        InfinispanHttpSession session = cache.get(e.getKey());
        if (listeners != null) {
             HttpSessionEvent event = new HttpSessionEvent(session);
             for (int i = 0; i < LazyList.size(listeners); i++) {
                 ((HttpSessionListener) LazyList.get(listeners, i)).sessionDestroyed(event);
             }
         }
     }

    /** Obsolete and deprecated methods */

    /**
     * @deprecated
     */
    public SessionIdManager getMetaManager() {
        throw new UnsupportedOperationException("This method is deprecated");
    }
}
