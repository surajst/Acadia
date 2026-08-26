package com.concept.common;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

/**
 * Renders a template to an HTML string.
 *
 * <p>Exists so a service can produce a document without depending on Thymeleaf.
 * The layering rule bans web technology from the application layer, and it is
 * right to: a report card is a thing the school gets, not a page the browser
 * renders, and the fact that we build it by filling in a template is an
 * implementation detail that should not reach the service.
 *
 * <p>Infrastructure, so it lives in {@code common} beside the other things
 * every slice may use.
 */
@Component
public class HtmlTemplateRenderer {

    private final SpringTemplateEngine templateEngine;

    public HtmlTemplateRenderer(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * @param templateName template to fill in, without extension
     * @param variables    values the template expects
     */
    public String render(String templateName, Map<String, Object> variables) {
        Context context = new Context();
        variables.forEach(context::setVariable);
        return templateEngine.process(templateName, context);
    }
}
