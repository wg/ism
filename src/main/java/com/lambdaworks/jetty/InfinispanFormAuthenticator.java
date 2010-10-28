// Copyright (C) 2010 - Will Glozer.  All rights reserved.

package com.lambdaworks.jetty;

import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.security.authentication.FormAuthenticator;
import org.eclipse.jetty.security.authentication.SessionAuthentication;
import org.eclipse.jetty.server.Authentication;
import org.eclipse.jetty.server.UserIdentity;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public class InfinispanFormAuthenticator extends FormAuthenticator {
    /**
     * Create a new instance.
     *
     * @param   login       Path to the login page.
     * @param   error       Path to the error page.
     * @param   dispatch    True to use dispatch, else redirect.
     */
    public InfinispanFormAuthenticator(String login, String error, boolean dispatch) {
        super(login, error, dispatch);
    }

    /**
     * On successful request validation replace the {@link SessionAuthentication} stored
     * in the current session with a {@link SerializableSessionAuthentication}.
     *
     * @param   req     Servlet request.
     * @param   res     Servlet response.
     *
     * @return  The authentication state.
     */
    @Override
    public Authentication validateRequest(ServletRequest req, ServletResponse res, boolean mandatory) throws ServerAuthException {
        Authentication formAuth = super.validateRequest(req, res, mandatory);

        HttpSession session = ((HttpServletRequest) req).getSession(false);

        if (formAuth instanceof FormAuthentication && session != null) {
            Authentication auth = (Authentication) session.getAttribute(SessionAuthentication.__J_AUTHENTICATED);
            if (auth instanceof SessionAuthentication) {
                SessionAuthentication sessionAuth = (SessionAuthentication) auth;
                String method         = sessionAuth.getAuthMethod();
                UserIdentity identity = sessionAuth.getUserIdentity();
                auth = new SerializableSessionAuthentication(method, identity);
                session.setAttribute(SessionAuthentication.__J_AUTHENTICATED, auth);
            }
        }

        return formAuth;
    }
}
