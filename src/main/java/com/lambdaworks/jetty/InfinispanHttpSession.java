// Copyright (C) 2010 - Will Glozer.  All rights reserved.

package com.lambdaworks.jetty;

import org.infinispan.Cache;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionContext;
import java.io.Serializable;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link HttpSession} implementation designed to be replicated in an
 * <a href="http://www.jboss.org/infinispan">Infinispan</a> distributed
 * {@link Cache}.
 *
 * @author  Will Glozer
 */
public class InfinispanHttpSession implements HttpSession, Serializable {
    static final long serialVersionUID = 542979088595232166L;

    private transient Cache<String, InfinispanHttpSession> cache;
    private transient ServletContext context;
    private transient boolean isModified;

    private String id;
    private long createdAt;
    private long lastAccessedAt;
    private long cookieCreatedAt;
    private int maxIdleTime;
    private Map<String, Object> attributes;
    private boolean isValid;

    /**
     * Create a new instance.
     *
     * @param   id              Session ID.
     * @param   maxIdleTime     Maximum idle time before eviction.
     */
    public InfinispanHttpSession(String id, int maxIdleTime) {
        this.id = id;
        this.createdAt = System.currentTimeMillis();
        this.lastAccessedAt = this.createdAt;
        this.cookieCreatedAt = this.createdAt;
        this.maxIdleTime = maxIdleTime;
        this.attributes = new ConcurrentHashMap<String, Object>();
        this.isValid = true;
    }

    public long getCreationTime() {
        return createdAt;
    }

    public String getId() {
        return id;
    }

    public long getLastAccessedTime() {
        return lastAccessedAt;
    }

    public ServletContext getServletContext() {
        return context;
    }

    public void setMaxInactiveInterval(int interval) {
        maxIdleTime = interval;
        isModified = true;
    }

    public int getMaxInactiveInterval() {
        return maxIdleTime;
    }

    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    public Enumeration getAttributeNames() {
        return Collections.enumeration(attributes.keySet());
    }

    public void setAttribute(String name, Object value) {
        Object oldValue = attributes.put(name, value);
        isModified = true;
        bind(name, value);
        unbind(name, oldValue);
    }

    public void removeAttribute(String name) {
        Object value = attributes.remove(name);
        isModified = true;
        unbind(name, value);
    }

    public void invalidate() {
        Iterator<Map.Entry<String, Object>> i = attributes.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry<String, Object> entry = i.next();
            unbind(entry.getKey(), entry.getValue());
            i.remove();
        }

        isModified = true;
        isValid = false;

        cache.remove(id);
    }

    public boolean isNew() {
        return createdAt == lastAccessedAt;
    }

    /** Internal methods **/

    /**
     * Restore transient fields to their server-local values.
     *
     * @param   cache   The {@link Cache} this jetty is associated with.
     * @param   context The {@link ServletContext} this jetty is associated with.
     */
    public void restore(Cache<String, InfinispanHttpSession> cache, ServletContext context) {
        this.cache = cache;
        this.context = context;
    }

    boolean isValid() {
        return isValid;
    }

    boolean isModified() {
        return isModified;
    }

    long getCookieCreatedAt() {
        return cookieCreatedAt;
    }

    void setCookieCreatedAt(long cookieCreatedAt) {
        this.cookieCreatedAt = cookieCreatedAt;
    }

    void access(long lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
        this.isModified = false;
    }

    protected void bind(String name, Object value) {
        if (value instanceof HttpSessionBindingListener) {
            HttpSessionBindingEvent event = new HttpSessionBindingEvent(this, name);
            ((HttpSessionBindingListener) value).valueBound(event);
        }
    }

    protected void unbind(String name, Object value) {
        if (value instanceof HttpSessionBindingListener) {
            HttpSessionBindingEvent event = new HttpSessionBindingEvent(this, name);
            ((HttpSessionBindingListener) value).valueUnbound(event);
        }
    }

    /** Obsolete and deprecated methods **/

    /**
     * @deprecated
     */
    public Object getValue(String name) {
        throw new UnsupportedOperationException("This method is deprecated");
    }

    /**
     * @deprecated
     */
    public void removeValue(String name) {
        throw new UnsupportedOperationException("This method is deprecated");
    }

    /**
     * @deprecated
     */
    public String[] getValueNames() {
        throw new UnsupportedOperationException("This method is deprecated");
    }

    /**
     * @deprecated
     */
    public void putValue(String name, Object value) {
        throw new UnsupportedOperationException("This method is deprecated");
    }

    /**
     * @deprecated
     */
    public HttpSessionContext getSessionContext() {
        throw new UnsupportedOperationException("This method is deprecated");
    }

}
