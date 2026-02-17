package com.odk.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.util.Enumeration;

@Component
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Logger les détails de la requête
        System.out.println("=== REQUEST DEBUG ===");
        System.out.println("Method: " + httpRequest.getMethod());
        System.out.println("URL: " + httpRequest.getRequestURL());
        System.out.println("URI: " + httpRequest.getRequestURI());
        System.out.println("Query: " + httpRequest.getQueryString());
        System.out.println("Remote Addr: " + httpRequest.getRemoteAddr());
        
        // Logger les headers
        System.out.println("Headers:");
        Enumeration<String> headerNames = httpRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            System.out.println("  " + headerName + ": " + httpRequest.getHeader(headerName));
        }
        
        // Logger les paramètres
        System.out.println("Parameters:");
        httpRequest.getParameterMap().forEach((key, values) -> {
            System.out.println("  " + key + ": " + String.join(", ", values));
        });
        
        System.out.println("===================");
        
        chain.doFilter(request, response);
    }
}
