package com.pos.common.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.pos.auditoria.web.BitacoraInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final BitacoraInterceptor bitacoraInterceptor;
    private final com.pos.common.access.DataReadPolicy policy;
    private final com.pos.common.access.EmpresaAccess access;

    public WebConfig(BitacoraInterceptor bitacoraInterceptor,com.pos.common.access.DataReadPolicy policy,com.pos.common.access.EmpresaAccess access) {
        this.bitacoraInterceptor = bitacoraInterceptor; this.policy=policy;this.access=access;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
            @Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response,Object handler) {
                if(!"true".equals(request.getHeader("X-Report-Export"))) return true;
                var match=java.util.regex.Pattern.compile("^/api/reportes/(\\d+)(?:/|$)").matcher(request.getRequestURI());
                if(!match.find()) throw com.pos.common.exception.ApiException.badRequest("Canal de exportación no admitido.");
                Integer company=Integer.valueOf(match.group(1));var user=com.pos.common.security.SecurityUtils.currentUser();
                if(request.getRequestURI().contains("/qbe/")) access.requirePermiso(user,company,"report:export");
                else policy.require(user,company,null,"report:export");
                response.setHeader("Cache-Control","no-store"); return true;
            }
        }).addPathPatterns("/api/reportes/**");
        registry.addInterceptor(bitacoraInterceptor).addPathPatterns("/api/**");
    }

}
