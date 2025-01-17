/*
 * The MIT License
 *
 * Copyright (c) 2009-2024 PrimeTek Informatics
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.primefaces.renderkit;

import org.primefaces.clientwindow.PrimeClientWindow;
import org.primefaces.clientwindow.PrimeClientWindowUtils;
import org.primefaces.context.PrimeApplicationContext;
import org.primefaces.context.PrimeRequestContext;
import org.primefaces.util.FacetUtils;
import org.primefaces.util.LocaleUtils;
import org.primefaces.util.MapBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.el.ELContext;
import javax.el.ExpressionFactory;
import javax.el.ValueExpression;
import javax.faces.FacesException;
import javax.faces.application.ProjectStage;
import javax.faces.application.Resource;
import javax.faces.application.ResourceHandler;
import javax.faces.component.UIComponent;
import javax.faces.component.UIViewRoot;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import javax.faces.lifecycle.ClientWindow;
import javax.faces.render.Renderer;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * Renders head content based on the following order
 * - First Facet
 * - Theme CSS
 * - FontAwesome
 * - Middle Facet
 * - Registered Resources
 * - Client Validation Scripts
 * - Locales
 * - PF Client Side Settings
 * - PF Initialization Scripts
 * - Head Content
 * - Last Facet
 */
public class HeadRenderer extends Renderer {

    private static final Logger LOGGER = Logger.getLogger(HeadRenderer.class.getName());
    private static final String LIBRARY = "primefaces";

    private static final Map<String, String> THEME_MAPPING = MapBuilder.<String, String>builder()
            .put("saga", "saga-blue")
            .put("arya", "arya-blue")
            .put("vela", "vela-blue")
            .build();

    @Override
    public void encodeBegin(FacesContext context, UIComponent component) throws IOException {
        ResponseWriter writer = context.getResponseWriter();
        PrimeRequestContext requestContext = PrimeRequestContext.getCurrentInstance(context);
        PrimeApplicationContext applicationContext = requestContext.getApplicationContext();

        writer.startElement("head", component);
        writer.writeAttribute("id", component.getClientId(context), "id");

        //First facet
        UIComponent first = component.getFacet("first");
        if (FacetUtils.shouldRenderFacet(first)) {
            first.encodeAll(context);
        }

        //Theme
        String theme;
        String themeParamValue = applicationContext.getConfig().getTheme();

        if (themeParamValue != null) {
            ELContext elContext = context.getELContext();
            ExpressionFactory expressionFactory = context.getApplication().getExpressionFactory();
            ValueExpression ve = expressionFactory.createValueExpression(elContext, themeParamValue, String.class);

            theme = (String) ve.getValue(elContext);
        }
        else {
            theme = "saga-blue";     //default
        }

        if (theme != null && !"none".equals(theme)) {
            if (THEME_MAPPING.containsKey(theme)) {
                theme = THEME_MAPPING.get(theme);
            }

            encodeCSS(context, LIBRARY + "-" + theme, "theme.css");
        }

        //Icons
        if (applicationContext.getConfig().isPrimeIconsEnabled()) {
            encodeCSS(context, LIBRARY, "primeicons/primeicons.css");
        }

        //Middle facet
        UIComponent middle = component.getFacet("middle");
        if (FacetUtils.shouldRenderFacet(middle)) {
            middle.encodeAll(context);
        }

        //Registered Resources
        UIViewRoot viewRoot = context.getViewRoot();
        List<UIComponent> resources = viewRoot.getComponentResources(context, "head");
        for (int i = 0; i < resources.size(); i++) {
            UIComponent resource = resources.get(i);
            resource.encodeAll(context);
        }

        if (applicationContext.getConfig().isClientSideValidationEnabled()) {
            // moment is needed for Date validation
            encodeJS(context, LIBRARY, "moment/moment.js");

            // BV CSV is optional and must be enabled by config
            if (applicationContext.getConfig().isBeanValidationEnabled()) {
                encodeJS(context, LIBRARY, "validation/validation.bv.js");
            }
        }

        if (applicationContext.getConfig().isClientSideLocalizationEnabled()) {
            try {
                Locale locale = LocaleUtils.getCurrentLocale(context);
                encodeJS(context, LIBRARY, "locales/locale-" + locale.getLanguage() + ".js");
            }
            catch (FacesException e) {
                if (context.isProjectStage(ProjectStage.Development)) {
                    LOGGER.log(Level.WARNING,
                            "Failed to load client side locale.js. {0}", e.getMessage());
                }
            }
        }

        encodeSettingScripts(context, applicationContext, requestContext, writer);

        // encode initialization scripts
        encodeInitScripts(context, writer);
    }

    @Override
    public void encodeEnd(FacesContext context, UIComponent component) throws IOException {
        ResponseWriter writer = context.getResponseWriter();

        //Last facet
        UIComponent last = component.getFacet("last");
        if (FacetUtils.shouldRenderFacet(last)) {
            last.encodeAll(context);
        }

        writer.endElement("head");
    }

    protected void encodeCSS(FacesContext context, String library, String resource) throws IOException {
        ResourceHandler resourceHandler = context.getApplication().getResourceHandler();
        if (resourceHandler.isResourceRendered(context, resource, library)) {
            // resource already rendered, skip
            return;
        }

        ResponseWriter writer = context.getResponseWriter();
        ExternalContext externalContext = context.getExternalContext();

        Resource cssResource = resourceHandler.createResource(resource, library);
        if (cssResource == null) {
            throw new FacesException("Error loading CSS, cannot find \"" + resource + "\" resource of \"" + library + "\" library");
        }
        else {
            writer.startElement("link", null);
            writer.writeAttribute("type", "text/css", null);
            writer.writeAttribute("rel", "stylesheet", null);
            writer.writeAttribute("href", externalContext.encodeResourceURL(cssResource.getRequestPath()), null);
            writer.endElement("link");
        }
    }

    protected void encodeJS(FacesContext context, String library, String script) throws IOException {
        ResourceHandler resourceHandler = context.getApplication().getResourceHandler();
        if (resourceHandler.isResourceRendered(context, script, library)) {
            // resource already rendered, skip
            return;
        }

        ResponseWriter writer = context.getResponseWriter();
        ExternalContext externalContext = context.getExternalContext();
        Resource resource = resourceHandler.createResource(script, library);

        if (resource == null) {
            throw new FacesException("Error loading JavaScript, cannot find \"" + script + "\" resource of \"" + library + "\" library");
        }
        else {
            writer.startElement("script", null);
            writer.writeAttribute("src", externalContext.encodeResourceURL(resource.getRequestPath()), null);
            writer.endElement("script");
        }
    }

    protected void encodeSettingScripts(FacesContext context, PrimeApplicationContext applicationContext, PrimeRequestContext requestContext,
            ResponseWriter writer) throws IOException {

        ProjectStage projectStage = context.getApplication().getProjectStage();

        writer.startElement("script", null);
        RendererUtils.encodeScriptTypeIfNecessary(context);
        writer.write("if(window.PrimeFaces){");

        writer.write("PrimeFaces.settings.locale='" + LocaleUtils.getCurrentLocale(context) + "';");
        writer.write("PrimeFaces.settings.viewId='" + context.getViewRoot().getViewId() + "';");
        writer.write("PrimeFaces.settings.contextPath='" + context.getExternalContext().getRequestContextPath() + "';");

        writer.write("PrimeFaces.settings.cookiesSecure=" + (requestContext.isSecure() && applicationContext.getConfig().isCookiesSecure()) + ";");
        if (applicationContext.getConfig().getCookiesSameSite() != null) {
            writer.write("PrimeFaces.settings.cookiesSameSite='" + applicationContext.getConfig().getCookiesSameSite() + "';");
        }

        writer.write("PrimeFaces.settings.validateEmptyFields=" + applicationContext.getConfig().isValidateEmptyFields() + ";");
        writer.write("PrimeFaces.settings.considerEmptyStringNull=" + applicationContext.getConfig().isInterpretEmptyStringAsNull() + ";");

        if (applicationContext.getConfig().isEarlyPostParamEvaluation()) {
            writer.write("PrimeFaces.settings.earlyPostParamEvaluation=true;");
        }

        if (applicationContext.getConfig().isPartialSubmitEnabled()) {
            writer.write("PrimeFaces.settings.partialSubmit=true;");
        }

        if (projectStage != ProjectStage.Production) {
            writer.write("PrimeFaces.settings.projectStage='" + projectStage.toString() + "';");
        }

        if (context.getExternalContext().getClientWindow() != null) {
            ClientWindow clientWindow = context.getExternalContext().getClientWindow();
            if (clientWindow instanceof PrimeClientWindow) {

                boolean initialRedirect = false;

                Object cookie = PrimeClientWindowUtils.getInitialRedirectCookie(context, clientWindow.getId());
                if (cookie instanceof Cookie) {
                    Cookie servletCookie = (Cookie) cookie;
                    initialRedirect = true;

                    // expire/remove cookie
                    servletCookie.setMaxAge(0);
                    ((HttpServletResponse) context.getExternalContext().getResponse()).addCookie(servletCookie);
                }
                writer.write(
                        String.format("PrimeFaces.clientwindow.init('%s', %s);",
                                PrimeClientWindowUtils.secureWindowId(clientWindow.getId()),
                                initialRedirect));
            }
        }

        writer.write("}");
        writer.endElement("script");
    }

    protected void encodeInitScripts(FacesContext context, ResponseWriter writer) throws IOException {
        List<String> scripts = PrimeRequestContext.getCurrentInstance().getInitScriptsToExecute();

        if (!scripts.isEmpty()) {
            writer.startElement("script", null);
            RendererUtils.encodeScriptTypeIfNecessary(context);

            boolean moveScriptsToBottom = PrimeRequestContext.getCurrentInstance().getApplicationContext().getConfig().isMoveScriptsToBottom();

            if (!moveScriptsToBottom) {
                writer.write("(function(){const pfInit=() => {");

                for (int i = 0; i < scripts.size(); i++) {
                    writer.write(scripts.get(i));
                    writer.write(';');
                }

                writer.write("};if(window.$){$(function(){pfInit()})}");
                writer.write("else if(document.readyState==='complete'){pfInit()}");
                writer.write("else{document.addEventListener('DOMContentLoaded', pfInit)}})();");
            }
            else {
                for (int i = 0; i < scripts.size(); i++) {
                    writer.write(scripts.get(i));
                    writer.write(';');
                }
            }

            writer.endElement("script");
        }
    }
}
