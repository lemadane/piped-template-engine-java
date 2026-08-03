package io.lemadane.piped.template.engine;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Main {
   Main() {
   }

   public static void main(String[] args) {
      final var engine = new TemplateEngine(
            Path.of("src/main/resources/pte-templates"));
      final var html = engine.render(
            "pages/products",
            Map.of(
                  "title", "Piped Template Engine",
                  "year", 2026,
                  "user", Map.of(
                        "profile",
                        Map.of(
                              "displayName", "Lemuel")),
                  "products",
                  List.of(
                        Map.of(
                              "name", "Rice",
                              "price", 120,
                              "available", true,
                              "tags", List.of("food", "staple")),
                        Map.of(
                              "name", "Coffee",
                              "price", 150,
                              "available", false,
                              "tags", List.of()),
                        Map.of(
                              "name", "Sugar",
                              "price", 90,
                              "available", true))));
      System.out.println(html);
   }
}