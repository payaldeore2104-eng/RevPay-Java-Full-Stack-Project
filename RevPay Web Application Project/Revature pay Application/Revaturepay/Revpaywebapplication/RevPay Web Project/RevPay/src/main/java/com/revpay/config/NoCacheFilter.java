package com.revpay.config;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

@Component
public class NoCacheFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse res = (HttpServletResponse) response;

        res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        res.setHeader("Pragma", "no-cache");
        res.setDateHeader("Expires", 0);

        chain.doFilter(request, response);
    }
}
