package com.clinic.backend.export;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.jsoup.helper.W3CDom;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Map;

/**
 * Rend un template Thymeleaf (bulletin / reçu / ordonnance) en PDF (P2.3).
 * <p>
 * Réutilise les vues d'impression existantes : le template est traité hors requête
 * web (contexte simple + variable {@code pdf=true} qui masque la barre d'outils),
 * puis jsoup normalise le HTML en XHTML strict attendu par openhtmltopdf.
 * <p>
 * openhtmltopdf (LGPL) est choisi plutôt qu'iText (AGPL) pour la compatibilité SaaS.
 */
@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final SpringTemplateEngine templateEngine;
    private final ApplicationContext applicationContext;

    /** Traite le template avec le modèle fourni, puis convertit le HTML obtenu en PDF. */
    public byte[] renderTemplate(String templateName, Map<String, Object> model) {
        Context ctx = new Context(Locale.FRENCH);
        ctx.setVariables(model);
        ctx.setVariable("pdf", true); // masque la toolbar dans les vues print
        // Hors requête web, le contexte SpEL n'a pas de résolveur de beans : on en
        // ajoute un (basé sur l'ApplicationContext) pour que les templates partagés
        // avec les vues web puissent résoudre les beans (ex. @paymentMethods).
        ctx.setVariable(
                ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(applicationContext, null));
        String html = templateEngine.process(templateName, ctx);
        return htmlToPdf(html);
    }

    /** Convertit une chaîne HTML en PDF (jsoup → XHTML → openhtmltopdf). */
    public byte[] htmlToPdf(String html) {
        org.jsoup.nodes.Document jsoupDoc = org.jsoup.Jsoup.parse(html);
        org.w3c.dom.Document w3cDoc = new W3CDom().fromJsoup(jsoupDoc);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(w3cDoc, "");
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Échec de génération du PDF : " + e.getMessage(), e);
        }
    }
}
