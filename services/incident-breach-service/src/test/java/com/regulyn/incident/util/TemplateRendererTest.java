package com.regulyn.incident.util;

import com.regulyn.incident.exception.TemplateVariableMissingException;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateRendererTest {

    @Test
    void extractPlaceholders_findsVariables() {
        Set<String> placeholders = TemplateRenderer.extractPlaceholders("Hello {{name}}, case {{case_id}}.");
        assertThat(placeholders).containsExactlyInAnyOrder("name", "case_id");
    }

    @Test
    void render_failsOnMissingVariable() {
        assertThatThrownBy(() -> TemplateRenderer.render("Hello {{name}}", Map.of()))
                .isInstanceOf(TemplateVariableMissingException.class);
    }

    @Test
    void render_replacesVariablesDeterministically() {
        String rendered = TemplateRenderer.render(
                "Hello {{name}}",
                Map.of("name", "Rajan")
        );

        assertThat(rendered).isEqualTo("Hello Rajan");
    }
}
