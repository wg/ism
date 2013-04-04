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
import org.infinispan.notifications.cachelistener.event.*;

import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.http.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Jetty {@link SessionManager} that stores sessions in an Infinispan
 * distributed {@link Cache}.
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
    private TimeUnit maxIdleUnit;

    private InfinispanSessionCookieConfig cookieConfig;
    private String sessionIdPathParameterName = __DefaultSessionIdPathParameterName;
    private String sessionIdPathParameterNamePrefix = ";"+ sessionIdPathParameterName + "=";
    private boolean checkRemoteSessionId;

    protected Object listeners;
    protected Object attributeListeners;

    /**
     * Create a new instance.
     *
     * @param   cache   The cache to manage sessions in.
     */
    public InfinispanSessionManager(Cache<String, InfinispanHttpSession> cache) {
        this.maxIdleUnit = TimeUnit.SECONDS;
        this.idManager = new InfinispanSessionIdManager(cache, maxIdleUnit);
        this.cache = cache;

        cookieConfig = new InfinispanSessionCookieConfig();
        cookieConfig.setName(__DefaultSessionCookie);
        cookieConfig.setDomain(__DefaultSessionDomain);
        cookieConfig.setPath(null);
        cookieConfig.setHttpOnly(false);
        cookieConfig.setSecure(false);

        cache.addListener(this);
    }

    @Override
    public HttpSession getHttpSession(String id) {
        return cache.get(id);
    }

    @Override
    public HttpSession newHttpSession(HttpServletRequest request) {
        String id = idManager.newSessionId(request, -1L);
        InfinispanHttpSession session = new InfinispanHttpSession(id, maxIdleTime);
        session.restore(cache, context);

        cache.put(id, session, -1, maxIdleUnit, maxIdleTime, maxIdleUnit);

        return session;
    }

    @Override
    public boolean getHttpOnly() {
        return cookieConfig.httpOnly;
    }

    @Override
    public int getMaxInactiveInterval() {
        return maxIdleTime;
    }

    @Override
    public void setMaxInactiveInterval(int interval) {
        this.maxIdleTime = interval;
    }

    public void setMaxInactiveInterval(int interval, TimeUnit unit) {
        this.maxIdleTime = interval;
        this.maxIdleUnit = unit;
    }

    @Override
    public void setSessionHandler(SessionHandler handler) {
        this.handler = handler;
    }

    @Override
    public void addEventListener(EventListener listener) {
        if (listener instanceof HttpSessionListener)
            listeners = LazyList.add(listeners, listener);
        if (listener instanceof HttpSessionAttributeListener)
            attributeListeners = LazyList.add(attributeListeners, listener);
    }

    @Override
    public void removeEventListener(EventListener listener) {
        if (listener instanceof HttpSessionListener)
            listeners = LazyList.remove(listeners, listener);
        if (listener instanceof HttpSessionAttributeListener)
            attributeListeners = LazyList.remove(attributeListeners, listener);
    }

    @Override
    public void clearEventListeners() {
        listeners = null;
        attributeListeners = null;
    }

    @Override
    public HttpCookie getSessionCookie(HttpSession session, String contextPath, boolean requestIsSecure) {
        String path = (cookieConfig.path == null) ? contextPath : cookieConfig.path;

        HttpCookie cookie = new HttpCookie(
            cookieConfig.name,
            session.getId(),
            cookieConfig.domain,
            (path == null || path.isEmpty()) ? "/" : path,
            cookieConfig.maxAge,
            cookieConfig.httpOnly,
            requestIsSecure && cookieConfig.secure);

        return cookie;
    }

    @Override
    public SessionIdManager getSessionIdManager() {
        return idManager;
    }

    @Override
    public void setSessionIdManager(SessionIdManager idManager) {
        this.idManager = idManager;
    }

    @Override
    public boolean isValid(HttpSession session) {
        return ((InfinispanHttpSession) session).isValid();
    }

    @Override
    public String getNodeId(HttpSession session) {
        return session.getId();
    }

    @Override
    public String getClusterId(HttpSession session) {
        return session.getId();
    }

    @Override
    public HttpCookie access(HttpSession httpSession, boolean secure) {
        HttpCookie cookie = null;

        InfinispanHttpSession session = (InfinispanHttpSession) httpSession;
        long now = System.currentTimeMillis();

        session.access(now);
        long cookieCreatedAt = session.getCookieCreatedAt();

        if (cookieConfig.maxAge > 0 && now >= cookieCreatedAt) {
            session.setCookieCreatedAt(now);
            String contextPath = (context == null) ? "/" : context.getContextPath();
            cookie = getSessionCookie(session, contextPath, secure);
        }

        return cookie;
    }

    @Override
    public void complete(HttpSession httpSession) {
        InfinispanHttpSession session = (InfinispanHttpSession) httpSession;
        if (session.isValid() && session.isModified()) {
            long maxIdleTime = session.getMaxInactiveInterval();
            cache.replace(session.getId(), session, -1, TimeUnit.SECONDS, maxIdleTime, TimeUnit.SECONDS);
        }
    }

    @Override
    public void setSessionIdPathParameterName(String sessionIdPathParameterName) {
        this.sessionIdPathParameterName = sessionIdPathParameterName;
    }

    @Override
    public String getSessionIdPathParameterName() {
        return sessionIdPathParameterName;
    }

    @Override
    public String getSessionIdPathParameterNamePrefix() {
        return sessionIdPathParameterNamePrefix;
    }

    @Override
    public boolean isUsingCookies() {
        return true;
    }

    @Override
    public boolean isUsingURLs() {
        return false;
    }

    @Override
    public boolean isCheckingRemoteSessionIdEncoding() {
        return checkRemoteSessionId;
    }

    @Override
    public void setCheckingRemoteSessionIdEncoding(boolean remote) {
        this.checkRemoteSessionId = remote;
    }

    @Override
    public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
        return EnumSet.of(SessionTrackingMode.COOKIE);
    }

    @Override
    public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
        return EnumSet.of(SessionTrackingMode.COOKIE);
    }

    @Override
    public void setSessionTrackingModes(Set<SessionTrackingMode> modes) {
        if (modes.size() != 1 || !modes.contains(SessionTrackingMode.COOKIE)) {
            throw new UnsupportedOperationException("Only cookie sessions are supported");
        }
    }

    @Override
    public SessionCookieConfig getSessionCookieConfig() {
        return cookieConfig;
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
    public void cacheEntryCreated(CacheEntryCreatedEvent<String, InfinispanHttpSession> e) {
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
    public void cacheEntryRemoved(CacheEntryRemovedEvent<String, InfinispanHttpSession> e) {
        InfinispanHttpSession session = e.getValue();
        if (session != null && listeners != null) {
            HttpSessionEvent event = new HttpSessionEvent(session);
            for (int i = 0; i < LazyList.size(listeners); i++) {
                ((HttpSessionListener) LazyList.get(listeners, i)).sessionDestroyed(event);
            }
        }
    }

    @CacheEntriesEvicted
    public void cacheEntriesEvicted(CacheEntriesEvictedEvent<String, InfinispanHttpSession> e) {
        if (listeners != null) {
            for (InfinispanHttpSession session : e.getEntries().values()) {
                HttpSessionEvent event = new HttpSessionEvent(session);
                for (int i = 0; i < LazyList.size(listeners); i++) {
                    ((HttpSessionListener) LazyList.get(listeners, i)).sessionDestroyed(event);
                }
            }
        }
    }

    /** Obsolete and deprecated methods */

    @Deprecated
    public SessionIdManager getMetaManager() {
        throw new UnsupportedOperationException("This method is deprecated");
    }
}
